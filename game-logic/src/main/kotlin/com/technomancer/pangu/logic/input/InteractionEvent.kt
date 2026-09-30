package com.technomancer.pangu.logic.input

import com.technomancer.pangu.logic.math.Vec3

/** Ids the app puts on interactive entities so gesture events can say what was touched. */
object TargetId {
    const val NONE = 0
    const val PAD_HANDLE = 1
    const val CARE_ORB = 2
    const val PET_BODY = 3
    const val PET_TIP = 4

    /** Snacks on the tray use FOOD_FIRST + index. */
    const val FOOD_FIRST = 10
    fun food(index: Int) = FOOD_FIRST + index
    fun foodIndex(target: Int) = if (target >= FOOD_FIRST) target - FOOD_FIRST else -1
}

enum class InteractionType {
    Tap,
    DragStart,
    DragMove,
    DragEnd,

    /** Panel buttons. */
    UiPrimary,
    UiSecondary,
    UiRecenter,

    /** A care-pie bubble; the target field holds the action index. */
    UiPie,
}

/** One input event. [delta] is the drag movement since the previous event, in Stage space. */
class InteractionEvent {
    var type: InteractionType = InteractionType.Tap
    var target: Int = TargetId.NONE
    var delta: Vec3 = Vec3.ZERO
}

/**
 * Fixed-size queue of events, filled by gesture callbacks and drained once per frame by the logic.
 * Events are recycled; when the queue is full the oldest event is dropped.
 */
class InteractionQueue(capacity: Int = 64) {
    private val events = Array(capacity) { InteractionEvent() }
    private var head = 0
    var size = 0
        private set

    fun push(type: InteractionType, target: Int = TargetId.NONE, delta: Vec3 = Vec3.ZERO) {
        if (size == events.size) {
            head = (head + 1) % events.size
            size--
        }
        val e = events[(head + size) % events.size]
        e.type = type
        e.target = target
        e.delta = delta
        size++
    }

    /** Calls [block] for each queued event in order, then empties the queue. */
    inline fun drain(block: (InteractionEvent) -> Unit) {
        while (size > 0) block(pop())
    }

    fun pop(): InteractionEvent {
        check(size > 0) { "queue is empty" }
        val e = events[head]
        head = (head + 1) % events.size
        size--
        return e
    }
}
