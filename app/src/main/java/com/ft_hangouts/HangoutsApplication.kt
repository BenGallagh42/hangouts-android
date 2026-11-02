package com.ft_hangouts

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import java.text.DateFormat
import java.util.Date

// Tracks when the WHOLE app (not just one screen) leaves/returns to the foreground,
// so we can save + show the "last backgrounded" timestamp the app shows on return.
class HangoutsApplication : Application(), Application.ActivityLifecycleCallbacks {

    companion object {
        private const val PREFS_NAME = "ft_hangouts_prefs"
        private const val KEY_LAST_BACKGROUNDED = "last_backgrounded"
    }

    private var startedActivityCount = 0
    private var wasRotating = false

    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(this)
    }

    // Fires for EVERY activity in the app, not just one — that's what lets us tell
    // "user switched screens inside our app" apart from "the app left the foreground."
    override fun onActivityStarted(activity: Activity) {
        val cameBackFromBackground = startedActivityCount == 0 && !wasRotating
        startedActivityCount++
        if (cameBackFromBackground) {
            showLastBackgroundedToast(activity)
        }
    }

    override fun onActivityStopped(activity: Activity) {
        startedActivityCount--
        wasRotating = activity.isChangingConfigurations
        if (startedActivityCount == 0 && !wasRotating) {
            saveTimestamp(activity)
        }
    }

    private fun saveTimestamp(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putLong(KEY_LAST_BACKGROUNDED, System.currentTimeMillis()).apply()
    }

    private fun showLastBackgroundedToast(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val timestamp = prefs.getLong(KEY_LAST_BACKGROUNDED, -1L)
        if (timestamp == -1L) return // app has never been backgrounded yet

        val formatted = DateFormat.getDateTimeInstance().format(Date(timestamp))
        Toast.makeText(
            context,
            context.getString(R.string.toast_last_backgrounded, formatted),
            Toast.LENGTH_LONG
        ).show()
    }

    // Required by the interface; unused here.
    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    override fun onActivityResumed(activity: Activity) {}
    override fun onActivityPaused(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {}
}
