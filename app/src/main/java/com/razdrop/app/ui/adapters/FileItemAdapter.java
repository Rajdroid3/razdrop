package com.razdrop.app.ui.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.razdrop.app.R;
import com.razdrop.app.model.TransferItem;
import com.razdrop.app.model.TransferState;
import java.util.ArrayList;
import java.util.List;

public class FileItemAdapter extends RecyclerView.Adapter<FileItemAdapter.FileViewHolder> {
    private final List<TransferItem> items = new ArrayList<>();

    public FileItemAdapter() {}

    public void updateItems(List<TransferItem> newItems) {
        items.clear();
        if (newItems != null) {
            items.addAll(newItems);
        }
        notifyDataSetChanged();
    }

    public void updateItem(int index, TransferItem item) {
        if (index >= 0 && index < items.size()) {
            items.set(index, item);
            notifyItemChanged(index);
        }
    }

    @NonNull
    @Override
    public FileViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_file_transfer, parent, false);
        return new FileViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull FileViewHolder holder, int position) {
        TransferItem item = items.get(position);
        holder.tvName.setText(item.getName());
        holder.tvSize.setText(item.getFormattedSize());
        int progress = item.getProgressPercent();
        holder.pbProgress.setProgress(progress);
        holder.tvPercent.setText(progress + "%");

        if (item.getState() == TransferState.COMPLETED) {
            holder.ivStatus.setVisibility(View.VISIBLE);
            holder.ivStatus.setImageResource(R.drawable.ic_check);
            holder.tvPercent.setVisibility(View.GONE);
        } else if (item.getState() == TransferState.FAILED || item.getState() == TransferState.CANCELLED) {
            holder.ivStatus.setVisibility(View.VISIBLE);
            holder.ivStatus.setImageResource(R.drawable.ic_close);
            holder.tvPercent.setVisibility(View.GONE);
        } else {
            holder.ivStatus.setVisibility(View.GONE);
            holder.tvPercent.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class FileViewHolder extends RecyclerView.ViewHolder {
        ImageView ivIcon;
        TextView tvName;
        TextView tvSize;
        ImageView ivStatus;
        TextView tvPercent;
        ProgressBar pbProgress;

        FileViewHolder(View itemView) {
            super(itemView);
            ivIcon = itemView.findViewById(R.id.iv_file_icon);
            tvName = itemView.findViewById(R.id.tv_file_name);
            tvSize = itemView.findViewById(R.id.tv_file_size);
            ivStatus = itemView.findViewById(R.id.iv_file_status);
            tvPercent = itemView.findViewById(R.id.tv_file_percent);
            pbProgress = itemView.findViewById(R.id.pb_file_progress);
        }
    }
}
