package com.tored.bridgelauncher.services.googlesearch

import kotlin.String
import android.content.Context
import android.content.Intent
import android.net.Uri
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray

class GoogleSearch(private val context: Context) {
    private val client = OkHttpClient()

    fun search(query: String) {
        val uri = Uri.parse("https://www.google.com/search?q=${Uri.encode(query)}")

        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        // Try using Google app if available
        intent.setPackage("com.google.android.googlequicksearchbox")
        if (intent.resolveActivity(context.packageManager) == null) {
            intent.setPackage(null) // fallback to default browser
        }

        context.startActivity(intent)
    }

    fun getSuggestions(query: String): List<String> {
        val url = "https://suggestqueries.google.com/complete/search?client=firefox&q=${Uri.encode(query)}"
        val request = Request.Builder().url(url).build()

        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                val body = response.body?.string() ?: return emptyList()
                val json = JSONArray(body)
                val suggestionsArray = json.getJSONArray(1)

                List(suggestionsArray.length()) { i ->
                    suggestionsArray.getString(i)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
}