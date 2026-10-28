package com.razdrop.app.ui;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Toast;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.razdrop.app.R;
import com.razdrop.app.model.TransferBatch;
import com.razdrop.app.ui.adapters.HistoryAdapter;
import com.razdrop.app.util.FileUtils;
import com.razdrop.app.util.PreferenceUtils;
import java.io.File;
import java.util.List;

public class HistoryActivity extends Activity {
    private RecyclerView rvHistory;
    private LinearLayout layoutEmpty;
    private HistoryAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        initViews();
        loadHistory();
    }

    private void initViews() {
        ImageButton btnBack = findViewById(R.id.btn_history_back);
        Button btnOpenDownloads = findViewById(R.id.btn_open_downloads);
        rvHistory = findViewById(R.id.rv_history);
        layoutEmpty = findViewById(R.id.layout_empty_history);

        btnBack.setOnClickListener(v -> finish());
        btnOpenDownloads.setOnClickListener(v -> openReceivedFolder());

        rvHistory.setLayoutManager(new LinearLayoutManager(this));
        adapter = new HistoryAdapter(batch -> openReceivedFolder());
        rvHistory.setAdapter(adapter);
    }

    private void loadHistory() {
        List<TransferBatch> history = PreferenceUtils.getTransferHistory(this);
        if (history.isEmpty()) {
            layoutEmpty.setVisibility(View.VISIBLE);
            rvHistory.setVisibility(View.GONE);
        } else {
            layoutEmpty.setVisibility(View.GONE);
            rvHistory.setVisibility(View.VISIBLE);
            adapter.updateHistory(history);
        }
    }

    private void openReceivedFolder() {
        try {
            File folder = FileUtils.getReceiveFolder(this);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            Uri uri = Uri.parse(folder.getAbsolutePath());
            intent.setDataAndType(uri, "resource/folder");
            if (intent.resolveActivity(getPackageManager()) != null) {
                startActivity(intent);
            } else {
                // General fallback: open download files or show path
                Toast.makeText(this, "Files saved in: " + folder.getAbsolutePath(), Toast.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "Destination: Download/RazDrop", Toast.LENGTH_SHORT).show();
        }
    }
}
