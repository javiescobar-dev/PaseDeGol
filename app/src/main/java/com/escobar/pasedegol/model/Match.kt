package com.escobar.pasedegol.model

data class Match(
    val id: String = "",
    val homeTeam: Team = Team(),
    val awayTeam: Team = Team(),
    val stadium: String = "",
    val date: com.google.firebase.Timestamp = com.google.firebase.Timestamp.now(),
    val price: Double = 0.0,
    val stock: Int = 0
)
