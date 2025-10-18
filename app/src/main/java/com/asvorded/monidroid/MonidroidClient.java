package com.asvorded.monidroid;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.util.Log;

import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.function.Consumer;

public class MonidroidClient {

    public enum ConnectionStates {
        Init, Connected, DisplayOff, Connecting;

        public static ConnectionStates next(ConnectionStates current) {
            ConnectionStates[] values = values();
            return values[(current.ordinal() + 1) % values.length];
        }
    }

    private int width;
    private int height;
    private int hertz;

    private InetAddress serverAddress;
    private Socket clientSocket;
    private Thread communicationThread;

    private boolean connected;

    private void setConnected(boolean b) {
        connected = b;
        if (connected && onConnected != null) {
            onConnected.run();
        } else if (!connected && onDisconnected != null) {
            onDisconnected.run();
        }
    }

    private Runnable onConnected;
    private Runnable onDisconnected;
    private Consumer<@Nullable Bitmap> onNewFrame;

    public MonidroidClient setServerAddress(InetAddress address) {
        this.serverAddress = address;
        return this;
    }

    public MonidroidClient setDisplaySettings(int width, int height, int hertz) {
        this.width = width;
        this.height = height;
        this.hertz = hertz;
        return this;
    }

    public MonidroidClient setConnectionCallbacks(Runnable onConnected, Runnable onDisconnected) {
        this.onConnected = onConnected;
        this.onDisconnected = onDisconnected;
        return this;
    }

    public MonidroidClient setNewFrameCallback(Consumer<@Nullable Bitmap> callback) {
        this.onNewFrame = callback;
        return this;
    }

    public void start() {
        communicationThread = new Thread(this::communicationMain);
        communicationThread.start();
    }

    public void stop() {
        communicationThread.interrupt();
        try {
            clientSocket.close();
        } catch (Exception ignored) { }
    }

    private void receiveNeedBytesCount(byte[] buf) throws IOException {
        int needCount = buf.length;
        int currentSize = 0;
        int bytesReceived;
        InputStream inStream = clientSocket.getInputStream();
        do {
            byte[] recvBuf = new byte[needCount];
            bytesReceived = inStream.read(recvBuf);
            if (bytesReceived <= 0) {
                throw new IOException("Connection closed by the host");
            }
            System.arraycopy(recvBuf, 0, buf, currentSize, bytesReceived);
            needCount -= bytesReceived;
            currentSize += bytesReceived;
        } while (needCount > 0);
    }

    private void communicationMain() {
        while (!Thread.interrupted()) {
            // Trying connect to server
            while (!connected && !Thread.interrupted()) {
                try {
                    clientSocket = new Socket(serverAddress, MonidroidProtocol.MONITOR_PORT);
                    setConnected(true);
                } catch (IOException e) {
                    Log.w(MonidroidProtocol.DEBUG_TAG, "Connection failed, retrying in 5 seconds");
                    try {
                        Thread.sleep(5000);
                    } catch (InterruptedException ex) {
                        return;
                    }
                }
            }

            // Send WELCOME message to identify device
            try {
                initDevice();

                // Receive display frames
                byte[] header = MonidroidProtocol.FRAME_WORD.getBytes(StandardCharsets.US_ASCII);
                while (true) {
                    byte[] headerBuf = new byte[header.length];
                    byte[] sizeBuf = new byte[4];
                    receiveNeedBytesCount(headerBuf);
                    if (Arrays.equals(headerBuf, header)) {
                        // Get image size
                        receiveNeedBytesCount(sizeBuf);
                        int imageSize = ByteBuffer.wrap(sizeBuf).order(ByteOrder.LITTLE_ENDIAN).getInt();

                        if (imageSize < 0) {
                            onNewFrame.accept(null);
                        } else if (imageSize > 0) {
                            // Get image buffer
                            byte[] imageBuf = new byte[imageSize];

                            receiveNeedBytesCount(imageBuf);

                            // Convert buffer to image
                            BitmapFactory.Options options = new BitmapFactory.Options();
                            Bitmap bitmap = BitmapFactory.decodeByteArray(
                                    imageBuf, 0, imageSize, options
                            );

                            if (onNewFrame != null) {
                                onNewFrame.accept(bitmap);
                            }
                        }
                    }
                }
            } catch (IOException e) {
                setConnected(false);
                try {
                    clientSocket.close();
                } catch (IOException ignored) { }
            }
        }
    }

    /**
     *  Sends WELCOME message with device info to server
     */
    private void initDevice() throws IOException {
        ByteArrayOutputStream bs = new ByteArrayOutputStream();

        // WELCOME
        byte[] word = MonidroidProtocol.WELCOME_WORD.getBytes(StandardCharsets.US_ASCII);
        bs.write(word);

        // model
        String model = String.format("%s %s", Build.BRAND, Build.MODEL);
        bs.write(ByteBuffer
                .allocate(4).order(ByteOrder.LITTLE_ENDIAN)
                .putInt(model.length()).array());
        bs.write(model.getBytes(StandardCharsets.UTF_16LE));

        // screen sides and hertz
        bs.write(ByteBuffer
                .allocate(4).order(ByteOrder.LITTLE_ENDIAN)
                .putInt(width).array());
        bs.write(ByteBuffer
                .allocate(4).order(ByteOrder.LITTLE_ENDIAN)
                .putInt(height).array());
        bs.write(ByteBuffer
                .allocate(4).order(ByteOrder.LITTLE_ENDIAN)
                .putInt(hertz).array());

        // send
        OutputStream out = clientSocket.getOutputStream();
        out.write(bs.toByteArray());
    }
}