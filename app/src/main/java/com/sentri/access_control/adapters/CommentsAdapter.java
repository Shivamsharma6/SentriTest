package com.sentri.access_control.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import com.sentri.access_control.R;
import com.sentri.access_control.models.CommentItem;


public class CommentsAdapter extends RecyclerView.Adapter<CommentsAdapter.CommentViewHolder> {

    private List<CommentItem> comments;

    public CommentsAdapter(List<CommentItem> comments) {
        this.comments = comments;
    }

    public void updateList(List<CommentItem> newComments) {
        this.comments = newComments;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public CommentViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_comment, parent, false);
        return new CommentViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CommentViewHolder holder, int position) {
        CommentItem item = comments.get(position);
        holder.tvComment.setText(item.getText());
        holder.tvDate.setText(item.getDate());
    }

    @Override
    public int getItemCount() {
        return comments.size();
    }

    static class CommentViewHolder extends RecyclerView.ViewHolder {
        TextView tvComment, tvDate;

        public CommentViewHolder(@NonNull View itemView) {
            super(itemView);
            tvComment = itemView.findViewById(R.id.tvComment);
            tvDate = itemView.findViewById(R.id.tvDate);
        }
    }
}

