package com.lesmangeursdurouleau.app.ui.chat

import android.graphics.drawable.Animatable
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
// import android.view.animation.AnimationUtils // Toujours commenté
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.lesmangeursdurouleau.app.R
import com.lesmangeursdurouleau.app.data.model.User // NOUVEL IMPORT
import com.lesmangeursdurouleau.app.databinding.FragmentChatBinding
import com.lesmangeursdurouleau.app.ui.chat.adapter.ChatAdapter
import com.lesmangeursdurouleau.app.utils.Resource
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class ChatFragment : Fragment() {

    private var _binding: FragmentChatBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ChatViewModel by viewModels()
    private lateinit var chatAdapter: ChatAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentChatBinding.inflate(inflater, container, false)
        Log.d("ChatFragment", "onCreateView")
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d("ChatFragment", "onViewCreated")

        setupRecyclerView()
        setupClickListeners()
        setupObservers()
    }

    private fun setupRecyclerView() {
        Log.d("ChatFragment", "setupRecyclerView")
        chatAdapter = ChatAdapter() // Créer l'instance de l'adapter
        binding.rvChatMessages.apply {
            adapter = chatAdapter
            layoutManager = LinearLayoutManager(requireContext()).apply {
                stackFromEnd = true
            }
        }
    }

    private fun setupClickListeners() {
        Log.d("ChatFragment", "setupClickListeners")
        binding.btnSendMessage.setOnClickListener {
            val messageText = binding.etChatMessageInput.text.toString().trim()
            if (messageText.isNotEmpty()) {
                Log.d("ChatFragment", "Bouton Envoyer cliqué avec le texte: \"$messageText\"")
                viewModel.sendMessage(messageText)
            } else {
                Log.w("ChatFragment", "Tentative d'envoi d'un message vide.")
                Toast.makeText(requireContext(), "Le message ne peut pas être vide", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupObservers() {
        Log.d("ChatFragment", "setupObservers")

        // Observer pour les messages
        viewModel.messages.observe(viewLifecycleOwner) { resource ->
            Log.d("ChatFragment", "Observation des messages: $resource")
            when (resource) {
                is Resource.Loading -> {
                    binding.progressBarChat.visibility = View.VISIBLE
                    binding.tvChatEmptyMessage.visibility = View.GONE
                }
                is Resource.Success -> {
                    binding.progressBarChat.visibility = View.GONE
                    val messages = resource.data
                    if (messages.isNullOrEmpty()) {
                        Log.d("ChatFragment", "Liste de messages vide ou nulle.")
                        binding.tvChatEmptyMessage.visibility = View.VISIBLE
                        chatAdapter.submitList(emptyList())
                    } else {
                        Log.d("ChatFragment", "Affichage de ${messages.size} messages.")
                        binding.tvChatEmptyMessage.visibility = View.GONE
                        chatAdapter.submitList(messages.toList()) {
                            val itemCount = chatAdapter.itemCount
                            if (itemCount > 0) {
                                val layoutManager = binding.rvChatMessages.layoutManager as LinearLayoutManager
                                val lastCompletelyVisibleItemPosition = layoutManager.findLastCompletelyVisibleItemPosition()
                                if (lastCompletelyVisibleItemPosition == -1 || lastCompletelyVisibleItemPosition >= itemCount - 2 || messages.size <= (layoutManager.childCount ?: 0) +1 ) { // +1 pour être plus agressif sur le scroll au début
                                    binding.rvChatMessages.smoothScrollToPosition(itemCount - 1)
                                    Log.d("ChatFragment", "Scroll vers le message ${itemCount - 1}")
                                }
                            }
                        }
                    }
                }
                is Resource.Error -> {
                    binding.progressBarChat.visibility = View.GONE
                    binding.tvChatEmptyMessage.visibility = View.GONE
                    Toast.makeText(requireContext(), "Erreur messages: ${resource.message}", Toast.LENGTH_LONG).show()
                    Log.e("ChatFragment", "Erreur chargement messages: ${resource.message}")
                }
            }
        }

        // NOUVEL OBSERVER: Pour les détails des utilisateurs (cache d'avatars/noms)
        viewModel.userDetailsCache.observe(viewLifecycleOwner) { userMap ->
            Log.d("ChatFragment", "Mise à jour du cache des détails utilisateurs: ${userMap.size} utilisateurs en cache.")
            chatAdapter.setUserDetails(userMap) // Nouvelle méthode à ajouter dans ChatAdapter
        }

        // Observer pour le statut d'envoi de message
        viewModel.sendMessageStatus.observe(viewLifecycleOwner) { resource ->
            Log.d("ChatFragment", "Observation statut envoi: $resource")
            binding.btnSendMessage.isEnabled = resource !is Resource.Loading
            binding.etChatMessageInput.isEnabled = resource !is Resource.Loading

            when (resource) {
                is Resource.Loading -> {
                    binding.btnSendMessage.setImageResource(R.drawable.ic_loading)
                    (binding.btnSendMessage.drawable as? Animatable)?.start()
                }
                is Resource.Success -> {
                    binding.etChatMessageInput.text.clear()
                    binding.btnSendMessage.setImageResource(R.drawable.ic_paper_plane)
                    (binding.btnSendMessage.drawable as? Animatable)?.stop()
                    Log.i("ChatFragment", "Statut envoi: Succès")
                }
                is Resource.Error -> {
                    binding.btnSendMessage.setImageResource(R.drawable.ic_paper_plane)
                    (binding.btnSendMessage.drawable as? Animatable)?.stop()
                    Toast.makeText(requireContext(), "Erreur d'envoi: ${resource.message}", Toast.LENGTH_LONG).show()
                    Log.e("ChatFragment", "Statut envoi: Erreur - ${resource.message}")
                }
                null -> {
                    binding.btnSendMessage.setImageResource(R.drawable.ic_paper_plane)
                    (binding.btnSendMessage.drawable as? Animatable)?.stop()
                    if (!binding.etChatMessageInput.isEnabled) binding.etChatMessageInput.isEnabled = true
                    if (!binding.btnSendMessage.isEnabled) binding.btnSendMessage.isEnabled = true
                }
            }

            if (resource != null && resource !is Resource.Loading) {
                viewModel.clearSendMessageStatus()
            }
        }
    }

    override fun onDestroyView() {
        Log.d("ChatFragment", "onDestroyView")
        binding.rvChatMessages.adapter = null
        super.onDestroyView()
        _binding = null
    }
}