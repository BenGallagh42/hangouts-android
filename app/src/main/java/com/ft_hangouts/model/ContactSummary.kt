package com.ft_hangouts.model

// One row of the homepage: the contact, plus their latest message if they have one.
// lastMessage is null for a contact nobody has written to yet — that row falls back
// to showing the phone number instead of a preview.
data class ContactSummary(
    val contact: Contact,
    val lastMessage: Message?,
    val unreadCount: Int
)
