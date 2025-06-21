package com.tored.bridgelauncher.services.contacts

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.ContactsContract
import android.util.Log
import androidx.core.content.ContextCompat
import com.tored.bridgelauncher.api2.server.ContactInfoSerializable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import androidx.core.net.toUri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ContactInfo(private val context: Context) {
    private val coroutineScope = CoroutineScope(Dispatchers.IO)

    private val _contacts = MutableStateFlow<List<ContactInfoSerializable>>(emptyList());
    val contacts = _contacts.asStateFlow()

    private val _initialLoadingFinished = MutableStateFlow(false)
    val initialLoadingFinished = _initialLoadingFinished.asStateFlow()

    fun startup() {
        if (!hasReadContactsPermission()) {
            Log.w("ContactInfo", "No READ_CONTACTS permission; skipping contacts load")
            _initialLoadingFinished.value = true
            return
        }
        coroutineScope.launch {
            loadContacts()
            _initialLoadingFinished.value = true
        }
    }

    private fun loadContacts() {
        val contactList = mutableListOf<ContactInfoSerializable>()
        val cr = context.contentResolver
        val cursor = cr.query(
            ContactsContract.Contacts.CONTENT_URI,
            null, null, null, null
        )

        cursor?.use {
            while (it.moveToNext()) {
                val id = it.getString(it.getColumnIndexOrThrow(ContactsContract.Contacts._ID)) ?: ""
                var telegramUsername: String? = null

                val imCursor = cr.query(
                    ContactsContract.Data.CONTENT_URI,
                    arrayOf(
                        ContactsContract.CommonDataKinds.Im.DATA,
                        ContactsContract.CommonDataKinds.Im.TYPE,
                        ContactsContract.CommonDataKinds.Im.LABEL
                    ),
                    "${ContactsContract.Data.CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ?",
                    arrayOf(id, ContactsContract.CommonDataKinds.Im.CONTENT_ITEM_TYPE),
                    null
                )
                imCursor?.use { ic ->
                    while (ic.moveToNext()) {
                        val imType = ic.getInt(ic.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Im.TYPE))
                        val imLabel = ic.getString(ic.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Im.LABEL))
                        val imData = ic.getString(ic.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Im.DATA))
                        // Puedes poner la label como "Telegram" al guardar o buscar por label si ya existe.
                        if ((imType == ContactsContract.CommonDataKinds.Im.TYPE_CUSTOM && imLabel?.equals("Telegram", ignoreCase = true) == true)
                            || imLabel?.contains("telegram", ignoreCase = true) == true) {
                            telegramUsername = imData
                            break
                        }
                    }
                }
                val name = it.getString(it.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME)) ?: ""
                val hasPhone = it.getInt(it.getColumnIndexOrThrow(ContactsContract.Contacts.HAS_PHONE_NUMBER)) > 0
                val photoUri = it.getString(it.getColumnIndexOrThrow(ContactsContract.Contacts.PHOTO_URI)) ?: ""
                val starred = it.getInt(it.getColumnIndexOrThrow(ContactsContract.Contacts.STARRED)) == 1
                val photoBase64 = uriToBase64(photoUri)

                val phoneNumbersSet = mutableSetOf<String>()
                if (hasPhone) {
                    val pCursor = cr.query(
                        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                        null,
                        "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
                        arrayOf(id), null
                    )
                    pCursor?.use { pc ->
                        while (pc.moveToNext()) {
                            val phone = pc.getString(pc.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER))
                            val cleaned = normalizeNumber(phone)
                            phoneNumbersSet.add(cleaned)
                        }
                    }
                }
                val phoneNumbers = phoneNumbersSet.toList()

                try {
                    contactList.add(ContactInfoSerializable(id, name, phoneNumbers, photoBase64, telegramUsername, starred))
                } catch (e: Exception) {
                    Log.e("ContactInfo", "Error adding contact: $id $name", e)
                }
            }
        }

        _contacts.value = contactList.sortedBy { it.name.lowercase() }
    }

    fun callContact(phoneNumber: String) {
        val cleanedNumber = phoneNumber.replace("\\s".toRegex(), "")
        val dialIntent = Intent(Intent.ACTION_DIAL, "tel:$cleanedNumber".toUri()).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(dialIntent)
    }

    fun messageContact(phoneNumber: String) {
        val cleanedNumber = phoneNumber.replace("\\s".toRegex(), "")
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = "sms:$cleanedNumber".toUri()
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    fun messageViaApp(
        phoneNumber: String,
        app: String,
        telegramUsername: String? = null,
        packageName: String? = null
    ) {
        val resolvedApp = MessagingApp.fromString(app)
        if (resolvedApp != null) {
            messageViaApp(phoneNumber, resolvedApp, telegramUsername, packageName)
        } else {
            // Puedes lanzar una excepción o hacer logging aquí
            throw IllegalArgumentException("Unknown MessagingApp: $app")
        }
    }

    fun messageViaApp(
        phoneNumber: String,
        app: MessagingApp,
        telegramUsername: String? = null,
        packageName: String? = null
    ) {
        val cleanedNumber = phoneNumber.replace("\\s".toRegex(), "")
        when (app) {
            MessagingApp.WHATSAPP -> {
                val uri = "https://wa.me/$cleanedNumber".toUri()
                val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                    val pkg = packageName ?: "com.whatsapp"
                    setPackage(pkg)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            }
            MessagingApp.TELEGRAM -> {
                if (telegramUsername != null && telegramUsername.isNotBlank()) {
                    val uri = "https://t.me/$telegramUsername".toUri()
                    val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                        val pkg = packageName ?: "org.telegram.messenger"
                        setPackage(pkg)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                } else {
                    // Fallback: abrir por número usando tg://resolve?phone=
                    val cleanedNumber = phoneNumber
                        .replace("\\s".toRegex(), "")
                        .replace("[^\\d+]".toRegex(), "") // Elimina cualquier carácter no numérico salvo '+'
                    val uri = "tg://resolve?phone=$cleanedNumber".toUri()
                    Intent(Intent.ACTION_VIEW, uri).apply {
                        val pkg = packageName ?: "org.telegram.messenger"
                        setPackage(pkg)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                }
            }
            MessagingApp.SIGNAL -> {
                val intent = Intent(Intent.ACTION_SENDTO).apply {
                    data = "smsto:$cleanedNumber".toUri()
                    val pkg = packageName ?: "org.thoughtcrime.securesms"
                    setPackage(pkg)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            }
            MessagingApp.SMS -> messageContact(phoneNumber)
        }
    }

    private fun uriToBase64(uriString: String?): String? {
        if (uriString.isNullOrBlank()) return null
        return try {
            val inputStream = context.contentResolver.openInputStream(uriString.toUri())
            val bytes = inputStream?.readBytes()
            inputStream?.close()
            if (bytes != null) android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP) else null
        } catch (e: Exception) {
            null
        }
    }

    private fun hasReadContactsPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun cleanNumber(number: String): String {
        // Removes spaces, dashes, parentheses, and dots
        return number.replace("[\\s\\-().]".toRegex(), "")
    }

    private fun normalizeNumber(number: String): String {
        val cleaned = cleanNumber(number)
        return when {
            cleaned.startsWith("+") -> cleaned
            cleaned.startsWith("00") -> "+" + cleaned.substring(2)
            else -> cleaned // If no international prefix, leave as is (you may want to set a default country)
        }
    }
}

enum class MessagingApp {
    WHATSAPP, TELEGRAM, SIGNAL, SMS;

    companion object {
        fun fromString(value: String): MessagingApp? {
            return values().firstOrNull { it.name.equals(value, ignoreCase = true) }
        }
    }
}