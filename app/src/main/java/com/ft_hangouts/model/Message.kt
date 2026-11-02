package com.ft_hangouts.model

// One message in a conversation with a contact.
data class Message(
    val id: Long = 0L,
    val contactId: Long,
    val body: String,
    val timestamp: Long,
    val incoming: Boolean,
    // Received messages start unread so the chat list can show a badge.
    val read: Boolean = false
)
