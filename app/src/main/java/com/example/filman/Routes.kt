package com.example.filman

import kotlinx.serialization.Serializable

@Serializable
object Auth

@Serializable
object Home

@Serializable
data class Details(val url: String)

@Serializable
data class Player(val url: String)
