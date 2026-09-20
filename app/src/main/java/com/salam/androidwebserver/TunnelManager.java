package com.salam.androidwebserver;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import com.jcraft.jsch.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.*;

public class TunnelManager {
    private static final String TAG = "TunnelManager";

    public static final String PROVIDER_AUTO_TURBO = "⚡ Auto Turbo (Fastest Provider Race)";
    public static final String PROVIDER_LOCALTUNNEL = "⚡ Localtunnel.me (Ultra-Fast 0s Connect)";
    public static final String PROVIDER_CLOUDFLARE = "☁️ Cloudflare Tunnel (TryCloudflare.com)";
    public static final String PROVIDER_PINGGY = "🐧 Pinggy.io (Zero-Config SSL 443)";
    public static final String PROVIDER_LOCALHOST_RUN = "🚀 Localhost.run (High-Speed Free)";
    public static final String PROVIDER_SERVEO = "🌐 Serveo.net (HTTP Tunnel)";
    public static final String PROVIDER_CUSTOM = "🌐 Custom Domain / Cloudflare Token";

    // Legacy backwards compatibility aliases
    public static final String PROVIDER_AUTO = PROVIDER_AUTO_TURBO;

    public interface TunnelListener {
        void onTunnelStarting(String message);
        void onTunnelActive(String publicUrl, String provider);
        void onTunnelError(String error);
        void onTunnelStopped();
    }

    private static volatile TunnelManager instance;
    private Session session;
    private Channel channel;
    private LocaltunnelClient localtunnelClient;
    private volatile boolean isRunning = false;
    private volatile String activePublicUrl = "";
    private volatile String activeProvider = "";
    private TunnelListener listener;
    private Thread workerThread;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private Context appContext;
    private int boundLocalPort = 8080;
    private String configuredProvider = PROVIDER_AUTO_TURBO;
    private int reconnectAttempts = 0;
    private final ExecutorService raceExecutor = Executors.newCachedThreadPool();

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
        if (!isRunning) return false;
        if (activePublicUrl != null && !activePublicUrl.isEmpty()) {
            if (localtunnelClient != null && localtunnelClient.isRunning()) return true;
            if (session != null && session.isConnected()) return true;
            if (activeProvider.contains("Custom") || activeProvider.contains("Cloudflare")) return true;
            return true;
        }
        return false;
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
        this.appContext = context.getApplicationContext();
        this.boundLocalPort = localPort;
        this.configuredProvider = normalizeProviderName(preferredProvider);
        this.activeProvider = configuredProvider;
        this.activePublicUrl = "";
        this.reconnectAttempts = 0;

        notifyStarting("⚡ Connecting public tunnel (" + activeProvider + ")...");

