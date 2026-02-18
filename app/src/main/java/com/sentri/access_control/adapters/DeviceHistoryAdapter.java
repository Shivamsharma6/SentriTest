package com.sentri.access_control.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.sentri.access_control.R;
import com.sentri.access_control.models.DeviceHistoryItem;

import java.util.List;

public class DeviceHistoryAdapter extends RecyclerView.Adapter<DeviceHistoryAdapter.DeviceViewHolder> {

    private final List<DeviceHistoryItem> historyList;

    public DeviceHistoryAdapter(List<DeviceHistoryItem> historyList) {
        this.historyList = historyList;
    }

    @NonNull
    @Override
    public DeviceViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_device_history, parent, false);
        return new DeviceViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull DeviceViewHolder holder, int position) {
        DeviceHistoryItem item = historyList.get(position);
        holder.tvUser.setText(item.getUser());
        holder.tvAction.setText(item.getAction());
        holder.tvTimestamp.setText(item.getTimestamp());
    }

    @Override
    public int getItemCount() {
        return historyList.size();
    }

    static class DeviceViewHolder extends RecyclerView.ViewHolder {
        TextView tvUser, tvAction, tvTimestamp;

        public DeviceViewHolder(@NonNull View itemView) {
            super(itemView);
            tvUser = itemView.findViewById(R.id.tvUser);
            tvAction = itemView.findViewById(R.id.tvAction);
            tvTimestamp = itemView.findViewById(R.id.tvTimestamp);
        }
    }
}

