package com.lesmangeursdurouleau.app.data.repository

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.lesmangeursdurouleau.app.data.model.Message
import com.lesmangeursdurouleau.app.remote.FirebaseConstants
import com.lesmangeursdurouleau.app.utils.Resource
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
// import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.tasks.await
// import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val firebaseAuth: FirebaseAuth
) : ChatRepository {

    companion object {
        private const val TAG = "ChatRepositoryImpl"
        private const val MESSAGES_LIMIT = 50L // Long pour limitToLast
    }

    // --- VRAIE IMPLÉMENTATION FIRESTORE POUR getGeneralChatMessages ---
    override fun getGeneralChatMessages(): Flow<Resource<List<Message>>> = callbackFlow {
        Log.d(TAG, "getGeneralChatMessages (Firestore) appelé.")
        trySend(Resource.Loading())

        val query = firestore.collection(FirebaseConstants.COLLECTION_GENERAL_CHAT)
            .orderBy("timestamp", Query.Direction.ASCENDING) // Messages du plus ancien au plus récent
            .limitToLast(MESSAGES_LIMIT) // Récupère les N derniers messages.
        // Convertir MESSAGES_LIMIT en Int si limitToLast l'exige strictement,
        // mais Long devrait fonctionner. Firestore SDK gère cela.

        Log.d(TAG, "getGeneralChatMessages (Firestore): Requête créée pour la collection ${FirebaseConstants.COLLECTION_GENERAL_CHAT}")

        val listenerRegistration = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e(TAG, "getGeneralChatMessages (Firestore): Erreur d'écoute - ${error.localizedMessage}", error)
                trySend(Resource.Error("Erreur Firestore: ${error.localizedMessage ?: "Erreur inconnue"}"))
                close(error) // Important de fermer le flow en cas d'erreur irrécupérable
                return@addSnapshotListener
            }

            if (snapshot != null) {
                val messages = mutableListOf<Message>()
                Log.d(TAG, "getGeneralChatMessages (Firestore): Snapshot reçu avec ${snapshot.documents.size} documents.")
                for (document in snapshot.documents) {
                    try {
                        val msg = document.toObject(Message::class.java)
                        if (msg != null) {
                            // Assigner l'ID du document au messageId car toObject ne le fait pas par défaut.
                            // Et s'assurer que le timestamp est bien un objet Date (géré par @ServerTimestamp et toObject)
                            messages.add(msg.copy(messageId = document.id))
                            Log.v(TAG, "Message converti: ID=${document.id}, Text=${msg.text}, Timestamp=${msg.timestamp}")
                        } else {
                            Log.w(TAG, "getGeneralChatMessages (Firestore): Document ${document.id} converti en Message null.")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "getGeneralChatMessages (Firestore): Erreur de conversion du document ${document.id}", e)
                    }
                }
                Log.i(TAG, "getGeneralChatMessages (Firestore): ${messages.size} messages traités et prêts à être émis.")
                trySend(Resource.Success(messages)) // La liste est déjà dans l'ordre chronologique (ASCENDING)
            } else {
                Log.d(TAG, "getGeneralChatMessages (Firestore): Snapshot est null, émission d'une liste vide.")
                trySend(Resource.Success(emptyList())) // Cas où le snapshot est null mais pas d'erreur
            }
        }
        // Cette partie est cruciale pour libérer les ressources lorsque le Flow n'est plus collecté.
        awaitClose {
            Log.d(TAG, "getGeneralChatMessages (Firestore): Fermeture du listener Snapshot.")
            listenerRegistration.remove()
        }
    }

    // --- VRAIE IMPLÉMENTATION FIRESTORE POUR sendGeneralChatMessage ---
    // (Cette version devrait déjà être celle que tu as et qui fonctionne pour l'envoi)
    override suspend fun sendGeneralChatMessage(message: Message): Resource<Unit> {
        val currentUser = firebaseAuth.currentUser
        if (currentUser == null) {
            Log.e(TAG, "sendGeneralChatMessage (Firestore): Utilisateur non authentifié.")
            return Resource.Error("Utilisateur non authentifié.")
        }
        if (message.text.isBlank()) {
            Log.e(TAG, "sendGeneralChatMessage (Firestore): Le message est vide.")
            return Resource.Error("Le message ne peut pas être vide.")
        }

        val messageToSend = message.copy(
            messageId = "", // Laisser vide pour que Firestore génère l'ID
            senderId = currentUser.uid,
            senderUsername = currentUser.displayName ?: "Utilisateur Anonyme",
            timestamp = null // Firestore remplira ceci grâce à @ServerTimestamp
        )
        Log.d(TAG, "sendGeneralChatMessage (Firestore): Tentative d'envoi du message: $messageToSend")

        return try {
            firestore.collection(FirebaseConstants.COLLECTION_GENERAL_CHAT)
                .add(messageToSend)
                .await()
            Log.i(TAG, "sendGeneralChatMessage (Firestore): Message envoyé avec succès à Firestore.")
            Resource.Success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "sendGeneralChatMessage (Firestore): Erreur d'envoi - ${e.localizedMessage}", e)
            Resource.Error("Erreur lors de l'envoi du message: ${e.localizedMessage}")
        }
    }
}