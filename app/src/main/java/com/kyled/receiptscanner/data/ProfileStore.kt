package com.kyled.receiptscanner.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The claimant's name, kept on the device. The full name goes in the form's "Name" box; the first
 * name is the default for each receipt's "Names" column.
 */
class ProfileStore(context: Context) {
    private val prefs = context.getSharedPreferences("profile", Context.MODE_PRIVATE)

    private val _fullName = MutableStateFlow(prefs.getString(KEY_FULL_NAME, null).orEmpty())
    val fullName: StateFlow<String> = _fullName.asStateFlow()

    fun setFullName(value: String) {
        val name = value.trim()
        prefs.edit().putString(KEY_FULL_NAME, name).apply()
        _fullName.value = name
    }

    private companion object {
        const val KEY_FULL_NAME = "full_name"
    }
}

/** "Pedro Dias Lobo" -> "Pedro". */
fun String.firstName(): String = trim().substringBefore(' ')
