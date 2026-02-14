package com.styropyr0.kairos

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.Application
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.util.Log
import com.styropyr0.kairos.interfaces.KairosEvent
import java.util.Calendar

/**
 * Core engine for scheduling and managing time-based events with precision.
 *
 * Kairos leverages [AlarmManager] to trigger exact alarms using
 * `setExactAndAllowWhileIdle`, ensuring reliable execution even under
 * Doze mode. This approach is significantly more power-efficient than
 * running continuous timers or polling loops.
 *
 * The engine provides APIs to create, restore, and cancel [KairosEvent]s,
 * while persisting scheduled state through cache storage to maintain
 * execution integrity across app restarts and device reboots.
 *
 * Requirements:
 * - On Android 12+ (API 31+), the app must hold the
 *   `SCHEDULE_EXACT_ALARM` permission in manifest, or
 * - The app must be excluded from battery optimization.
 *
 * At least one of the above privileges shall be granted.
 * Otherwise, system will crash the process.
 *
 * Designed for systems that must act at the right moment.
 *
 * @author Saurav Sajeev
 */

object Kairos {
    private const val DATE_TIME_FORMAT = "dd-MM-yyyy HH:mm:ss"

    /**
     * Initializes the Kairos engine.
     *
     * This must be invoked once at the application level
     * (typically inside `Application.onCreate()`) before
     * using any other Kairos APIs.
     *
     * @param application The application context.
     */
    fun init(application: Application) {
        KairosCacheHelper.init(application)
    }

