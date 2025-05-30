package com.lesmangeursdurouleau.app.ui.chat.adapter

import android.text.format.DateUtils
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.google.firebase.auth.FirebaseAuth
import com.lesmangeursdurouleau.app.R
import com.lesmangeursdurouleau.app.data.model.Message
import com.lesmangeursdurouleau.app.data.model.User // NOUVEL IMPORT
import com.lesmangeursdurouleau.app.databinding.ItemChatMessageBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ChatAdapter : ListAdapter<Message, ChatAdapter.MessageViewHolder>(MessageDiffCallback()) {

    private var currentUserId: String? = FirebaseAuth.getInstance().currentUser?.uid

    // NOUVEAU: Variable pour stocker les détails des utilisateurs (y compris les URLs d'avatar)
    private var userDetailsMap: Map<String, User> = emptyMap()

    companion object {
        private const val VIEW_TYPE_SENT = 1
        private const val VIEW_TYPE_RECEIVED = 2
        private const val SENDER_INFO_CONSOLIDATION_THRESHOLD_MS = 5 * 60 * 1000L // 5 minutes
    }

    // NOUVELLE MÉTHODE: Pour mettre à jour les détails des utilisateurs
    fun setUserDetails(newUserMap: Map<String, User>) {
        val oldUserMap = userDetailsMap
        userDetailsMap = newUserMap
        // Redessiner les items visibles si les détails des utilisateurs ont changé
        // C'est un peu brutal, idéalement on ne notifierait que les items concernés,
        // mais pour l'instant, ListAdapter devrait gérer cela raisonnablement bien
        // si la liste de messages elle-même n'a pas changé.
        // Si des problèmes de performance surviennent, il faudra optimiser.
        // Une simple comparaison de taille peut ne pas suffire si les URLs d'avatar changent.
        if (oldUserMap != newUserMap) { // Vérifier si la map a réellement changé
            Log.d("ChatAdapter", "setUserDetails: Mise à jour des détails utilisateurs, notification des changements.")
            // Il faut trouver un moyen de notifier l'adapter que les données *externes* (avatars)
            // pour les items *existants* ont changé. notifyDataSetChanged() est trop brutal.
            // Une solution est de re-soumettre la même liste, ce qui forcera un rebind.
            // Ou, si l'ID de l'item ne change pas mais son contenu (avatar) oui, il faut
            // que le DiffUtil le détecte (areContentsTheSame). Pour cela, Message ne suffit pas.
            // Pour l'instant, nous allons compter sur le rebind si la liste de messages est aussi mise à jour.
            // Si seuls les avatars changent, il faudra une stratégie de mise à jour plus fine,
            // ou que le Fragment re-soumette la liste des messages actuels.
            // Pour l'instant, on ne fait rien ici, on compte sur le fait que `submitList` pour les messages
            // sera appelé et forcera un rebind. Ou alors, on peut essayer :
            notifyItemRangeChanged(0, itemCount) // Notifie que tous les items visibles ont pu changer
        }
    }


    override fun getItemViewType(position: Int): Int {
        val message = getItem(position)
        return if (message.senderId == currentUserId) {
            VIEW_TYPE_SENT
        } else {
            VIEW_TYPE_RECEIVED
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val binding = ItemChatMessageBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return MessageViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        val message = getItem(position)
        val previousMessage = if (position > 0) getItem(position - 1) else null
        holder.bind(message, getItemViewType(position), previousMessage)
    }

    inner class MessageViewHolder(private val binding: ItemChatMessageBinding) :
        RecyclerView.ViewHolder(binding.root) {

        private val chatBubbleMarginStartForReceived = itemView.context.resources.getDimensionPixelSize(R.dimen.chat_bubble_margin_start_received)

        fun bind(message: Message, viewType: Int, previousMessage: Message?) {
            binding.tvMessageText.text = message.text

            val showSenderDetails = shouldShowSenderDetails(message, previousMessage, viewType)

            if (viewType == VIEW_TYPE_RECEIVED) {
                val senderDetails = userDetailsMap[message.senderId] // Récupérer les détails de l'utilisateur

                if (showSenderDetails) {
                    binding.tvMessageSender.text = senderDetails?.username?.takeIf { it.isNotEmpty() }
                        ?: message.senderUsername.takeIf { it.isNotEmpty() }
                                ?: itemView.context.getString(R.string.unknown_user)
                    binding.tvMessageSender.visibility = View.VISIBLE

                    binding.ivSenderAvatar.visibility = View.VISIBLE
                    Glide.with(itemView.context)
                        .load(senderDetails?.profilePictureUrl) // UTILISER L'URL DE L'AVATAR DU CACHE
                        .apply(RequestOptions.circleCropTransform())
                        .placeholder(R.drawable.ic_profile_placeholder)
                        .error(R.drawable.ic_profile_placeholder)
                        .into(binding.ivSenderAvatar)
                } else {
                    binding.tvMessageSender.visibility = View.GONE
                    binding.ivSenderAvatar.visibility = View.INVISIBLE
                }
            } else { // VIEW_TYPE_SENT
                binding.tvMessageSender.visibility = View.GONE
                binding.ivSenderAvatar.visibility = View.GONE
            }

            if (message.timestamp != null) {
                val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
                binding.tvMessageTimestamp.text = sdf.format(message.timestamp)
                binding.tvMessageTimestamp.visibility = View.VISIBLE
                binding.tvMessageTimestamp.setTextAppearance(R.style.ChatTimestamp)
            } else {
                binding.tvMessageTimestamp.visibility = View.GONE
            }

            val constraintLayoutRoot = binding.root as ConstraintLayout
            val set = ConstraintSet()
            set.clone(constraintLayoutRoot)

            set.clear(binding.bubbleLayoutContainer.id, ConstraintSet.START)
            set.clear(binding.bubbleLayoutContainer.id, ConstraintSet.END)

            if (viewType == VIEW_TYPE_SENT) {
                binding.bubbleLayout.setBackgroundResource(R.drawable.bg_chat_bubble_sent_dark)
                binding.tvMessageText.setTextAppearance(R.style.ChatTextSent)
                binding.tvMessageTimestamp.setTextColor(itemView.context.getColor(R.color.chat_text_sent_dark))

                set.connect(binding.bubbleLayoutContainer.id, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END, 0)
                set.connect(binding.bubbleLayoutContainer.id, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START, 0)
                set.setHorizontalBias(binding.bubbleLayoutContainer.id, 1.0f)

            } else { // VIEW_TYPE_RECEIVED
                binding.bubbleLayout.setBackgroundResource(R.drawable.bg_chat_bubble_received_dark)
                binding.tvMessageText.setTextAppearance(R.style.ChatTextReceived)
                binding.tvMessageTimestamp.setTextColor(itemView.context.getColor(R.color.chat_text_received_dark))

                set.connect(binding.bubbleLayoutContainer.id, ConstraintSet.START, binding.ivSenderAvatar.id, ConstraintSet.END, if (showSenderDetails || binding.ivSenderAvatar.visibility == View.INVISIBLE) chatBubbleMarginStartForReceived else 0)
                set.connect(binding.bubbleLayoutContainer.id, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END, 0)
                set.setHorizontalBias(binding.bubbleLayoutContainer.id, 0.0f)
            }
            set.applyTo(constraintLayoutRoot)
        }

        private fun shouldShowSenderDetails(currentMessage: Message, previousMessage: Message?, viewType: Int): Boolean {
            if (viewType == VIEW_TYPE_SENT) return false
            if (previousMessage == null) return true
            if (previousMessage.senderId != currentMessage.senderId) return true
            val timeDiff = currentMessage.timestamp?.time?.minus(previousMessage.timestamp?.time ?: 0L) ?: (SENDER_INFO_CONSOLIDATION_THRESHOLD_MS + 1)
            return timeDiff > SENDER_INFO_CONSOLIDATION_THRESHOLD_MS
        }
    }

    class MessageDiffCallback : DiffUtil.ItemCallback<Message>() {
        override fun areItemsTheSame(oldItem: Message, newItem: Message): Boolean {
            return oldItem.messageId == newItem.messageId
        }
        override fun areContentsTheSame(oldItem: Message, newItem: Message): Boolean {
            // Si on veut que DiffUtil redessine si l'avatar change (même si le message est le même),
            // il faudrait que Message contienne l'URL de l'avatar ou que areContentsTheSame soit plus intelligent.
            // Pour l'instant, on se base sur l'objet Message lui-même.
            return oldItem == newItem
        }
    }
}