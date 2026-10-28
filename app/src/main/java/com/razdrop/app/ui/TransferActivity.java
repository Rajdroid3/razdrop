package com.razdrop.app.ui;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.razdrop.app.R;
import com.razdrop.app.model.PeerDevice;
import com.razdrop.app.model.TransferBatch;
import com.razdrop.app.model.TransferItem;
import com.razdrop.app.model.TransferState;
import com.razdrop.app.network.engine.TransferCallback;
import com.razdrop.app.service.TransferService;
import com.razdrop.app.ui.adapters.FileItemAdapter;
import com.razdrop.app.util.FileUtils;
import java.io.File;

public class TransferActivity extends Activity implements TransferCallback {
    private ImageView ivDirection;
    private TextView tvTitle;
    private TextView tvPeer;
    private TextView tvSpeed;
    private TextView tvEta;
    private TextView tvBytesRatio;
    private TextView tvPercent;
    private ProgressBar pbOverall;
    private RecyclerView rvFiles;
    private Button btnAction;

    private FileItemAdapter adapter;
    private TransferService transferService;
    private boolean isBound = false;
    private boolean isTransferFinished = false;

    private final ServiceConnection serviceConn = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            TransferService.LocalBinder binder = (TransferService.LocalBinder) service;
            transferService = binder.getService();
            isBound = true;
            transferService.addListener(TransferActivity.this);

            TransferBatch currentBatch = transferService.getCurrentBatch();
            if (currentBatch != null) {
                setupBatchUi(currentBatch);
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            transferService = null;
            isBound = false;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_transfer);

        initViews();
        bindService(new Intent(this, TransferService.class), serviceConn, Context.BIND_AUTO_CREATE);
    }

    private void initViews() {
        ivDirection = findViewById(R.id.iv_transfer_direction);
        tvTitle = findViewById(R.id.tv_transfer_title);
        tvPeer = findViewById(R.id.tv_transfer_peer);
        tvSpeed = findViewById(R.id.tv_speed);
        tvEta = findViewById(R.id.tv_eta);
        tvBytesRatio = findViewById(R.id.tv_bytes_ratio);
        tvPercent = findViewById(R.id.tv_overall_percent);
        pbOverall = findViewById(R.id.pb_overall_transfer);
        rvFiles = findViewById(R.id.rv_transfer_files);
        btnAction = findViewById(R.id.btn_transfer_action);

        rvFiles.setLayoutManager(new LinearLayoutManager(this));
        adapter = new FileItemAdapter();
        rvFiles.setAdapter(adapter);

        btnAction.setOnClickListener(v -> {
            if (isTransferFinished) {
                finish();
            } else {
                if (transferService != null) {
                    transferService.cancelTransfer();
                }
                finish();
            }
        });
    }

    private void setupBatchUi(TransferBatch batch) {
        if (batch == null) return;
        if (batch.isOutgoing()) {
            ivDirection.setImageResource(R.drawable.ic_send);
            tvTitle.setText("Sending Files");
        } else {
            ivDirection.setImageResource(R.drawable.ic_receive);
            tvTitle.setText("Receiving Files");
        }

        if (batch.getPeerDevice() != null) {
            tvPeer.setText("Connected to " + batch.getPeerDevice().getName());
        }

        if (batch.getItems() != null) {
            adapter.updateItems(batch.getItems());
            tvBytesRatio.setText("0 B / " + TransferItem.formatBytes(batch.getTotalBytes()));
        }
    }

    @Override
    public void onConnected(PeerDevice peer) {
        tvPeer.setText("Connected to " + peer.getName());
    }

    @Override
    public void onHandshakeReceived(PeerDevice peer, String pin, boolean autoAccept) {}

    @Override
    public void onManifestReceived(TransferBatch batch) {
        setupBatchUi(batch);
    }

    @Override
    public void onFileStarted(TransferItem item, int index, int totalFiles) {
        tvTitle.setText((index + 1) + " of " + totalFiles + " files");
        adapter.updateItem(index, item);
    }

    @Override
    public void onProgress(TransferBatch batch, TransferItem currentItem, long transferredBytes, long totalBytes, double speedBytesPerSec, long etaSeconds) {
        tvSpeed.setText(batch.getFormattedSpeed());
        tvEta.setText("ETA: " + batch.getFormattedEta());
        tvBytesRatio.setText(TransferItem.formatBytes(transferredBytes) + " / " + TransferItem.formatBytes(totalBytes));

        int percent = batch.getOverallProgressPercent();
        pbOverall.setProgress(percent);
        tvPercent.setText(percent + "%");

        if (batch.getCurrentFileIndex() >= 0 && batch.getCurrentFileIndex() < batch.getItems().size()) {
            adapter.updateItem(batch.getCurrentFileIndex(), currentItem);
        }
    }

    @Override
    public void onFileCompleted(TransferItem item, int index) {
        adapter.updateItem(index, item);
    }

    @Override
    public void onBatchCompleted(TransferBatch batch) {
        isTransferFinished = true;
        tvTitle.setText(R.string.transfer_state_completed);
        tvSpeed.setText("Done");
        tvEta.setText("");
        pbOverall.setProgress(100);
        tvPercent.setText("100%");

        btnAction.setText(R.string.action_done);
        btnAction.setBackgroundResource(R.drawable.bg_button_primary);
        btnAction.setTextColor(0xFF0A0F1D);

        Toast.makeText(this, "All files transferred successfully!", Toast.LENGTH_LONG).show();
    }

    @Override
    public void onError(String message, Throwable throwable) {
        isTransferFinished = true;
        tvTitle.setText(R.string.transfer_state_failed);
        tvSpeed.setText("Error");
        tvEta.setText(message);

        btnAction.setText(R.string.action_done);
        btnAction.setBackgroundResource(R.drawable.bg_button_secondary);
        btnAction.setTextColor(getResources().getColor(R.color.raz_text_primary));

        Toast.makeText(this, "Transfer error: " + message, Toast.LENGTH_LONG).show();
    }

    @Override
    public void onCancelled() {
        isTransferFinished = true;
        tvTitle.setText(R.string.transfer_state_cancelled);
        tvSpeed.setText("Cancelled");

        btnAction.setText(R.string.action_done);
        btnAction.setBackgroundResource(R.drawable.bg_button_secondary);
        btnAction.setTextColor(getResources().getColor(R.color.raz_text_primary));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (isBound && transferService != null) {
            transferService.removeListener(this);
            unbindService(serviceConn);
            isBound = false;
        }
    }
}
