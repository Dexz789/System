package com.example.strawberry2.Chat

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.strawberry2.R

class ChatAdapter(
    private val messages: MutableList<ChatMessage>,
    private val onSaveClick: (String) -> Unit  // Callback for save action
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val VIEW_TYPE_USER = 1
        private const val VIEW_TYPE_AI = 2
    }

    override fun getItemViewType(position: Int): Int {
        return if (messages[position].isUser) VIEW_TYPE_USER else VIEW_TYPE_AI
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == VIEW_TYPE_USER) {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_message_user, parent, false)
            UserMessageViewHolder(view)
        } else {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_message_ai, parent, false)
            AiMessageViewHolder(view, onSaveClick)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = messages[position]
        when (holder) {
            is UserMessageViewHolder -> holder.bind(message)
            is AiMessageViewHolder -> holder.bind(message)
        }
    }

    override fun getItemCount(): Int = messages.size

    fun addMessage(message: ChatMessage) {
        messages.add(message)
        notifyItemInserted(messages.size - 1)
    }

    class UserMessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvMessage: TextView = itemView.findViewById(R.id.tvMessage)
        private val ivMessageImage: ImageView? = itemView.findViewById(R.id.ivMessageImage)

        fun bind(message: ChatMessage) {
            val markwon = io.noties.markwon.Markwon.create(itemView.context)
            markwon.setMarkdown(tvMessage, message.message)

            if (message.image != null && ivMessageImage != null) {
                ivMessageImage.visibility = View.VISIBLE
                ivMessageImage.setImageBitmap(message.image)
            } else if (ivMessageImage != null) {
                ivMessageImage.visibility = View.GONE
            }
        }
    }

    class AiMessageViewHolder(
        itemView: View,
        private val onSaveClick: (String) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {
        private val tvMessage: TextView = itemView.findViewById(R.id.tvMessage)
        private val btnSaveResponse: Button? = itemView.findViewById(R.id.btnSaveResponse)
        private val ivDiagnosisImageChat: ImageView? = itemView.findViewById(R.id.ivDiagnosisImageChat)

        fun bind(message: ChatMessage) {
            val markwon = io.noties.markwon.Markwon.create(itemView.context)
            markwon.setMarkdown(tvMessage, message.message)

            // Show the strawberry image (e.g. in the greeting when launched from history)
            if (message.image != null && ivDiagnosisImageChat != null) {
                ivDiagnosisImageChat.setImageBitmap(message.image)
                ivDiagnosisImageChat.visibility = View.VISIBLE
            } else if (ivDiagnosisImageChat != null) {
                ivDiagnosisImageChat.visibility = View.GONE
            }

            // Show save button only when explicitly flagged
            if (message.canBeSaved && btnSaveResponse != null) {
                btnSaveResponse.visibility = View.GONE
                btnSaveResponse.setOnClickListener {
                    onSaveClick(message.message)
                }
            } else if (btnSaveResponse != null) {
                btnSaveResponse.visibility = View.GONE
            }
        }
    }
}