    /**
     * Schedules a new time-based event for execution at its designated moment.
     *
     * Once registered, the event will be triggered by the provided
     * [broadcastReceiver] when its scheduled time is reached.
     *
     * Note: Reliable execution requires appropriate exact alarm capability
     * and exemption from battery optimization where applicable.
     *
     * @param context The application context.
     * @param event The [KairosEvent] to schedule.
     * @param broadcastReceiver The receiver class that will handle the event trigger.
     *
     * @throws IllegalStateException
     * @throws UnprivilegedActionByKairos
     */
    @SuppressLint("MissingPermission")
    fun createMilestoneEvent(context: Context, event: KairosEvent, broadcastReceiver: Class<out KairosReceiver>) {
        if (!KairosCacheHelper.isInit())
            throw IllegalStateException(Constant.KAIROS_INIT_FAILED_EXCEPT)

        if (!hasPowerExemption(context)) throw UnprivilegedActionByKairos()

        val uniqueId = event.uniqueId
        val slotId = event.slotId

        val operation = PendingIntent.getBroadcast(
            context,
            (uniqueId + slotId).hashCode(),
            Intent(context, broadcastReceiver)
                .setAction(Constant.SCHEDULED_TIMER)
                .putExtra("UNIQUE_ID", uniqueId)
                .putExtra("SLOT_ID", slotId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val eventType = event.type
        val formattedTime = event.timeSlot.startTime.toString(DATE_TIME_FORMAT)

        event.timeSlot.let {
            if (checkIfMilestoneExists(it.startTime.toString(), "$uniqueId==$slotId", eventType))
                return
            alarmManager?.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                it.startTime.timeInMillis,
                operation
            )
            addMilestoneToPrefs(formattedTime, "$uniqueId==$slotId", eventType)
            saveMilestoneType(eventType)
            Log.d("Kairos", "Milestone $uniqueId with slot $slotId scheduled for $formattedTime")
        }
    }

    /**
     * Cancels and removes a previously scheduled event.
     *
     * This call ensures that the specified event will no longer
     * be triggered and clears any associated scheduling state.
     *
     * @param context The application context.
     * @param uniqueId Unique identifier of the event.
     * @param slotId Identifier representing the specific execution slot.
     * @param type Logical grouping or category of the event.
     * @param receiver The [KairosReceiver] associated with this event.
     * @param log Whether removal activity should be logged.
     */
    @SuppressLint("NewApi")
    fun cleanUpMilestoneEvent(
        context: Context,
        uniqueId: String,
        slotId: String,
        type: String,
        receiver: Class<out KairosReceiver>,
        log: Boolean = true
    ) {
        PendingIntent.getBroadcast(
            context,
            (uniqueId + slotId).hashCode(),
            Intent(context, receiver)
                .setAction(Constant.SCHEDULED_TIMER)
                .putExtra("UNIQUE_ID", uniqueId)
                .putExtra("SLOT_ID", slotId),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        ).apply {
            if (this == null) return@apply
            cancel()
            val alarmManager = context.getSystemService(AlarmManager::class.java)
            alarmManager?.cancel(this)
        }
        removeScheduledTimer(uniqueId, slotId, type, log)
    }

    private fun logAllTimersByType(type: String) = with(KairosCacheHelper) {
        val timers = getPref<String>(Constant.SCHEDULED_TIMER + type)
        Log.d("Kairos-LOG_ALL", "Timers for $type: $timers")
    }

    /**
     * Restores previously scheduled events from persisted state.
     *
     * This should be invoked after a device reboot or process restart
     * to ensure that any future-dated events are re-registered for execution.
     *
     * @param context The application context.
     * @param broadcastReceiver The [KairosReceiver] that will handle event triggers.
     * @param type Logical grouping or category of the events to restore.
     */
    @SuppressLint("MissingPermission")
    fun rescheduleCachedEvents(context: Context, broadcastReceiver: Class<out KairosReceiver>, type: String) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        if (!KairosCacheHelper.isPrefExists(Constant.SCHEDULED_TIMER + type)) return
        val timers = KairosCacheHelper.getPref<String?>(Constant.SCHEDULED_TIMER + type) ?: return
        timers.split(",").forEach {
            try {
                if (it.trim().isEmpty()) return
                val slotId = it.split("==")[1]
                val uniqueId = it.split("==")[0]
                val time = it.split("==")[2].toCalendar(DATE_TIME_FORMAT)
                val operation = PendingIntent.getBroadcast(
                    context,
                    (uniqueId + slotId).hashCode(),
                    Intent(context, broadcastReceiver)
                        .setAction(Constant.SCHEDULED_TIMER)
                        .putExtra("UNIQUE_ID", uniqueId)
                        .putExtra("SLOT_ID", slotId),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                alarmManager?.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    time.timeInMillis,
                    operation
                )
            } catch(_: Exception) { }
        }
    }

    /**
     * Cleans up expired scheduled events.
     *
     * This removes any events whose execution time has already passed,
     * ensuring that only valid future events remain registered.
     *
     * Intended to be executed on a background thread.
     *
     * @param context The application context.
     * @param type Logical grouping or category of the events.
     * @param receiver The [KairosReceiver] associated with the events.
     */
    fun defragment(context: Context, type: String, receiver: Class<out KairosReceiver>) {
        val timers = KairosCacheHelper.getPref<String?>(Constant.SCHEDULED_TIMER + type) ?: return
        timers.split(",").forEach {
            try {
                if (it.trim().isEmpty()) return
                val slotId = it.split("==")[1]
                val uniqueId = it.split("==")[0]
                val time = it.split("==")[2].toCalendar(DATE_TIME_FORMAT)
                Calendar.getInstance().apply {
                    if (time.before(this)) {
                        Log.d("Kairos-DEFRAGMENT", "Milestone $uniqueId with slot $slotId has been removed.")
                        cleanUpMilestoneEvent(context, uniqueId, slotId, type, receiver, false)
                    }
                }
            } catch (_: Exception) { }
        }
    }

    private fun checkIfMilestoneExists(id: String, slotId: String, type: String) =
        with(KairosCacheHelper) {
            val timers = if (isPrefExists(Constant.SCHEDULED_TIMER + type)) getPref<String>(Constant.SCHEDULED_TIMER + type)
            else return false
            (timers ?: return false).split(",").any { it.contains(slotId) && it.contains(id) }
        }

