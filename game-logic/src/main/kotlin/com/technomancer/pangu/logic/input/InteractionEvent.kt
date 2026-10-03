package com.technomancer.pangu.logic.input

import com.technomancer.pangu.logic.math.Vec3

/** Ids the app puts on interactive entities so gesture events can say what was touched. */
object TargetId {
    const val NONE = 0
    const val PAD_HANDLE = 1
    const val CARE_ORB = 2
    const val PET_BODY = 3
    const val PET_TIP = 4
    const val BALL = 5
    const val LAMP_BULB = 6
    const val BLANKET = 7
    const val EGG = 8
    const val CRANK = 9

    /** Crumbs use CRUMB_FIRST + index. */
    const val CRUMB_FIRST = 30
    fun crumb(index: Int) = CRUMB_FIRST + index
    fun crumbIndex(target: Int) = if (target in CRUMB_FIRST until CRUMB_FIRST + 10) target - CRUMB_FIRST else -1

    /** Snacks on the tray use FOOD_FIRST + index. */
    const val FOOD_FIRST = 10
    fun food(index: Int) = FOOD_FIRST + index
    fun foodIndex(target: Int) = if (target in FOOD_FIRST until FOOD_FIRST + 10) target - FOOD_FIRST else -1
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

    /** A studio swatch or shape; the target field holds [LookPick.encode]. */
    UiLook,

    /** A name wheel; the target field holds the wheel index 0..2. */
    UiWheel,

    /** The microphone permission answer; the target field is 1 when granted. */
    UiMic,

    /** Debug "Complete step" on a ritual panel in the Emulator. */
    UiAssist,

    /** The "?" on the needs panel: show the personality card again. */
    UiInfo,

    /** The ⚙ button: open Settings. */
    UiSettings,

    /** A language row or segment; the target field holds the Language ordinal. */

    /** The volume slider; the target field holds 0..100. */
    UiVolume,

    /** The Sound and Haptics switches; the target field is 1 for on. */
    UiSound,
    UiHaptics,
}

/** One input event. [delta] is the drag movement since the previous event, in Stage space; [timeNanos] when it happened. */
class InteractionEvent {
    var type: InteractionType = InteractionType.Tap
    var target: Int = TargetId.NONE
    var delta: Vec3 = Vec3.ZERO
    var timeNanos: Long = 0L

    /** True when a drag comes from a ray, gaze or mouse pointer rather than a hand or controller holding the object. */
    var viaRay: Boolean = false
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

    fun push(type: InteractionType, target: Int = TargetId.NONE, delta: Vec3 = Vec3.ZERO, timeNanos: Long = 0L, viaRay: Boolean = false) {
        if (size == events.size) {
            head = (head + 1) % events.size
            size--
        }
        val e = events[(head + size) % events.size]
        e.type = type
        e.target = target
        e.delta = delta
        e.timeNanos = timeNanos
        e.viaRay = viaRay
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

/** Packs a studio choice into an event target: which row (body, tip, shape) and which item. */
object LookPick {
    const val BODY = 0
    const val TIP = 1
    const val SHAPE = 2

    fun encode(row: Int, index: Int) = row * 16 + index
    fun row(target: Int) = target / 16
    fun index(target: Int) = target % 16
}
