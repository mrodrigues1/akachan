package com.babytracker.sharing.domain.model

@JvmInline
value class ShareCode(val value: String) {
    companion object {
        fun isValid(value: String): Boolean =
            value.length == 8 && value.all { it.isUpperCase() || it.isDigit() }
    }
}
