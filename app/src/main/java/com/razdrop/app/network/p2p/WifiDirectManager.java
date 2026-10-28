package com.razdrop.app.network.p2p;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.NetworkInfo;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Looper;
import android.util.Log;
import com.razdrop.app.model.ConnectionMode;
import com.razdrop.app.model.DeviceType;
import com.razdrop.app.model.PeerDevice;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class WifiDirectManager {
    private static final String TAG = "RazDrop-P2P";

    private final Context context;
    private final WifiP2pManager p2pManager;
    private WifiP2pManager.Channel channel;
    private final List<WifiP2pDevice> p2pDevices = new ArrayList<>();
    private WifiDirectListener listener;
    private BroadcastReceiver receiver;
    private boolean isP2pEnabled = false;

    public interface WifiDirectListener {
        void onP2pEnabled(boolean enabled);
        void onPeersUpdated(List<PeerDevice> peers);
        void onConnected(String hostAddress, boolean isGroupOwner);
        void onDisconnected();
        void onError(String message);
    }

    public WifiDirectManager(Context context) {
        this.context = context.getApplicationContext();
        this.p2pManager = (WifiP2pManager) this.context.getSystemService(Context.WIFI_P2P_SERVICE);
        if (p2pManager != null) {
            this.channel = p2pManager.initialize(this.context, Looper.getMainLooper(), null);
        }
    }

    public void setListener(WifiDirectListener listener) {
        this.listener = listener;
    }

    public boolean isSupported() {
        return p2pManager != null && channel != null;
    }

    public void register() {
        if (!isSupported()) return;

        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);

        receiver = new BroadcastReceiver() {
            @SuppressLint("MissingPermission")
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (action == null) return;

                switch (action) {
                    case WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION: {
                        int state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1);
                        isP2pEnabled = (state == WifiP2pManager.WIFI_P2P_STATE_ENABLED);
                        if (listener != null) listener.onP2pEnabled(isP2pEnabled);
                        break;
                    }
                    case WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION: {
                        if (p2pManager != null && channel != null) {
                            try {
                                p2pManager.requestPeers(channel, new WifiP2pManager.PeerListListener() {
                                    @Override
                                    public void onPeersAvailable(WifiP2pDeviceList peers) {
                                        Collection<WifiP2pDevice> deviceList = peers.getDeviceList();
                                        p2pDevices.clear();
                                        p2pDevices.addAll(deviceList);

                                        List<PeerDevice> converted = new ArrayList<>();
                                        for (WifiP2pDevice dev : deviceList) {
                                            PeerDevice pd = new PeerDevice(
                                                    dev.deviceAddress,
                                                    dev.deviceName,
                                                    DeviceType.PHONE,
                                                    "", // IP resolved upon connection
                                                    0,
                                                    ConnectionMode.WIFI_DIRECT
                                            );
                                            converted.add(pd);
                                        }
                                        if (listener != null) {
                                            listener.onPeersUpdated(converted);
                                        }
                                    }
                                });
                            } catch (SecurityException e) {
                                Log.e(TAG, "Missing permission for requestPeers", e);
                            }
                        }
                        break;
                    }
                    case WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION: {
                        NetworkInfo networkInfo = intent.getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO);
                        if (networkInfo != null && networkInfo.isConnected()) {
                            try {
                                p2pManager.requestConnectionInfo(channel, new WifiP2pManager.ConnectionInfoListener() {
                                    @Override
                                    public void onConnectionInfoAvailable(WifiP2pInfo info) {
                                        if (info.groupFormed && info.groupOwnerAddress != null) {
                                            String host = info.groupOwnerAddress.getHostAddress();
                                            Log.d(TAG, "P2P Connected! Group owner: " + host + ", isOwner: " + info.isGroupOwner);
                                            if (listener != null) {
                                                listener.onConnected(host, info.isGroupOwner);
                                            }
                                        }
                                    }
                                });
                            } catch (SecurityException e) {
                                Log.e(TAG, "Missing permission for requestConnectionInfo", e);
                            }
                        } else {
                            if (listener != null) {
                                listener.onDisconnected();
                            }
                        }
                        break;
                    }
                }
            }
        };

        try {
            context.registerReceiver(receiver, intentFilter);
        } catch (Exception e) {
            Log.e(TAG, "Failed to register P2P receiver", e);
        }
    }

    public void unregister() {
        if (receiver != null) {
            try {
                context.unregisterReceiver(receiver);
            } catch (Exception ignored) {}
            receiver = null;
        }
    }

    @SuppressLint("MissingPermission")
    public void discoverPeers() {
        if (!isSupported()) return;
        try {
            p2pManager.discoverPeers(channel, new WifiP2pManager.ActionListener() {
                @Override
                public void onSuccess() {
                    Log.d(TAG, "Wi-Fi Direct peer discovery started");
                }

                @Override
                public void onFailure(int reason) {
                    Log.w(TAG, "Wi-Fi Direct discoverPeers failed code: " + reason);
                    if (listener != null) {
                        listener.onError("Discovery failed (" + reason + ")");
                    }
                }
            });
        } catch (SecurityException e) {
            Log.e(TAG, "Missing permission for discoverPeers", e);
        }
    }

    @SuppressLint("MissingPermission")
    public void connect(String deviceAddress) {
        if (!isSupported() || deviceAddress == null) return;
        WifiP2pConfig config = new WifiP2pConfig();
        config.deviceAddress = deviceAddress;
        try {
            p2pManager.connect(channel, config, new WifiP2pManager.ActionListener() {
                @Override
                public void onSuccess() {
                    Log.d(TAG, "Connecting to " + deviceAddress);
                }

                @Override
                public void onFailure(int reason) {
                    Log.w(TAG, "Connect failed: " + reason);
                    if (listener != null) {
                        listener.onError("Failed to connect to peer (" + reason + ")");
                    }
                }
            });
        } catch (SecurityException e) {
            Log.e(TAG, "Missing permission to connect", e);
        }
    }

    @SuppressLint("MissingPermission")
    public void disconnect() {
        if (!isSupported()) return;
        try {
            p2pManager.removeGroup(channel, null);
        } catch (Exception ignored) {}
    }
}
