package com.ft_hangouts

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.telephony.PhoneNumberUtils
import com.ft_hangouts.data.AppDbHelper
import com.ft_hangouts.model.Contact
import com.ft_hangouts.model.Message

// Listens for real incoming SMS. Declared in the manifest, so Android starts it even
// if the app isn't currently open.
class SmsReceiver : BroadcastReceiver() {

    companion object {
        // Sent to any open screen so it can refresh itself immediately.
        const val ACTION_MESSAGE_RECEIVED = "com.ft_hangouts.MESSAGE_RECEIVED"
        const val EXTRA_CONTACT_ID = "extra_contact_id"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        // A crash in a manifest receiver kills the whole app process, so nothing in
        // here is allowed to throw — a malformed SMS must never take the app down.
        try {
            handleSms(context, intent)
        } catch (e: Exception) {
            // Dropping one unreadable message is better than crashing.
        }
    }

    private fun handleSms(context: Context, intent: Intent) {
        val dbHelper = AppDbHelper.getInstance(context)

        // A long SMS arrives split into several parts, all from the same sender —
        // group by sender and stitch the bodies back into one message.
        // Individual parts can be null when a PDU fails to parse, hence filterNotNull.
        val bodiesBySender = mutableMapOf<String, StringBuilder>()
        val parts = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        for (sms in parts.filterNotNull()) {
            val sender = sms.displayOriginatingAddress?.trim().orEmpty()
            if (sender.isEmpty()) continue
            bodiesBySender.getOrPut(sender) { StringBuilder() }.append(sms.displayMessageBody.orEmpty())
        }

        for ((sender, body) in bodiesBySender) {
            val contact = dbHelper.getContactByPhone(sender)
                ?: createContactFromNumber(dbHelper, sender)
                ?: continue

            dbHelper.insertMessage(
                Message(
                    contactId = contact.id,
                    body = body.toString(),
                    timestamp = System.currentTimeMillis(),
                    incoming = true
                )
            )

            // Nudge any open screen (list or conversation) to reload.
            context.sendBroadcast(
                Intent(ACTION_MESSAGE_RECEIVED)
                    .putExtra(EXTRA_CONTACT_ID, contact.id)
                    .setPackage(context.packageName)
            )
        }
    }

    // A message from a number we don't know creates a contact on the fly,
    // using the number itself as the name, so the conversation has somewhere to live.
    // Only real dialable numbers qualify — otherwise every "Google"/"VERIFY" style
    // alphanumeric sender ID would litter the contact list with junk entries.
    private fun createContactFromNumber(dbHelper: AppDbHelper, number: String): Contact? {
        if (!PhoneNumberUtils.isGlobalPhoneNumber(number)) return null
        val newId = dbHelper.insertContact(
            Contact(firstName = number, lastName = "", phoneNumber = number, email = "", address = "")
        )
        return if (newId == -1L) null else dbHelper.getContact(newId)
    }
}
