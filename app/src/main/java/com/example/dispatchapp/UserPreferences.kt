package com.example.dispatchapp

import android.content.Context
import android.content.SharedPreferences

class UserPreferences(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE)

    fun saveUserRole(role: String) {
        prefs.edit().putString("user_role", role).apply()
    }
    fun saveUserEmail(email: String){
        prefs.edit().putString("user_email", email).apply()
    }

    fun saveUserName(name: String){
        prefs.edit().putString("user_name", name).apply()
    }

    fun saveOrgName(name: String) {
        prefs.edit().putString("org_name", name).apply()
    }

    fun saveAccountStatus(status: String) {
        prefs.edit().putString("account_status", status).apply()
    }

    fun getUserRole(): String? {
        return prefs.getString("user_role", null)
    }

    fun getUserEmail(): String? {
        return prefs.getString("user_email", null)
    }

    fun getUserName(): String? {
        return prefs.getString("user_name", null)
    }

    fun getOrgName(): String? {
        return prefs.getString("org_name", null)
    }

    fun getAccountStatus(): String? {
        return prefs.getString("account_status", null)
    }

    fun saveStudentId(studentId: String) {
        prefs.edit().putString("student_id", studentId).apply()
    }

    fun getStudentId(): String? {
        return prefs.getString("student_id", null)
    }

    fun saveUserAvatarUrl(url: String) { prefs.edit().putString("user_avatar", url).apply() }
    fun getUserAvatarUrl(): String? { return prefs.getString("user_avatar", null) }

    fun saveUserNis(nis: String) { prefs.edit().putString("user_nis", nis).apply() }
    fun getUserNis(): String? { return prefs.getString("user_nis", null) }

    fun saveUserKelas(kelas: String) { prefs.edit().putString("user_kelas", kelas).apply() }
    fun getUserKelas(): String? { return prefs.getString("user_kelas", null) }

    fun saveUserJurusan(jurusan: String) { prefs.edit().putString("user_jurusan", jurusan).apply() }
    fun getUserJurusan(): String? { return prefs.getString("user_jurusan", null) }

    fun saveUserProdi(prodi: String) { prefs.edit().putString("user_prodi", prodi).apply() }
    fun getUserProdi(): String? { return prefs.getString("user_prodi", null) }

    fun saveStudentName(name: String) { prefs.edit().putString("student_name", name).apply() }
    fun getStudentName(): String? { return prefs.getString("student_name", null) }

    fun saveDarkMode(enabled: Boolean) { prefs.edit().putBoolean("dark_mode", enabled).apply() }
    fun isDarkMode(): Boolean { return prefs.getBoolean("dark_mode", false) }

    fun saveCurrentUserId(userId: String) {
        prefs.edit().putString("current_user_id", userId).apply()
    }

    fun getCurrentUserId(): String? {
        return prefs.getString("current_user_id", null)
    }

    fun clearStudentData() {
        prefs.edit()
            .remove("student_id")
            .remove("user_nis")
            .remove("user_kelas")
            .remove("user_jurusan")
            .remove("user_prodi")
            .remove("student_name")
            .apply()
    }

    fun clearProfileData() {
        prefs.edit()
            .remove("user_role")
            .remove("user_email")
            .remove("user_name")
            .remove("user_avatar")
            .apply()
    }

    fun clearUserDataKeepTheme() {
        clearProfileData()
        clearStudentData()
        prefs.edit().remove("current_user_id").apply()
    }
    
    fun clearAll() {
        val darkMode = isDarkMode()
        prefs.edit().clear().apply()
        saveDarkMode(darkMode)
    }
}