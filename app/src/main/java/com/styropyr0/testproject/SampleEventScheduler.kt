package com.styropyr0.testproject

import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.Intent
import android.content.SharedPreferences
import android.util.Log
import com.styropyr0.kairos.Kairos
import com.styropyr0.kairos.KairosReceiver
import com.styropyr0.kairos.KairosTimeSlot
import com.styropyr0.kairos.interfaces.KairosEvent

/**
 * SampleEventScheduler demonstrates the recommended way to integrate Kairos
 * within a feature module.
 *
 * This class acts as a feature-level abstraction over the Kairos engine.
 * It encapsulates scheduling logic specific to a single event type and
 * prevents business logic from directly interacting with Kairos APIs.
 *
 * Each feature should provide its own:
 * - Event implementation (implements [com.styropyr0.kairos.interfaces.KairosEvent])
 * - Receiver (extends [com.styropyr0.kairos.KairosReceiver])
 * - Scheduler wrapper (like this class)
 *
 * This pattern preserves modularity and separation of concerns.
 */
class SampleEventScheduler(private val context: Context) {

    /**
     * Receiver responsible for handling execution of this event type.
     */
    private val receiver = SampleBroadcastReceiver::class.java

    /**
     * Schedules a new [SampleEvent].
     *
     * This delegates scheduling to the Kairos engine while keeping
     * domain-specific logic isolated within this feature module.
     */
    fun addToSchedule(event: SampleEvent) {
        Kairos.createMilestoneEvent(
            context,
            event,
            receiver
        )
    }

    /**
     * Synchronizes scheduled events with persisted state.
     *
     * Recommended to call during application start or feature initialization.
     * Performs cleanup of expired entries before restoring valid events.
     */
    fun syncSchedule() {
        defragmentSchedule()
        Kairos.rescheduleCachedEvents(
            context,
            receiver,
            SampleEvent.KEY
        )
    }

    /**
     * Removes expired events whose execution time has already passed.
     *
     * Helps maintain clean persisted state.
     */
    fun defragmentSchedule() {
        Kairos.defragment(context, SampleEvent.KEY, receiver)
    }

    /**
     * Cancels a specific scheduled event instance.
     *
     * @param uniqueId Logical identifier of the event
     * @param slotId Specific execution instance identifier
     */
    fun remove(uniqueId: String, slotId: String) {
        Kairos.cleanUpMilestoneEvent(
            context,
            uniqueId,
            slotId,
            SampleEvent.KEY,
            receiver
        )
    }

    /**
     * Clears all scheduled events of this feature type.
     *
     * If [uniqueId] is `"*"`, all events under this type are removed.
     */
    fun clear(uniqueId: String = "*") {
        Kairos.clearAllScheduledEvents(
            context,
            SampleEvent.KEY,
            receiver,
            uniqueId
        )
    }

    /**
     * Updates an existing event by removing the previous instance
     * and scheduling the new one.
     */
    fun update(oldEvent: SampleEvent, newEvent: SampleEvent) {
        Kairos.cleanUpMilestoneEvent(
            context,
            oldEvent.uniqueId,
            oldEvent.slotId,
            SampleEvent.KEY,
            receiver
        )
        addToSchedule(newEvent)
    }
}

/**
 * SampleEvent represents a feature-level event definition.
 *
 * Every feature event must implement [com.styropyr0.kairos.interfaces.KairosEvent] and provide:
 * - A unique identifier
 * - A slot identifier
 * - A logical grouping type
 * - A time slot indicating execution time
 *
 * The [type] constant ensures events of this feature are grouped
 * separately within Kairos persistence.
 */
data class SampleEvent(val id: String, override val timeSlot: KairosTimeSlot) : KairosEvent {
    override val uniqueId: String = id
    override val slotId: String = id
    override val type: String = KEY

    companion object {
        /**
         * Logical grouping key for this event type.
         * Must be unique across different feature modules.
         */
        const val KEY = "SAMPLE_EVENT"
    }
}

/**
 * SampleBroadcastReceiver handles execution when Kairos triggers the event.
 *
 * This is where feature-specific logic should be implemented.
 * The receiver should remain lightweight and delegate heavy work
 * to appropriate repositories or managers.
 */
class SampleBroadcastReceiver : KairosReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        val uniqueId = intent?.getStringExtra("UNIQUE_ID")
        val slotId = intent?.getStringExtra("SLOT_ID")
        val sharedPreferences: SharedPreferences =
            context?.getSharedPreferences("testProject", MODE_PRIVATE) ?: return
        val editor: SharedPreferences.Editor = sharedPreferences.edit()

        Log.d("SampleEventScheduler", "Event $uniqueId has been fired.")

        editor.putBoolean("isScheduled", false).apply()
    }
}
