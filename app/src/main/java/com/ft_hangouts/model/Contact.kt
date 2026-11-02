package com.ft_hangouts.model

// Blueprint for one contact. Every saved contact is one Contact object.
data class Contact(
    val id: Long = 0L,
    val firstName: String,
    val lastName: String,
    val phoneNumber: String,
    val email: String,
    val address: String,
    val photoUri: String? = null
)
