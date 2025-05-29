package com.lesmangeursdurouleau.app.data.repository

import com.lesmangeursdurouleau.app.utils.Resource

interface UserRepository {
    suspend fun updateUserProfile(userId: String, username: String): Resource<Unit>
    suspend fun updateUserProfilePicture(userId: String, imageData: ByteArray): Resource<String>
}