package nz.co.katproductions.notificationhub

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class NotificationMessage(
    val id: String,
    val receivedAtMillis: Long,
    val title: String,
    val body: String
)

object NotificationMessageStore {
    private const val PREFS_NAME = "notificationhub_messages"
    private const val KEY_MESSAGES = "messages"

    fun addMessage(context: Context, title: String, body: String) {
        val message = NotificationMessage(
            id = UUID.randomUUID().toString(),
            receivedAtMillis = System.currentTimeMillis(),
            title = title,
            body = body
        )

        val messages = listOf(message) + getMessages(context)
        saveMessages(context, messages)
    }

    fun deleteMessage(context: Context, id: String) {
        val messages = getMessages(context).filterNot { it.id == id }
        saveMessages(context, messages)
    }

    fun clearMessages(context: Context) {
        saveMessages(context, emptyList())
    }

    fun getMessages(context: Context): List<NotificationMessage> {
        val raw = prefs(context).getString(KEY_MESSAGES, "[]") ?: "[]"
        val array = runCatching { JSONArray(raw) }.getOrDefault(JSONArray())
        return buildList {
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val id = item.optString("id").takeIf { it.isNotBlank() } ?: continue
                add(
                    NotificationMessage(
                        id = id,
                        receivedAtMillis = item.optLong("receivedAtMillis"),
                        title = item.optString("title"),
                        body = item.optString("body")
                    )
                )
            }
        }.sortedByDescending { it.receivedAtMillis }
    }

    fun registerChangeListener(
        context: Context,
        listener: SharedPreferences.OnSharedPreferenceChangeListener
    ) {
        prefs(context).registerOnSharedPreferenceChangeListener(listener)
    }

    fun unregisterChangeListener(
        context: Context,
        listener: SharedPreferences.OnSharedPreferenceChangeListener
    ) {
        prefs(context).unregisterOnSharedPreferenceChangeListener(listener)
    }

    private fun saveMessages(context: Context, messages: List<NotificationMessage>) {
        val array = JSONArray()
        messages.forEach { message ->
            array.put(
                JSONObject()
                    .put("id", message.id)
                    .put("receivedAtMillis", message.receivedAtMillis)
                    .put("title", message.title)
                    .put("body", message.body)
            )
        }

        prefs(context)
            .edit()
            .putString(KEY_MESSAGES, array.toString())
            .apply()
    }

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
