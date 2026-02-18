package com.sentri.access_control.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.sentri.access_control.R;
import com.sentri.access_control.models.ShiftItem;

import java.util.List;

public class ShiftAdapter extends RecyclerView.Adapter<ShiftAdapter.ViewHolder> {

    private List<ShiftItem> items;

    public ShiftAdapter(List<ShiftItem> items) {
        this.items = items;
    }

    public void updateList(List<ShiftItem> newItems) {
        this.items = newItems;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_shift, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ShiftItem item = items.get(position);
        holder.tvDateAdmin.setText(item.getDateAdmin());
        holder.tvTimeSlot.setText(item.getTimeSlot());
        holder.tvSubDates.setText(item.getSubStartDate() + " - " + item.getSubEndDate());
        holder.tvSeatInfo.setText(item.getSeatInfo());
    }

    @Override
    public int getItemCount() {
        return items == null ? 0 : items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvDateAdmin;
        TextView tvTimeSlot;
        TextView tvSubDates;
        TextView tvSeatInfo;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvDateAdmin = itemView.findViewById(R.id.tvDateAdmin);
            tvTimeSlot = itemView.findViewById(R.id.tvTimeSlot);
            tvSubDates = itemView.findViewById(R.id.tvSubDates);
            tvSeatInfo = itemView.findViewById(R.id.tvSeatInfo);
        }
    }
}
