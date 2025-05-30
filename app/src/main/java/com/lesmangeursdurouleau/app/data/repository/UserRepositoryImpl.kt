package com.lesmangeursdurouleau.app.data.repository

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import com.lesmangeursdurouleau.app.data.model.User
import com.lesmangeursdurouleau.app.data.remote.FirebaseStorageService
// Vérifie cet import si FirebaseConstants a été déplacé :
// import com.lesmangeursdurouleau.app.core.constants.FirebaseConstants
import com.lesmangeursdurouleau.app.remote.FirebaseConstants
import com.lesmangeursdurouleau.app.utils.Resource
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class UserRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val firebaseAuth: FirebaseAuth,
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

            val uploadResult = firebaseStorageService.uploadProfilePicture(userId, imageData)
            if (uploadResult is Resource.Success) {
                val photoUrl = uploadResult.data!!
                val userDocRef = firestore.collection(FirebaseConstants.COLLECTION_USERS).document(userId)
                userDocRef.update("profilePictureUrl", photoUrl).await()
                Log.d(TAG, "Photo de profil mise à jour dans Firestore pour $userId.")

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

    override fun getAllUsers(): Flow<Resource<List<User>>> = callbackFlow {
        trySend(Resource.Loading())
        Log.d(TAG, "Tentative de récupération de tous les utilisateurs depuis Firestore.")

        val listenerRegistration = firestore.collection(FirebaseConstants.COLLECTION_USERS)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Erreur lors de l'écoute des utilisateurs: ${error.message}", error)
                    trySend(Resource.Error("Erreur Firestore: ${error.localizedMessage ?: "Erreur inconnue"}"))
                    close(error)
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val usersList = mutableListOf<User>()
                    for (document in snapshot.documents) {
                        try {
                            val userId = document.id
                            val username = document.getString("username") ?: ""
                            val email = document.getString("email") ?: ""
                            val profilePictureUrl = document.getString("profilePictureUrl")
                            val createdAtTimestamp = document.getTimestamp("createdAt")
                            val createdAtLong = createdAtTimestamp?.toDate()?.time

                            val user = User(
                                uid = userId,
                                username = username,
                                email = email,
                                profilePictureUrl = profilePictureUrl,
                                createdAt = createdAtLong
                            )
                            usersList.add(user)
                            Log.d(TAG, "Utilisateur converti (getAllUsers): $user")

                        } catch (e: Exception) {
                            Log.e(TAG, "Erreur de conversion du document utilisateur ${document.id} (getAllUsers): ${e.message}", e)
                        }
                    }
                    Log.d(TAG, "${usersList.size} utilisateurs récupérés avec succès (getAllUsers).")
                    if (usersList.isEmpty() && !snapshot.isEmpty) {
                        Log.w(TAG, "Snapshot des utilisateurs non vide, mais la liste convertie est vide (getAllUsers).")
                    }
                    trySend(Resource.Success(usersList))
                } else {
                    Log.d(TAG, "Snapshot des utilisateurs est null (getAllUsers).")
                    trySend(Resource.Success(emptyList()))
                }
            }

        awaitClose {
            Log.d(TAG, "Fermeture du listener des utilisateurs (getAllUsers).")
            listenerRegistration.remove()
        }
    }

    // NOUVELLE IMPLÉMENTATION CI-DESSOUS
    override fun getUserById(userId: String): Flow<Resource<User>> = callbackFlow {
        if (userId.isBlank()) {
            trySend(Resource.Error("L'ID utilisateur ne peut pas être vide."))
            close()
            return@callbackFlow
        }
        trySend(Resource.Loading())
        Log.d(TAG, "Tentative de récupération de l'utilisateur ID: $userId depuis Firestore.")

        val docRef = firestore.collection(FirebaseConstants.COLLECTION_USERS).document(userId)
        val listenerRegistration = docRef.addSnapshotListener { documentSnapshot, error ->
            if (error != null) {
                Log.e(TAG, "Erreur lors de l'écoute de l'utilisateur ID $userId: ${error.message}", error)
                trySend(Resource.Error("Erreur Firestore: ${error.localizedMessage ?: "Erreur inconnue"}"))
                close(error)
                return@addSnapshotListener
            }

            if (documentSnapshot != null && documentSnapshot.exists()) {
                try {
                    val docId = documentSnapshot.id
                    val username = documentSnapshot.getString("username") ?: ""
                    val email = documentSnapshot.getString("email") ?: ""
                    val profilePictureUrl = documentSnapshot.getString("profilePictureUrl")
                    val createdAtTimestamp = documentSnapshot.getTimestamp("createdAt")
                    val createdAtLong = createdAtTimestamp?.toDate()?.time

                    val user = User(
                        uid = docId, // Utiliser docId qui est userId
                        username = username,
                        email = email,
                        profilePictureUrl = profilePictureUrl,
                        createdAt = createdAtLong
                    )
                    Log.d(TAG, "Utilisateur converti (getUserById): $user")
                    trySend(Resource.Success(user))

                } catch (e: Exception) {
                    Log.e(TAG, "Erreur de conversion du document utilisateur ${documentSnapshot.id} (getUserById): ${e.message}", e)
                    trySend(Resource.Error("Erreur de conversion des données utilisateur."))
                }
            } else {
                Log.w(TAG, "Aucun document trouvé pour l'utilisateur ID $userId (getUserById).")
                trySend(Resource.Error("Utilisateur non trouvé."))
            }
        }

        awaitClose {
            Log.d(TAG, "Fermeture du listener pour l'utilisateur ID $userId (getUserById).")
            listenerRegistration.remove()
        }
    }
}