package com.example.dispatchapp.utils

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

object ContactIntentHelper {
    
    fun formatEmailUrl(email: String): String {
        return "mailto:$email"
    }
    
    fun formatWhatsAppUrl(phone: String): String {
        
        val cleanPhone = if (phone.startsWith("+")) {
            "+" + phone.substring(1).filter { it.isDigit() }
        } else {
            phone.filter { it.isDigit() }
        }
        return "https://wa.me/$cleanPhone"
    }
    
    fun formatDiscordUrl(value: String): String {
        return if (value.startsWith("http://") || value.startsWith("https://")) {
            value
        } else {
            "https://discord.gg/$value"
        }
    }
    
    fun launchContactIntent(context: Context, contactType: String, contactValue: String) {
        val intent = when (contactType) {
            "email" -> createEmailIntent(contactValue)
            "whatsapp" -> createWhatsAppIntent(contactValue)
            "discord" -> createDiscordIntent(contactValue)
            else -> null
        }
        
        intent?.let {
            try {
                context.startActivity(it)
            } catch (e: ActivityNotFoundException) {
                val appName = when (contactType) {
                    "email" -> "Email"
                    "whatsapp" -> "WhatsApp"
                    "discord" -> "Discord"
                    else -> "Aplikasi"
                }
                Toast.makeText(context, "Aplikasi $appName tidak tersedia", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun createEmailIntent(email: String): Intent {
        val uri = Uri.parse(formatEmailUrl(email))
        return Intent(Intent.ACTION_SENDTO, uri)
    }
    
    private fun createWhatsAppIntent(phone: String): Intent {
        val uri = Uri.parse(formatWhatsAppUrl(phone))
        return Intent(Intent.ACTION_VIEW, uri)
    }
    
    private fun createDiscordIntent(value: String): Intent {
        val uri = Uri.parse(formatDiscordUrl(value))
        return Intent(Intent.ACTION_VIEW, uri)
    }
}
