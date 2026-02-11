package com.example.vtsdaily.ui

import android.content.Context
import androidx.core.content.edit

object CallReturnStore {
    private const val PREFS = "vts_call_return"
    private const val KEY_ORIGIN = "origin"

    enum class Origin { SCHEDULE, CONTACTS }

    fun setOrigin(context: Context, origin: Origin) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit {
                putString(KEY_ORIGIN, origin.name)
            }
    }

    fun consumeOrigin(context: Context): Origin? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_ORIGIN, null) ?: return null

        // Clear immediately so it’s one-shot
        prefs.edit().remove(KEY_ORIGIN).apply()

        return runCatching { Origin.valueOf(raw) }.getOrNull()
    }
}
