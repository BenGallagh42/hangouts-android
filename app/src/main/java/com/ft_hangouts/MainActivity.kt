package com.ft_hangouts

import android.Manifest
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ListView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.ft_hangouts.data.AppDbHelper

// Homepage: every contact in one list, each row showing their latest message
// preview and unread count when there is a conversation.
class MainActivity : BaseActivity() {

    private lateinit var dbHelper: AppDbHelper
    private lateinit var adapter: ContactListAdapter

    // The homepage is the root of the app — nothing to go back to.
    override fun showsUpButton(): Boolean = false

    // Guards against a fast double-tap launching two copies of the next screen before
    // the first startActivity() transition steals focus. Reset in onResume().
    private var isNavigating = false

    // Result ignored on purpose: if the user declines, the rest of the app still
    // works, only incoming SMS won't be picked up.
    private val requestReceiveSms = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    // Fired by SmsReceiver so a new message (or a contact auto-created from an
    // unknown number) shows up in the list right away.
    private val messageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            refreshContactList()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        dbHelper = AppDbHelper.getInstance(this)
        adapter = ContactListAdapter(this, emptyList())

        val listView = findViewById<ListView>(R.id.list_contacts)
        listView.adapter = adapter

        // Tapping the row opens the conversation — the app is message-first.
        listView.setOnItemClickListener { _, _, position, _ ->
            if (isNavigating) return@setOnItemClickListener
            isNavigating = true
            startActivity(
                Intent(this, ConversationActivity::class.java)
                    .putExtra(ConversationActivity.EXTRA_CONTACT_ID, adapter.getItem(position).contact.id)
            )
        }

        // Tapping the avatar opens that contact's profile instead.
        adapter.onAvatarClick = { contactId ->
            if (!isNavigating) {
                isNavigating = true
                startActivity(
                    Intent(this, ContactDetailActivity::class.java)
                        .putExtra(ContactDetailActivity.EXTRA_CONTACT_ID, contactId)
                )
            }
        }

        findViewById<FloatingActionButton>(R.id.fab_add_contact).setOnClickListener {
            if (isNavigating) return@setOnClickListener
            isNavigating = true
            startActivity(Intent(this, AddEditContactActivity::class.java))
        }

        // Asked once at startup: without it SmsReceiver never fires, so incoming
        // messages would silently never arrive. The savedInstanceState guard stops it
        // re-prompting on every rotation while the permission is denied.
        if (savedInstanceState == null &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestReceiveSms.launch(Manifest.permission.RECEIVE_SMS)
        }
    }

    // Runs every time this screen becomes visible again — including when we return
    // here from another screen — so the list stays current.
    override fun onResume() {
        super.onResume()
        isNavigating = false
        refreshContactList()

        ContextCompat.registerReceiver(
            this,
            messageReceiver,
            IntentFilter(SmsReceiver.ACTION_MESSAGE_RECEIVED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onPause() {
        super.onPause()
        runCatching { unregisterReceiver(messageReceiver) }
    }

    private fun refreshContactList() {
        val summaries = dbHelper.getContactSummaries()
        adapter.updateSummaries(summaries)

        val emptyView = findViewById<TextView>(R.id.text_empty_state)
        emptyView.visibility = if (summaries.isEmpty()) View.VISIBLE else View.GONE
    }

    // Inflates the "⋮" overflow menu (currently: change header color).
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.action_change_header_color) {
            showHeaderColorPicker()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun showHeaderColorPicker() {
        val names = resources.getStringArray(R.array.header_color_names)
        AlertDialog.Builder(this)
            .setTitle(R.string.action_change_header_color)
            .setItems(names) { _, which ->
                HeaderColorManager.setColor(this, HeaderColorManager.colorValues[which])
                HeaderColorManager.applyTo(this)
            }
            .show()
    }
}
