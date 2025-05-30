package com.lesmangeursdurouleau.app.ui.chat

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.lesmangeursdurouleau.app.data.model.Message
import com.lesmangeursdurouleau.app.data.model.User
import com.lesmangeursdurouleau.app.data.repository.ChatRepository
import com.lesmangeursdurouleau.app.data.repository.UserRepository
import com.lesmangeursdurouleau.app.utils.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val userRepository: UserRepository,
    private val firebaseAuth: FirebaseAuth
) : ViewModel() {

    private val _messages = MutableLiveData<Resource<List<Message>>>()
    val messages: LiveData<Resource<List<Message>>> = _messages

    private val _sendMessageStatus = MutableLiveData<Resource<Unit>?>()
    val sendMessageStatus: LiveData<Resource<Unit>?> = _sendMessageStatus

    private val _userDetailsCache = MutableLiveData<Map<String, User>>(emptyMap())
    val userDetailsCache: LiveData<Map<String, User>> = _userDetailsCache

    init {
        Log.d("ChatViewModel", "ViewModel initialisé.")
        loadMessages()
    }

    private fun loadMessages() {
        Log.d("ChatViewModel", "loadMessages appelé.")
        viewModelScope.launch {
            chatRepository.getGeneralChatMessages()
                .catch { e ->
                    Log.e("ChatViewModel", "Exception dans le flow getGeneralChatMessages", e)
                    _messages.postValue(Resource.Error("Erreur technique: ${e.localizedMessage}"))
                }
                .collectLatest { resource ->
                    Log.d("ChatViewModel", "Nouveau statut de messages reçu: $resource")
                    _messages.value = resource
                    if (resource is Resource.Success && resource.data != null) {
                        fetchUserDetailsForMessages(resource.data)
                    }
                }
        }
    }

    private fun fetchUserDetailsForMessages(messages: List<Message>) {
        viewModelScope.launch {
            val senderIds = messages.map { it.senderId }.distinct()
            val currentCache = _userDetailsCache.value ?: emptyMap()
            val newCache = currentCache.toMutableMap()
            var cacheUpdated = false

            for (senderId in senderIds) {
                if (senderId.isNotBlank() && !newCache.containsKey(senderId)) { // Vérifier aussi si senderId n'est pas vide
                    Log.d("ChatViewModel", "Récupération des détails pour l'utilisateur: $senderId")
                    val userResource = userRepository.getUserById(senderId)
                        .filterNotNull()
                        .firstOrNull { it is Resource.Success || it is Resource.Error }

                    if (userResource is Resource.Success && userResource.data != null) {
                        newCache[senderId] = userResource.data
                        cacheUpdated = true
                        Log.d("ChatViewModel", "Détails récupérés pour $senderId: ${userResource.data.username}")
                    } else if (userResource is Resource.Error) {
                        Log.e("ChatViewModel", "Erreur lors de la récupération des détails pour $senderId: ${userResource.message}")
                        // Optionnel: Stocker un User placeholder avec un nom d'erreur ou laisser vide
                        // pour que l'adapter utilise "Utilisateur inconnu"
                    } else if (userResource == null) {
                        Log.w("ChatViewModel", "Aucune ressource (Succès/Erreur) reçue pour $senderId après filterNotNull/firstOrNull.")
                    }
                }
            }
            if (cacheUpdated) {
                _userDetailsCache.value = newCache
            }
        }
    }

    fun sendMessage(text: String) {
        Log.d("ChatViewModel", "sendMessage appelé avec le texte: \"$text\"")
        val currentUser = firebaseAuth.currentUser
        if (currentUser == null) {
            Log.e("ChatViewModel", "sendMessage: currentUser est null. Impossible d'envoyer.")
            _sendMessageStatus.value = Resource.Error("Utilisateur non authentifié pour envoyer un message.")
            return
        }
        Log.d("ChatViewModel", "sendMessage: currentUser UID: ${currentUser.uid}, DisplayName: ${currentUser.displayName}")

        if (text.isBlank()) {
            Log.w("ChatViewModel", "sendMessage: Tentative d'envoi d'un message vide.")
            _sendMessageStatus.value = Resource.Error("Le message ne peut pas être vide.")
            return
        }

        val message = Message(
            messageId = UUID.randomUUID().toString(),
            senderId = currentUser.uid,
            senderUsername = currentUser.displayName ?: "Utilisateur Anonyme", // CORRIGÉ : Chaîne en dur
            text = text.trim()
        )

        Log.d("ChatViewModel", "sendMessage: Création du message: $message")
        viewModelScope.launch {
            _sendMessageStatus.value = Resource.Loading()
            Log.d("ChatViewModel", "sendMessage: Envoi du message au repository...")
            try {
                val result = chatRepository.sendGeneralChatMessage(message)
                Log.d("ChatViewModel", "sendMessage: Résultat du repository: $result")
                _sendMessageStatus.value = result
                if (result is Resource.Success) {
                    Log.i("ChatViewModel", "Message envoyé avec succès (via ViewModel).")
                } else if (result is Resource.Error) {
                    Log.e("ChatViewModel", "Erreur lors de l'envoi du message (depuis repository): ${result.message}")
                }
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Exception non gérée lors de l'appel à chatRepository.sendGeneralChatMessage", e)
                _sendMessageStatus.value = Resource.Error("Erreur inattendue lors de l'envoi: ${e.localizedMessage}")
            }
        }
    }

    fun clearSendMessageStatus() {
        Log.d("ChatViewModel", "clearSendMessageStatus appelé.")
        _sendMessageStatus.value = null
    }
}