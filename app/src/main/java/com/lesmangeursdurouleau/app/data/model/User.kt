package com.lesmangeursdurouleau.app.data.model

data class User(
    val uid: String = "",
    val username: String = "",
    val email: String = "",
    val profilePictureUrl: String? = null,
    val createdAt: Long? = null
)