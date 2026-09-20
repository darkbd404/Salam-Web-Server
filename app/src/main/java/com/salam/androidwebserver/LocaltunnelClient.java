package com.salam.androidwebserver;

import android.util.Log;
import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Pure Java Localtunnel Client (localtunnel.me / custom localtunnel servers).
 * Connects over standard HTTPS/TCP to provide instant public HTTPS URLs
 * without requiring SSH or root binaries. Ultra-fast (< 500ms).
 */
public class LocaltunnelClient {
    private static final String TAG = "LocaltunnelClient";
    private static final String DEFAULT_SERVER = "https://localtunnel.me";

    private final String serverUrl;
    private final int localPort;
    private final TunnelManager.TunnelListener listener;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final ExecutorService threadPool = Executors.newCachedThreadPool();
    private String publicUrl = "";
    private int tunnelRemotePort = 0;
    private String tunnelHost = "localtunnel.me";

    public LocaltunnelClient(String serverUrl, int localPort, TunnelManager.TunnelListener listener) {
        this.serverUrl = (serverUrl == null || serverUrl.trim().isEmpty()) ? DEFAULT_SERVER : serverUrl.trim();
        this.localPort = localPort;
        this.listener = listener;
    }

    public String getPublicUrl() {
        return publicUrl;
    }

    public boolean isRunning() {
        return running.get();
    }

    public void start() throws Exception {
        running.set(true);
        // Step 1: Request new tunnel endpoint from localtunnel server
        String endpoint = serverUrl + (serverUrl.endsWith("/") ? "?new" : "/?new");
        URL url = new URL(endpoint);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(6000);
        conn.setReadTimeout(6000);
        conn.setRequestProperty("User-Agent", "SalamWebServer/10.0");

        int code = conn.getResponseCode();
        if (code != 200) {
            throw new IOException("Localtunnel server returned HTTP " + code);
        }

        BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line);
        }
        reader.close();

        JSONObject json = new JSONObject(sb.toString());
        this.publicUrl = json.getString("url");
        this.tunnelRemotePort = json.getInt("port");
        this.tunnelHost = url.getHost();

        Log.i(TAG, "Localtunnel created: " + publicUrl + " -> " + tunnelHost + ":" + tunnelRemotePort);

        // Step 2: Launch connection pool workers
        int initialPoolSize = 6;
        for (int i = 0; i < initialPoolSize; i++) {
            spawnTunnelSocket();
        }
    }

    private void spawnTunnelSocket() {
        if (!running.get()) return;

        threadPool.submit(() -> {
            Socket remoteSocket = null;
            Socket localSocket = null;
            try {
                remoteSocket = new Socket();
                remoteSocket.setTcpNoDelay(true);
                remoteSocket.setKeepAlive(true);
                remoteSocket.connect(new InetSocketAddress(tunnelHost, tunnelRemotePort), 8000);

                // Wait for data from remote server (meaning incoming HTTP request)
                InputStream remoteIn = remoteSocket.getInputStream();
                OutputStream remoteOut = remoteSocket.getOutputStream();

                int firstByte = remoteIn.read();
                if (firstByte == -1) {
                    // Connection closed by server, reconnect standby socket
                    closeQuietly(remoteSocket);
                    if (running.get()) {
                        spawnTunnelSocket();
                    }
                    return;
                }

                // Immediately spawn a new standby socket for next incoming requests
                if (running.get()) {
                    spawnTunnelSocket();
                }

                // Connect to local web server
                localSocket = new Socket();
                localSocket.setTcpNoDelay(true);
                localSocket.connect(new InetSocketAddress("127.0.0.1", localPort), 5000);

                InputStream localIn = localSocket.getInputStream();
                OutputStream localOut = localSocket.getOutputStream();

                // Forward the first read byte + remaining bytes to local web server
                localOut.write(firstByte);
                localOut.flush();

                // Pipe remote -> local in this thread, local -> remote in another thread
                final Socket fRemote = remoteSocket;
                final Socket fLocal = localSocket;

                Future<?> pipe1 = threadPool.submit(() -> pipeStream(remoteIn, localOut, fRemote, fLocal));
                pipeStream(localIn, remoteOut, fLocal, fRemote);
                try {
                    pipe1.get(15, TimeUnit.SECONDS);
                } catch (Exception ignored) {}

            } catch (Exception e) {
                if (running.get()) {
                    Log.d(TAG, "Worker cycle ended: " + e.getMessage());
                }
            } finally {
                closeQuietly(remoteSocket);
                closeQuietly(localSocket);
                if (running.get()) {
                    // Maintain pool size
                    try {
                        Thread.sleep(200);
                    } catch (InterruptedException ignored) {}
                    spawnTunnelSocket();
                }
            }
        });
    }

    private void pipeStream(InputStream in, OutputStream out, Socket s1, Socket s2) {
        byte[] buffer = new byte[16384];
        int read;
        try {
            while (running.get() && (read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
                out.flush();
            }
        } catch (Exception ignored) {
        } finally {
            closeQuietly(s1);
            closeQuietly(s2);
        }
    }

    public void stop() {
        running.set(false);
        try {
            threadPool.shutdownNow();
        } catch (Exception ignored) {}
    }

    private static void closeQuietly(Socket s) {
        if (s != null) {
            try {
                s.close();
            } catch (Exception ignored) {}
        }
    }
}
