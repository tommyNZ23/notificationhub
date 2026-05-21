package nz.co.katproductions.notificationhub

import android.content.Context
import android.util.Log
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import kotlin.concurrent.thread

object NotificationHubRegistration {
    private const val TAG = "NotificationHubReg"
    private const val PREFS_NAME = "notificationhub_registration"
    private const val KEY_EMAIL = "email"

    private val http = OkHttpClient()

    private fun saveRegisteredEmail(context: Context, email: String) {
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_EMAIL, email.trim().lowercase())
            .apply()
    }

    fun registerDeviceWithBackend(context: Context, idToken: String, email: String, fcmToken: String) {
        val url = "${BuildConfig.API_BASE_URL}/register-device"
        val json = JSONObject()
            .put("idToken", idToken)
            .put("deviceToken", fcmToken)
            .toString()

        postRegistration(url, json, "register-device") { success ->
            if (success) {
                saveRegisteredEmail(context, email)
                Log.d(TAG, "Registered FCM token for $email")
            }
        }
    }

    fun updateDeviceTokenIfPossible(context: Context, fcmToken: String) {
        val email = context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_EMAIL, null)
            ?.takeIf { it.isNotBlank() }

        if (email == null) {
            Log.d(TAG, "No saved email; skipping background FCM token update")
            return
        }

        val url = "${BuildConfig.API_BASE_URL}/update-device-token"
        val json = JSONObject()
            .put("email", email)
            .put("deviceToken", fcmToken)
            .toString()

        postRegistration(url, json, "update-device-token")
    }

    private fun postRegistration(
        url: String,
        json: String,
        operation: String,
        onComplete: (Boolean) -> Unit = {}
    ) {
        val body = json.toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder()
            .url(url)
            .post(body)
            .addHeader("Content-Type", "application/json")
            .addHeader("x-notificationhub-secret", BuildConfig.NOTIFICATION_HUB_SHARED_SECRET)
            .build()

        thread {
            try {
                http.newCall(request).execute().use { resp ->
                    val respText = resp.body?.string()
                    Log.d(TAG, "$operation HTTP ${resp.code} body=$respText")
                    onComplete(resp.isSuccessful)
                }
            } catch (e: Exception) {
                Log.e(TAG, "$operation call failed", e)
                onComplete(false)
            }
        }
    }
}
