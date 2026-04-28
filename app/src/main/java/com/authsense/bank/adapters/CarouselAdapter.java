package com.authsense.bank.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.authsense.bank.R;

public class CarouselAdapter extends RecyclerView.Adapter<CarouselAdapter.CarouselViewHolder> {

    private final Context context;
    private final String[] titles;
    private final String[] subtitles;

    // Background colors for each slide
    private final int[] colors = {
            0xFF0A1628, 0xFF0D4A3A, 0xFF1A3A6B, 0xFF1C1C2E
    };

    public CarouselAdapter(Context context, String[] titles, String[] subtitles) {
        this.context = context;
        this.titles = titles;
        this.subtitles = subtitles;
    }

    @NonNull
    @Override
    public CarouselViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_carousel, parent, false);
        return new CarouselViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CarouselViewHolder holder, int position) {
        holder.title.setText(titles[position]);
        holder.subtitle.setText(subtitles[position]);
        holder.itemView.setBackgroundColor(colors[position % colors.length]);
    }

    @Override
    public int getItemCount() {
        return titles.length;
    }

    public static class CarouselViewHolder extends RecyclerView.ViewHolder {
        TextView title, subtitle;

        public CarouselViewHolder(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.carousel_title);
            subtitle = itemView.findViewById(R.id.carousel_subtitle);
        }
    }
}