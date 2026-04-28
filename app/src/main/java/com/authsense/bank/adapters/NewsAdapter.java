package com.authsense.bank.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.authsense.bank.R;
import java.util.List;

public class NewsAdapter extends RecyclerView.Adapter<NewsAdapter.NewsViewHolder> {

    private final Context context;
    private final List<NewsItem> newsList;

    public NewsAdapter(Context context, List<NewsItem> newsList) {
        this.context = context;
        this.newsList = newsList;
    }

    @NonNull
    @Override
    public NewsViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_news, parent, false);
        return new NewsViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull NewsViewHolder holder, int position) {
        NewsItem item = newsList.get(position);
        holder.tag.setText(item.tag);
        holder.date.setText(item.date);
        holder.headline.setText(item.headline);
        holder.summary.setText(item.summary);
        holder.readMore.setOnClickListener(v ->
                Toast.makeText(context, "Full article coming soon!", Toast.LENGTH_SHORT).show());
    }

    @Override
    public int getItemCount() { return newsList.size(); }

    public static class NewsViewHolder extends RecyclerView.ViewHolder {
        TextView tag, date, headline, summary, readMore;
        public NewsViewHolder(@NonNull View itemView) {
            super(itemView);
            tag = itemView.findViewById(R.id.news_tag);
            date = itemView.findViewById(R.id.news_date);
            headline = itemView.findViewById(R.id.news_headline);
            summary = itemView.findViewById(R.id.news_summary);
            readMore = itemView.findViewById(R.id.news_read_more);
        }
    }

    public static class NewsItem {
        public String tag, date, headline, summary;
        public NewsItem(String tag, String date, String headline, String summary) {
            this.tag = tag; this.date = date;
            this.headline = headline; this.summary = summary;
        }
    }
}