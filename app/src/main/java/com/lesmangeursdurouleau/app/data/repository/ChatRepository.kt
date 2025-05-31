package com.lesmangeursdurouleau.app.data.repository

import com.lesmangeursdurouleau.app.data.model.Message
import com.lesmangeursdurouleau.app.utils.Resource
import kotlinx.coroutines.flow.Flow
import java.util.Date // NOUVEL IMPORT

interface ChatRepository {
    /**
     * Récupère les messages initiaux du chat général en temps réel (les plus récents).
     */
    fun getGeneralChatMessages(): Flow<Resource<List<Message>>>

    /**
     * Envoie un nouveau message au chat général.
     */
    suspend fun sendGeneralChatMessage(message: Message): Resource<Unit>

    /**
     * Supprime un message spécifique du chat général.
     */
    suspend fun deleteChatMessage(messageId: String): Resource<Unit>

    /**
     * Récupère un lot de messages plus anciens que le timestamp fourni.
     * @param oldestMessageTimestamp Le timestamp du message le plus ancien actuellement affiché.
     *                               Les nouveaux messages récupérés seront antérieurs à celui-ci.
     * @param limit Le nombre maximum de messages à récupérer.
     */
    // NOUVELLE MÉTHODE
    fun getPreviousChatMessages(oldestMessageTimestamp: Date, limit: Long): Flow<Resource<List<Message>>>
}