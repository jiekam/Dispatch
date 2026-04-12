package com.example.dispatchapp.utils

object UsernamePolicy {
    const val MIN_LENGTH = 3
    const val MAX_LENGTH = 30

    private val allowedPattern = Regex("^[\\p{L}\\p{N}._' -]+$")

    fun validate(username: String): String? {
        val value = username.trim()

        if (value.isEmpty()) return "Username tidak boleh kosong"
        if (value.length < MIN_LENGTH) return "Username minimal $MIN_LENGTH karakter"
        if (value.length > MAX_LENGTH) return "Username maksimal $MAX_LENGTH karakter"
        if (!allowedPattern.matches(value)) {
            return "Username hanya boleh huruf, angka, spasi, titik, underscore, apostrof, atau tanda hubung"
        }

        return null
    }
}
