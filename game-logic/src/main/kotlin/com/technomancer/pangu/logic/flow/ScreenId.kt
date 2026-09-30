package com.technomancer.pangu.logic.flow

/** Every screen of the game, C01 to C37. */
enum class ScreenId {
    C01, C02, C03, C04, C05, C06, C07, C08, C09, C10,
    C11, C12, C13, C14, C15, C16, C17, C18, C19, C20,
    C21, C22, C23, C24, C25, C26, C27, C28, C29, C30,
    C31, C32, C33, C34, C35, C36, C37;

    /** String-table prefix, e.g. "scr.C02". */
    val keyPrefix: String = "scr.$name"
}
