package com.lesmangeursdurouleau.app.data.repository

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import com.lesmangeursdurouleau.app.data.remote.FirebaseStorageService
import com.lesmangeursdurouleau.app.remote.FirebaseConstants
import com.lesmangeursdurouleau.app.utils.Resource
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class UserRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val firebaseAuth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val firebaseStorageService: FirebaseStorageService
) : UserRepository {

    companion object {
        private const val TAG = "UserRepositoryImpl"
    }

    override suspend fun updateUserProfile(userId: String, username: String): Resource<Unit> {
        if (username.isBlank()) {
            return Resource.Error("Le pseudo ne peut pas être vide.")
        }

        try {
            // 1. Mettre à jour le displayName dans Firebase Auth
            val user = firebaseAuth.currentUser
            if (user == null || user.uid != userId) {
                Log.e(TAG, "Utilisateur non authentifié ou ID ne correspond pas pour la mise à jour du profil Auth.")
                return Resource.Error("Erreur d'authentification pour la mise à jour du profil.")
            }

            val profileUpdates = UserProfileChangeRequest.Builder()
                .setDisplayName(username)
                .build()

            user.updateProfile(profileUpdates).await()
            Log.d(TAG, "Firebase Auth displayName mis à jour pour $userId.")

            // 2. Mettre à jour le champ 'username' dans le document Firestore
            val userDocRef = firestore.collection(FirebaseConstants.COLLECTION_USERS).document(userId)
            userDocRef.update("username", username).await()
            Log.d(TAG, "Champ 'username' dans Firestore mis à jour pour $userId.")

            return Resource.Success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Erreur lors de la mise à jour du profil pour $userId: ${e.message}", e)
            return Resource.Error("Erreur lors de la mise à jour du profil: ${e.localizedMessage}")
        }
    }

    override suspend fun updateUserProfilePicture(userId: String, imageData: ByteArray): Resource<String> {
        try {
            val user = firebaseAuth.currentUser
            if (user == null || user.uid != userId) {
                Log.e(TAG, "Utilisateur non authentifié ou ID ne correspond pas.")
                return Resource.Error("Erreur d'authentification.")
            }

            // Upload de la photo vers Firebase Storage
            val uploadResult = firebaseStorageService.uploadProfilePicture(userId, imageData)
            if (uploadResult is Resource.Success) {
                val photoUrl = uploadResult.data!!
                // Mettre à jour le champ photoUrl dans Firestore
                val userDocRef = firestore.collection(FirebaseConstants.COLLECTION_USERS).document(userId)
                userDocRef.update("profilePictureUrl", photoUrl).await()
                Log.d(TAG, "Photo de profil mise à jour dans Firestore pour $userId.")
                // Mettre à jour Firebase Auth
                val profileUpdates = UserProfileChangeRequest.Builder()
                    .setPhotoUri(android.net.Uri.parse(photoUrl))
                    .build()
                user.updateProfile(profileUpdates).await()
                Log.d(TAG, "Photo de profil mise à jour dans Firebase Auth pour $userId.")
                return Resource.Success(photoUrl)
            } else {
                return Resource.Error(uploadResult.message ?: "Erreur lors de l'upload de la photo.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erreur lors de la mise à jour de la photo de profil: ${e.message}", e)
            return Resource.Error("Erreur: ${e.localizedMessage}")
        }
    }
}