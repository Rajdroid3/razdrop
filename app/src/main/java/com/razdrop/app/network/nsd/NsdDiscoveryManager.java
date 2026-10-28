package com.razdrop.app.network.nsd;

import android.content.Context;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.util.Log;
import com.razdrop.app.model.ConnectionMode;
import com.razdrop.app.model.DeviceType;
import com.razdrop.app.model.PeerDevice;
import com.razdrop.app.util.DeviceUtils;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

public class NsdDiscoveryManager {
    private static final String TAG = "RazDrop-Nsd";
    public static final String SERVICE_TYPE = "_razdrop._tcp.";

    private final Context context;
    private final NsdManager nsdManager;
    private final List<PeerDevice> discoveredPeers = new ArrayList<>();
    private PeerDiscoveryListener listener;

    private NsdManager.RegistrationListener registrationListener;
    private NsdManager.DiscoveryListener discoveryListener;
    private String registeredServiceName;
    private boolean isRegistered = false;
    private boolean isDiscovering = false;

    public interface PeerDiscoveryListener {
        void onPeerFound(PeerDevice peer);
        void onPeerLost(PeerDevice peer);
        void onDiscoveryStarted();
        void onDiscoveryStopped();
    }

    public NsdDiscoveryManager(Context context) {
        this.context = context.getApplicationContext();
        this.nsdManager = (NsdManager) this.context.getSystemService(Context.NSD_SERVICE);
    }

    public void setListener(PeerDiscoveryListener listener) {
        this.listener = listener;
    }

    public synchronized void registerService(String deviceName, DeviceType type, int port) {
        if (nsdManager == null || isRegistered) return;

        NsdServiceInfo serviceInfo = new NsdServiceInfo();
        serviceInfo.setServiceName("RazDrop-" + deviceName + "-" + type.toInt());
        serviceInfo.setServiceType(SERVICE_TYPE);
        serviceInfo.setPort(port);

        registrationListener = new NsdManager.RegistrationListener() {
            @Override
            public void onServiceRegistered(NsdServiceInfo nsdServiceInfo) {
                registeredServiceName = nsdServiceInfo.getServiceName();
                isRegistered = true;
                Log.d(TAG, "Service registered: " + registeredServiceName);
            }

            @Override
            public void onRegistrationFailed(NsdServiceInfo serviceInfo, int errorCode) {
                Log.e(TAG, "Registration failed with code: " + errorCode);
                isRegistered = false;
            }

            @Override
            public void onServiceUnregistered(NsdServiceInfo serviceInfo) {
                Log.d(TAG, "Service unregistered");
                isRegistered = false;
            }

            @Override
            public void onUnregistrationFailed(NsdServiceInfo serviceInfo, int errorCode) {
                Log.e(TAG, "Unregistration failed with code: " + errorCode);
            }
        };

        try {
            nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener);
        } catch (Exception e) {
            Log.e(TAG, "Failed to register NSD service", e);
        }
    }

    public synchronized void startDiscovery() {
        if (nsdManager == null || isDiscovering) return;

        discoveryListener = new NsdManager.DiscoveryListener() {
            @Override
            public void onStartDiscoveryFailed(String serviceType, int errorCode) {
                Log.e(TAG, "Discovery start failed: " + errorCode);
                isDiscovering = false;
                if (listener != null) listener.onDiscoveryStopped();
            }

            @Override
            public void onStopDiscoveryFailed(String serviceType, int errorCode) {
                Log.e(TAG, "Discovery stop failed: " + errorCode);
            }

            @Override
            public void onDiscoveryStarted(String serviceType) {
                Log.d(TAG, "NSD discovery started for " + serviceType);
                isDiscovering = true;
                if (listener != null) listener.onDiscoveryStarted();
            }

            @Override
            public void onDiscoveryStopped(String serviceType) {
                Log.d(TAG, "NSD discovery stopped");
                isDiscovering = false;
                if (listener != null) listener.onDiscoveryStopped();
            }

            @Override
            public void onServiceFound(NsdServiceInfo serviceInfo) {
                if (!serviceInfo.getServiceType().contains("razdrop")) return;
                if (serviceInfo.getServiceName().equals(registeredServiceName)) return;

                Log.d(TAG, "Service found: " + serviceInfo.getServiceName() + ", resolving...");
                try {
                    nsdManager.resolveService(serviceInfo, new NsdManager.ResolveListener() {
                        @Override
                        public void onResolveFailed(NsdServiceInfo serviceInfo, int errorCode) {
                            Log.w(TAG, "Resolve failed for " + serviceInfo.getServiceName() + " code: " + errorCode);
                        }

                        @Override
                        public void onServiceResolved(NsdServiceInfo resolvedService) {
                            InetAddress host = resolvedService.getHost();
                            if (host == null) return;

                            String rawName = resolvedService.getServiceName();
                            String parsedName = rawName;
                            DeviceType deviceType = DeviceType.PHONE;

                            // Format: RazDrop-Name-TypeInt
                            if (rawName.startsWith("RazDrop-")) {
                                String[] parts = rawName.split("-");
                                if (parts.length >= 3) {
                                    parsedName = parts[1];
                                    try {
                                        int typeInt = Integer.parseInt(parts[2]);
                                        deviceType = DeviceType.fromInt(typeInt);
                                    } catch (NumberFormatException ignored) {}
                                }
                            }

                            PeerDevice peer = new PeerDevice(
                                    rawName,
                                    parsedName,
                                    deviceType,
                                    host.getHostAddress(),
                                    resolvedService.getPort(),
                                    ConnectionMode.LOCAL_LAN
                            );

                            synchronized (discoveredPeers) {
                                discoveredPeers.remove(peer);
                                discoveredPeers.add(peer);
                            }

                            if (listener != null) {
                                listener.onPeerFound(peer);
                            }
                        }
                    });
                } catch (Exception e) {
                    Log.e(TAG, "Error initiating resolve", e);
                }
            }

            @Override
            public void onServiceLost(NsdServiceInfo serviceInfo) {
                Log.d(TAG, "Service lost: " + serviceInfo.getServiceName());
                PeerDevice lostPeer = null;
                synchronized (discoveredPeers) {
                    for (PeerDevice p : discoveredPeers) {
                        if (p.getId().equals(serviceInfo.getServiceName())) {
                            lostPeer = p;
                            break;
                        }
                    }
                    if (lostPeer != null) {
                        discoveredPeers.remove(lostPeer);
                    }
                }
                if (lostPeer != null && listener != null) {
                    listener.onPeerLost(lostPeer);
                }
            }
        };

        try {
            nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener);
        } catch (Exception e) {
            Log.e(TAG, "Failed to start NSD discovery", e);
        }
    }

    public synchronized void stopDiscovery() {
        if (nsdManager != null && isDiscovering && discoveryListener != null) {
            try {
                nsdManager.stopServiceDiscovery(discoveryListener);
            } catch (Exception e) {
                Log.w(TAG, "Error stopping discovery", e);
            }
            isDiscovering = false;
        }
    }

    public synchronized void unregisterService() {
        if (nsdManager != null && isRegistered && registrationListener != null) {
            try {
                nsdManager.unregisterService(registrationListener);
            } catch (Exception e) {
                Log.w(TAG, "Error unregistering service", e);
            }
            isRegistered = false;
        }
    }

    public List<PeerDevice> getDiscoveredPeers() {
        synchronized (discoveredPeers) {
            return new ArrayList<>(discoveredPeers);
        }
    }
}
