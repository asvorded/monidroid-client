package com.asvorded.monidroid;

import android.util.Log;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;
import java.util.function.Consumer;

public class EchoClient {

    public static class HostInfo {
        private final InetAddress address;
        private final String hostName;

        public HostInfo(InetAddress address, String hostName) {
            this.address = address;
            this.hostName = hostName;
        }

        public String getHostName() {
            return hostName;
        }

        public InetAddress getAddress() {
            return address;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            HostInfo hostInfo = (HostInfo) o;
            return Objects.equals(address, hostInfo.address) && Objects.equals(hostName, hostInfo.hostName);
        }

        @Override
        public int hashCode() {
            return Objects.hash(address, hostName);
        }
    }

    public enum AutoDetectingOptions {
        Enabled, Error, Disabled
    }

    public EchoClient() {
        echoSendThread = new Thread(this::sendMain);
        echoReceiveThread = new Thread(this::receiveMain);
    }

    private final Thread echoSendThread;
    private final Thread echoReceiveThread;

    private DatagramSocket echoSocket;

    private Runnable onFailed;
    private Consumer<HostInfo> onDeviceDetected;

    public void startEcho(Runnable failCallback, Consumer<HostInfo> deviceDetectedCallback) throws IOException {
        echoSocket = new DatagramSocket(MonidroidProtocol.MONITOR_PORT);
        echoSocket.setBroadcast(true);

        onDeviceDetected = deviceDetectedCallback;
        onFailed = failCallback;

        echoSendThread.start();
        echoReceiveThread.start();
    }

    public void startEcho2(Runnable failCallback, Consumer<HostInfo> deviceDetectedCallback) throws IOException {
        echoSocket = new DatagramSocket(MonidroidProtocol.MONITOR_PORT);
        echoSocket.setBroadcast(true);

        onDeviceDetected = deviceDetectedCallback;
        onFailed = failCallback;

        echoReceiveThread.start();
    }

    private void sendMain() {
        byte[] sendBuf = MonidroidProtocol.CLIENT_ECHO_WORD.getBytes(StandardCharsets.US_ASCII);
        InetAddress broadcastAddress;
        try {
            broadcastAddress = InetAddress.getByName("255.255.255.255");
        } catch (IOException ignored) {
            return;
        }
        DatagramPacket dgram = new DatagramPacket(
            sendBuf, sendBuf.length, broadcastAddress, MonidroidProtocol.MONITOR_PORT
        );

        boolean sending = true;
        while (sending) {
            try {
                onDeviceDetected.accept(null);

                echoSocket.send(dgram);
                Log.d(MonidroidProtocol.DEBUG_TAG, "Successfully sent datagram, waiting 5 seconds");
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                return;
            } catch (IOException e) {
                sending = false;
                onFailed.run();
            }
        }
    }

    private void receiveMain() {
        byte[] header = MonidroidProtocol.SERVER_ECHO_WORD.getBytes(StandardCharsets.US_ASCII);
        byte[] buf = new byte[128];

        byte[] headerBuf = new byte[header.length];
        byte[] lenBuf = new byte[4];

        boolean accepting = true;
        while (accepting) {
            try {
                DatagramPacket dgram = new DatagramPacket(buf, buf.length);
                echoSocket.receive(dgram);
                if (dgram.getLength() > header.length + 4) {
                    byte[] data = dgram.getData();

                    // Check header
                    System.arraycopy(data, 0, headerBuf, 0, header.length);
                    if (Arrays.equals(header, headerBuf)) {
                        // Get host name length
                        System.arraycopy(data, header.length, lenBuf, 0, 4);

                        int len = ByteBuffer.wrap(lenBuf)
                                .order(ByteOrder.LITTLE_ENDIAN)
                                .getInt();

                        if (header.length + 4 + len * 2 == dgram.getLength()) {
                            // Get host name
                            byte[] nameBuf = new byte[len * 2];
                            System.arraycopy(data, header.length + 4, nameBuf, 0, nameBuf.length);
                            String hostName = new String(nameBuf, StandardCharsets.UTF_16LE);

                            // Trigger event
                            HostInfo hostInfo = new HostInfo(dgram.getAddress(), hostName);
                            onDeviceDetected.accept(hostInfo);
                        }
                    }
                }
            } catch (IOException e) {
                accepting = false;
                onFailed.run();
            }
        }
    }

    public void endEcho() {
        echoSendThread.interrupt();
        echoReceiveThread.interrupt();
        echoSocket.close();
    }

    public void endEcho2() {
        echoSendThread.interrupt();
        echoSocket.close();
    }
}
