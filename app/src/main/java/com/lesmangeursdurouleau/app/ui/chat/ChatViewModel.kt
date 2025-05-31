package com.lesmangeursdurouleau.app.ui.chat

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore // AJOUT NÉCESSAIRE
import com.google.firebase.firestore.ListenerRegistration // AJOUT NÉCESSAIRE
import com.lesmangeursdurouleau.app.data.model.Message
import com.lesmangeursdurouleau.app.data.model.User
import com.lesmangeursdurouleau.app.data.repository.ChatRepository
import com.lesmangeursdurouleau.app.data.repository.UserRepository
import com.lesmangeursdurouleau.app.remote.FirebaseConstants // AJOUT NÉCESSAIRE
import com.lesmangeursdurouleau.app.utils.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.util.Date
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val userRepository: UserRepository,
    private val firestore: FirebaseFirestore, // AJOUTÉ: Injection de Firestore
    val firebaseAuth: FirebaseAuth
) : ViewModel() {

    private val _messages = MutableLiveData<Resource<List<Message>>>()
    val messages: LiveData<Resource<List<Message>>> = _messages

    private val _sendMessageStatus = MutableLiveData<Resource<Unit>?>()
    val sendMessageStatus: LiveData<Resource<Unit>?> = _sendMessageStatus

    private val _userDetailsCache = MutableLiveData<Map<String, User>>(emptyMap())
    val userDetailsCache: LiveData<Map<String, User>> = _userDetailsCache

    private val _deleteMessageStatus = MutableLiveData<Resource<Unit>?>()
    val deleteMessageStatus: LiveData<Resource<Unit>?> = _deleteMessageStatus

    // Variables pour la pagination (gardées pour quand on y reviendra)
    private val _oldMessagesState = MutableLiveData<Resource<List<Message>>?>()
    val oldMessagesState: LiveData<Resource<List<Message>>?> = _oldMessagesState
    private var isLoadingMoreMessages = false
    private var allOldMessagesLoaded = false
    private var oldestLoadedMessageTimestamp: Date? = null

    private var typingTimeoutJob: Job? = null
    private var currentUserIsActuallyTyping = false

    private val _typingUsers = MutableLiveData<Set<String>>(emptySet())
    val typingUsers: LiveData<Set<String>> = _typingUsers

    // AJOUT: Listener pour les statuts de frappe des autres
    private var typingStatusListener: ListenerRegistration? = null


    companion object {
        private const val TAG = "ChatViewModel"
        private const val PAGINATION_LIMIT = 20L
        private const val TYPING_STATUS_TIMEOUT_MS = 3000L
    }

    init {
        Log.d(TAG, "ViewModel initialisé.")
        loadInitialMessages()
        observeOtherUsersTypingStatus() // AJOUT: Appel à la nouvelle fonction
    }

    // --- Gestion des Messages Initiaux et Pagination ---
    private fun loadInitialMessages() {
        Log.d(TAG, "loadInitialMessages appelé.")
        viewModelScope.launch {
            chatRepository.getGeneralChatMessages()
                .catch { e -> Log.e(TAG, "Exception dans getGeneralChatMessages", e); _messages.postValue(Resource.Error("Erreur tech.: ${e.localizedMessage}")) }
                .collectLatest { resource ->
                    Log.d(TAG, "Messages initiaux/nouveaux reçus: $resource")
                    if (resource is Resource.Success) {
                        val newMessages = resource.data ?: emptyList()
                        if (newMessages.isNotEmpty()) {
                            oldestLoadedMessageTimestamp = newMessages.first().timestamp
                            allOldMessagesLoaded = newMessages.size < 50 // Approx. basé sur MESSAGES_INITIAL_LIMIT
                        } else {
                            allOldMessagesLoaded = true
                        }
                        fetchUserDetailsForMessages(newMessages)
                    }
                    _messages.value = resource
                }
        }
    }
    fun loadPreviousMessages() { /* ... (logique de pagination, à réactiver plus tard) ... */ }

    // --- Gestion des Détails Utilisateurs ---
    private fun fetchUserDetailsForMessages(messages: List<Message>) {
        viewModelScope.launch {
            val senderIds = messages.map { it.senderId }.distinct()
            val currentCache = _userDetailsCache.value ?: emptyMap()
            val newCache = currentCache.toMutableMap()
            var cacheUpdated = false
            for (senderId in senderIds) {
                if (senderId.isNotBlank() && !newCache.containsKey(senderId)) {
                    val userResource = userRepository.getUserById(senderId).filterNotNull().firstOrNull { it is Resource.Success || it is Resource.Error }
                    if (userResource is Resource.Success && userResource.data != null) {
                        newCache[senderId] = userResource.data; cacheUpdated = true
                    } else if (userResource is Resource.Error) {
                        Log.e(TAG, "Erreur récupération détails pour $senderId: ${userResource.message}")
                    }
                }
            }
            if (cacheUpdated) { _userDetailsCache.value = newCache }
        }
    }

    // --- Gestion de l'Envoi de Message ---
    fun sendMessage(text: String) {
        Log.i(TAG, "sendMessage DANS ViewModel: \"$text\"")
        val currentUser = firebaseAuth.currentUser
        if (currentUser == null) { _sendMessageStatus.value = Resource.Error("Non authentifié."); return }
        if (text.isBlank()) { _sendMessageStatus.value = Resource.Error("Message vide."); return }
        userStoppedTyping()
        val message = Message(messageId = "", senderId = currentUser.uid, senderUsername = currentUser.displayName ?: "Utilisateur Anonyme", text = text.trim())
        viewModelScope.launch {
            _sendMessageStatus.value = Resource.Loading()
            val result = chatRepository.sendGeneralChatMessage(message)
            _sendMessageStatus.value = result
        }
    }
    fun clearSendMessageStatus() { _sendMessageStatus.value = null }

    // --- Gestion de la Suppression de Message ---
    fun deleteMessage(messageId: String) {
        if (messageId.isBlank()){ _deleteMessageStatus.value = Resource.Error("ID message invalide."); return }
        viewModelScope.launch {
            _deleteMessageStatus.value = Resource.Loading()
            val result = chatRepository.deleteChatMessage(messageId)
            _deleteMessageStatus.value = result
        }
    }
    fun clearDeleteMessageStatus() { _deleteMessageStatus.value = null }

    // --- Gestion du Statut "En train d'écrire" pour l'Utilisateur Actuel ---
    fun userStartedTyping() {
        val userId = firebaseAuth.currentUser?.uid ?: return
        typingTimeoutJob?.cancel()
        if (!currentUserIsActuallyTyping) {
            currentUserIsActuallyTyping = true
            updateCurrentUserTypingStatus(true)
            Log.d(TAG, "userStartedTyping: Statut mis à true pour $userId")
        }
        typingTimeoutJob = viewModelScope.launch {
            delay(TYPING_STATUS_TIMEOUT_MS)
            if (currentUserIsActuallyTyping) {
                Log.d(TAG, "Timeout de frappe atteint pour $userId. Mise à jour du statut à false.")
                currentUserIsActuallyTyping = false
                updateCurrentUserTypingStatus(false)
            }
        }
    }

    fun userStoppedTyping() {
        val userId = firebaseAuth.currentUser?.uid ?: return
        typingTimeoutJob?.cancel()
        if (currentUserIsActuallyTyping) {
            currentUserIsActuallyTyping = false
            updateCurrentUserTypingStatus(false)
            Log.d(TAG, "userStoppedTyping: Statut mis à false pour $userId")
        }
    }

    private fun updateCurrentUserTypingStatus(isTyping: Boolean) {
        val userId = firebaseAuth.currentUser?.uid ?: return
        viewModelScope.launch {
            Log.d(TAG, "Mise à jour de isTypingInGeneralChat à $isTyping pour $userId dans Firestore.")
            val result = userRepository.updateUserTypingStatus(userId, isTyping)
            if (result is Resource.Error) {
                Log.e(TAG, "Erreur Firestore MAJ statut frappe pour $userId: ${result.message}")
            }
        }
    }

    // AJOUT: Nouvelle fonction pour observer le statut de frappe des autres
    private fun observeOtherUsersTypingStatus() {
        val currentUserId = firebaseAuth.currentUser?.uid ?: return

        Log.d(TAG, "Initialisation de l'écouteur pour les statuts de frappe des autres utilisateurs.")
        typingStatusListener?.remove() // S'assurer de détacher l'ancien s'il existe

        typingStatusListener = firestore.collection(FirebaseConstants.COLLECTION_USERS)
            .whereEqualTo("isTypingInGeneralChat", true)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Erreur écoute statut de frappe: ${error.message}", error)
                    _typingUsers.value = emptySet()
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val typingUserIds = mutableSetOf<String>()
                    for (doc in snapshot.documents) {
                        if (doc.id != currentUserId) { // Exclure l'utilisateur actuel
                            typingUserIds.add(doc.id)
                        }
                    }
                    Log.d(TAG, "Statuts de frappe reçus. Autres utilisateurs qui tapent: ${typingUserIds.joinToString()}")
                    _typingUsers.value = typingUserIds
                } else {
                    Log.d(TAG, "Snapshot pour statuts de frappe est null.")
                    _typingUsers.value = emptySet()
                }
            }
    }

    override fun onCleared() {
        super.onCleared()
        Log.d(TAG, "onCleared: ViewModel détruit.")
        userStoppedTyping()
        typingTimeoutJob?.cancel()
        typingTimeoutJob = null
        typingStatusListener?.remove() // AJOUT: Détacher le listener ici
        typingStatusListener = null
        Log.d(TAG, "Listener de statut de frappe détaché dans onCleared.")
    }
}