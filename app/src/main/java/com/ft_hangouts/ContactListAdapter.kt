package com.ft_hangouts

import android.content.Context
import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.TextView
import com.ft_hangouts.data.PhotoStore
import com.ft_hangouts.model.ContactSummary

// Turns contact summaries into homepage rows: name, avatar, and either the last
// message preview or the phone number, plus time and unread badge.
class ContactListAdapter(
    private val context: Context,
    private var summaries: List<ContactSummary>
) : BaseAdapter() {

    // Set by MainActivity: tapping the avatar opens that contact's profile.
    // (Tapping the row itself opens the conversation, handled by the ListView.)
    var onAvatarClick: ((Long) -> Unit)? = null

    override fun getCount(): Int = summaries.size
    override fun getItem(position: Int): ContactSummary = summaries[position]
    override fun getItemId(position: Int): Long = summaries[position].contact.id

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val view = convertView
            ?: LayoutInflater.from(context).inflate(R.layout.list_item_contact, parent, false)

        val summary = summaries[position]
        val contact = summary.contact
        val message = summary.lastMessage
        val hasUnread = summary.unreadCount > 0

        view.findViewById<TextView>(R.id.text_contact_avatar).text =
            contact.firstName.take(1).uppercase()
        view.findViewById<TextView>(R.id.text_contact_name).text =
            "${contact.firstName} ${contact.lastName}".trim()

        // Preview when there's a conversation, phone number when there isn't.
        val subtitle = view.findViewById<TextView>(R.id.text_contact_subtitle)
        subtitle.text = when {
            message == null -> contact.phoneNumber
            message.incoming -> message.body
            else -> context.getString(R.string.preview_sent_prefix, message.body)
        }
        // Unread conversations read bolder, the usual messaging-app cue.
        subtitle.setTypeface(null, if (hasUnread) Typeface.BOLD else Typeface.NORMAL)

        // Every branch below must reset its view: rows get recycled, so a photo,
        // timestamp or badge left over from another contact would bleed through.
        val timeView = view.findViewById<TextView>(R.id.text_contact_time)
        if (message != null) {
            timeView.text = TimeFormat.forConversationList(context, message.timestamp)
            timeView.visibility = View.VISIBLE
        } else {
            timeView.visibility = View.GONE
        }

        view.findViewById<View>(R.id.dot_unread).visibility =
            if (hasUnread) View.VISIBLE else View.GONE

        view.findViewById<View>(R.id.container_avatar).setOnClickListener {
            onAvatarClick?.invoke(contact.id)
        }

        val photoView = view.findViewById<ImageView>(R.id.image_contact_photo)
        val photo = PhotoStore.loadThumbnail(contact.photoUri)
        if (photo != null) {
            photoView.setImageBitmap(photo)
            photoView.visibility = View.VISIBLE
        } else {
            photoView.setImageDrawable(null)
            photoView.visibility = View.GONE
        }

        return view
    }

    fun updateSummaries(newSummaries: List<ContactSummary>) {
        summaries = newSummaries
        notifyDataSetChanged()
    }
}
