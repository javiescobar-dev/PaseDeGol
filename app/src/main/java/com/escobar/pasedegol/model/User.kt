package com.escobar.pasedegol.model

data class User(
    val id: String = "",
    val email: String = "",
    val name: String = "",
    val isAdmin: Boolean = false,
    val notificationsEnabled: Boolean = true
)
