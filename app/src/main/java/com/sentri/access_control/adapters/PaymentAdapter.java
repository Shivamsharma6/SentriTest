package com.sentri.access_control.adapters;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.sentri.access_control.R;
import com.sentri.access_control.models.PaymentItem;

import java.util.List;

public class PaymentAdapter extends RecyclerView.Adapter<PaymentAdapter.ViewHolder> {
    private List<PaymentItem> list;

    public PaymentAdapter(List<PaymentItem> list) {
        this.list = list;
    }

    public void updateList(List<PaymentItem> newList) {
        this.list = newList;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_payment, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        PaymentItem payment = list.get(position);
        holder.date.setText(payment.getDate());
        holder.method.setText(payment.getMethod());
        holder.processedBy.setText(payment.getProcessedBy());
        holder.type.setText(payment.getType());
        holder.rate.setText("Rs " + payment.getRate());
        holder.amount.setText((payment.isPositive() ? "+" : "-") + "Rs " + payment.getAmount());
        holder.amount.setTextColor(
                payment.isPositive()
                        ? Color.parseColor("#00FF99")
                        : Color.parseColor("#FF4D4D")
        );
    }

    @Override
    public int getItemCount() {
        return list != null ? list.size() : 0;
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView date;
        TextView method;
        TextView processedBy;
        TextView type;
        TextView rate;
        TextView amount;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            date = itemView.findViewById(R.id.tvDate);
            method = itemView.findViewById(R.id.tvMethod);
            processedBy = itemView.findViewById(R.id.tvProcessedBy);
            type = itemView.findViewById(R.id.tvType);
            rate = itemView.findViewById(R.id.tvRate);
            amount = itemView.findViewById(R.id.tvAmount);
        }
    }
}
