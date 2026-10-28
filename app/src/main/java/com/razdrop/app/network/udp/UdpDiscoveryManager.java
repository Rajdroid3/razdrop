package com.razdrop.app.network.udp;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import com.razdrop.app.model.ConnectionMode;
import com.razdrop.app.model.DeviceType;
import com.razdrop.app.model.PeerDevice;
import com.razdrop.app.network.protocol.RazPacket;
import com.razdrop.app.util.DeviceUtils;
import com.razdrop.app.util.NetworkUtils;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

public class UdpDiscoveryManager {
    private static final String TAG = "RazDrop-UDP";
    public static final int UDP_PORT = 58921;

    private final Context context;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private UdpDiscoveryListener listener;

    private DatagramSocket socket;
    private Thread listenThread;
    private Thread beaconThread;
    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private WifiManager.MulticastLock multicastLock;

    public interface UdpDiscoveryListener {
        void onPeerDiscovered(PeerDevice peer);
    }

    public UdpDiscoveryManager(Context context) {
        this.context = context.getApplicationContext();
    }

    public void setListener(UdpDiscoveryListener listener) {
        this.listener = listener;
    }

    public synchronized void start(boolean isBeacon) {
        if (isRunning.get()) return;
        isRunning.set(true);

        acquireMulticastLock();

        listenThread = new Thread(this::listenLoop, "RazDrop-UdpListener");
        listenThread.start();

        if (isBeacon) {
            beaconThread = new Thread(this::beaconLoop, "RazDrop-UdpBeacon");
            beaconThread.start();
        }
    }

    private void acquireMulticastLock() {
        try {
            WifiManager wm = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
            if (wm != null) {
                multicastLock = wm.createMulticastLock("RazDropMulticast");
                multicastLock.setReferenceCounted(true);
                multicastLock.acquire();
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed acquiring multicast lock", e);
        }
    }

    private void releaseMulticastLock() {
        try {
            if (multicastLock != null && multicastLock.isHeld()) {
                multicastLock.release();
            }
        } catch (Exception ignored) {}
    }

    private void listenLoop() {
        try {
            socket = new DatagramSocket(UDP_PORT);
            socket.setBroadcast(true);
            socket.setReuseAddress(true);
            byte[] buf = new byte[1024];

            while (isRunning.get()) {
                DatagramPacket packet = new DatagramPacket(buf, buf.length);
                socket.receive(packet);

                String msg = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8).trim();
                // Format: RAZDROP_BEACON|deviceName|deviceTypeInt|tcpPort|pin
                if (msg.startsWith("RAZDROP_BEACON|")) {
                    String[] parts = msg.split("\\|");
                    if (parts.length >= 4) {
                        String name = parts[1];
                        int typeInt = 1;
                        try { typeInt = Integer.parseInt(parts[2]); } catch (Exception ignored) {}
                        int tcpPort = RazPacket.DEFAULT_PORT;
                        try { tcpPort = Integer.parseInt(parts[3]); } catch (Exception ignored) {}
                        String pin = parts.length >= 5 ? parts[4] : "";

                        String senderIp = packet.getAddress().getHostAddress();
                        String myIp = NetworkUtils.getLocalIpAddress();

                        // Don't discover ourselves
                        if (!senderIp.equals(myIp)) {
                            PeerDevice peer = new PeerDevice(
                                    senderIp + ":" + tcpPort,
                                    name,
                                    DeviceType.fromInt(typeInt),
                                    senderIp,
                                    tcpPort,
                                    ConnectionMode.LOCAL_LAN
                            );
                            peer.setPinCode(pin);

                            if (listener != null) {
                                mainHandler.post(() -> listener.onPeerDiscovered(peer));
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            if (isRunning.get()) {
                Log.w(TAG, "UDP listen stopped", e);
            }
        } finally {
            closeSocket();
        }
    }

    private void beaconLoop() {
        try {
            DatagramSocket broadcastSocket = new DatagramSocket();
            broadcastSocket.setBroadcast(true);

            while (isRunning.get()) {
                String myName = DeviceUtils.getDeviceName(context);
                int myType = DeviceUtils.getDeviceType(context).toInt();
                String myPin = "";

                String beaconMsg = "RAZDROP_BEACON|" + myName + "|" + myType + "|" + RazPacket.DEFAULT_PORT + "|" + myPin;
                byte[] data = beaconMsg.getBytes(StandardCharsets.UTF_8);

                // Broadcast to 255.255.255.255
                DatagramPacket packet = new DatagramPacket(
                        data,
                        data.length,
                        InetAddress.getByName("255.255.255.255"),
                        UDP_PORT
                );
                broadcastSocket.send(packet);

                Thread.sleep(2000); // Send beacon every 2 seconds
            }
            broadcastSocket.close();
        } catch (Exception e) {
            if (isRunning.get()) {
                Log.w(TAG, "Beacon loop error", e);
            }
        }
    }

    public synchronized void stop() {
        isRunning.set(false);
        closeSocket();
        releaseMulticastLock();
        if (listenThread != null) {
            listenThread.interrupt();
            listenThread = null;
        }
        if (beaconThread != null) {
            beaconThread.interrupt();
            beaconThread = null;
        }
    }

    private void closeSocket() {
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (Exception ignored) {}
    }
}
