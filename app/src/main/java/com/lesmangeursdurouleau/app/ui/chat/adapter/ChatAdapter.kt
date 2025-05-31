package com.lesmangeursdurouleau.app.ui.chat.adapter

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
import com.lesmangeursdurouleau.app.data.model.User // Ensure this import is present
import com.lesmangeursdurouleau.app.databinding.ItemChatMessageBinding
import java.text.SimpleDateFormat
import java.util.Locale

// Interface existante pour le clic sur le profil
interface OnProfileClickListener {
    fun onProfileClicked(userId: String, username: String)
}

// NOUVELLE INTERFACE pour les interactions avec les messages (appui long)
interface OnMessageInteractionListener {
    fun onMessageLongClicked(message: Message, anchorView: View)
}

class ChatAdapter(
    private val profileClickListener: OnProfileClickListener,
    private val messageInteractionListener: OnMessageInteractionListener
) : ListAdapter<Message, ChatAdapter.MessageViewHolder>(MessageDiffCallback()) {

    private var currentUserId: String? = FirebaseAuth.getInstance().currentUser?.uid
    // Corrected: userDetailsMap now correctly holds User objects
    private var userDetailsMap: Map<String, User> = emptyMap()

    companion object {
        private const val TAG = "ChatAdapter"
        private const val VIEW_TYPE_SENT = 1
        private const val VIEW_TYPE_RECEIVED = 2
        private const val SENDER_INFO_CONSOLIDATION_THRESHOLD_MS = 5 * 60 * 1000L
    }

    // Corrected: This function now accepts Map<String, User>
    fun setUserDetails(newUserMap: Map<String, User>) {
        val oldUserMap = userDetailsMap
        userDetailsMap = newUserMap
        // Only notify if there's a significant change to avoid unnecessary redraws
        if (oldUserMap != newUserMap) {
            Log.d(TAG, "setUserDetails: Cache mis à jour. Items: ${itemCount}")
            notifyItemRangeChanged(0, itemCount)
        }
    }

    override fun getItemViewType(position: Int): Int {
        val message = getItem(position)
        return if (message.senderId == currentUserId) VIEW_TYPE_SENT else VIEW_TYPE_RECEIVED
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

        init {
            Log.d(TAG, "MessageViewHolder init pour item à la position (au moment de la création): $adapterPosition")

            // Listener pour le clic sur le profil (avatar ou nom)
            val clickListener = View.OnClickListener { view ->
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    val message = getItem(position)
                    Log.d(TAG, "Listener de clic profil activé pour message: ${message.text}")
                    if (message.senderId != currentUserId && message.senderId.isNotBlank()) {
                        val userDetails = userDetailsMap[message.senderId]
                        // Accessing username from User object
                        val usernameToPass = userDetails?.username?.takeIf { it.isNotEmpty() }
                            ?: message.senderUsername.takeIf { it.isNotEmpty() }
                            ?: itemView.context.getString(R.string.unknown_user)

                        Log.i(TAG, "APPEL profileClickListener.onProfileClicked. UserID: ${message.senderId}")
                        profileClickListener.onProfileClicked(message.senderId, usernameToPass)
                    } else {
                        Log.d(TAG, "Clic sur profil/avatar ignoré (message envoyé ou senderId vide). senderId: '${message.senderId}'")
                    }
                } else {
                    Log.w(TAG, "Clic sur profil/avatar ignoré. NO_POSITION")
                }
            }
            binding.ivSenderAvatar.setOnClickListener(clickListener)
            binding.tvMessageSender.setOnClickListener(clickListener)

            // AJOUT DU LISTENER POUR L'APPUI LONG
            binding.bubbleLayoutContainer.setOnLongClickListener { view ->
                Log.d(TAG, "bubbleLayoutContainer APPUI LONG DÉTECTÉ pour item à la position: $adapterPosition")
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    val message = getItem(position)
                    Log.i(TAG, "APPEL messageInteractionListener.onMessageLongClicked pour MessageID: ${message.messageId}")
                    messageInteractionListener.onMessageLongClicked(message, binding.bubbleLayoutContainer)
                    return@setOnLongClickListener true // Consommer l'événement
                }
                Log.w(TAG, "Appui long BUBBLE ignoré. NO_POSITION")
                return@setOnLongClickListener false
            }
        }

        fun bind(message: Message, viewType: Int, previousMessage: Message?) {
            binding.tvMessageText.text = message.text
            val senderDetails = userDetailsMap[message.senderId] // Now retrieves a User object
            val showSenderDetails = shouldShowSenderDetails(message, previousMessage, viewType)

            if (viewType == VIEW_TYPE_RECEIVED) {
                if (showSenderDetails) {
                    // Accessing username from User object
                    binding.tvMessageSender.text = senderDetails?.username?.takeIf { it.isNotEmpty() }
                        ?: message.senderUsername.takeIf { it.isNotEmpty() }
                                ?: itemView.context.getString(R.string.unknown_user)
                    binding.tvMessageSender.visibility = View.VISIBLE
                    binding.ivSenderAvatar.visibility = View.VISIBLE
                    Glide.with(itemView.context)
                        .load(senderDetails?.profilePictureUrl) // Using profilePictureUrl from User object
                        .apply(RequestOptions.circleCropTransform())
                        .placeholder(R.drawable.ic_profile_placeholder)
                        .error(R.drawable.ic_profile_placeholder)
                        .into(binding.ivSenderAvatar)
                } else {
                    binding.tvMessageSender.visibility = View.GONE
                    binding.ivSenderAvatar.visibility = View.INVISIBLE
                }
            } else {
                binding.tvMessageSender.visibility = View.GONE
                binding.ivSenderAvatar.visibility = View.GONE
            }

            if (message.timestamp != null) {
                val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
                binding.tvMessageTimestamp.text = sdf.format(message.timestamp)
                binding.tvMessageTimestamp.visibility = View.VISIBLE
                binding.tvMessageTimestamp.setTextAppearance(R.style.ChatTimestamp)
                if (viewType == VIEW_TYPE_SENT) {
                    binding.tvMessageTimestamp.setTextColor(itemView.context.getColor(R.color.chat_text_sent_dark))
                } else {
                    binding.tvMessageTimestamp.setTextColor(itemView.context.getColor(R.color.chat_text_received_dark))
                }
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
                set.connect(binding.bubbleLayoutContainer.id, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END, 0)
                set.connect(binding.bubbleLayoutContainer.id, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START, 0)
                set.setHorizontalBias(binding.bubbleLayoutContainer.id, 1.0f)
            } else { // VIEW_TYPE_RECEIVED
                binding.bubbleLayout.setBackgroundResource(R.drawable.bg_chat_bubble_received_dark)
                binding.tvMessageText.setTextAppearance(R.style.ChatTextReceived)
                val marginStartForBubble = if (binding.ivSenderAvatar.visibility != View.GONE) chatBubbleMarginStartForReceived else 0
                set.connect(binding.bubbleLayoutContainer.id, ConstraintSet.START, binding.ivSenderAvatar.id, ConstraintSet.END, marginStartForBubble)
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
        override fun areItemsTheSame(oldItem: Message, newItem: Message): Boolean = oldItem.messageId == newItem.messageId
        override fun areContentsTheSame(oldItem: Message, newItem: Message): Boolean = oldItem == newItem
    }
}