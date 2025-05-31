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
import kotlinx.coroutines.tasks.await
import java.util.Date // Assure-toi que cet import est là
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val firebaseAuth: FirebaseAuth
) : ChatRepository {

    companion object {
        private const val TAG = "ChatRepositoryImpl"
        const val MESSAGES_INITIAL_LIMIT = 50L
        private const val MESSAGES_PAGINATION_LIMIT = 20L // Nombre de messages à charger par page d'historique
    }

    override fun getGeneralChatMessages(): Flow<Resource<List<Message>>> = callbackFlow {
        Log.d(TAG, "getGeneralChatMessages (Firestore) appelé.")
        trySend(Resource.Loading())
        val query = firestore.collection(FirebaseConstants.COLLECTION_GENERAL_CHAT)
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .limitToLast(MESSAGES_INITIAL_LIMIT) // Limite initiale

        // ... (reste de getGeneralChatMessages inchangé) ...
        Log.d(TAG, "getGeneralChatMessages (Firestore): Requête créée pour la collection ${FirebaseConstants.COLLECTION_GENERAL_CHAT}")
        val listenerRegistration = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e(TAG, "getGeneralChatMessages (Firestore): Erreur d'écoute - ${error.localizedMessage}", error)
                trySend(Resource.Error("Erreur Firestore: ${error.localizedMessage ?: "Erreur inconnue"}"))
                close(error)
                return@addSnapshotListener
            }
            if (snapshot != null) {
                val messages = mutableListOf<Message>()
                Log.d(TAG, "getGeneralChatMessages (Firestore): Snapshot reçu avec ${snapshot.documents.size} documents.")
                for (document in snapshot.documents) {
                    try {
                        val msg = document.toObject(Message::class.java)
                        if (msg != null) {
                            messages.add(msg.copy(messageId = document.id))
                        } else {
                            Log.w(TAG, "getGeneralChatMessages (Firestore): Document ${document.id} converti en Message null.")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "getGeneralChatMessages (Firestore): Erreur de conversion du document ${document.id}", e)
                    }
                }
                Log.i(TAG, "getGeneralChatMessages (Firestore): ${messages.size} messages traités et prêts à être émis.")
                trySend(Resource.Success(messages))
            } else {
                Log.d(TAG, "getGeneralChatMessages (Firestore): Snapshot est null, émission d'une liste vide.")
                trySend(Resource.Success(emptyList()))
            }
        }
        awaitClose {
            Log.d(TAG, "getGeneralChatMessages (Firestore): Fermeture du listener Snapshot.")
            listenerRegistration.remove()
        }
    }

    // NOUVELLE IMPLÉMENTATION
    override fun getPreviousChatMessages(oldestMessageTimestamp: Date, limit: Long): Flow<Resource<List<Message>>> = callbackFlow {
        Log.d(TAG, "getPreviousChatMessages (Firestore) appelé. Timestamp pivot: $oldestMessageTimestamp, Limite: $limit")
        trySend(Resource.Loading())

        val query = firestore.collection(FirebaseConstants.COLLECTION_GENERAL_CHAT)
            .orderBy("timestamp", Query.Direction.DESCENDING) // Messages du plus récent au plus ancien
            .startAfter(oldestMessageTimestamp) // Commencer APRÈS le timestamp du plus ancien message affiché
            // Note: Pour firestore, startAfter() avec un timestamp signifie "commencer après CE timestamp".
            // Puisque nous trions par DESCENDING, cela nous donne les messages plus anciens.
            .limit(limit) // Limiter le nombre de messages récupérés

        Log.d(TAG, "getPreviousChatMessages (Firestore): Requête créée.")

        // Pas besoin d'un listener en temps réel ici, une simple récupération suffit pour l'historique.
        // Mais pour la cohérence du retour (Flow<Resource<...>>), on peut utiliser callbackFlow ou un simple flow { try { emit(Success) } catch { emit(Error) } }
        // Utiliser get() pour une seule lecture
        query.get()
            .addOnSuccessListener { snapshot ->
                if (snapshot != null) {
                    val messages = mutableListOf<Message>()
                    Log.d(TAG, "getPreviousChatMessages (Firestore): Snapshot reçu avec ${snapshot.documents.size} documents d'historique.")
                    for (document in snapshot.documents) {
                        try {
                            val msg = document.toObject(Message::class.java)
                            if (msg != null) {
                                messages.add(msg.copy(messageId = document.id))
                            } else {
                                Log.w(TAG, "getPreviousChatMessages (Firestore): Document ${document.id} converti en Message null.")
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "getPreviousChatMessages (Firestore): Erreur de conversion du document ${document.id}", e)
                        }
                    }
                    // Les messages sont récupérés en ordre DESCENDING (plus récent des anciens en premier).
                    // Pour les ajouter en haut de la liste existante, il faut les inverser pour avoir le plus ancien de ce lot en premier.
                    Log.i(TAG, "getPreviousChatMessages (Firestore): ${messages.size} messages d'historique traités.")
                    trySend(Resource.Success(messages.reversed()))
                } else {
                    Log.d(TAG, "getPreviousChatMessages (Firestore): Snapshot est null, émission d'une liste vide.")
                    trySend(Resource.Success(emptyList()))
                }
                close() // Fermer le flow après avoir émis les données car ce n'est pas un listener continu
            }
            .addOnFailureListener { error ->
                Log.e(TAG, "getPreviousChatMessages (Firestore): Erreur de récupération - ${error.localizedMessage}", error)
                trySend(Resource.Error("Erreur Firestore: ${error.localizedMessage ?: "Erreur inconnue"}"))
                close(error)
            }
        // callbackFlow nécessite awaitClose
        awaitClose { Log.d(TAG, "getPreviousChatMessages (Firestore): Flow fermé.") }
    }


    override suspend fun sendGeneralChatMessage(message: Message): Resource<Unit> {
        // ... (code de sendGeneralChatMessage inchangé) ...
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
            messageId = "",
            senderId = currentUser.uid,
            senderUsername = currentUser.displayName ?: "Utilisateur Anonyme",
            timestamp = null
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

    override suspend fun deleteChatMessage(messageId: String): Resource<Unit> {
        // ... (code de deleteChatMessage inchangé) ...
        if (messageId.isBlank()) {
            Log.e(TAG, "deleteChatMessage: messageId est vide.")
            return Resource.Error("ID de message invalide.")
        }
        Log.d(TAG, "deleteChatMessage: Tentative de suppression du message ID: $messageId")
        return try {
            firestore.collection(FirebaseConstants.COLLECTION_GENERAL_CHAT)
                .document(messageId)
                .delete()
                .await()
            Log.i(TAG, "deleteChatMessage: Message ID: $messageId supprimé avec succès de Firestore.")
            Resource.Success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "deleteChatMessage: Erreur lors de la suppression du message ID: $messageId - ${e.localizedMessage}", e)
            Resource.Error("Erreur lors de la suppression du message: ${e.localizedMessage}")
        }
    }
}