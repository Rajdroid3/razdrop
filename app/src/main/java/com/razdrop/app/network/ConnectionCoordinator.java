package com.razdrop.app.network;

import android.content.Context;
import android.util.Log;
import com.razdrop.app.model.ConnectionMode;
import com.razdrop.app.model.DeviceType;
import com.razdrop.app.model.PeerDevice;
import com.razdrop.app.network.hotspot.LocalHotspotManager;
import com.razdrop.app.network.nsd.NsdDiscoveryManager;
import com.razdrop.app.network.p2p.WifiDirectManager;
import com.razdrop.app.network.protocol.RazPacket;
import com.razdrop.app.network.udp.UdpDiscoveryManager;
import com.razdrop.app.util.DeviceUtils;
import com.razdrop.app.util.NetworkUtils;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ConnectionCoordinator {
    private static final String TAG = "RazDrop-Coordinator";

    private final Context context;
    private final NsdDiscoveryManager nsdManager;
    private final UdpDiscoveryManager udpManager;
    private final WifiDirectManager p2pManager;
    private final LocalHotspotManager hotspotManager;

    private final Map<String, PeerDevice> activePeers = Collections.synchronizedMap(new LinkedHashMap<>());
    private CoordinatorListener listener;

    private boolean isDiscovering = false;
    private boolean isAdvertising = false;

    public interface CoordinatorListener {
        void onPeersChanged(List<PeerDevice> peers);
        void onDirectConnectionReady(String hostAddress, boolean isHost);
        void onHotspotReady(String ssid, String password, String hostIp);
        void onStatusMessage(String message);
    }

    public ConnectionCoordinator(Context context) {
        this.context = context.getApplicationContext();
        this.nsdManager = new NsdDiscoveryManager(this.context);
        this.udpManager = new UdpDiscoveryManager(this.context);
        this.p2pManager = new WifiDirectManager(this.context);
        this.hotspotManager = new LocalHotspotManager(this.context);

        setupListeners();
    }

    public void setListener(CoordinatorListener listener) {
        this.listener = listener;
    }

    private void setupListeners() {
        // NSD Listener
        nsdManager.setListener(new NsdDiscoveryManager.PeerDiscoveryListener() {
            @Override
            public void onPeerFound(PeerDevice peer) {
                if (isValidIp(peer.getIpAddress())) {
                    activePeers.put(peer.getId(), peer);
                    notifyPeersChanged();
                }
            }

            @Override
            public void onPeerLost(PeerDevice peer) {
                activePeers.remove(peer.getId());
                notifyPeersChanged();
            }

            @Override
            public void onDiscoveryStarted() {
                if (listener != null) listener.onStatusMessage("Scanning local Wi-Fi...");
            }

            @Override
            public void onDiscoveryStopped() {}
        });

        // UDP Broadcast Discovery Listener
        udpManager.setListener(peer -> {
            if (isValidIp(peer.getIpAddress())) {
                activePeers.put(peer.getId(), peer);
                notifyPeersChanged();
            }
        });

        // Wi-Fi Direct Listener
        if (p2pManager.isSupported()) {
            p2pManager.setListener(new WifiDirectManager.WifiDirectListener() {
                @Override
                public void onP2pEnabled(boolean enabled) {
                    Log.d(TAG, "P2P enabled: " + enabled);
                }

                @Override
                public void onPeersUpdated(List<PeerDevice> peers) {
                    for (PeerDevice pd : peers) {
                        activePeers.put(pd.getId(), pd);
                    }
                    notifyPeersChanged();
                }

                @Override
                public void onConnected(String hostAddress, boolean isGroupOwner) {
                    if (listener != null) {
                        listener.onDirectConnectionReady(hostAddress, isGroupOwner);
                    }
                }

                @Override
                public void onDisconnected() {
                    if (listener != null) listener.onStatusMessage("Wi-Fi Direct disconnected");
                }

                @Override
                public void onError(String message) {
                    Log.w(TAG, "Wi-Fi Direct error: " + message);
                }
            });
        }

        // Hotspot Listener
        hotspotManager.setListener(new LocalHotspotManager.HotspotStateListener() {
            @Override
            public void onHotspotStarted(String ssid, String password, String hostIp) {
                if (listener != null) {
                    listener.onHotspotReady(ssid, password, hostIp);
                }
            }

            @Override
            public void onHotspotStopped() {
                if (listener != null) listener.onStatusMessage("Local hotspot stopped");
            }

            @Override
            public void onHotspotFailed(int reason) {
                if (listener != null) listener.onStatusMessage("Hotspot failed (code: " + reason + ")");
            }
        });
    }

    public synchronized void startDiscovery() {
        isDiscovering = true;
        activePeers.clear();
        notifyPeersChanged();

        // 1. Start NSD
        nsdManager.startDiscovery();

        // 2. Start UDP Broadcast Listener
        udpManager.start(false);

        // 3. Start Wi-Fi Direct discovery if supported
        if (p2pManager.isSupported()) {
            p2pManager.register();
            p2pManager.discoverPeers();
        }
    }

    public synchronized void startAdvertising(String sessionPin) {
        isAdvertising = true;
        String devName = DeviceUtils.getDeviceName(context);
        DeviceType devType = DeviceUtils.getDeviceType(context);

        // 1. Register NSD service
        nsdManager.registerService(devName, devType, RazPacket.DEFAULT_PORT);

        // 2. Start UDP Broadcast Beacon
        udpManager.start(true);

        // 3. Register Wi-Fi Direct receiver
        if (p2pManager.isSupported()) {
            p2pManager.register();
        }
    }

    public synchronized void startHotspotFallback() {
        if (hotspotManager.isSupported()) {
            hotspotManager.startHotspot();
        }
    }

    public synchronized void stopHotspot() {
        hotspotManager.stopHotspot();
    }

    public synchronized void stopAll() {
        isDiscovering = false;
        isAdvertising = false;
        nsdManager.stopDiscovery();
        nsdManager.unregisterService();
        udpManager.stop();
        if (p2pManager.isSupported()) {
            p2pManager.unregister();
        }
        hotspotManager.stopHotspot();
        activePeers.clear();
    }

    public void addManualPeer(String ip, int port, String name, String pin) {
        PeerDevice manual = new PeerDevice(
                ip + ":" + port,
                name != null ? name : "Android TV",
                DeviceType.TV,
                ip,
                port,
                ConnectionMode.MANUAL_IP
        );
        manual.setPinCode(pin);
        activePeers.put(manual.getId(), manual);
        notifyPeersChanged();
    }

    private boolean isValidIp(String ip) {
        if (ip == null || ip.isEmpty()) return false;
        String[] parts = ip.split("\\.");
        return parts.length == 4;
    }

    private void notifyPeersChanged() {
        if (listener != null) {
            listener.onPeersChanged(getPeers());
        }
    }

    public List<PeerDevice> getPeers() {
        synchronized (activePeers) {
            return new ArrayList<>(activePeers.values());
        }
    }

    public WifiDirectManager getP2pManager() { return p2pManager; }
    public LocalHotspotManager getHotspotManager() { return hotspotManager; }
}
