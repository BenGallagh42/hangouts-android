package com.ft_hangouts

import android.app.AlertDialog
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import com.ft_hangouts.data.AppDbHelper
import com.ft_hangouts.data.PhotoStore
import com.ft_hangouts.model.Contact

// Create screen when no contact id is passed in; edit screen when one is.
class AddEditContactActivity : BaseActivity() {

    companion object {
        const val EXTRA_CONTACT_ID = "extra_contact_id"
        private const val STATE_PHOTO_PATH = "state_photo_path"
    }

    private lateinit var dbHelper: AppDbHelper
    private var existingContact: Contact? = null
    private var isSaving = false

    // Path of the photo currently shown. Null means "no photo".
    private var photoPath: String? = null

    private lateinit var editFirstName: EditText
    private lateinit var editLastName: EditText
    private lateinit var editPhone: EditText
    private lateinit var editEmail: EditText
    private lateinit var editAddress: EditText
    private lateinit var imagePhoto: ImageView
    private lateinit var buttonRemovePhoto: Button

    // System photo picker — no storage permission needed, the user grants one image.
    private val pickPhoto = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        val savedPath = PhotoStore.savePhoto(this, uri)
        if (savedPath == null) {
            Toast.makeText(this, R.string.error_photo_failed, Toast.LENGTH_SHORT).show()
        } else {
            photoPath = savedPath
            showPhoto()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_edit_contact)

        dbHelper = AppDbHelper.getInstance(this)

        editFirstName = findViewById(R.id.edit_first_name)
        editLastName = findViewById(R.id.edit_last_name)
        editPhone = findViewById(R.id.edit_phone)
        editEmail = findViewById(R.id.edit_email)
        editAddress = findViewById(R.id.edit_address)
        imagePhoto = findViewById(R.id.image_photo)
        buttonRemovePhoto = findViewById(R.id.button_remove_photo)

        val contactId = intent.getLongExtra(EXTRA_CONTACT_ID, -1L)
        if (contactId != -1L) {
            existingContact = dbHelper.getContact(contactId)
            if (existingContact == null) {
                // Id was passed but no longer matches a real row (e.g. deleted elsewhere).
                // Bail out instead of silently falling through to "create" mode, which
                // would let Save quietly insert an unrelated duplicate contact.
                finish()
                return
            }
        }

        if (existingContact != null) {
            title = getString(R.string.title_edit_contact)
            prefillFields(existingContact!!)
            findViewById<Button>(R.id.button_delete).apply {
                visibility = View.VISIBLE
                setOnClickListener { confirmDelete() }
            }
        } else {
            title = getString(R.string.title_add_contact)
        }

        // Survives rotation: without this the picked photo would be lost on recreate.
        // containsKey, not a null-check — a saved null means "user removed the photo",
        // which must override the prefilled one rather than be ignored.
        if (savedInstanceState?.containsKey(STATE_PHOTO_PATH) == true) {
            photoPath = savedInstanceState.getString(STATE_PHOTO_PATH)
        }
        showPhoto()

        val openPicker = View.OnClickListener {
            pickPhoto.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        }
        imagePhoto.setOnClickListener(openPicker)
        findViewById<Button>(R.id.button_choose_photo).setOnClickListener(openPicker)
        buttonRemovePhoto.setOnClickListener {
            photoPath = null
            showPhoto()
        }

        findViewById<Button>(R.id.button_save).setOnClickListener { saveContact() }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_PHOTO_PATH, photoPath)
    }

    // Shows the chosen photo, or a camera placeholder when there isn't one.
    private fun showPhoto() {
        val bitmap = PhotoStore.loadPhoto(photoPath)
        if (bitmap == null) {
            imagePhoto.setImageResource(android.R.drawable.ic_menu_camera)
            buttonRemovePhoto.visibility = View.GONE
        } else {
            imagePhoto.setImageBitmap(bitmap)
            buttonRemovePhoto.visibility = View.VISIBLE
        }
    }

    private fun prefillFields(contact: Contact) {
        editFirstName.setText(contact.firstName)
        editLastName.setText(contact.lastName)
        editPhone.setText(contact.phoneNumber)
        editEmail.setText(contact.email)
        editAddress.setText(contact.address)
        photoPath = contact.photoUri
    }

    // Checks the required fields and shows an inline error on the first empty one.
    // Last name is deliberately optional: plenty of real contacts have only one name,
    // and a contact auto-created from an unknown number has no surname to give — with
    // last name required, such a contact could never be edited and saved again.
    private fun fieldsAreValid(): Boolean {
        if (editFirstName.text.isBlank()) {
            editFirstName.error = getString(R.string.error_first_name_required)
            return false
        }
        if (editPhone.text.isBlank()) {
            editPhone.error = getString(R.string.error_phone_required)
            return false
        }
        return true
    }

    private fun saveContact() {
        // Guards against a fast double-tap firing saveContact() twice before finish()
        // takes effect, which would otherwise insert the same contact twice.
        if (isSaving) return
        if (!fieldsAreValid()) return
        isSaving = true

        // If the photo was swapped or removed, delete the old file so it doesn't linger.
        val previousPhoto = existingContact?.photoUri
        if (previousPhoto != null && previousPhoto != photoPath) {
            PhotoStore.deletePhoto(previousPhoto)
        }

        val contact = Contact(
            id = existingContact?.id ?: 0L,
            firstName = editFirstName.text.toString().trim(),
            lastName = editLastName.text.toString().trim(),
            phoneNumber = editPhone.text.toString().trim(),
            email = editEmail.text.toString().trim(),
            address = editAddress.text.toString().trim(),
            photoUri = photoPath
        )

        if (existingContact != null) {
            dbHelper.updateContact(contact)
        } else {
            dbHelper.insertContact(contact)
        }
        finish()
    }

    private fun confirmDelete() {
        AlertDialog.Builder(this)
            .setTitle(R.string.dialog_delete_contact_title)
            .setMessage(R.string.dialog_delete_contact_message)
            .setPositiveButton(R.string.button_delete) { _, _ ->
                PhotoStore.deletePhoto(existingContact!!.photoUri)
                dbHelper.deleteContact(existingContact!!.id)
                finish()
            }
            .setNegativeButton(R.string.button_cancel, null)
            .show()
    }
}
