package com.digitalclockpro.domain.usecase

import com.digitalclockpro.domain.model.Alarm
import com.digitalclockpro.domain.repository.AlarmRepository
import com.digitalclockpro.domain.scheduler.AlarmPlanner
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveAlarmsUseCase @Inject constructor(
    private val repository: AlarmRepository
) {
    operator fun invoke(): Flow<List<Alarm>> = repository.observeAlarms()
}

/** Saves an alarm and (re)schedules it atomically. */
class SaveAlarmUseCase @Inject constructor(
    private val repository: AlarmRepository,
    private val planner: AlarmPlanner
) {
    suspend operator fun invoke(alarm: Alarm): Long {
        val id = repository.upsert(alarm)
        val saved = alarm.copy(id = id)
        if (saved.enabled) planner.schedule(saved) else planner.cancel(id)
        planner.refreshNextAlarmIndicators()
        return id
    }
}

class ToggleAlarmUseCase @Inject constructor(
    private val repository: AlarmRepository,
    private val planner: AlarmPlanner
) {
    suspend operator fun invoke(id: Long, enabled: Boolean) {
        repository.setEnabled(id, enabled)
        val alarm = repository.getAlarm(id) ?: return
        if (enabled) planner.schedule(alarm) else planner.cancel(id)
        planner.refreshNextAlarmIndicators()
    }
}

class DeleteAlarmUseCase @Inject constructor(
    private val repository: AlarmRepository,
    private val planner: AlarmPlanner
) {
    suspend operator fun invoke(id: Long) {
        planner.cancel(id)
        repository.delete(id)
        planner.refreshNextAlarmIndicators()
    }
}

/** Re-arms every enabled alarm – used after BOOT_COMPLETED / TIME_SET / app update. */
class RescheduleAllAlarmsUseCase @Inject constructor(
    private val repository: AlarmRepository,
    private val planner: AlarmPlanner
) {
    suspend operator fun invoke() {
        repository.getEnabledAlarms().forEach { planner.schedule(it) }
        planner.refreshNextAlarmIndicators()
    }
}

class SnoozeAlarmUseCase @Inject constructor(
    private val repository: AlarmRepository,
    private val planner: AlarmPlanner
) {
    /** @return true when snoozed, false when the snooze budget is exhausted. */
    suspend operator fun invoke(id: Long): Boolean {
        val alarm = repository.getAlarm(id) ?: return false
        if (alarm.currentSnoozeCount >= alarm.maxSnoozeCount) return false
        val next = alarm.copy(currentSnoozeCount = alarm.currentSnoozeCount + 1)
        repository.upsert(next)
        planner.scheduleSnooze(next, alarm.snoozeMinutes)
        return true
    }
}

/** Called when an alarm is dismissed: resets snooze counter and re-arms repeating alarms. */
class DismissAlarmUseCase @Inject constructor(
    private val repository: AlarmRepository,
    private val planner: AlarmPlanner
) {
    suspend operator fun invoke(id: Long) {
        val alarm = repository.getAlarm(id) ?: return
        val reset = alarm.copy(currentSnoozeCount = 0, skipNextOccurrence = false)
        if (reset.isRepeating) {
            repository.upsert(reset)
            planner.schedule(reset)
        } else {
            repository.upsert(reset.copy(enabled = false))
            planner.cancel(id)
        }
        planner.refreshNextAlarmIndicators()
    }
}

class GetNextAlarmUseCase @Inject constructor(
    private val repository: AlarmRepository
) {
    suspend operator fun invoke(): Alarm? =
        repository.getEnabledAlarms().minByOrNull { it.nextTriggerAtMillis() }
}