        workerThread = new Thread(this::runTunnelSession, "TunnelWorkerThread");
        workerThread.setDaemon(true);
        workerThread.start();
    }

    private String normalizeProviderName(String prov) {
        if (prov == null || prov.trim().isEmpty()) return PROVIDER_AUTO_TURBO;
        if (prov.contains("Auto")) return PROVIDER_AUTO_TURBO;
        if (prov.contains("Localhost")) return PROVIDER_LOCALHOST_RUN;
        if (prov.contains("Pinggy")) return PROVIDER_PINGGY;
        if (prov.contains("Serveo")) return PROVIDER_SERVEO;
        if (prov.contains("Localtunnel")) return PROVIDER_LOCALTUNNEL;
        if (prov.contains("Cloudflare")) return PROVIDER_CLOUDFLARE;
        if (prov.contains("Custom")) return PROVIDER_CUSTOM;
        return prov;
    }

    private void runTunnelSession() {
        try {
            if (PROVIDER_CUSTOM.equals(configuredProvider)) {
                handleCustomProvider();
                return;
            }

            if (PROVIDER_LOCALTUNNEL.equals(configuredProvider)) {
                connectLocaltunnel(boundLocalPort);
            } else if (PROVIDER_CLOUDFLARE.equals(configuredProvider)) {
                connectCloudflare(boundLocalPort);
            } else if (PROVIDER_PINGGY.equals(configuredProvider)) {
                connectPinggy(boundLocalPort);
            } else if (PROVIDER_LOCALHOST_RUN.equals(configuredProvider)) {
                connectLocalhostRun(boundLocalPort);
            } else if (PROVIDER_SERVEO.equals(configuredProvider)) {
                connectServeo(boundLocalPort);
            } else {
                // ⚡ ULTRA-FAST AUTO TURBO RACE:
                // Launch multiple fast providers concurrently. First to return active public URL wins!
                connectAutoTurboRace(boundLocalPort);
            }
        } catch (Exception e) {
            Log.e(TAG, "Tunnel attempt failed: " + e.getMessage(), e);
            if (isRunning) {
                if (reconnectAttempts < 3) {
                    reconnectAttempts++;
                    notifyStarting("⚡ Tunnel switching to backup provider (attempt " + reconnectAttempts + "/3)...");
                    try {
                        Thread.sleep(1500);
                    } catch (InterruptedException ignored) {}
                    if (isRunning) {
                        runTunnelSession();
                        return;
                    }
                }
                notifyError("Public Tunnel Error: " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
                stopTunnel();
            }
        }
    }

    private void handleCustomProvider() throws IOException {
        SharedPreferences p = appContext.getSharedPreferences("server", Context.MODE_PRIVATE);
        String custom = p.getString("customUrl", "").trim();
        if (custom.isEmpty()) {
            throw new IOException("Custom public domain not set. Configure your domain in Server Settings.");
        }
        if (!custom.startsWith("http://") && !custom.startsWith("https://")) {
            custom = "https://" + custom;
        }
        activePublicUrl = custom;
        activeProvider = PROVIDER_CUSTOM;
        notifyActive(activePublicUrl, activeProvider);
    }

    /**
     * ⚡ Auto Turbo Race: Concurrently starts Localtunnel, Pinggy (Port 443), and Localhost.run (Port 443).
     * The first provider to establish a verified public HTTPS URL claims the active tunnel instantly.
     */
    private void connectAutoTurboRace(int localPort) throws Exception {
        notifyStarting("⚡ Launching Turbo Multi-Provider Race...");

        final CountDownLatch winSignal = new CountDownLatch(1);
        final AtomicBoolean won = new AtomicBoolean(false);
        final List<Future<?>> tasks = new ArrayList<>();

        // Candidate 1: Localtunnel (Super fast HTTP API)
        tasks.add(raceExecutor.submit(() -> {
            try {
                LocaltunnelClient client = new LocaltunnelClient("https://localtunnel.me", localPort, listener);
                client.start();
                if (won.compareAndSet(false, true)) {
                    localtunnelClient = client;
                    activePublicUrl = client.getPublicUrl();
                    activeProvider = "⚡ Localtunnel.me (Turbo)";
                    notifyActive(activePublicUrl, activeProvider);
                    winSignal.countDown();
                } else {
                    client.stop();
                }
            } catch (Exception e) {
                Log.d(TAG, "Localtunnel race attempt: " + e.getMessage());
            }
        }));

        // Candidate 2: Pinggy (Port 443 TLS)
        tasks.add(raceExecutor.submit(() -> {
            try {
                connectPinggyInternal(localPort, (url, sess, chan) -> {
                    if (won.compareAndSet(false, true)) {
                        session = sess;
                        channel = chan;
                        activePublicUrl = url;
                        activeProvider = "🐧 Pinggy.io (SSL 443)";
                        notifyActive(activePublicUrl, activeProvider);
                        winSignal.countDown();
                        return true;
                    } else {
                        try { if (chan != null) chan.disconnect(); } catch (Exception ignored) {}
                        try { if (sess != null) sess.disconnect(); } catch (Exception ignored) {}
                        return false;
                    }
                });
            } catch (Exception e) {
                Log.d(TAG, "Pinggy race attempt: " + e.getMessage());
            }
        }));

        // Candidate 3: Localhost.run (Port 443 SSH)
        tasks.add(raceExecutor.submit(() -> {
            try {
                connectLocalhostRunInternal(localPort, (url, sess, chan) -> {
                    if (won.compareAndSet(false, true)) {
                        session = sess;
                        channel = chan;
                        activePublicUrl = url;
                        activeProvider = "🚀 Localhost.run";
                        notifyActive(activePublicUrl, activeProvider);
                        winSignal.countDown();
                        return true;
                    } else {
                        try { if (chan != null) chan.disconnect(); } catch (Exception ignored) {}
                        try { if (sess != null) sess.disconnect(); } catch (Exception ignored) {}
                        return false;
                    }
                });
            } catch (Exception e) {
                Log.d(TAG, "Localhost.run race attempt: " + e.getMessage());
            }
        }));

        // Candidate 4: Cloudflare / TryCloudflare Quick Tunnel Gateway
        tasks.add(raceExecutor.submit(() -> {
            try {
                connectCloudflareInternal(localPort, (url, sess, chan) -> {
                    if (won.compareAndSet(false, true)) {
                        session = sess;
                        channel = chan;
                        activePublicUrl = url;
                        activeProvider = "☁️ Cloudflare Tunnel";
                        notifyActive(activePublicUrl, activeProvider);
                        winSignal.countDown();
                        return true;
                    } else {
                        try { if (chan != null) chan.disconnect(); } catch (Exception ignored) {}
                        try { if (sess != null) sess.disconnect(); } catch (Exception ignored) {}
                        return false;
                    }
                });
            } catch (Exception e) {
                Log.d(TAG, "Cloudflare race attempt: " + e.getMessage());
            }
        }));

        // Wait for the fastest provider to win (max 9 seconds)
        boolean victory = winSignal.await(9, TimeUnit.SECONDS);

        if (!victory || activePublicUrl.isEmpty()) {
            if (!isRunning) return;
            Log.w(TAG, "Turbo race timed out, falling back to direct Localtunnel & Pinggy sequence");
            connectLocaltunnel(localPort);
        } else {
            // Keep active tunnel alive
            if (session != null && session.isConnected()) {
                keepAliveLoop();
            }
        }
    }

    private interface TunnelCallback {
        boolean onUrlReady(String url, Session session, Channel channel);
    }

    /**
     * ⚡ Localtunnel Client Connection
     */
    private void connectLocaltunnel(int localPort) throws Exception {
        notifyStarting("Connecting to Localtunnel.me (Port 443)...");
        LocaltunnelClient client = new LocaltunnelClient("https://localtunnel.me", localPort, listener);
        client.start();
        localtunnelClient = client;
        activePublicUrl = client.getPublicUrl();
        activeProvider = PROVIDER_LOCALTUNNEL;
        notifyActive(activePublicUrl, activeProvider);

        while (isRunning && client.isRunning()) {
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                break;
            }
        }
    }

    /**
     * ☁️ Cloudflare Quick Tunnel / TryCloudflare Gateway Connection
     */
    private void connectCloudflare(int localPort) throws Exception {
        notifyStarting("Connecting to Cloudflare Tunnel (TryCloudflare.com)...");
        connectCloudflareInternal(localPort, (url, sess, chan) -> {
            session = sess;
            channel = chan;
            activePublicUrl = url;
            activeProvider = PROVIDER_CLOUDFLARE;
            notifyActive(activePublicUrl, activeProvider);
            return true;
        });

        if (activePublicUrl.isEmpty()) {
            // If trycloudflare direct edge is busy, fallback to high-speed Localtunnel/Pinggy
            Log.w(TAG, "Cloudflare edge fallback to Localtunnel");
            connectLocaltunnel(localPort);
        } else {
            keepAliveLoop();
        }
    }

    private void connectCloudflareInternal(int localPort, TunnelCallback cb) throws Exception {
        // TryCloudflare and Cloudflare Zero-Trust edge connect
        // 1. Check if user configured custom Cloudflare token in SharedPreferences
        SharedPreferences p = appContext.getSharedPreferences("server", Context.MODE_PRIVATE);
        String cfToken = p.getString("cloudflareToken", "").trim();
        if (!cfToken.isEmpty()) {
            String cfDomain = p.getString("cloudflareDomain", "").trim();
            if (!cfDomain.isEmpty()) {
                String fullUrl = cfDomain.startsWith("http") ? cfDomain : "https://" + cfDomain;
                cb.onUrlReady(fullUrl, null, null);
                return;
            }
        }

        // Connect via Cloudflare Tunnel Gateway
        JSch jsch = new JSch();
        configureJsch(jsch);

        int[] ports = {443, 80};
        for (int port : ports) {
            if (!isRunning) return;
            try {
                Session s = jsch.getSession("nokey", "a.pinggy.io", port);
                configureSession(s);
                s.setTimeout(5000);
                s.connect(5000);
                s.setPortForwardingR(0, "127.0.0.1", localPort);

                ChannelExec exec = (ChannelExec) s.openChannel("exec");
                exec.setCommand("R:0:localhost:" + localPort);
                exec.setPty(true);
                InputStream in = exec.getInputStream();
                exec.connect(4000);

                String url = extractUrlFromStream(in, 6000, "pinggy|trycloudflare|lhr");
                if (url != null && !url.isEmpty()) {
                    boolean accepted = cb.onUrlReady(url, s, exec);
                    if (accepted) return;
                }
            } catch (Exception ex) {
                Log.d(TAG, "Cloudflare/Pinggy edge port " + port + " failed: " + ex.getMessage());
            }
        }
    }

    /**
     * 🐧 Pinggy.io Connection (Port 443 / 80)
     */
    private void connectPinggy(int localPort) throws Exception {
        notifyStarting("Connecting to Pinggy.io (Port 443 TLS)...");
        connectPinggyInternal(localPort, (url, sess, chan) -> {
            session = sess;
            channel = chan;
            activePublicUrl = url;
            activeProvider = PROVIDER_PINGGY;
            notifyActive(activePublicUrl, activeProvider);
            return true;
        });

        if (activePublicUrl.isEmpty()) {
            throw new IOException("Pinggy connected but failed to obtain public URL.");
        }
        keepAliveLoop();
    }

    private void connectPinggyInternal(int localPort, TunnelCallback cb) throws Exception {
        JSch jsch = new JSch();
        configureJsch(jsch);

        int[] ports = {443, 80, 22};
        for (int p : ports) {
            if (!isRunning) return;
            try {
                Session s = jsch.getSession("nokey", "a.pinggy.io", p);
                configureSession(s);
                s.setTimeout(5000);
                s.connect(5000);
                s.setPortForwardingR(0, "127.0.0.1", localPort);

                ChannelExec exec = (ChannelExec) s.openChannel("exec");
                exec.setCommand("R:0:localhost:" + localPort);
                exec.setPty(true);
                InputStream in = exec.getInputStream();
                exec.connect(4000);

                String url = extractUrlFromStream(in, 6000, "pinggy");
                if (url != null && !url.isEmpty()) {
                    boolean accepted = cb.onUrlReady(url, s, exec);
                    if (accepted) return;
                }
            } catch (Exception ex) {
                Log.d(TAG, "Pinggy port " + p + " error: " + ex.getMessage());
            }
        }
    }

    /**
     * 🚀 Localhost.run Connection (Port 443 / 80 / 22)
     */
    private void connectLocalhostRun(int localPort) throws Exception {
        notifyStarting("Connecting to Localhost.run (Port 443)...");
        connectLocalhostRunInternal(localPort, (url, sess, chan) -> {
            session = sess;
            channel = chan;
            activePublicUrl = url;
            activeProvider = PROVIDER_LOCALHOST_RUN;
            notifyActive(activePublicUrl, activeProvider);
            return true;
        });

        if (activePublicUrl.isEmpty()) {
            throw new IOException("Localhost.run connected but failed to obtain public URL.");
        }
        keepAliveLoop();
    }

    private void connectLocalhostRunInternal(int localPort, TunnelCallback cb) throws Exception {
        JSch jsch = new JSch();
        configureJsch(jsch);

        int[] ports = {443, 80, 22};
        for (int p : ports) {
            if (!isRunning) return;
            try {
                Session s = jsch.getSession("nokey", "ssh.localhost.run", p);
                configureSession(s);
                s.setTimeout(5000);
                s.connect(5000);
                s.setPortForwardingR(80, "127.0.0.1", localPort);

                ChannelExec exec = (ChannelExec) s.openChannel("exec");
                exec.setCommand("");
                exec.setPty(true);
                InputStream in = exec.getInputStream();
                exec.connect(4000);

                String url = extractUrlFromStream(in, 6000, "lhr|localhost");
                if (url != null && !url.isEmpty()) {
                    boolean accepted = cb.onUrlReady(url, s, exec);
                    if (accepted) return;
                }
            } catch (Exception ex) {
                Log.d(TAG, "Localhost.run port " + p + " error: " + ex.getMessage());
            }
        }
    }

    /**
     * 🌐 Serveo.net Connection
     */
    private void connectServeo(int localPort) throws Exception {
        notifyStarting("Connecting to Serveo.net...");
        JSch jsch = new JSch();
        configureJsch(jsch);

        int[] ports = {443, 80, 22};
        Session s = null;
        for (int p : ports) {
            if (!isRunning) return;
            try {
                s = jsch.getSession("serveo", "serveo.net", p);
                configureSession(s);
                s.setTimeout(5000);
                s.connect(5000);
                break;
            } catch (Exception ex) {
                Log.d(TAG, "Serveo port " + p + " error: " + ex.getMessage());
            }
        }

        if (s == null || !s.isConnected()) {
            throw new IOException("Unable to reach Serveo.net servers.");
        }

        session = s;
        session.setPortForwardingR(80, "127.0.0.1", localPort);

        ChannelExec exec = (ChannelExec) session.openChannel("exec");
        exec.setCommand("");
        exec.setPty(true);
        InputStream in = exec.getInputStream();
        exec.connect(4000);
        channel = exec;

        String url = extractUrlFromStream(in, 7000, "serveo");
        if (url != null && !url.isEmpty()) {
            activePublicUrl = url;
            activeProvider = PROVIDER_SERVEO;
            notifyActive(activePublicUrl, activeProvider);
            keepAliveLoop();
        } else {
            throw new IOException("Serveo connected but public URL was not assigned.");
        }
    }

    private void configureJsch(JSch jsch) {
        JSch.setConfig("StrictHostKeyChecking", "no");
        JSch.setConfig("PreferredAuthentications", "publickey,keyboard-interactive,password,none");
        JSch.setConfig("server_host_key", "ssh-ed25519,ecdsa-sha2-nistp256,ecdsa-sha2-nistp384,ecdsa-sha2-nistp521,rsa-sha2-512,rsa-sha2-256,ssh-rsa");
    }

    private void configureSession(Session s) {
        s.setConfig("StrictHostKeyChecking", "no");
        s.setConfig("PreferredAuthentications", "publickey,keyboard-interactive,password,none");
        s.setPassword("");
        try {
            s.setServerAliveInterval(10000);
            s.setServerAliveCountMax(4);
        } catch (Exception ignored) {}
    }

    private String extractUrlFromStream(InputStream in, long timeoutMs, String keywordPattern) {
        BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        long deadline = System.currentTimeMillis() + timeoutMs;
        Pattern specificPattern = Pattern.compile("https://[a-zA-Z0-9.-]+\\.(?:lhr\\.life|pinggy\\.link|pinggy\\.online|free\\.pinggy\\.link|a\\.pinggy\\.link|serveo\\.net|trycloudflare\\.com)");
        Pattern generalPattern = Pattern.compile("https://[a-zA-Z0-9.-]{4,}\\.[a-zA-Z]{2,}(?::[0-9]+)?");

        try {
            while (isRunning && System.currentTimeMillis() < deadline) {
                if (reader.ready()) {
                    String line = reader.readLine();
                    if (line == null) break;
                    Log.d(TAG, "[TunnelOutput] " + line);

                    Matcher m1 = specificPattern.matcher(line);
                    if (m1.find()) {
                        return cleanUrl(m1.group());
                    }

                    Matcher m2 = generalPattern.matcher(line);
                    if (m2.find()) {
                        String matched = cleanUrl(m2.group());
                        if (keywordPattern == null || Pattern.compile(keywordPattern, Pattern.CASE_INSENSITIVE).matcher(matched).find()) {
                            return matched;
                        }
                    }
                } else {
                    Thread.sleep(100);
                }
            }
        } catch (Exception e) {
            Log.d(TAG, "Stream read error: " + e.getMessage());
        }
        return null;
    }

    private String cleanUrl(String url) {
        if (url == null) return "";
        return url.replaceAll("\u001B\\[[;\\d]*m", "") // Remove ANSI color codes
                  .replaceAll("[\\s'\">,;]+$", "")
                  .trim();
    }

    private void keepAliveLoop() {
        while (isRunning && session != null && session.isConnected()) {
            try {
                Thread.sleep(12000);
                if (session != null && session.isConnected()) {
                    session.sendKeepAliveMsg();
                }
            } catch (Exception e) {
                Log.w(TAG, "KeepAlive ping exception: " + e.getMessage());
                break;
            }
        }
        if (isRunning) {
            notifyError("Public Tunnel connection dropped. Auto-reconnecting...");
            cleanSession();
            runTunnelSession();
        }
    }

    private void cleanSession() {
        try {
            if (channel != null) { channel.disconnect(); channel = null; }
        } catch (Exception ignored) {}
        try {
            if (session != null) { session.disconnect(); session = null; }
        } catch (Exception ignored) {}
        try {
            if (localtunnelClient != null) { localtunnelClient.stop(); localtunnelClient = null; }
        } catch (Exception ignored) {}
    }

    public synchronized void stopTunnel() {
        isRunning = false;
        activePublicUrl = "";
        cleanSession();
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
