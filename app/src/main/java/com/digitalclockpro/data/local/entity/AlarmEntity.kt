package com.digitalclockpro.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.digitalclockpro.domain.model.Alarm
import com.digitalclockpro.domain.model.DismissChallenge
import com.digitalclockpro.domain.model.VibrationPattern
import java.time.DayOfWeek

@Entity(tableName = "alarms")
data class AlarmEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val label: String,
    val hour: Int,
    val minute: Int,
    val enabled: Boolean,
    /** Bitmask: bit 0 = MONDAY … bit 6 = SUNDAY. */
    @ColumnInfo(name = "repeat_mask") val repeatMask: Int,
    val soundUri: String?,
    val soundTitle: String,
    val volumePercent: Int,
    val volumeRampSeconds: Int,
    val vibrationPattern: String,
    val snoozeMinutes: Int,
    val maxSnoozeCount: Int,
    val currentSnoozeCount: Int,
    val challengeType: String,
    val challengeDifficulty: String,
    val challengeAmount: Int,
    val skipNextOccurrence: Boolean,
    val createdAt: Long
)

fun AlarmEntity.toDomain(): Alarm = Alarm(
    id = id,
    label = label,
    hour = hour,
    minute = minute,
    enabled = enabled,
    repeatDays = repeatMask.toDays(),
    soundUri = soundUri,
    soundTitle = soundTitle,
    volumePercent = volumePercent,
    volumeRampSeconds = volumeRampSeconds,
    vibrationPattern = runCatching { VibrationPattern.valueOf(vibrationPattern) }
        .getOrDefault(VibrationPattern.PULSE),
    snoozeMinutes = snoozeMinutes,
    maxSnoozeCount = maxSnoozeCount,
    currentSnoozeCount = currentSnoozeCount,
    challenge = when (challengeType) {
        DismissChallenge.TYPE_MATH -> DismissChallenge.Math(
            difficulty = runCatching { DismissChallenge.Difficulty.valueOf(challengeDifficulty) }
                .getOrDefault(DismissChallenge.Difficulty.MEDIUM),
            problemCount = challengeAmount.coerceIn(1, 5)
        )
        DismissChallenge.TYPE_SHAKE -> DismissChallenge.Shake(challengeAmount.coerceIn(10, 50))
        DismissChallenge.TYPE_SEQUENCE -> DismissChallenge.Sequence(challengeAmount.coerceIn(4, 16))
        else -> DismissChallenge.None
    },
    skipNextOccurrence = skipNextOccurrence,
    createdAt = createdAt
)

fun Alarm.toEntity(): AlarmEntity = AlarmEntity(
    id = id,
    label = label,
    hour = hour,
    minute = minute,
    enabled = enabled,
    repeatMask = repeatDays.toMask(),
    soundUri = soundUri,
    soundTitle = soundTitle,
    volumePercent = volumePercent,
    volumeRampSeconds = volumeRampSeconds,
    vibrationPattern = vibrationPattern.name,
    snoozeMinutes = snoozeMinutes,
    maxSnoozeCount = maxSnoozeCount,
    currentSnoozeCount = currentSnoozeCount,
    challengeType = when (challenge) {
        is DismissChallenge.Math -> DismissChallenge.TYPE_MATH
        is DismissChallenge.Shake -> DismissChallenge.TYPE_SHAKE
        is DismissChallenge.Sequence -> DismissChallenge.TYPE_SEQUENCE
        DismissChallenge.None -> DismissChallenge.TYPE_NONE
    },
    challengeDifficulty = (challenge as? DismissChallenge.Math)?.difficulty?.name
        ?: DismissChallenge.Difficulty.MEDIUM.name,
    challengeAmount = when (val c = challenge) {
        is DismissChallenge.Math -> c.problemCount
        is DismissChallenge.Shake -> c.shakeCount
        is DismissChallenge.Sequence -> c.tiles
        DismissChallenge.None -> 0
    },
    skipNextOccurrence = skipNextOccurrence,
    createdAt = createdAt
)

fun Int.toDays(): Set<DayOfWeek> =
    DayOfWeek.entries.filter { (this shr (it.value - 1)) and 1 == 1 }.toSet()

fun Set<DayOfWeek>.toMask(): Int =
    fold(0) { acc, day -> acc or (1 shl (day.value - 1)) }
