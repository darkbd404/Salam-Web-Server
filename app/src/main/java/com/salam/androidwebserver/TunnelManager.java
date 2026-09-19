package com.salam.androidwebserver;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import com.jcraft.jsch.*;
import java.io.*;
import java.util.regex.*;

public class TunnelManager {
    private static final String TAG = "TunnelManager";
    public static final String PROVIDER_LOCALHOST_RUN = "Localhost.run (Fast & Free)";
    public static final String PROVIDER_PINGGY = "Pinggy.io (Zero-Config)";
    public static final String PROVIDER_SERVEO = "Serveo.net (HTTP Tunnel)";
    public static final String PROVIDER_CUSTOM = "Custom Public Domain";

    public interface TunnelListener {
        void onTunnelStarting(String message);
        void onTunnelActive(String publicUrl, String provider);
        void onTunnelError(String error);
        void onTunnelStopped();
    }

    private static volatile TunnelManager instance;
    private Session session;
    private Channel channel;
    private volatile boolean isRunning = false;
    private volatile String activePublicUrl = "";
    private volatile String activeProvider = "";
    private TunnelListener listener;
    private Thread workerThread;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private TunnelManager() {}

    public static synchronized TunnelManager getInstance() {
        if (instance == null) {
            instance = new TunnelManager();
        }
        return instance;
    }

    public void setListener(TunnelListener listener) {
        this.listener = listener;
    }

    public boolean isTunnelRunning() {
        return isRunning && session != null && session.isConnected();
    }

    public String getActivePublicUrl() {
        return activePublicUrl;
    }

    public String getActiveProvider() {
        return activeProvider;
    }

