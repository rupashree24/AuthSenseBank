package com.authsense.bank;

import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.widget.ViewPager2;
import com.authsense.bank.adapters.CarouselAdapter;
import java.util.Arrays;
import java.util.List;

public class HomeFragment extends Fragment {

    private ViewPager2 carousel;
    private LinearLayout dotsLayout;
    private Handler autoSlideHandler = new Handler();
    private int currentPage = 0;

    // Carousel data: title + subtitle pairs
    private final String[] titles = {
            "Banking Redefined", "Zero Fee Accounts", "Premium Credit Cards", "Invest & Grow"
    };
    private final String[] subtitles = {
            "Your trusted financial partner", "No hidden charges, ever",
            "Rewards that match your lifestyle", "Start your wealth journey"
    };

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home, container, false);

        carousel = view.findViewById(R.id.carousel);
        dotsLayout = view.findViewById(R.id.dots_layout);

        // Setup carousel
        CarouselAdapter adapter = new CarouselAdapter(requireContext(), titles, subtitles);
        carousel.setAdapter(adapter);
        setupDots(titles.length);

        carousel.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                currentPage = position;
                updateDots(position);
            }
        });

        // Auto-slide every 3 seconds
        Runnable autoSlide = new Runnable() {
            @Override
            public void run() {
                int next = (currentPage + 1) % titles.length;
                carousel.setCurrentItem(next, true);
                autoSlideHandler.postDelayed(this, 3000);
            }
        };
        autoSlideHandler.postDelayed(autoSlide, 3000);

        return view;
    }

    private void setupDots(int count) {
        ImageView[] dots = new ImageView[count];
        for (int i = 0; i < count; i++) {
            dots[i] = new ImageView(requireContext());
            dots[i].setImageDrawable(ContextCompat.getDrawable(requireContext(),
                    i == 0 ? R.drawable.dot_active : R.drawable.dot_inactive));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(16, 16);
            params.setMargins(6, 0, 6, 0);
            dotsLayout.addView(dots[i], params);
        }
    }

    private void updateDots(int selected) {
        for (int i = 0; i < dotsLayout.getChildCount(); i++) {
            ImageView dot = (ImageView) dotsLayout.getChildAt(i);
            dot.setImageDrawable(ContextCompat.getDrawable(requireContext(),
                    i == selected ? R.drawable.dot_active : R.drawable.dot_inactive));
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        autoSlideHandler.removeCallbacksAndMessages(null);
    }
}