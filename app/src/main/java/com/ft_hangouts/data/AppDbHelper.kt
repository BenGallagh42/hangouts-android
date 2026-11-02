package com.ft_hangouts.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.ft_hangouts.model.Contact
import com.ft_hangouts.model.ContactSummary
import com.ft_hangouts.model.Message

// Owns the SQLite database: the "contacts" table and the "messages" table.
// Private constructor + getInstance(): every Activity shares one instance instead of
// each opening its own database connection (which used to leak one per rotation,
// since a config change destroys and recreates the current Activity).
class AppDbHelper private constructor(context: Context) :
    SQLiteOpenHelper(context.applicationContext, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        @Volatile private var instance: AppDbHelper? = null

        fun getInstance(context: Context): AppDbHelper {
            return instance ?: synchronized(this) {
                instance ?: AppDbHelper(context.applicationContext).also { instance = it }
            }
        }

        private const val DATABASE_NAME = "ft_hangouts.db"
        // v2 added the messages.read column used for unread badges in the chat list.
        private const val DATABASE_VERSION = 2

        // How many trailing digits must agree for two numbers to count as the same.
        private const val MIN_MATCH_DIGITS = 7

        private const val TABLE_CONTACTS = "contacts"
        private const val COL_ID = "id"
        private const val COL_FIRST_NAME = "first_name"
        private const val COL_LAST_NAME = "last_name"
        private const val COL_PHONE = "phone_number"
        private const val COL_EMAIL = "email"
        private const val COL_ADDRESS = "address"
        private const val COL_PHOTO_URI = "photo_uri"

        private const val TABLE_MESSAGES = "messages"
        private const val COL_MSG_ID = "id"
        private const val COL_MSG_CONTACT_ID = "contact_id"
        private const val COL_MSG_BODY = "body"
        private const val COL_MSG_TIMESTAMP = "timestamp"
        private const val COL_MSG_INCOMING = "incoming"
        private const val COL_MSG_READ = "read"
    }

    // Called once, the first time the app ever opens this database.
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE_CONTACTS (
                $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_FIRST_NAME TEXT NOT NULL,
                $COL_LAST_NAME TEXT NOT NULL,
                $COL_PHONE TEXT NOT NULL,
                $COL_EMAIL TEXT,
                $COL_ADDRESS TEXT,
                $COL_PHOTO_URI TEXT
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE $TABLE_MESSAGES (
                $COL_MSG_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_MSG_CONTACT_ID INTEGER NOT NULL,
                $COL_MSG_BODY TEXT NOT NULL,
                $COL_MSG_TIMESTAMP INTEGER NOT NULL,
                $COL_MSG_INCOMING INTEGER NOT NULL,
                $COL_MSG_READ INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
    }

    // Called if DATABASE_VERSION is ever raised. Simplest safe approach: wipe and rebuild.
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_CONTACTS")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_MESSAGES")
        onCreate(db)
    }

    // ---- Contacts ----

    private fun contactToValues(contact: Contact): ContentValues {
        return ContentValues().apply {
            put(COL_FIRST_NAME, contact.firstName)
            put(COL_LAST_NAME, contact.lastName)
            put(COL_PHONE, contact.phoneNumber)
            put(COL_EMAIL, contact.email)
            put(COL_ADDRESS, contact.address)
            put(COL_PHOTO_URI, contact.photoUri)
        }
    }

    fun insertContact(contact: Contact): Long {
        return writableDatabase.insert(TABLE_CONTACTS, null, contactToValues(contact))
    }

    fun updateContact(contact: Contact): Int {
        return writableDatabase.update(
            TABLE_CONTACTS,
            contactToValues(contact),
            "$COL_ID = ?",
            arrayOf(contact.id.toString())
        )
    }

    fun deleteContact(id: Long): Int {
        // Also clear any conversation history that belonged to this contact.
        writableDatabase.delete(TABLE_MESSAGES, "$COL_MSG_CONTACT_ID = ?", arrayOf(id.toString()))
        return writableDatabase.delete(TABLE_CONTACTS, "$COL_ID = ?", arrayOf(id.toString()))
    }

    fun getAllContacts(): List<Contact> {
        val contacts = mutableListOf<Contact>()
        val cursor = readableDatabase.query(
            TABLE_CONTACTS, null, null, null, null, null, "$COL_FIRST_NAME ASC"
        )
        cursor.use {
            while (it.moveToNext()) contacts.add(it.toContact())
        }
        return contacts
    }

    fun getContact(id: Long): Contact? {
        val cursor = readableDatabase.query(
            TABLE_CONTACTS, null, "$COL_ID = ?", arrayOf(id.toString()), null, null, null
        )
        cursor.use {
            return if (it.moveToFirst()) it.toContact() else null
        }
    }

    // Finds the saved contact an incoming SMS came from, or null if the number is unknown.
    // Matched with samePhoneNumber() rather than a plain SQL "=", because the number the
    // network reports is often formatted differently from what the user typed
    // (e.g. "+66812345678" vs "0812345678" vs "081 234 5678").
    fun getContactByPhone(phoneNumber: String): Contact? {
        val cursor = readableDatabase.query(TABLE_CONTACTS, null, null, null, null, null, null)
        cursor.use {
            while (it.moveToNext()) {
                val contact = it.toContact()
                if (samePhoneNumber(contact.phoneNumber, phoneNumber)) return contact
            }
        }
        return null
    }

    // Two numbers are the same if their last MIN_MATCH_DIGITS digits agree, which is how
    // Android's own PhoneNumberUtils.compare works — it lets a local number match its
    // international form while ignoring spaces, dashes and brackets. Numbers shorter than
    // that (short codes, emulator ports) have to match exactly, or unrelated ones collide.
    // Written here rather than calling PhoneNumberUtils, whose compare() overloads are both
    // deprecated in favour of libphonenumber — an external library this app deliberately avoids.
    private fun samePhoneNumber(a: String, b: String): Boolean {
        val digitsA = a.filter { it.isDigit() }
        val digitsB = b.filter { it.isDigit() }
        if (digitsA.isEmpty() || digitsB.isEmpty()) return false
        if (digitsA.length < MIN_MATCH_DIGITS || digitsB.length < MIN_MATCH_DIGITS) {
            return digitsA == digitsB
        }
        return digitsA.takeLast(MIN_MATCH_DIGITS) == digitsB.takeLast(MIN_MATCH_DIGITS)
    }

    private fun Cursor.toContact(): Contact {
        return Contact(
            id = getLong(getColumnIndexOrThrow(COL_ID)),
            firstName = getString(getColumnIndexOrThrow(COL_FIRST_NAME)),
            lastName = getString(getColumnIndexOrThrow(COL_LAST_NAME)),
            phoneNumber = getString(getColumnIndexOrThrow(COL_PHONE)),
            email = getString(getColumnIndexOrThrow(COL_EMAIL)) ?: "",
            address = getString(getColumnIndexOrThrow(COL_ADDRESS)) ?: "",
            photoUri = getString(getColumnIndexOrThrow(COL_PHOTO_URI))
        )
    }

    // ---- Messages ----

    fun insertMessage(message: Message): Long {
        val values = ContentValues().apply {
            put(COL_MSG_CONTACT_ID, message.contactId)
            put(COL_MSG_BODY, message.body)
            put(COL_MSG_TIMESTAMP, message.timestamp)
            put(COL_MSG_INCOMING, if (message.incoming) 1 else 0)
            put(COL_MSG_READ, if (message.read) 1 else 0)
        }
        return writableDatabase.insert(TABLE_MESSAGES, null, values)
    }

    // Called when a conversation is opened — clears its unread badge.
    fun markConversationRead(contactId: Long) {
        val values = ContentValues().apply { put(COL_MSG_READ, 1) }
        writableDatabase.update(
            TABLE_MESSAGES,
            values,
            "$COL_MSG_CONTACT_ID = ? AND $COL_MSG_READ = 0",
            arrayOf(contactId.toString())
        )
    }

    // Builds the homepage list: every contact, each with their latest message (if any)
    // and unread count. Contacts with messages float to the top, newest first, so the
    // list behaves like a message inbox; the rest follow alphabetically.
    fun getContactSummaries(): List<ContactSummary> {
        val summaries = getAllContacts().map { contact ->
            ContactSummary(contact, getLastMessage(contact.id), countUnread(contact.id))
        }
        return summaries.sortedWith(
            compareByDescending<ContactSummary> { it.lastMessage?.timestamp ?: Long.MIN_VALUE }
                .thenBy { it.contact.firstName.lowercase() }
        )
    }

    private fun getLastMessage(contactId: Long): Message? {
        val cursor = readableDatabase.query(
            TABLE_MESSAGES, null, "$COL_MSG_CONTACT_ID = ?", arrayOf(contactId.toString()),
            null, null, "$COL_MSG_TIMESTAMP DESC", "1"
        )
        cursor.use {
            return if (it.moveToFirst()) it.toMessage() else null
        }
    }

    private fun countUnread(contactId: Long): Int {
        val cursor = readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM $TABLE_MESSAGES " +
                "WHERE $COL_MSG_CONTACT_ID = ? AND $COL_MSG_INCOMING = 1 AND $COL_MSG_READ = 0",
            arrayOf(contactId.toString())
        )
        cursor.use {
            return if (it.moveToFirst()) it.getInt(0) else 0
        }
    }

    // Full conversation history with one contact, oldest first.
    fun getMessages(contactId: Long): List<Message> {
        val messages = mutableListOf<Message>()
        val cursor = readableDatabase.query(
            TABLE_MESSAGES, null, "$COL_MSG_CONTACT_ID = ?", arrayOf(contactId.toString()),
            null, null, "$COL_MSG_TIMESTAMP ASC"
        )
        cursor.use {
            while (it.moveToNext()) messages.add(it.toMessage())
        }
        return messages
    }

    private fun Cursor.toMessage(): Message {
        return Message(
            id = getLong(getColumnIndexOrThrow(COL_MSG_ID)),
            contactId = getLong(getColumnIndexOrThrow(COL_MSG_CONTACT_ID)),
            body = getString(getColumnIndexOrThrow(COL_MSG_BODY)),
            timestamp = getLong(getColumnIndexOrThrow(COL_MSG_TIMESTAMP)),
            incoming = getInt(getColumnIndexOrThrow(COL_MSG_INCOMING)) == 1,
            read = getInt(getColumnIndexOrThrow(COL_MSG_READ)) == 1
        )
    }
}
