package com.sentri.access_control.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.sentri.access_control.R;
import com.sentri.access_control.models.DeviceItem;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class DeviceAdapter extends RecyclerView.Adapter<DeviceAdapter.DeviceVH> {

    public interface OnDeviceClick {
        void onClick(DeviceItem item);
    }

    private final List<DeviceItem> full = new ArrayList<>();
    private final List<DeviceItem> visible = new ArrayList<>();
    private final OnDeviceClick click;

    public DeviceAdapter(OnDeviceClick click) {
        this.click = click;
    }

    public void setItems(List<DeviceItem> items) {
        full.clear();
        if (items != null) full.addAll(items);
        visible.clear();
        visible.addAll(full);
        notifyDataSetChanged();
    }

    public void filter(String query) {
        visible.clear();
        if (query == null || query.trim().isEmpty()) {
            visible.addAll(full);
        } else {
            String q = query.toLowerCase(Locale.getDefault());
            for (DeviceItem d : full) {
                String name = d.getName() != null ? d.getName() : "";
                String mac  = d.getMac() != null ? d.getMac() : "";
                if (name.toLowerCase().contains(q) || mac.toLowerCase().contains(q)) {
                    visible.add(d);
                }
            }
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public DeviceVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_device, parent, false);
        return new DeviceVH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull DeviceVH h, int pos) {
        DeviceItem d = visible.get(pos);

        // Name
        String name = (d.getName() != null && !d.getName().isEmpty())
                ? d.getName()
                : (d.getMac() != null ? d.getMac() : d.getId());
        h.tvName.setText(name);

        // Last online
        if (d.getLastSeen() != null) {
            String when = DateFormat.getDateTimeInstance(
                    DateFormat.MEDIUM, DateFormat.SHORT, Locale.getDefault()
            ).format(d.getLastSeen().toDate());
            h.tvLastOnline.setText("" +when);
        } else {
            h.tvLastOnline.setText("—");
        }

        // Status
        if (d.isOnline()) {
            h.tvRight.setText("Online");
            h.tvRight.setTextColor(0xFF4CAF50); // green
        } else {
            h.tvRight.setText("Offline");
            h.tvRight.setTextColor(0xFFF44336); // red
        }

        h.itemView.setOnClickListener(v -> {
            if (click != null) click.onClick(d);
        });
    }

    @Override
    public int getItemCount() {
        return visible.size();
    }

    static class DeviceVH extends RecyclerView.ViewHolder {
        TextView tvName, tvLastOnline, tvRight;
        DeviceVH(@NonNull View itemView) {
            super(itemView);
            tvName       = itemView.findViewById(R.id.tvDeviceName);
            tvLastOnline = itemView.findViewById(R.id.tvLastOnline);
            tvRight      = itemView.findViewById(R.id.tvStatusRight);
        }
    }
}
