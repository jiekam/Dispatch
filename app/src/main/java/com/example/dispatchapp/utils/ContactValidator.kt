package com.example.dispatchapp.utils

object ContactValidator {
    sealed class ValidationResult {
        object Valid : ValidationResult()
        data class Invalid(val message: String) : ValidationResult()
    }

    fun validate(contactType: String, contactValue: String): ValidationResult {
        return when (contactType) {
            "email" -> validateEmail(contactValue)
            "whatsapp" -> validateWhatsApp(contactValue)
            "discord" -> validateDiscord(contactValue)
            else -> ValidationResult.Invalid("Tipe kontak tidak valid")
        }
    }

    private fun validateEmail(email: String): ValidationResult {
        if (!email.contains("@")) {
            return ValidationResult.Invalid("Format email tidak valid. Contoh: nama@domain.com")
        }
        
        val parts = email.split("@")
        if (parts.size != 2 || parts[0].isEmpty()) {
            return ValidationResult.Invalid("Format email tidak valid. Contoh: nama@domain.com")
        }
        
        val domain = parts[1]
        if (!domain.contains(".") || domain.endsWith(".") || domain.startsWith(".")) {
            return ValidationResult.Invalid("Format email tidak valid. Contoh: nama@domain.com")
        }
        
        return ValidationResult.Valid
    }

    private fun validateWhatsApp(phone: String): ValidationResult {
        val cleanPhone = phone.trim()
        
        if (cleanPhone.isEmpty()) {
            return ValidationResult.Invalid("Nomor WhatsApp tidak boleh kosong")
        }
        
        val hasPlus = cleanPhone.startsWith("+")
        val numberPart = if (hasPlus) cleanPhone.substring(1) else cleanPhone
        
        if (!numberPart.all { it.isDigit() }) {
            return ValidationResult.Invalid("Nomor WhatsApp harus berisi angka. Contoh: 628123456789")
        }
        
        if (numberPart.length < 10) {
            return ValidationResult.Invalid("Nomor WhatsApp terlalu pendek. Minimal 10 digit")
        }
        
        return ValidationResult.Valid
    }

    private fun validateDiscord(value: String): ValidationResult {
        if (value.trim().isEmpty()) {
            return ValidationResult.Invalid("Kontak Discord tidak boleh kosong")
        }
        
        return ValidationResult.Valid
    }
}
