package com.ft_hangouts

import android.content.Context
import android.graphics.drawable.ColorDrawable
import androidx.appcompat.app.AppCompatActivity

// Reads/writes the user's chosen header (ActionBar) color, shared across every screen.
object HeaderColorManager {

    private const val PREFS_NAME = "ft_hangouts_prefs"
    private const val KEY_HEADER_COLOR = "header_color"

    // Selectable header colors; index matches the "header_color_names" string-array.
    val colorValues = intArrayOf(
        0xFF6750A4.toInt(), // Default
        0xFFD32F2F.toInt(), // Red
        0xFF388E3C.toInt(), // Green
        0xFF1976D2.toInt(), // Blue
        0xFFF57C00.toInt()  // Orange
    )

    fun getColor(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_HEADER_COLOR, colorValues[0])
    }

    fun setColor(context: Context, color: Int) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt(KEY_HEADER_COLOR, color).apply()
    }

    // Paints the saved color onto this activity's header bar.
    fun applyTo(activity: AppCompatActivity) {
        activity.supportActionBar?.setBackgroundDrawable(ColorDrawable(getColor(activity)))
    }
}
