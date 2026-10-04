package com.digitalclockpro.di

import android.content.Context
import android.os.Build
import android.os.UserManager
import android.util.Log
import androidx.room.Room
import com.digitalclockpro.data.local.AppDatabase
import com.digitalclockpro.data.local.dao.AlarmDao
import com.digitalclockpro.data.local.dao.CityDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    private const val TAG = "DatabaseModule"

    /**
     * The alarm database lives in **device-protected storage**.
     *
     * Why: [com.digitalclockpro.alarm.BootReceiver] and [com.digitalclockpro.alarm.AlarmReceiver]
     * are `directBootAware`, so they run after `LOCKED_BOOT_COMPLETED` — *before* the user has
     * entered their PIN on an FBE (File-Based Encryption) device. Credential-protected storage
     * (the default Room location) is still locked at that moment, so opening the database there
     * throws and every alarm silently fails to be rescheduled until the first unlock.
     *
     * Device-protected storage is readable immediately at boot, which is exactly what an alarm
     * clock needs. The trade-off is that this storage is not encrypted with the user credential,
     * so it must only hold non-sensitive data (alarm times and city names qualify).
     *
     * Existing installs are migrated once via [Context.moveDatabaseFrom]; the move is a no-op
     * when there is nothing to move.
     */
    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): AppDatabase {
        val storageContext = context.deviceProtectedStorageContextCompat()
        migrateToDeviceProtectedStorage(context, storageContext)

        return Room.databaseBuilder(storageContext, AppDatabase::class.java, AppDatabase.NAME)
            .fallbackToDestructiveMigrationOnDowngrade()
            // Alarm queries happen on a Direct-Boot broadcast with a ~10 s budget: let Room open
            // the file lazily but keep WAL so concurrent reads from the widget never block.
            .setJournalMode(androidx.room.RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
            .build()
    }

    @Provides fun alarmDao(db: AppDatabase): AlarmDao = db.alarmDao()
    @Provides fun cityDao(db: AppDatabase): CityDao = db.cityDao()

    /**
     * One-time move of a pre-existing credential-protected database into device-protected
     * storage. Safe to call on every start: once the file is gone the call does nothing.
     */
    private fun migrateToDeviceProtectedStorage(credentialContext: Context, deviceContext: Context) {
        if (credentialContext === deviceContext) return
        val legacy = credentialContext.getDatabasePath(AppDatabase.NAME)
        if (!legacy.exists()) return

        val moved = runCatching { deviceContext.moveDatabaseFrom(credentialContext, AppDatabase.NAME) }
            .getOrElse { error ->
                Log.e(TAG, "Failed to move ${AppDatabase.NAME} to device-protected storage", error)
                false
            }
        Log.i(TAG, "Database migration to device-protected storage: moved=$moved")
    }
}

/**
 * Device-protected storage context on every supported API level (minSdk 26 always has it, the
 * guard keeps the helper reusable and self-documenting).
 */
fun Context.deviceProtectedStorageContextCompat(): Context =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        createDeviceProtectedStorageContext() ?: this
    } else {
        this
    }

/**
 * True while the device has booted but the user has not yet entered their credential
 * (FBE Direct Boot window). Credential-protected storage is unavailable in this state.
 */
fun Context.isUserUnlockedCompat(): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        getSystemService(UserManager::class.java)?.isUserUnlocked ?: true
    } else {
        true
    }
