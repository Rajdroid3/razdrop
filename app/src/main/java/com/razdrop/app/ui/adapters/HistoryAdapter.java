package com.razdrop.app.ui.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.razdrop.app.R;
import com.razdrop.app.model.TransferBatch;
import com.razdrop.app.model.TransferItem;
import java.util.ArrayList;
import java.util.List;

public class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.HistoryViewHolder> {
    private final List<TransferBatch> historyList = new ArrayList<>();
    private final OnHistoryActionListener listener;

    public interface OnHistoryActionListener {
        void onOpenFolder(TransferBatch batch);
    }

    public HistoryAdapter(OnHistoryActionListener listener) {
        this.listener = listener;
    }

    public void updateHistory(List<TransferBatch> batches) {
        historyList.clear();
        if (batches != null) {
            historyList.addAll(batches);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public HistoryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_history, parent, false);
        return new HistoryViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull HistoryViewHolder holder, int position) {
        TransferBatch batch = historyList.get(position);
        String peerName = batch.getPeerDevice() != null ? batch.getPeerDevice().getName() : "Unknown Device";

        if (batch.isOutgoing()) {
            holder.ivDirection.setImageResource(R.drawable.ic_send);
            holder.tvPeer.setText("Sent to " + peerName);
        } else {
            holder.ivDirection.setImageResource(R.drawable.ic_receive);
            holder.tvPeer.setText("Received from " + peerName);
        }

        int fileCount = batch.getItems() != null ? batch.getItems().size() : 0;
        String sizeFormatted = TransferItem.formatBytes(batch.getTotalBytes());
        holder.tvDetails.setText(fileCount + " files • " + sizeFormatted + " • " + batch.getOverallState().getDescription());

        holder.btnOpen.setOnClickListener(v -> {
            if (listener != null) listener.onOpenFolder(batch);
        });
    }

    @Override
    public int getItemCount() {
        return historyList.size();
    }

    static class HistoryViewHolder extends RecyclerView.ViewHolder {
        ImageView ivDirection;
        TextView tvPeer;
        TextView tvDetails;
        Button btnOpen;

        HistoryViewHolder(View itemView) {
            super(itemView);
            ivDirection = itemView.findViewById(R.id.iv_history_direction);
            tvPeer = itemView.findViewById(R.id.tv_history_peer);
            tvDetails = itemView.findViewById(R.id.tv_history_details);
            btnOpen = itemView.findViewById(R.id.btn_open_history);
        }
    }
}
