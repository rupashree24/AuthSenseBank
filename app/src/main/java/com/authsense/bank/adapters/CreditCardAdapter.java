package com.authsense.bank.adapters;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.authsense.bank.R;
import java.util.List;

public class CreditCardAdapter extends RecyclerView.Adapter<CreditCardAdapter.CardViewHolder> {

    private final Context context;
    private final List<CardData> cards;

    public CreditCardAdapter(Context context, List<CardData> cards) {
        this.context = context;
        this.cards = cards;
    }

    @NonNull
    @Override
    public CardViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_credit_card, parent, false);
        return new CardViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CardViewHolder holder, int position) {
        CardData card = cards.get(position);
        holder.cardName.setText(card.name);
        holder.cardType.setText(card.type);
        holder.cardBadge.setText(card.badge);
        holder.cardFee.setText(card.annualFee);
        holder.cardLimit.setText(card.creditLimit);
        holder.cardBenefits.setText(card.benefits);
        holder.cardEmoji.setText(card.emoji);
        holder.cardBg.setBackgroundColor((int) card.bgColor);
        holder.btnApply.setOnClickListener(v ->
                Toast.makeText(context, "Applying for " + card.name + "...", Toast.LENGTH_SHORT).show());
    }

    @Override
    public int getItemCount() {
        return cards.size();
    }

    public static class CardViewHolder extends RecyclerView.ViewHolder {
        LinearLayout cardBg;
        TextView cardEmoji, cardName, cardType, cardBadge, cardFee, cardLimit, cardBenefits, btnApply;

        public CardViewHolder(@NonNull View itemView) {
            super(itemView);
            cardBg = itemView.findViewById(R.id.card_bg);
            cardEmoji = itemView.findViewById(R.id.card_emoji);
            cardName = itemView.findViewById(R.id.card_name);
            cardType = itemView.findViewById(R.id.card_type);
            cardBadge = itemView.findViewById(R.id.card_badge);
            cardFee = itemView.findViewById(R.id.card_fee);
            cardLimit = itemView.findViewById(R.id.card_limit);
            cardBenefits = itemView.findViewById(R.id.card_benefits);
            btnApply = itemView.findViewById(R.id.btn_apply);
        }
    }

    // Data model for a card
    public static class CardData {
        public String name, type, badge, annualFee, creditLimit, benefits, emoji;
        public long bgColor;

        public CardData(String name, String type, String badge, String annualFee,
                        String creditLimit, String benefits, long bgColor, String emoji) {
            this.name = name; this.type = type; this.badge = badge;
            this.annualFee = annualFee; this.creditLimit = creditLimit;
            this.benefits = benefits; this.bgColor = bgColor; this.emoji = emoji;
        }
    }
}