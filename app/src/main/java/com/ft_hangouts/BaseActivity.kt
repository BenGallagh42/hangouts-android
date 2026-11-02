package com.ft_hangouts

import android.util.TypedValue
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

// Common parent for every screen: keeps the header color consistent app-wide,
// puts a back arrow in the header of every screen except the homepage, and pushes
// content clear of the status bar + header bar (top), the navigation bar (bottom),
// and any display cutout (camera notch — left/right in landscape) — all otherwise
// get drawn over on newer Android versions that use edge-to-edge by default.
open class BaseActivity : AppCompatActivity() {

    // Every screen shows a back arrow except the homepage, which overrides this.
    protected open fun showsUpButton(): Boolean = true

    override fun setContentView(layoutResID: Int) {
        super.setContentView(layoutResID)

        val actionBarSize = TypedValue().also {
            theme.resolveAttribute(android.R.attr.actionBarSize, it, true)
        }.let { TypedValue.complexToDimensionPixelSize(it.data, resources.displayMetrics) }

        val content = findViewById<android.view.View>(android.R.id.content)
        ViewCompat.setOnApplyWindowInsetsListener(content) { view, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            view.setPadding(bars.left, bars.top + actionBarSize, bars.right, bars.bottom)
            insets
        }
    }

    // Makes the header's back arrow behave exactly like the system back gesture.
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressedDispatcher.onBackPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onResume() {
        super.onResume()
        HeaderColorManager.applyTo(this)
        // Set here rather than in setContentView(): the support ActionBar isn't
        // available yet at that point, so the arrow silently never appeared.
        supportActionBar?.setDisplayHomeAsUpEnabled(showsUpButton())
    }
}
