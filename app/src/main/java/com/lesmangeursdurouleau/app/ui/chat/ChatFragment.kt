package com.lesmangeursdurouleau.app.ui.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.drawable.Animatable
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
// import androidx.recyclerview.widget.RecyclerView // Pas d'utilisation directe
import com.lesmangeursdurouleau.app.R
import com.lesmangeursdurouleau.app.data.model.Message
import com.lesmangeursdurouleau.app.data.model.User
import com.lesmangeursdurouleau.app.databinding.FragmentChatBinding
import com.lesmangeursdurouleau.app.ui.chat.adapter.ChatAdapter
import com.lesmangeursdurouleau.app.ui.chat.adapter.OnMessageInteractionListener // AJOUTÉ
import com.lesmangeursdurouleau.app.ui.chat.adapter.OnProfileClickListener
import com.lesmangeursdurouleau.app.utils.Resource
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class ChatFragment : Fragment(), OnProfileClickListener, OnMessageInteractionListener { // AJOUTÉ OnMessageInteractionListener

    private var _binding: FragmentChatBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ChatViewModel by viewModels()
    private lateinit var chatAdapter: ChatAdapter
    // lateinit var linearLayoutManager: LinearLayoutManager // Pas besoin en tant que membre

    // AJOUTÉ: Pour le TextWatcher et le Handler du timeout de frappe
    private val typingHandler = Handler(Looper.getMainLooper())
    private var typingRunnable: Runnable? = null

    companion object {
        private const val TAG_FRAGMENT = "ChatFragment"
        private const val TYPING_UI_DEBOUNCE_MS = 1500L
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
        setupInputTextWatcher() // AJOUTÉ: Appel
        setupClickListeners()
        setupObservers()
    }

    private fun setupRecyclerView() {
        Log.d(TAG_FRAGMENT, "setupRecyclerView")
        // AJOUTÉ: Passe 'this' pour le nouveau listener aussi
        chatAdapter = ChatAdapter(this, this)
        val linearLayoutManager = LinearLayoutManager(requireContext()).apply {
            stackFromEnd = true
        }
        binding.rvChatMessages.apply {
            adapter = chatAdapter
            layoutManager = linearLayoutManager
        }
    }

    // AJOUTÉ: Fonction pour le TextWatcher
    private fun setupInputTextWatcher() {
        binding.etChatMessageInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                typingRunnable?.let { typingHandler.removeCallbacks(it) }
                if (s.toString().trim().isNotEmpty()) {
                    viewModel.userStartedTyping()
                } else {
                    viewModel.userStoppedTyping()
                }
            }
            override fun afterTextChanged(s: Editable?) {
                if (s.toString().trim().isNotEmpty()) {
                    typingRunnable = Runnable { viewModel.userStoppedTyping() }
                    typingHandler.postDelayed(typingRunnable!!, TYPING_UI_DEBOUNCE_MS)
                }
            }
        })
    }

    private fun setupClickListeners() {
        Log.d(TAG_FRAGMENT, "setupClickListeners")
        binding.btnSendMessage.setOnClickListener {
            val messageText = binding.etChatMessageInput.text.toString().trim()
            if (messageText.isNotEmpty()) {
                Log.d(TAG_FRAGMENT, "Bouton Envoyer cliqué avec le texte: \"$messageText\"")
                viewModel.sendMessage(messageText)
                // L'effacement du texte est géré par l'observateur de sendMessageStatus en cas de succès
            } else {
                Log.w(TAG_FRAGMENT, "Tentative d'envoi d'un message vide.")
                Toast.makeText(requireContext(), R.string.message_cannot_be_empty, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupObservers() {
        Log.d(TAG_FRAGMENT, "setupObservers")
        // Ton observateur viewModel.messages existant
        viewModel.messages.observe(viewLifecycleOwner) { resource ->
            Log.d(TAG_FRAGMENT, "Observation des messages: $resource")
            when (resource) {
                is Resource.Loading -> { /* ... */ }
                is Resource.Success -> { /* ... */
                    val messages = resource.data
                    if (messages.isNullOrEmpty()) { /* ... */ }
                    else {
                        val currentList = chatAdapter.currentList
                        chatAdapter.submitList(messages.toList()) {
                            if (messages.size > currentList.size || currentList.isEmpty()) {
                                if (messages.isNotEmpty()) {
                                    binding.rvChatMessages.smoothScrollToPosition(messages.size - 1)
                                }
                            }
                        }
                    }
                }
                is Resource.Error -> { /* ... */ }
            }
        }
        // Ton observateur viewModel.userDetailsCache existant
        viewModel.userDetailsCache.observe(viewLifecycleOwner) { userMap ->
            chatAdapter.setUserDetails(userMap)
        }
        // Ton observateur viewModel.sendMessageStatus existant
        viewModel.sendMessageStatus.observe(viewLifecycleOwner) { resource ->
            binding.btnSendMessage.isEnabled = resource !is Resource.Loading
            binding.etChatMessageInput.isEnabled = resource !is Resource.Loading
            when (resource) {
                is Resource.Loading -> { /* ... */ }
                is Resource.Success -> { binding.etChatMessageInput.text.clear() /* ... */ }
                is Resource.Error -> { /* ... */ }
                null -> { /* ... */ }
            }
            if (resource != null && resource !is Resource.Loading) {
                viewModel.clearSendMessageStatus()
            }
        }

        // AJOUTÉ: Observateur pour deleteMessageStatus
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

        // AJOUTÉ: Observateur pour typingUsers
        viewModel.typingUsers.observe(viewLifecycleOwner) { typingUserIds ->
            Log.d(TAG_FRAGMENT, "Utilisateurs en train d'écrire (observé): ${typingUserIds.joinToString()}")
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

    // Ton implémentation de OnProfileClickListener existante
    override fun onProfileClicked(userId: String, username: String) {
        Log.d(TAG_FRAGMENT, "onProfileClicked: userId=$userId, username=$username")
        try {
            val action = ChatFragmentDirections.actionChatFragmentToPublicProfileFragment(userId, username)
            findNavController().navigate(action)
        } catch (e: Exception) {
            Log.e(TAG_FRAGMENT, "Erreur de navigation vers le profil public: ${e.localizedMessage}", e)
            Toast.makeText(requireContext(), "Impossible d'ouvrir le profil.", Toast.LENGTH_SHORT).show()
        }
    }

    // AJOUTÉ: Implémentation de OnMessageInteractionListener
    override fun onMessageLongClicked(message: Message, anchorView: View) {
        Log.d(TAG_FRAGMENT, "onMessageLongClicked: messageId=${message.messageId}")
        val popupMenu = PopupMenu(requireContext(), anchorView)
        popupMenu.menu.add(0, R.id.menu_item_copy_text, 0, getString(R.string.copy_text_action))
        if (message.senderId == viewModel.firebaseAuth.currentUser?.uid) {
            popupMenu.menu.add(0, R.id.menu_item_delete_message, 1, getString(R.string.delete_message_action))
        }
        popupMenu.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.menu_item_copy_text -> { copyTextToClipboard(message.text); true }
                R.id.menu_item_delete_message -> {
                    if (message.messageId.isNotBlank()) viewModel.deleteMessage(message.messageId)
                    else Toast.makeText(requireContext(), "ID message invalide", Toast.LENGTH_SHORT).show()
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
        Toast.makeText(requireContext(), R.string.text_copied_to_clipboard, Toast.LENGTH_SHORT).show()
    }

    override fun onStop() { // AJOUTÉ
        super.onStop()
        Log.d(TAG_FRAGMENT, "onStop: Appel de viewModel.userStoppedTyping()")
        viewModel.userStoppedTyping()
    }

    override fun onDestroyView() {
        Log.d(TAG_FRAGMENT, "onDestroyView: Appel de viewModel.userStoppedTyping()") // AJOUTÉ
        viewModel.userStoppedTyping()
        typingRunnable?.let { typingHandler.removeCallbacks(it) } // AJOUTÉ
        typingRunnable = null // AJOUTÉ
        binding.rvChatMessages.adapter = null
        super.onDestroyView()
        _binding = null
    }
}