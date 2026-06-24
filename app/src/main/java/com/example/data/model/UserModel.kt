package com.example.data.model

import java.io.Serializable

data class UserModel(
    val uid: String = "",
    val name: String = "",
    val firstName: String = "",
    val lastName: String = "",
    val email: String = "",
    val photoUrl: String = "",
    val createdAt: Long = 0L,
    val nativeLanguage: String? = null,
    val targetLanguage: String? = null,
    val xp: Int = 0,
    val streak: Int = 0,
    val level: Int = 1,
    val isVerified: Boolean = false
) : Serializable
