package com.escobar.pasedegol.model

data class CartItem(
    val id: String = "",
    val matchId: String = "",
    val homeTeamName: String = "",
    val awayTeamName: String = "",
    val homeBadgeUrl: String = "",
    val awayBadgeUrl: String = "",
    val stadium: String = "",
    val quantity: Int = 0,
    val price: Double = 0.0,
    val matchDate: Long = 0L
) {
    fun calculateTotal(): Double = price * quantity
}