    public synchronized void startTunnel(Context context, int localPort, String preferredProvider) {
        stopTunnel();
        isRunning = true;
        activeProvider = preferredProvider == null ? PROVIDER_LOCALHOST_RUN : preferredProvider;
        activePublicUrl = "";

        notifyStarting("Connecting to public tunnel server (" + activeProvider + ")...");

        workerThread = new Thread(() -> {
            try {
                if (PROVIDER_CUSTOM.equals(activeProvider)) {
                    SharedPreferences p = context.getSharedPreferences("server", Context.MODE_PRIVATE);
                    String custom = p.getString("customUrl", "").trim();
                    if (custom.isEmpty()) {
                        throw new IOException("Custom public domain not configured. Please set in Server Settings.");
                    }
                    activePublicUrl = custom;
                    notifyActive(activePublicUrl, activeProvider);
                    return;
                }

                if (PROVIDER_PINGGY.equals(activeProvider)) {
                    connectPinggy(localPort);
                } else if (PROVIDER_SERVEO.equals(activeProvider)) {
                    connectServeo(localPort);
                } else {
                    connectLocalhostRun(localPort);
                }
            } catch (Exception e) {
                Log.e(TAG, "Tunnel startup failed: " + e.getMessage(), e);
                if (isRunning) {
                    // Try fallback provider if first choice failed
                    if (PROVIDER_LOCALHOST_RUN.equals(activeProvider)) {
                        notifyStarting("Localhost.run busy, switching to Pinggy tunnel...");
                        try {
                            connectPinggy(localPort);
                            return;
                        } catch (Exception ex) {
                            Log.e(TAG, "Pinggy fallback failed: " + ex.getMessage());
                        }
                    }
                    notifyError("Public Tunnel Error: " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
                    stopTunnel();
                }
            }
        });
        workerThread.setDaemon(true);
        workerThread.start();
    }

    private void connectLocalhostRun(int localPort) throws Exception {
        JSch jsch = new JSch();
        JSch.setConfig("StrictHostKeyChecking", "no");
        JSch.setConfig("PreferredAuthentications", "publickey,password,keyboard-interactive");

        // Try port 443 first (avoids ISP blocks on port 22), fallback to 80 or 22
        int[] ports = {443, 80, 22};
        Session s = null;
        Exception lastErr = null;

        for (int p : ports) {
            if (!isRunning) return;
            try {
                notifyStarting("Connecting to ssh.localhost.run:" + p + "...");
                s = jsch.getSession("nokey", "ssh.localhost.run", p);
                s.setConfig("StrictHostKeyChecking", "no");
                s.setTimeout(12000);
                s.connect(12000);
                break;
            } catch (Exception ex) {
                lastErr = ex;
                Log.w(TAG, "Failed connecting to ssh.localhost.run:" + p + " - " + ex.getMessage());
            }
        }

        if (s == null || !s.isConnected()) {
            throw lastErr != null ? lastErr : new IOException("Could not reach ssh.localhost.run");
        }

        session = s;
        // Request remote port forward: remote 80 to 127.0.0.1:localPort
        session.setPortForwardingR(80, "127.0.0.1", localPort);

        // Open shell/exec to read public URL
        ChannelExec exec = (ChannelExec) session.openChannel("exec");
        exec.setCommand("");
        InputStream in = exec.getInputStream();
        exec.connect(10000);
        channel = exec;

        BufferedReader reader = new BufferedReader(new InputStreamReader(in));
        String line = null;
        Pattern urlPattern = Pattern.compile("https://[a-zA-Z0-9.-]+\\.lhr\\.life");
        long deadline = System.currentTimeMillis() + 15000;

        while (isRunning && (line = reader.readLine()) != null) {
            Log.d(TAG, "[localhost.run] " + line);
            Matcher m = urlPattern.matcher(line);
            if (m.find()) {
                activePublicUrl = m.group();
                notifyActive(activePublicUrl, PROVIDER_LOCALHOST_RUN);
                break;
            }
            if (System.currentTimeMillis() > deadline && activePublicUrl.isEmpty()) {
                break;
            }
        }

        if (activePublicUrl.isEmpty() && isRunning) {
            // Keep looking for any https:// url
            Pattern genericHttps = Pattern.compile("https://[a-zA-Z0-9.-]+");
            if (line != null) {
                Matcher m2 = genericHttps.matcher(line);
                if (m2.find()) {
                    activePublicUrl = m2.group();
                    notifyActive(activePublicUrl, PROVIDER_LOCALHOST_RUN);
                }
            }
        }

        if (activePublicUrl.isEmpty()) {
            throw new IOException("Tunnel connected but no public URL was provided.");
        }

        // Keep connection alive
        keepAliveLoop();
    }

    private void connectPinggy(int localPort) throws Exception {
        JSch jsch = new JSch();
        JSch.setConfig("StrictHostKeyChecking", "no");

        notifyStarting("Connecting to Pinggy Tunnel (a.pinggy.io:443)...");
        Session s = jsch.getSession("nokey", "a.pinggy.io", 443);
        s.setConfig("StrictHostKeyChecking", "no");
        s.setTimeout(12000);
        s.connect(12000);
        session = s;

        session.setPortForwardingR(0, "127.0.0.1", localPort);

        ChannelExec exec = (ChannelExec) session.openChannel("exec");
        exec.setCommand("");
        InputStream in = exec.getInputStream();
        exec.connect(10000);
        channel = exec;

        BufferedReader reader = new BufferedReader(new InputStreamReader(in));
        String line;
        Pattern urlPattern = Pattern.compile("https://[a-zA-Z0-9.-]+(?:\\.pinggy\\.link|\\.pinggy\\.online|\\.free\\.pinggy\\.link)");
        long deadline = System.currentTimeMillis() + 15000;

        while (isRunning && (line = reader.readLine()) != null) {
            Log.d(TAG, "[pinggy] " + line);
            Matcher m = urlPattern.matcher(line);
            if (m.find()) {
                activePublicUrl = m.group();
                notifyActive(activePublicUrl, PROVIDER_PINGGY);
                break;
            }
            if (System.currentTimeMillis() > deadline && activePublicUrl.isEmpty()) {
                break;
            }
        }

        if (activePublicUrl.isEmpty()) {
            throw new IOException("Pinggy connected but failed to return public URL.");
        }

        keepAliveLoop();
    }

    private void connectServeo(int localPort) throws Exception {
        JSch jsch = new JSch();
        JSch.setConfig("StrictHostKeyChecking", "no");

        notifyStarting("Connecting to Serveo.net:22...");
        Session s = jsch.getSession("serveo", "serveo.net", 22);
        s.setConfig("StrictHostKeyChecking", "no");
        s.setTimeout(12000);
        s.connect(12000);
        session = s;

        session.setPortForwardingR(80, "127.0.0.1", localPort);

        ChannelExec exec = (ChannelExec) session.openChannel("exec");
        exec.setCommand("");
        InputStream in = exec.getInputStream();
        exec.connect(10000);
        channel = exec;

        BufferedReader reader = new BufferedReader(new InputStreamReader(in));
        String line;
        Pattern urlPattern = Pattern.compile("https://[a-zA-Z0-9.-]+\\.serveo\\.net");
        long deadline = System.currentTimeMillis() + 15000;

        while (isRunning && (line = reader.readLine()) != null) {
            Log.d(TAG, "[serveo] " + line);
            Matcher m = urlPattern.matcher(line);
            if (m.find()) {
                activePublicUrl = m.group();
                notifyActive(activePublicUrl, PROVIDER_SERVEO);
                break;
            }
            if (System.currentTimeMillis() > deadline && activePublicUrl.isEmpty()) {
                break;
            }
        }

        if (activePublicUrl.isEmpty()) {
            throw new IOException("Serveo tunnel connected but public URL was not assigned.");
        }

        keepAliveLoop();
    }

    private void keepAliveLoop() {
        while (isRunning && session != null && session.isConnected()) {
            try {
                Thread.sleep(15000);
                if (session != null && session.isConnected()) {
                    session.sendKeepAliveMsg();
                }
            } catch (Exception e) {
                Log.w(TAG, "KeepAlive ping interrupted: " + e.getMessage());
                break;
            }
        }
        if (isRunning) {
            notifyError("Public Tunnel disconnected.");
            stopTunnel();
        }
    }

    public synchronized void stopTunnel() {
        isRunning = false;
        activePublicUrl = "";
        try {
            if (channel != null) {
                channel.disconnect();
                channel = null;
            }
        } catch (Exception ignored) {}
        try {
            if (session != null) {
                session.disconnect();
                session = null;
            }
        } catch (Exception ignored) {}
        if (workerThread != null) {
            workerThread.interrupt();
            workerThread = null;
        }
        notifyStopped();
    }

    private void notifyStarting(String msg) {
        mainHandler.post(() -> {
            if (listener != null) listener.onTunnelStarting(msg);
        });
    }

    private void notifyActive(String url, String provider) {
        mainHandler.post(() -> {
            if (listener != null) listener.onTunnelActive(url, provider);
        });
    }

    private void notifyError(String err) {
        mainHandler.post(() -> {
            if (listener != null) listener.onTunnelError(err);
        });
    }

    private void notifyStopped() {
        mainHandler.post(() -> {
            if (listener != null) listener.onTunnelStopped();
        });
    }
}
