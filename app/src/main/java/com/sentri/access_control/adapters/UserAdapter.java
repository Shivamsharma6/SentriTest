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
import com.sentri.access_control.models.UserModel;

import java.util.ArrayList;
import java.util.List;

public class UserAdapter extends RecyclerView.Adapter<UserAdapter.VH> {
    public interface OnUserClick { void onUserClick(UserModel u); }

    private final List<UserModel> fullList;
    private final List<UserModel> displayList;
    private final OnUserClick listener;

    public UserAdapter(List<UserModel> data, OnUserClick listener) {
        this.fullList    = new ArrayList<>(data);
        this.displayList = new ArrayList<>(data);
        this.listener    = listener;
    }

    public void updateList(List<UserModel> data) {
        fullList.clear();
        fullList.addAll(data);
        filter("");
    }

    public void filter(String q) {
        displayList.clear();
        if (q.isEmpty()) {
            displayList.addAll(fullList);
        } else {
            q = q.toLowerCase();
            for (UserModel u : fullList) {
                if (u.getName().toLowerCase().contains(q) ||
                        u.getEmail().toLowerCase().contains(q) ||
                        u.getAccessLevel().toLowerCase().contains(q)) {
                    displayList.add(u);
                }
            }
        }
        notifyDataSetChanged();
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_user, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        UserModel u = displayList.get(position);
        holder.tvName.setText(u.getName());
        holder.tvEmail.setText(u.getEmail());
        holder.tvAccess.setText(u.getAccessLevel());
        if (u.getPhotoUrl() != null && !u.getPhotoUrl().isEmpty()) {
            Glide.with(holder.ivPhoto.getContext())
                    .load(u.getPhotoUrl())
                    .circleCrop()
                    .into(holder.ivPhoto);
        } else {
            holder.ivPhoto.setImageResource(R.drawable.ic_profile_placeholder);
        }
        holder.itemView.setOnClickListener(v -> listener.onUserClick(u));
    }

    @Override public int getItemCount() { return displayList.size(); }

    static class VH extends RecyclerView.ViewHolder {
        ImageView ivPhoto;
        TextView  tvName, tvEmail, tvAccess;

        VH(@NonNull View item) {
            super(item);
            ivPhoto  = item.findViewById(R.id.ivUserPhoto);
            tvName   = item.findViewById(R.id.tvUserName);
            tvEmail  = item.findViewById(R.id.tvUserEmail);
            tvAccess = item.findViewById(R.id.tvAccessLevel);
        }
    }
}
