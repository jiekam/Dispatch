package com.example.dispatchapp

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.dispatchapp.models.Student
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns

@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        checkSessionLogic()
    }

    private fun checkSessionLogic() {
        lifecycleScope.launch(Dispatchers.IO) {
            if (!isNetworkAvailable()) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@SplashActivity, "Koneksi Internet Diperlukan", Toast.LENGTH_SHORT).show()
                }
                return@launch
            }

            try {
                SupabaseClient.client.auth.awaitInitialization()
                val session = SupabaseClient.client.auth.currentSessionOrNull()
                val prefs = UserPreferences(this@SplashActivity)

                if (session == null) {
                    prefs.clearUserDataKeepTheme()
                    navigateTo(RoleSelection::class.java)
                    return@launch
                }

                SupabaseClient.client.auth.retrieveUserForCurrentSession(updateSession = true)

                syncUserData(session.user?.id, session.user?.email, prefs)

                navigateTo(MainActivity::class.java)

            } catch (e: Exception) {
                Log.e("Splash", "Error detail: ${e.message}")
                withContext(Dispatchers.Main) {
                    SupabaseClient.client.auth.clearSession()
                    UserPreferences(this@SplashActivity).clearUserDataKeepTheme()
                    Toast.makeText(this@SplashActivity, "Sesi tidak valid", Toast.LENGTH_SHORT).show()
                    navigateTo(RoleSelection::class.java)
                }
            }
        }
    }

    private suspend fun syncUserData(uuid: String?, email: String?, prefs: UserPreferences) {
        if (uuid == null) return

        if (prefs.getCurrentUserId() != uuid) {
            prefs.clearUserDataKeepTheme()
        }

        prefs.saveCurrentUserId(uuid)
        email?.let { prefs.saveUserEmail(it) }

        try {
            // Sync Student Data
            val studentResponse = SupabaseClient.client.from("students")
                .select(columns = Columns.list("student_id, nis, kelas, jurusan, prodi, student_name")) {
                    filter { eq("id", uuid) }
                }.decodeSingleOrNull<Student>()

            if (studentResponse != null) {
                val studentIdStr = studentResponse.student_id?.toString() ?: ""
                if (studentIdStr.isNotEmpty()) prefs.saveStudentId(studentIdStr)
                studentResponse.nis?.toString()?.let { prefs.saveUserNis(it) }
                studentResponse.kelas?.let { prefs.saveUserKelas(it) }
                studentResponse.jurusan?.let { prefs.saveUserJurusan(it) }
                studentResponse.prodi?.let { prefs.saveUserProdi(it) }
                studentResponse.studentName?.let { prefs.saveStudentName(it) }
            } else {
                prefs.clearStudentData()
            }

            // Sync Profile Data
            val profileResponse = SupabaseClient.client.from("profiles")
                .select() {
                    filter { eq("id", uuid) }
                }.decodeSingleOrNull<com.example.dispatchapp.models.Profile>()

            if (profileResponse != null) {
                profileResponse.username?.let { prefs.saveUserName(it) }
                profileResponse.role?.let { prefs.saveUserRole(it) }
            } else {
                prefs.clearProfileData()
                email?.let { prefs.saveUserEmail(it) }
            }
        } catch (e: Exception) {
            Log.e("Splash", "Gagal sinkron data: ${e.localizedMessage}")
        }
    }

    private suspend fun navigateTo(target: Class<*>) {
        withContext(Dispatchers.Main) {
            startActivity(Intent(this@SplashActivity, target))
            finish()
        }
    }

    private fun isNetworkAvailable(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val nw = cm.activeNetwork ?: return false
        val actNw = cm.getNetworkCapabilities(nw) ?: return false
        return actNw.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                actNw.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                actNw.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }
}