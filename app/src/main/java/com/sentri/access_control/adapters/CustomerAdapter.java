package com.sentri.access_control.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.sentri.access_control.R;
import com.sentri.access_control.models.Customer;

import java.util.ArrayList;
import java.util.List;

public class CustomerAdapter extends RecyclerView.Adapter<CustomerAdapter.ViewHolder> {

    private final List<Customer> originalList;
    private final List<Customer> filteredList;
    private OnItemClickListener listener;

    /** Callback for item clicks */
    public interface OnItemClickListener {
        void onItemClick(Customer customer);
    }

    /** Set from your Activity/Fragment */
    public void setOnItemClickListener(OnItemClickListener listener) {
        this.listener = listener;
    }

    public CustomerAdapter(List<Customer> list) {
        this.originalList = new ArrayList<>(list);
        this.filteredList = new ArrayList<>(list);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.customer_item, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Customer c = filteredList.get(position);
        holder.tvName.setText(c.getCustomerName());
        holder.tvId  .setText(c.getCustomerId());
        holder.tvCard.setText(c.getCustomerCurrentCardId());

        // status indicator
        if (holder.statusDot != null) {
            int backgroundRes = c.isCustomerStatus()
                    ? R.drawable.bg_status_active
                    : R.drawable.bg_status_inactive;
            holder.statusDot.setBackgroundResource(backgroundRes);
        }

        String url = c.getCustomerPhoto();
        if (url != null && !url.isEmpty()) {
            Glide.with(holder.img.getContext())
                    .load(url)
                    .placeholder(R.drawable.ic_profile_placeholder)
                    .error(R.drawable.ic_profile_placeholder)
                    .circleCrop()
                    .into(holder.img);
        } else {
            holder.img.setImageResource(R.drawable.ic_profile_placeholder);
        }

        // ←— ADDED: forward clicks to listener
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onItemClick(c);
            }
        });
    }

    @Override
    public int getItemCount() {
        return filteredList.size();
    }

    /** Replace entire data set and reset filter */
    public void updateList(List<Customer> newList) {
        originalList.clear();
        originalList.addAll(newList);
        filter("");  // show all by default
    }

    /** Case-insensitive filter on name or ID */
    public void filter(String query) {
        filteredList.clear();
        if (query == null || query.trim().isEmpty()) {
            filteredList.addAll(originalList);
        } else {
            String q = query.toLowerCase();
            for (Customer c : originalList) {
                if (c.getCustomerName().toLowerCase().contains(q)
                        || c.getCustomerId()  .toLowerCase().contains(q)) {
                    filteredList.add(c);
                }
            }
        }
        notifyDataSetChanged();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView img;
        TextView  tvName, tvId, tvCard;
        View      statusDot;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            img       = itemView.findViewById(R.id.imgCustomerPhoto);
            tvName    = itemView.findViewById(R.id.tvCustomerName);
            tvId      = itemView.findViewById(R.id.tvCustomerId);
            tvCard    = itemView.findViewById(R.id.tvCardId);
            statusDot = itemView.findViewById(R.id.viewStatusDot);
        }
    }
}
