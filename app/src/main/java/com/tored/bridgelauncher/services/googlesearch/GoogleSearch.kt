package com.tored.bridgelauncher.services.googlesearch

import kotlin.String
import android.content.Context
import android.content.Intent
import android.net.Uri

class GoogleSearch(private val context: Context) {
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
}