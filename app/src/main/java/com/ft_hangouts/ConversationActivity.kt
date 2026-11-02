package com.ft_hangouts

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.telephony.SmsManager
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.ft_hangouts.data.AppDbHelper
import com.ft_hangouts.model.Message

// Real SMS conversation with one saved contact, backed by the local database.
class ConversationActivity : BaseActivity() {

    companion object {
        const val EXTRA_CONTACT_ID = "extra_contact_id"
    }

    private lateinit var dbHelper: AppDbHelper
    private lateinit var adapter: MessageListAdapter
    private var contactId: Long = -1L
    private var contactPhone: String = ""

    // Asks for SEND_SMS the first time the user actually sends something.
    private val requestSendSms = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) sendMessage() else toast(getString(R.string.error_sms_permission_required))
    }

    // SmsReceiver fires this when a real SMS arrives, so an open conversation
    // updates immediately instead of only on the next onResume().
    private val messageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.getLongExtra(SmsReceiver.EXTRA_CONTACT_ID, -1L) == contactId) {
                refreshMessages()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_conversation)

        dbHelper = AppDbHelper.getInstance(this)
        contactId = intent.getLongExtra(EXTRA_CONTACT_ID, -1L)

        adapter = MessageListAdapter(this, emptyList(), "")
        findViewById<ListView>(R.id.list_messages).adapter = adapter

        findViewById<Button>(R.id.button_send).setOnClickListener { onSendClicked() }
    }

    // Reloads every time this screen is shown, and guards against the contact having
    // been deleted from another screen while this one sat in the back stack.
    override fun onResume() {
        super.onResume()
        val contact = dbHelper.getContact(contactId)
        if (contact == null) {
            finish()
            return
        }
        contactPhone = contact.phoneNumber
        title = "${contact.firstName} ${contact.lastName}".trim()
            .ifBlank { getString(R.string.title_conversation) }
        adapter.contactName = contact.firstName
        refreshMessages()

        // Reading the conversation clears its unread badge.
        dbHelper.markConversationRead(contactId)

        ContextCompat.registerReceiver(
            this,
            messageReceiver,
            IntentFilter(SmsReceiver.ACTION_MESSAGE_RECEIVED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onPause() {
        super.onPause()
        // Paired with the registration above; unregistering twice would throw.
        runCatching { unregisterReceiver(messageReceiver) }
    }

    // A person icon in the header opens this contact's profile, so you can get to
    // edit/call/delete without going back to the list first.
    override fun onCreateOptionsMenu(menu: android.view.Menu): Boolean {
        menuInflater.inflate(R.menu.menu_conversation, menu)
        return true
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        if (item.itemId == R.id.action_contact_details) {
            startActivity(
                Intent(this, ContactDetailActivity::class.java)
                    .putExtra(ContactDetailActivity.EXTRA_CONTACT_ID, contactId)
            )
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun refreshMessages() {
        adapter.updateMessages(dbHelper.getMessages(contactId))
        // A message that arrives while we're looking at it counts as read.
        dbHelper.markConversationRead(contactId)
    }

    // Checks the permission first; the actual send happens in sendMessage().
    private fun onSendClicked() {
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) ==
                PackageManager.PERMISSION_GRANTED
        if (granted) sendMessage() else requestSendSms.launch(Manifest.permission.SEND_SMS)
    }

    private fun sendMessage() {
        val input = findViewById<EditText>(R.id.edit_message)
        val body = input.text.toString().trim()

        // Read the number straight from the database rather than the cached field:
        // a permission callback can fire before onResume() has populated it (e.g. if
        // the screen was rotated while the permission dialog was up).
        val phone = dbHelper.getContact(contactId)?.phoneNumber.orEmpty()
        if (body.isBlank() || phone.isBlank()) return

        if (!sendSms(phone, body)) {
            toast(getString(R.string.error_sms_send_failed))
            return
        }

        // Stored once the system has accepted the message for delivery. Note this means
        // "queued by the radio", not "confirmed delivered" — confirming delivery would
        // need a sentIntent/deliveryIntent, which this project doesn't require.
        dbHelper.insertMessage(
            Message(
                contactId = contactId,
                body = body,
                timestamp = System.currentTimeMillis(),
                incoming = false
            )
        )
        input.setText("")
        refreshMessages()
    }

    // Returns false instead of throwing if the device can't send (no SIM, bad number,
    // no telephony hardware) so the caller can show a message rather than crash.
    private fun sendSms(phoneNumber: String, body: String): Boolean {
        return try {
            val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            } ?: return false

            // An SMS longer than one part has to be split, or it gets truncated.
            val parts = smsManager.divideMessage(body)
            if (parts.size > 1) {
                smsManager.sendMultipartTextMessage(phoneNumber, null, parts, null, null)
            } else {
                smsManager.sendTextMessage(phoneNumber, null, body, null, null)
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun toast(text: String) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
    }
}
