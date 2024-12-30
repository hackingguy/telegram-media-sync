package com.yourpackage.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.yourpackage.R
import com.yourpackage.data.remote.TelegramBotService.ChatInfo

class ChatListAdapter(
    private val onChatSelected: (ChatInfo) -> Unit
) : RecyclerView.Adapter<ChatListAdapter.ViewHolder>() {

    private var chats: List<ChatInfo> = emptyList()

    fun updateChats(newChats: List<ChatInfo>) {
        chats = newChats
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_chat, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(chats[position])
    }

    override fun getItemCount() = chats.size

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val titleView: TextView = itemView.findViewById(R.id.chatTitle)
        private val idView: TextView = itemView.findViewById(R.id.chatId)

        fun bind(chat: ChatInfo) {
            titleView.text = chat.title
            idView.text = chat.id
            itemView.setOnClickListener { onChatSelected(chat) }
        }
    }
} 