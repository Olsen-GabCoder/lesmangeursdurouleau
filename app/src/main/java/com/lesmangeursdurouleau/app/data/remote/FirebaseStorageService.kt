package com.lesmangeursdurouleau.app.data.remote

import android.util.Log
import com.google.firebase.storage.FirebaseStorage
import com.lesmangeursdurouleau.app.utils.Resource
import kotlinx.coroutines.tasks.await
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton // Important pour la portée avec Hilt
class FirebaseStorageService @Inject constructor(
    private val storage: FirebaseStorage // Uniquement le paramètre, pas d'initialisation ici
) {
    companion object {
        private const val TAG = "FirebaseStorageService"
    }

    suspend fun uploadProfilePicture(userId: String, imageData: ByteArray): Resource<String> {
        return try {
            val storageRef = storage.reference.child("profile_pictures/$userId/${UUID.randomUUID()}.jpg")
            Log.d(TAG, "Début de l'upload de la photo pour l'utilisateur $userId")
            val uploadTask = storageRef.putBytes(imageData).await()
            val downloadUrl = storageRef.downloadUrl.await().toString()
            Log.d(TAG, "Photo uploadée avec succès, URL: $downloadUrl")
            Resource.Success(downloadUrl)
        } catch (e: Exception) {
            Log.e(TAG, "Erreur lors de l'upload de la photo: ${e.message}", e)
            Resource.Error("Erreur lors de l'upload de la photo: ${e.localizedMessage}")
        }
    }
}