package com.authsense.bank;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.authsense.bank.adapters.CreditCardAdapter;
import java.util.ArrayList;
import java.util.List;

public class CreditCardFragment extends Fragment {

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_credit_card, container, false);

        RecyclerView rv = view.findViewById(R.id.rv_credit_cards);
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        rv.setAdapter(new CreditCardAdapter(requireContext(), getCardList()));

        return view;
    }

    private List<CreditCardAdapter.CardData> getCardList() {
        List<CreditCardAdapter.CardData> cards = new ArrayList<>();

        cards.add(new CreditCardAdapter.CardData(
                "AuthSense Platinum", "VISA", "PREMIUM",
                "₹5,000/yr", "Up to ₹10 Lakhs",
                "✓ 5X rewards on dining & travel\n✓ International airport lounge access\n✓ Zero foreign transaction fees\n✓ Complimentary travel insurance",
                0xFF1A3A6B, "💳"
        ));

        cards.add(new CreditCardAdapter.CardData(
                "AuthSense Gold", "Mastercard", "POPULAR",
                "₹2,000/yr", "Up to ₹5 Lakhs",
                "✓ 3X rewards on groceries\n✓ 1% cashback on all spends\n✓ EMI conversion at 0% interest\n✓ Fuel surcharge waiver",
                0xFFC9A84C, "🥇"
        ));

        cards.add(new CreditCardAdapter.CardData(
                "AuthSense Signature", "RuPay", "ELITE",
                "₹10,000/yr", "Up to ₹25 Lakhs",
                "✓ 10X rewards on luxury brands\n✓ Dedicated concierge service\n✓ Golf course access worldwide\n✓ Premium hotel benefits",
                0xFF1C1C2E, "👑"
        ));

        cards.add(new CreditCardAdapter.CardData(
                "AuthSense Student", "VISA", "NO FEE",
                "₹0/yr", "Up to ₹50,000",
                "✓ 2X rewards on food delivery\n✓ Free movie tickets monthly\n✓ No annual fee ever\n✓ Easy approval for students",
                0xFF0D4A3A, "🎓"
        ));

        cards.add(new CreditCardAdapter.CardData(
                "AuthSense Cashback+", "Mastercard", "CASHBACK",
                "₹999/yr", "Up to ₹3 Lakhs",
                "✓ 5% cashback on online shopping\n✓ 2% cashback on utilities\n✓ Monthly cashback cap ₹1,000\n✓ Instant cashback, no points",
                0xFF2D1B69, "💰"
        ));

        return cards;
    }
}