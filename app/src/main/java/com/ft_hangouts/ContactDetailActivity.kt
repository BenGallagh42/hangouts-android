package com.ft_hangouts

import android.Manifest
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.ft_hangouts.data.AppDbHelper
import com.ft_hangouts.data.PhotoStore
import com.ft_hangouts.model.Contact

// Shows one contact's details, with entry points to message, call, edit, or delete them.
class ContactDetailActivity : BaseActivity() {

    companion object {
        const val EXTRA_CONTACT_ID = "extra_contact_id"
    }

    private lateinit var dbHelper: AppDbHelper
    private var contactId: Long = -1L
    private var contactPhone: String = ""

    // If the user grants CALL_PHONE we dial straight away; if not, we fall back to
    // opening the phone app rather than doing nothing.
    private val requestCallPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) placeCall() else openDialer()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_contact_detail)
        title = getString(R.string.title_contact_detail)

        dbHelper = AppDbHelper.getInstance(this)
        contactId = intent.getLongExtra(EXTRA_CONTACT_ID, -1L)

        findViewById<Button>(R.id.button_message).setOnClickListener {
            startActivity(
                Intent(this, ConversationActivity::class.java)
                    .putExtra(ConversationActivity.EXTRA_CONTACT_ID, contactId)
            )
        }
        findViewById<Button>(R.id.button_call).setOnClickListener { callContact() }
        findViewById<Button>(R.id.button_edit).setOnClickListener {
            startActivity(
                Intent(this, AddEditContactActivity::class.java)
                    .putExtra(AddEditContactActivity.EXTRA_CONTACT_ID, contactId)
            )
        }
        findViewById<Button>(R.id.button_delete).setOnClickListener { confirmDelete() }
    }

    // Reloads every time this screen is shown, in case the contact was just edited or deleted.
    override fun onResume() {
        super.onResume()
        val contact = dbHelper.getContact(contactId)
        if (contact == null) {
            finish()
        } else {
            showContact(contact)
        }
    }

    private fun showContact(contact: Contact) {
        contactPhone = contact.phoneNumber
        findViewById<TextView>(R.id.text_avatar).text = contact.firstName.take(1).uppercase()
        findViewById<TextView>(R.id.text_name).text = "${contact.firstName} ${contact.lastName}"
        findViewById<TextView>(R.id.text_phone).text = contact.phoneNumber
        findViewById<TextView>(R.id.text_email).text = contact.email
        findViewById<TextView>(R.id.text_address).text = contact.address

        // Photo covers the letter avatar when there is one.
        val photoView = findViewById<ImageView>(R.id.image_photo)
        val photo = PhotoStore.loadPhoto(contact.photoUri)
        if (photo != null) {
            photoView.setImageBitmap(photo)
            photoView.visibility = View.VISIBLE
        } else {
            photoView.visibility = View.GONE
        }
    }

    // Places the call directly (ACTION_CALL). Needs CALL_PHONE, so ask first.
    private fun callContact() {
        if (contactPhone.isBlank()) return
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) ==
                PackageManager.PERMISSION_GRANTED
        if (granted) placeCall() else requestCallPermission.launch(Manifest.permission.CALL_PHONE)
    }

    private fun placeCall() {
        val phone = currentPhone()
        if (phone.isBlank()) return
        try {
            startActivity(Intent(Intent.ACTION_CALL, telUri(phone)))
        } catch (e: SecurityException) {
            openDialer()
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, R.string.error_no_dialer, Toast.LENGTH_SHORT).show()
        }
    }

    // Fallback when the user won't grant CALL_PHONE: hand the number to the phone app
    // pre-dialled, so calling still works, it just needs one extra tap.
    private fun openDialer() {
        val phone = currentPhone()
        if (phone.isBlank()) return
        try {
            startActivity(Intent(Intent.ACTION_DIAL, telUri(phone)))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, R.string.error_no_dialer, Toast.LENGTH_SHORT).show()
        }
    }

    // Read fresh from the database: a permission callback can fire before onResume()
    // has repopulated contactPhone (e.g. rotating while the permission dialog is up).
    private fun currentPhone(): String =
        contactPhone.ifBlank { dbHelper.getContact(contactId)?.phoneNumber.orEmpty() }

    // fromParts encodes the number properly. Uri.parse("tel:$n") would treat a '#'
    // in the number as a URI fragment and silently truncate it.
    private fun telUri(phone: String): Uri = Uri.fromParts("tel", phone, null)

    private fun confirmDelete() {
        AlertDialog.Builder(this)
            .setTitle(R.string.dialog_delete_contact_title)
            .setMessage(R.string.dialog_delete_contact_message)
            .setPositiveButton(R.string.button_delete) { _, _ ->
                PhotoStore.deletePhoto(dbHelper.getContact(contactId)?.photoUri)
                dbHelper.deleteContact(contactId)
                finish()
            }
            .setNegativeButton(R.string.button_cancel, null)
            .show()
    }
}
