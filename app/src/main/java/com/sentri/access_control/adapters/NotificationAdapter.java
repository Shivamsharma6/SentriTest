package com.sentri.access_control.adapters;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.sentri.access_control.R;
import com.sentri.access_control.models.NotificationItem;

import java.util.List;

public class NotificationAdapter extends RecyclerView.Adapter<NotificationAdapter.ViewHolder> {

    private List<NotificationItem> notifications;

    public NotificationAdapter(List<NotificationItem> notifications) {
        this.notifications = notifications;
    }

    public void updateList(List<NotificationItem> newItems) {
        this.notifications = newItems;
        notifyDataSetChanged();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvText, tvDate;
        View     typeIndicator;

        public ViewHolder(View view) {
            super(view);
            tvText = view.findViewById(R.id.tvNotificationText);
            tvDate = view.findViewById(R.id.tvNotificationDate);
            typeIndicator = view.findViewById(R.id.viewTypeIndicator);
        }
    }

    @NonNull
    @Override
    public NotificationAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_notification, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull NotificationAdapter.ViewHolder holder, int position) {
        NotificationItem item = notifications.get(position);
        String rawMessage = item.getMessage();
        holder.tvText.setText(stripTypePrefix(rawMessage));
        holder.tvDate.setText(item.getDate());

        if (holder.typeIndicator != null) {
            String type = extractTypeFromMessage(rawMessage);
            int color = resolveColorForType(type);
            holder.typeIndicator.setBackgroundColor(color);
        }
    }

    private String stripTypePrefix(String message) {
        if (message == null) {
            return "";
        }
        // Remove leading [TYPE] if present.
        if (message.startsWith("[") && message.contains("]")) {
            int end = message.indexOf("]");
            if (end >= 0 && end + 1 < message.length()) {
                return message.substring(end + 1).trim();
            }
        }
        return message;
    }

    private String extractTypeFromMessage(String message) {
        if (message == null) {
            return "";
        }
        if (message.startsWith("[") && message.contains("]")) {
            int end = message.indexOf("]");
            if (end > 1) {
                return message.substring(1, end).trim();
            }
        }
        return "";
    }

    private int resolveColorForType(String type) {
        if (type == null) {
            return Color.parseColor("#448AFF");
        }
        String t = type.toLowerCase();
        if (t.contains("payment")) {
            return Color.parseColor("#4CAF50"); // green
        } else if (t.contains("shift")) {
            return Color.parseColor("#FFC107"); // amber
        } else if (t.contains("card")) {
            return Color.parseColor("#03A9F4"); // light blue
        } else if (t.contains("leave")) {
            return Color.parseColor("#9C27B0"); // purple
        } else if (t.contains("customer")) {
            return Color.parseColor("#FF5722"); // deep orange
        }
        return Color.parseColor("#448AFF");
    }

    @Override
    public int getItemCount() {
        return notifications.size();
    }
}