    private fun addMilestoneToPrefs(time: String, id: String, type: String) {
        with(KairosCacheHelper) {
            val timers = if (isPrefExists(Constant.SCHEDULED_TIMER + type))
                "${getPref<String>(Constant.SCHEDULED_TIMER + type)}$id==$time,"
            else "$id==$time,"
            savePref(Constant.SCHEDULED_TIMER + type, timers)
        }
    }

    private fun saveMilestoneType(type: String) {
        with(KairosCacheHelper) {
            val storedTypes =
                if (isPrefExists(Constant.SCHEDULED_TIMER_TYPE)) getPref(Constant.SCHEDULED_TIMER_TYPE)
                else ""

            storedTypes.apply {
                val types = (this ?: return).split(",").toMutableList()
                if (!types.contains(type))
                    savePref(
                        Constant.SCHEDULED_TIMER_TYPE,
                        types.apply { add(type) }.joinToString(",")
                    )
            }
        }
    }

    /**
     * Clears scheduled events for the specified type.
     *
     * If a [uniqueId] is provided, only events associated with that
     * identifier will be removed. If [uniqueId] is `"*"`, all events
     * under the given type will be cleared.
     *
     * This is typically used when resetting application state,
     * such as during user logout or data invalidation.
     *
     * @param context The application context.
     * @param type Logical grouping or category of the events.
     * @param receiver The [KairosReceiver] associated with the events.
     * @param uniqueId Optional identifier used to target a specific event.
     *
     * @see cleanUpMilestoneEvent
     */
    fun clearAllScheduledEvents(context: Context, type: String, receiver: Class<out KairosReceiver>, uniqueId: String = "*") {
        with(KairosCacheHelper) {
            (Constant.SCHEDULED_TIMER + type).let { it1 ->
                val timers = getPref<String?>(it1)
                timers?.split(",")?.forEach { timer ->
                    try {
                        if (timer.trim().isEmpty()) return
                        val slotId = timer.split("==")[1]
                        val uid = timer.split("==")[0]
                        if (uniqueId == uid || uniqueId == "*")
                            cleanUpMilestoneEvent(context, uid, slotId, type, receiver, uniqueId != "*")
                        if (uniqueId == "*") Log.d(
                            "Kairos-CLEAR_ALL",
                            "Cleared from schedule: $timer"
                        )
                    } catch (_: Exception) {
                        Log.d("Kairos-CLEAR_ALL", "Error while clearing milestone $timer")
                    }
                }
            }
        }
    }

    /**
     * Removes a scheduled timer from the schedule.
     * @param uniqueId Unique identifier for the event.
     * @param slotId Unique identifier for the slot.
     */
    private fun removeScheduledTimer(uniqueId: String, slotId: String, type: String, log: Boolean) {
        with(KairosCacheHelper) {
            if (!isPrefExists(Constant.SCHEDULED_TIMER + type)) return
            val timers = (getPref<String>(Constant.SCHEDULED_TIMER + type) ?: return).split(",").toMutableList()
            val res = timers.removeIf {
                it.split("==").let { part ->
                    part[0] == uniqueId && (part[1] == slotId || slotId == "*")
                }
            }
            if (log && res) Log.d("Kairos-REMOVED", "Milestone $uniqueId with slot $slotId has been removed.")
            savePref(Constant.SCHEDULED_TIMER + type, timers.joinToString(","))
        }
    }

    /**
     * Checks whether the application is exempt from battery optimization.
     *
     * Returns `true` if the system is currently allowing the app to run
     * without background execution restrictions.
     *
     * @param context The application context.
     * @return `true` if battery optimization is being ignored for this app,
     * otherwise `false`.
     */
    fun hasPowerExemption(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }
}