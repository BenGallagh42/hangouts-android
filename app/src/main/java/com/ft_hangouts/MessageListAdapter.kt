package com.ft_hangouts

import android.content.Context
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.ft_hangouts.model.Message
import com.google.android.material.card.MaterialCardView

// Turns a list of Message objects into chat-style rows, aligned by sender.
class MessageListAdapter(
    private val context: Context,
    private var messages: List<Message>,
    var contactName: String
) : BaseAdapter() {

    override fun getCount(): Int = messages.size
    override fun getItem(position: Int): Message = messages[position]
    override fun getItemId(position: Int): Long = messages[position].id

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val view = convertView
            ?: LayoutInflater.from(context).inflate(R.layout.list_item_message, parent, false)

        val message = messages[position]
        val rowContainer = view.findViewById<LinearLayout>(R.id.row_container)
        val bubble = view.findViewById<MaterialCardView>(R.id.bubble)

        view.findViewById<TextView>(R.id.text_sender).text =
            if (message.incoming) contactName else context.getString(R.string.label_you)
        view.findViewById<TextView>(R.id.text_body).text = message.body
        view.findViewById<TextView>(R.id.text_time).text =
            TimeFormat.forMessageBubble(context, message.timestamp)

        // Received messages hug the left, sent messages hug the right — like any chat app.
        rowContainer.gravity = if (message.incoming) Gravity.START else Gravity.END
        val bubbleColorRes = if (message.incoming) R.color.bubble_incoming else R.color.bubble_outgoing
        bubble.setCardBackgroundColor(ContextCompat.getColor(context, bubbleColorRes))

        return view
    }

    fun updateMessages(newMessages: List<Message>) {
        messages = newMessages
        notifyDataSetChanged()
    }
}
