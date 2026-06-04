package com.escobar.pasedegol.model

data class Ticket(
    val ticketId: String = "",
    val userId: String = "",
    val matchId: String = "",
    val homeTeamName: String = "",
    val awayTeamName: String = "",
    val homeBadgeUrl: String = "",
    val awayBadgeUrl: String = "",
    val stadium: String = "",
    val quantity: Int = 0,
    val totalPrice: Double = 0.0,
    val matchDate: Long = 0L,
    val purchaseDate: com.google.firebase.Timestamp = com.google.firebase.Timestamp.now(),
    val qrCodigo: String = ""
)
