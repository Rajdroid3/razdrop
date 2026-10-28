package com.razdrop.app.ui.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.razdrop.app.R;
import com.razdrop.app.model.DeviceType;
import com.razdrop.app.model.PeerDevice;
import java.util.ArrayList;
import java.util.List;

public class PeerAdapter extends RecyclerView.Adapter<PeerAdapter.PeerViewHolder> {
    private final List<PeerDevice> peers = new ArrayList<>();
    private final OnPeerClickListener listener;

    public interface OnPeerClickListener {
        void onPeerClick(PeerDevice peer);
    }

    public PeerAdapter(OnPeerClickListener listener) {
        this.listener = listener;
    }

    public void updatePeers(List<PeerDevice> newPeers) {
        peers.clear();
        if (newPeers != null) {
            peers.addAll(newPeers);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public PeerViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_peer, parent, false);
        return new PeerViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PeerViewHolder holder, int position) {
        PeerDevice peer = peers.get(position);
        holder.tvName.setText(peer.getName());
        holder.tvAddress.setText(peer.getDisplayAddress() + " • " + peer.getConnectionMode().getDisplayName());
        holder.tvBadge.setText(peer.getConnectionMode().name().replace("LOCAL_", "").replace("WIFI_", ""));

        if (peer.getType() == DeviceType.TV) {
            holder.ivDevice.setImageResource(R.drawable.ic_tv);
        } else if (peer.getType() == DeviceType.TABLET) {
            holder.ivDevice.setImageResource(R.drawable.ic_tablet);
        } else {
            holder.ivDevice.setImageResource(R.drawable.ic_phone);
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onPeerClick(peer);
        });
    }

    @Override
    public int getItemCount() {
        return peers.size();
    }

    static class PeerViewHolder extends RecyclerView.ViewHolder {
        ImageView ivDevice;
        TextView tvName;
        TextView tvAddress;
        TextView tvBadge;

        PeerViewHolder(View itemView) {
            super(itemView);
            ivDevice = itemView.findViewById(R.id.iv_device_type);
            tvName = itemView.findViewById(R.id.tv_peer_name);
            tvAddress = itemView.findViewById(R.id.tv_peer_address);
            tvBadge = itemView.findViewById(R.id.tv_badge_mode);
        }
    }
}
