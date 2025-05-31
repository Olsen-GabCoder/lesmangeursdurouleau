package com.lesmangeursdurouleau.app.data.repository

import com.lesmangeursdurouleau.app.data.model.User
import com.lesmangeursdurouleau.app.utils.Resource
import kotlinx.coroutines.flow.Flow

interface UserRepository {
    suspend fun updateUserProfile(userId: String, username: String): Resource<Unit>
    suspend fun updateUserProfilePicture(userId: String, imageData: ByteArray): Resource<String>
    fun getAllUsers(): Flow<Resource<List<User>>>
    fun getUserById(userId: String): Flow<Resource<User>>

    // NOUVELLE MÉTHODE
    suspend fun updateUserTypingStatus(userId: String, isTyping: Boolean): Resource<Unit>
}