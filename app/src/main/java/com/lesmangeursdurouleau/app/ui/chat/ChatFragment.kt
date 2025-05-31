package com.lesmangeursdurouleau.app.ui.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.lesmangeursdurouleau.app.R
import com.lesmangeursdurouleau.app.data.model.Message
import com.lesmangeursdurouleau.app.databinding.FragmentChatBinding
import com.lesmangeursdurouleau.app.ui.chat.adapter.ChatAdapter
import com.lesmangeursdurouleau.app.ui.chat.adapter.OnMessageInteractionListener
import com.lesmangeursdurouleau.app.ui.chat.adapter.OnProfileClickListener
import com.lesmangeursdurouleau.app.utils.Resource
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class ChatFragment : Fragment(), OnProfileClickListener, OnMessageInteractionListener {

    private var _binding: FragmentChatBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ChatViewModel by viewModels()
    private lateinit var chatAdapter: ChatAdapter
    private lateinit var linearLayoutManager: LinearLayoutManager

    // Pour le TextWatcher et le Handler du timeout de frappe
    private val typingHandler = Handler(Looper.getMainLooper())
    private var typingStoppedRunnable: Runnable? = null // Renamed for clarity to match ViewModel's logic

    // Variables pour la gestion de la pagination et du scroll
    private var isLoadingHistory = false
    private var isScrollingProgrammatically = false

    // Variables pour maintenir la position de scroll
    private var savedScrollPosition = -1
    private var savedScrollOffset = 0

    companion object {
        private const val TAG_FRAGMENT = "ChatFragment"
        // UI debounce for typing indicator can be shorter than ViewModel's backend timeout
        private const val TYPING_UI_DEBOUNCE_MS = 1000L
        private const val LOAD_MORE_THRESHOLD = 5 // Seuil pour déclencher le chargement
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentChatBinding.inflate(inflater, container, false)
        Log.d(TAG_FRAGMENT, "onCreateView")
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d(TAG_FRAGMENT, "onViewCreated")
        setupRecyclerView()
        setupInputTextWatcher()
        setupClickListeners()
        setupObservers()
    }

    private fun setupRecyclerView() {
        Log.d(TAG_FRAGMENT, "setupRecyclerView")
        chatAdapter = ChatAdapter(this, this)
        linearLayoutManager = LinearLayoutManager(requireContext()).apply {
            stackFromEnd = true
        }

        // Changed ID from rv_chat_messages to recyclerView_chat to match layout
        binding.recyclerViewChat.apply {
            adapter = chatAdapter
            layoutManager = linearLayoutManager

            // Ajout du ScrollListener pour la pagination
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    super.onScrolled(recyclerView, dx, dy)

                    // Ne pas déclencher le chargement si on scroll par programmation
                    if (isScrollingProgrammatically) return

                    // Vérifier si on scroll vers le haut (dy < 0)
                    if (dy < 0) {
                        val firstVisibleItemPosition = linearLayoutManager.findFirstVisibleItemPosition()

                        // Déclencher le chargement si on est proche du début et pas déjà en train de charger
                        if (firstVisibleItemPosition <= LOAD_MORE_THRESHOLD &&
                            !isLoadingHistory &&
                            !viewModel.allOldMessagesLoaded) {

                            Log.d(TAG_FRAGMENT, "Déclenchement du chargement de l'historique")
                            loadPreviousMessages()
                        }
                    }
                }
            })
        }
    }

    private fun setupInputTextWatcher() {
        // Changed ID from et_chat_message_input to et_message_input to match layout
        binding.etMessageInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                // Remove any pending callbacks from the handler first
                typingStoppedRunnable?.let { typingHandler.removeCallbacks(it) }

                if (s.toString().trim().isNotEmpty()) {
                    viewModel.userStartedTyping()
                } else {
                    // If text becomes empty, user has stopped typing immediately
                    viewModel.userStoppedTyping()
                }
            }
            override fun afterTextChanged(s: Editable?) {
                if (s.toString().trim().isNotEmpty()) {
                    // Schedule a delayed callback for 'stopped typing'
                    // This ensures that if the user pauses, 'stopped typing' is sent after a delay
                    typingStoppedRunnable = Runnable { viewModel.userStoppedTyping() }
                    typingHandler.postDelayed(typingStoppedRunnable!!, TYPING_UI_DEBOUNCE_MS)
                }
            }
        })
    }

    private fun setupClickListeners() {
        Log.d(TAG_FRAGMENT, "setupClickListeners")
        // Changed ID from btn_send_message to btn_send to match layout
        binding.btnSend.setOnClickListener {
            val messageText = binding.etMessageInput.text.toString().trim()
            if (messageText.isNotEmpty()) {
                Log.d(TAG_FRAGMENT, "Bouton Envoyer cliqué avec le texte: \"$messageText\"")
                viewModel.sendMessage(messageText)
            } else {
                Log.w(TAG_FRAGMENT, "Tentative d'envoi d'un message vide.")
                // Ensure this string resource exists in strings.xml
                Toast.makeText(requireContext(), R.string.message_cannot_be_empty, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupObservers() {
        Log.d(TAG_FRAGMENT, "setupObservers")

        // Observateur pour les messages principaux (temps réel)
        viewModel.messages.observe(viewLifecycleOwner) { resource ->
            Log.d(TAG_FRAGMENT, "Observation des messages: $resource")
            when (resource) {
                is Resource.Loading -> {
                    // Optionnel : afficher un indicateur de chargement initial
                    showLoadingIndicator(true) // For initial load
                }
                is Resource.Success -> {
                    showLoadingIndicator(false) // Hide initial loading
                    val messages = resource.data
                    if (messages.isNullOrEmpty()) {
                        chatAdapter.submitList(emptyList())
                        binding.tvChatEmptyMessage.visibility = View.VISIBLE // Show empty message if no messages
                    } else {
                        binding.tvChatEmptyMessage.visibility = View.GONE // Hide empty message
                        val currentList = chatAdapter.currentList
                        chatAdapter.submitList(messages.toList()) {
                            // Auto-scroll uniquement pour les nouveaux messages (pas l'historique)
                            // Scroll to bottom if current list was empty, or if new messages arrived
                            // and user is near the bottom, or not actively scrolling up.
                            // Better scroll logic for new messages:
                            val lastVisiblePosition = linearLayoutManager.findLastVisibleItemPosition()
                            val totalItemCount = linearLayoutManager.itemCount
                            val isAtBottom = lastVisiblePosition == totalItemCount - 1 || totalItemCount == 0

                            if (isAtBottom || currentList.isEmpty() || messages.size > currentList.size) {
                                binding.recyclerViewChat.smoothScrollToPosition(chatAdapter.itemCount - 1)
                            }
                        }
                    }
                }
                is Resource.Error -> {
                    showLoadingIndicator(false) // Hide initial loading
                    Log.e(TAG_FRAGMENT, "Erreur lors du chargement des messages: ${resource.message}")
                    Toast.makeText(requireContext(), "Erreur: ${resource.message}", Toast.LENGTH_LONG).show()
                    binding.tvChatEmptyMessage.visibility = View.VISIBLE // Can show empty message or error state
                }
            }
        }

        // Observateur pour l'historique des messages (pagination)
        viewModel.oldMessagesState.observe(viewLifecycleOwner) { resource ->
            Log.d(TAG_FRAGMENT, "Observation de l'historique: $resource")
            when (resource) {
                is Resource.Loading -> {
                    isLoadingHistory = true
                    // Optionnel : afficher un indicateur de chargement en haut
                    showLoadingIndicator(true) // For loading more history
                }
                is Resource.Success -> {
                    isLoadingHistory = false
                    showLoadingIndicator(false)

                    val oldMessages = resource.data
                    if (!oldMessages.isNullOrEmpty()) {
                        addOldMessagesToList(oldMessages)
                    }
                }
                is Resource.Error -> {
                    isLoadingHistory = false
                    showLoadingIndicator(false)
                    Log.e(TAG_FRAGMENT, "Erreur lors du chargement de l'historique: ${resource.message}")
                    Toast.makeText(requireContext(), "Erreur historique: ${resource.message}", Toast.LENGTH_SHORT).show()
                }
                null -> {
                    isLoadingHistory = false
                    showLoadingIndicator(false)
                }
            }
        }

        // Observateur pour les détails utilisateur
        viewModel.userDetailsCache.observe(viewLifecycleOwner) { userMap ->
            chatAdapter.setUserDetails(userMap)
        }

        // Observateur pour le statut d'envoi de message
        viewModel.sendMessageStatus.observe(viewLifecycleOwner) { resource ->
            // Changed ID from et_chat_message_input to et_message_input
            binding.btnSend.isEnabled = resource !is Resource.Loading
            binding.etMessageInput.isEnabled = resource !is Resource.Loading

            when (resource) {
                is Resource.Loading -> {
                    // Optionnel : afficher un indicateur de chargement
                }
                is Resource.Success -> {
                    binding.etMessageInput.text.clear()
                    // The ViewModel handles calling userStoppedTyping after sending
                    // No need to call it here again explicitly, unless you want an immediate UI reset.
                }
                is Resource.Error -> {
                    Toast.makeText(requireContext(), "Erreur envoi: ${resource.message}", Toast.LENGTH_LONG).show()
                }
                null -> {} // Do nothing when null
            }

            if (resource != null && resource !is Resource.Loading) {
                viewModel.clearSendMessageStatus()
            }
        }

        // Observateur pour le statut de suppression
        viewModel.deleteMessageStatus.observe(viewLifecycleOwner) { resource ->
            Log.d(TAG_FRAGMENT, "Observation statut suppression: $resource")
            when (resource) {
                is Resource.Loading -> Toast.makeText(requireContext(), R.string.deleting_message, Toast.LENGTH_SHORT).show()
                is Resource.Success -> Toast.makeText(requireContext(), R.string.message_deleted_successfully, Toast.LENGTH_SHORT).show()
                is Resource.Error -> Toast.makeText(requireContext(), "Erreur suppression: ${resource.message}", Toast.LENGTH_LONG).show()
                null -> {}
            }
            if (resource != null && resource !is Resource.Loading) {
                viewModel.clearDeleteMessageStatus()
            }
        }

        // Observateur pour les utilisateurs en train d'écrire
        viewModel.typingUsers.observe(viewLifecycleOwner) { typingUserIds ->
            Log.d(TAG_FRAGMENT, "Utilisateurs en train d'écrire: ${typingUserIds.joinToString()}")
            val currentUserUid = viewModel.firebaseAuth.currentUser?.uid
            val otherTypingUsers = typingUserIds.filter { it != currentUserUid }

            if (otherTypingUsers.isNotEmpty()) {
                val textToShow = if (otherTypingUsers.size == 1) {
                    getString(R.string.single_member_typing)
                } else {
                    getString(R.string.multiple_members_typing)
                }
                binding.tvTypingIndicator.text = textToShow
                binding.tvTypingIndicator.visibility = View.VISIBLE
            } else {
                binding.tvTypingIndicator.visibility = View.GONE
            }
        }
    }

    private fun loadPreviousMessages() {
        Log.d(TAG_FRAGMENT, "loadPreviousMessages appelé")
        viewModel.loadPreviousMessages()
    }

    private fun addOldMessagesToList(oldMessages: List<Message>) {
        Log.d(TAG_FRAGMENT, "Ajout de ${oldMessages.size} messages d'historique")

        // Sauvegarder la position actuelle avant d'ajouter les messages
        saveScrollPosition()

        val currentList = chatAdapter.currentList.toMutableList()
        // Ajouter les anciens messages AU DÉBUT de la liste
        // Using distinctBy to prevent duplicates if any message is fetched again
        val newList = (oldMessages + currentList).distinctBy { it.messageId }.sortedBy { it.timestamp }

        isScrollingProgrammatically = true
        chatAdapter.submitList(newList) {
            // Restaurer la position de scroll après que la liste soit mise à jour
            restoreScrollPosition(oldMessages.size)
            isScrollingProgrammatically = false
        }
    }

    private fun saveScrollPosition() {
        val firstVisiblePosition = linearLayoutManager.findFirstVisibleItemPosition()
        if (firstVisiblePosition != RecyclerView.NO_POSITION) {
            savedScrollPosition = firstVisiblePosition
            val firstVisibleView = linearLayoutManager.findViewByPosition(firstVisiblePosition)
            savedScrollOffset = firstVisibleView?.top ?: 0
            Log.d(TAG_FRAGMENT, "Position sauvegardée: $savedScrollPosition, offset: $savedScrollOffset")
        }
    }

    private fun restoreScrollPosition(newItemsCount: Int) {
        if (savedScrollPosition != -1) {
            val newPosition = savedScrollPosition + newItemsCount
            // Ensure the new position is valid before scrolling
            if (newPosition >= 0 && newPosition < chatAdapter.itemCount) {
                linearLayoutManager.scrollToPositionWithOffset(newPosition, savedScrollOffset)
                Log.d(TAG_FRAGMENT, "Position restaurée: $newPosition (ajout de $newItemsCount items)")
            } else {
                // Fallback: if calculated position is invalid, scroll to bottom
                Log.w(TAG_FRAGMENT, "Calculated scroll position invalid ($newPosition), scrolling to bottom.")
                binding.recyclerViewChat.scrollToPosition(chatAdapter.itemCount - 1)
            }
        }
    }

    private fun showLoadingIndicator(show: Boolean) {
        // Toggles the visibility of your ProgressBar
        binding.progressBarChat.visibility = if (show) View.VISIBLE else View.GONE
        Log.d(TAG_FRAGMENT, "Indicateur de chargement: ${if (show) "VISIBLE" else "GONE"}")
    }

    // Implémentation de OnProfileClickListener
    override fun onProfileClicked(userId: String, username: String) {
        Log.d(TAG_FRAGMENT, "onProfileClicked: userId=$userId, username=$username")
        try {
            // Ensure you have a navigation action defined in your nav graph
            // example: <action android:id="@+id/action_chatFragment_to_publicProfileFragment" app:destination="@id/publicProfileFragment" />
            // and the arguments are correctly passed
            val action = ChatFragmentDirections.actionChatFragmentToPublicProfileFragment(userId, username)
            findNavController().navigate(action)
        } catch (e: Exception) {
            Log.e(TAG_FRAGMENT, "Erreur de navigation vers le profil public: ${e.localizedMessage}", e)
            Toast.makeText(requireContext(), "Impossible d'ouvrir le profil.", Toast.LENGTH_SHORT).show()
        }
    }

    // Implémentation de OnMessageInteractionListener
    override fun onMessageLongClicked(message: Message, anchorView: View) {
        Log.d(TAG_FRAGMENT, "onMessageLongClicked: messageId=${message.messageId}")
        val popupMenu = PopupMenu(requireContext(), anchorView)
        // Ensure these string resources exist in strings.xml
        popupMenu.menu.add(0, R.id.menu_item_copy_text, 0, getString(R.string.copy_text_action))

        if (message.senderId == viewModel.firebaseAuth.currentUser?.uid) {
            popupMenu.menu.add(0, R.id.menu_item_delete_message, 1, getString(R.string.delete_message_action))
        }

        popupMenu.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.menu_item_copy_text -> {
                    copyTextToClipboard(message.text)
                    true
                }
                R.id.menu_item_delete_message -> {
                    if (message.messageId.isNotBlank()) {
                        viewModel.deleteMessage(message.messageId)
                    } else {
                        Toast.makeText(requireContext(), "ID message invalide", Toast.LENGTH_SHORT).show()
                    }
                    true
                }
                else -> false
            }
        }
        popupMenu.show()
    }

    private fun copyTextToClipboard(text: String) {
        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("message_text", text)
        clipboard.setPrimaryClip(clip)
        // Ensure this string resource exists in strings.xml
        Toast.makeText(requireContext(), R.string.text_copied_to_clipboard, Toast.LENGTH_SHORT).show()
    }

    override fun onStop() {
        super.onStop()
        Log.d(TAG_FRAGMENT, "onStop: Appel de viewModel.userStoppedTyping()")
        viewModel.userStoppedTyping()
    }

    override fun onDestroyView() {
        Log.d(TAG_FRAGMENT, "onDestroyView")
        viewModel.userStoppedTyping() // Ensure typing status is reset when the view is destroyed
        typingStoppedRunnable?.let { typingHandler.removeCallbacks(it) } // Remove any pending UI-level callbacks
        typingStoppedRunnable = null
        binding.recyclerViewChat.adapter = null // Clear adapter to prevent memory leaks
        super.onDestroyView()
        _binding = null
    }
}