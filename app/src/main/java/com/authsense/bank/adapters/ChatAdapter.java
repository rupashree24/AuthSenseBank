package com.authsense.bank.adapters;

import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.authsense.bank.ChatMessage;
import com.authsense.bank.R;
import java.util.List;

public class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.ChatViewHolder> {
    private List<ChatMessage> messages;

    public ChatAdapter(List<ChatMessage> messages) {
        this.messages = messages;
    }

    @NonNull
    @Override
    public ChatViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_chat_message, parent, false);
        return new ChatViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ChatViewHolder holder, int position) {
        ChatMessage message = messages.get(position);
        holder.tvMessage.setText(message.getText());

        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) holder.container.getLayoutParams();
        if (message.isUser()) {
            params.gravity = Gravity.END;
            holder.container.setBackgroundResource(R.drawable.bg_message_user);
        } else {
            params.gravity = Gravity.START;
            holder.container.setBackgroundResource(R.drawable.bg_message_bot);
        }
        holder.container.setLayoutParams(params);
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    static class ChatViewHolder extends RecyclerView.ViewHolder {
        TextView tvMessage;
        View container;

        ChatViewHolder(View itemView) {
            super(itemView);
            tvMessage = itemView.findViewById(R.id.tv_message_text);
            container = itemView.findViewById(R.id.layout_message_container);
        }
    }
}
