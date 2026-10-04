package com.digitalclockpro.domain.scheduler

import com.digitalclockpro.domain.model.Alarm

/**
 * Domain-level abstraction over [android.app.AlarmManager] so the use-case layer
 * stays free of framework dependencies (implemented by AlarmScheduler in the data layer).
 */
interface AlarmPlanner {
    fun schedule(alarm: Alarm)
    fun scheduleSnooze(alarm: Alarm, minutes: Int)
    fun cancel(alarmId: Long)
    fun canScheduleExactAlarms(): Boolean
    /** Pushes "next alarm" text to every placed widget. */
    fun refreshNextAlarmIndicators()
}
