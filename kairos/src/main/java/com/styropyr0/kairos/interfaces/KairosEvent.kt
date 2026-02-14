package com.styropyr0.kairos.interfaces

import com.styropyr0.kairos.KairosTimeSlot

/**
 * Interface for Kairos milestone events. Contains basic information about the event.
 *
 * @property uniqueId Unique identifier for the event.
 * @property slotId Unique identifier for the slot.
 * @property timeSlot The time slot associated with the event.
 *
 * @author Saurav Sajeev
 */
interface KairosEvent {
    val uniqueId: String
    val slotId: String
    val type: String
    val timeSlot: KairosTimeSlot
}