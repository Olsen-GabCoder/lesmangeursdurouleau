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
import com.lesmangeursdurouleau.app.data.model.User
import com.lesmangeursdurouleau.app.databinding.ItemChatMessageBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

interface OnProfileClickListener {
    fun onProfileClicked(userId: String, username: String)
}

class ChatAdapter(
    private val profileClickListener: OnProfileClickListener
) : ListAdapter<Message, ChatAdapter.MessageViewHolder>(MessageDiffCallback()) {

    private var currentUserId: String? = FirebaseAuth.getInstance().currentUser?.uid
    private var userDetailsMap: Map<String, User> = emptyMap()

    companion object {
        private const val TAG = "ChatAdapter" // Pour les logs
        private const val VIEW_TYPE_SENT = 1
        private const val VIEW_TYPE_RECEIVED = 2
        private const val SENDER_INFO_CONSOLIDATION_THRESHOLD_MS = 5 * 60 * 1000L
    }

    fun setUserDetails(newUserMap: Map<String, User>) {
        // ... (méthode setUserDetails inchangée, elle était déjà correcte) ...
        val oldUserMap = userDetailsMap
        userDetailsMap = newUserMap
        if (oldUserMap != newUserMap) {
            Log.d(TAG, "setUserDetails: Cache des détails utilisateurs mis à jour. Notification des changements pour ${itemCount} items.")
            notifyItemRangeChanged(0, itemCount)
        }
    }

    override fun getItemViewType(position: Int): Int { /* ... inchangé ... */
        val message = getItem(position)
        return if (message.senderId == currentUserId) VIEW_TYPE_SENT else VIEW_TYPE_RECEIVED
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder { /* ... inchangé ... */
        val binding = ItemChatMessageBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return MessageViewHolder(binding)
    }
    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) { /* ... inchangé ... */
        val message = getItem(position)
        val previousMessage = if (position > 0) getItem(position - 1) else null
        holder.bind(message, getItemViewType(position), previousMessage)
    }

    inner class MessageViewHolder(private val binding: ItemChatMessageBinding) :
        RecyclerView.ViewHolder(binding.root) {

        private val chatBubbleMarginStartForReceived = itemView.context.resources.getDimensionPixelSize(R.dimen.chat_bubble_margin_start_received)

        init {
            val clickListener = View.OnClickListener { view ->
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    val message = getItem(position)
                    if (message.senderId != currentUserId && message.senderId.isNotBlank()) {
                        val userDetails = userDetailsMap[message.senderId]
                        val usernameToPass = userDetails?.username?.takeIf { it.isNotEmpty() }
                            ?: message.senderUsername.takeIf { it.isNotEmpty() }
                            ?: itemView.context.getString(R.string.unknown_user)

                        Log.d(TAG, "Clic sur profil/avatar pour senderId: '${message.senderId}', usernameToPass: '$usernameToPass'")
                        profileClickListener.onProfileClicked(message.senderId, usernameToPass)
                    } else {
                        Log.d(TAG, "Clic sur profil/avatar ignoré (message envoyé ou senderId vide). senderId: '${message.senderId}'")
                    }
                }
            }
            binding.ivSenderAvatar.setOnClickListener(clickListener)
            binding.tvMessageSender.setOnClickListener(clickListener)
        }

        fun bind(message: Message, viewType: Int, previousMessage: Message?) {
            // ... (le reste de la logique de bind pour l'affichage reste inchangé, elle était déjà correcte) ...
            binding.tvMessageText.text = message.text
            val senderDetails = userDetailsMap[message.senderId]
            val showSenderDetails = shouldShowSenderDetails(message, previousMessage, viewType)

            if (viewType == VIEW_TYPE_RECEIVED) {
                if (showSenderDetails) {
                    binding.tvMessageSender.text = senderDetails?.username?.takeIf { it.isNotEmpty() }
                        ?: message.senderUsername.takeIf { it.isNotEmpty() }
                                ?: itemView.context.getString(R.string.unknown_user)
                    binding.tvMessageSender.visibility = View.VISIBLE
                    binding.ivSenderAvatar.visibility = View.VISIBLE
                    Glide.with(itemView.context)
                        .load(senderDetails?.profilePictureUrl)
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
            } else {
                binding.bubbleLayout.setBackgroundResource(R.drawable.bg_chat_bubble_received_dark)
                binding.tvMessageText.setTextAppearance(R.style.ChatTextReceived)
                binding.tvMessageTimestamp.setTextColor(itemView.context.getColor(R.color.chat_text_received_dark))
                set.connect(binding.bubbleLayoutContainer.id, ConstraintSet.START, binding.ivSenderAvatar.id, ConstraintSet.END, if (showSenderDetails || binding.ivSenderAvatar.visibility == View.INVISIBLE) chatBubbleMarginStartForReceived else 0)
                set.connect(binding.bubbleLayoutContainer.id, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END, 0)
                set.setHorizontalBias(binding.bubbleLayoutContainer.id, 0.0f)
            }
            set.applyTo(constraintLayoutRoot)
        }

        private fun shouldShowSenderDetails(currentMessage: Message, previousMessage: Message?, viewType: Int): Boolean { /* ... inchangé ... */
            if (viewType == VIEW_TYPE_SENT) return false
            if (previousMessage == null) return true
            if (previousMessage.senderId != currentMessage.senderId) return true
            val timeDiff = currentMessage.timestamp?.time?.minus(previousMessage.timestamp?.time ?: 0L) ?: (SENDER_INFO_CONSOLIDATION_THRESHOLD_MS + 1)
            return timeDiff > SENDER_INFO_CONSOLIDATION_THRESHOLD_MS
        }
    }
    class MessageDiffCallback : DiffUtil.ItemCallback<Message>() { /* ... inchangé ... */
        override fun areItemsTheSame(oldItem: Message, newItem: Message): Boolean = oldItem.messageId == newItem.messageId
        override fun areContentsTheSame(oldItem: Message, newItem: Message): Boolean = oldItem == newItem
    }
}