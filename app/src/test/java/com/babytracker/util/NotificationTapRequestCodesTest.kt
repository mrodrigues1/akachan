package com.babytracker.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class NotificationTapRequestCodesTest {

    @Test
    fun `every tap request code is unique`() {
        // The Compose compiler plugin injects a synthetic "$stable" int field into every class in
        // this module (unrelated to notifications); exclude anything not declared as a real const.
        val codes = NotificationTapRequestCodes::class.java.fields
            .filter { it.type == Int::class.javaPrimitiveType && !it.name.startsWith("$") }
            .map { it.getInt(null) }

        assertEquals(
            codes.size,
            codes.toSet().size,
            "Two notification tap targets share a request code: $codes. " +
                "Intent.filterEquals ignores extras, so PendingIntent.getActivity calls to " +
                "MainActivity with the same request code collapse into one shared system " +
                "record and silently rewrite each other's EXTRA_NAV_ROUTE (see #745).",
        )
    }
}
