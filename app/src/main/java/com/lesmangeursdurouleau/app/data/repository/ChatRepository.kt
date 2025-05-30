package com.lesmangeursdurouleau.app.data.repository

import com.lesmangeursdurouleau.app.data.model.Message
import com.lesmangeursdurouleau.app.utils.Resource
import kotlinx.coroutines.flow.Flow

interface ChatRepository {
    /**
     * Récupère les messages du chat général en temps réel.
     */
    fun getGeneralChatMessages(): Flow<Resource<List<Message>>>

    /**
     * Envoie un nouveau message au chat général.
     * @param message L'objet Message à envoyer. L'ID du message et le timestamp
     *                seront gérés par Firestore ou avant l'appel.
     */
    suspend fun sendGeneralChatMessage(message: Message): Resource<Unit>
}