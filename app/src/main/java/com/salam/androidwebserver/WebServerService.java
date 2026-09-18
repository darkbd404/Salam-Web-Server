package com.salam.androidwebserver;

import android.app.ActivityManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.StatFs;
import android.util.Base64;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Salam Web Server V10
 *
 * Clean replacement for the previous WebServerService.java.
 * This file intentionally contains only one implementation of every helper,
 * including zip()/zipRec(), so the previous duplicate-method errors are removed.
 */
public class WebServerService extends Service {

    public static volatile boolean running = false;

    public static final AtomicInteger requests = new AtomicInteger(0);
    public static final Set<String> clients = ConcurrentHashMap.newKeySet();
    public static final CopyOnWriteArrayList<String> LOGS = new CopyOnWriteArrayList<>();
    public static final CopyOnWriteArrayList<String> HISTORY = new CopyOnWriteArrayList<>();

    public static volatile String password = "";
    public static volatile String customHost = "";
    public static volatile int rateLimit = 60;
    public static volatile int maxClients = 32;

    private static final Set<String> ALLOW = ConcurrentHashMap.newKeySet();
    private static final Set<String> BLOCK = ConcurrentHashMap.newKeySet();
    private static final ConcurrentHashMap<String, ArrayDeque<Long>> RATE =
            new ConcurrentHashMap<>();

    private static volatile long startedAt = 0L;
    private static volatile long rxBytes = 0L;
    private static volatile long txBytes = 0L;

    private static volatile ServerSocket serverSocket;
    private static ExecutorService serverPool = Executors.newCachedThreadPool();

    private static final Object SERVER_LOCK = new Object();
    private static final Object SETTINGS_LOCK = new Object();

    private static final String PREFS = "salam_security";
    private static final String CHANNEL_ID = "salam_server_v10";
    private static final int NOTIFICATION_ID = 77010;
    private static final int PORT = 8080;

    private static long previousTotalCpu = -1L;
    private static long previousIdleCpu = -1L;

    @Override
    public void onCreate() {
        super.onCreate();

        loadSettings(this);
        createNotificationChannel();

        // Android requires foreground services to call startForeground promptly.
        startForeground(NOTIFICATION_ID, buildNotification());

        // If the service was intentionally left enabled, restore the server.
        SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE);
        if (p.getBoolean("server_enabled", true)) {
            startServer();
        } else {
            running = false;
            updateNotification();
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();

        if ("STOP".equals(action)) {
            getSharedPreferences(PREFS, MODE_PRIVATE)
                    .edit().putBoolean("server_enabled", false).apply();
            stopServer();
            return START_NOT_STICKY;
        }

        if ("RESTART".equals(action)) {
            getSharedPreferences(PREFS, MODE_PRIVATE)
                    .edit().putBoolean("server_enabled", true).apply();

            new Thread(() -> {
                stopServerInternal(false);
                try {
                    Thread.sleep(250L);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
                startServer();
            }, "salam-restart").start();

            return START_STICKY;
        }

        getSharedPreferences(PREFS, MODE_PRIVATE)
                .edit().putBoolean("server_enabled", true).apply();

        startServer();
        return START_STICKY;
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        // Keep the foreground service alive when the Activity is removed
        // from recents. START_STICKY also allows Android to recreate it later.
        if (running) {
            updateNotification();
        }
        super.onTaskRemoved(rootIntent);
    }

    @Override
    public void onDestroy() {
        stopServerInternal(false);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    /* ============================================================
       SERVER LIFECYCLE
       ============================================================ */

    public void startServer() {
        synchronized (SERVER_LOCK) {
            if (running && serverSocket != null && !serverSocket.isClosed()) {
                updateNotification();
                return;
            }

            try {
                if (serverPool == null || serverPool.isShutdown()) {
                    serverPool = Executors.newCachedThreadPool();
                }

                ServerSocket ss = new ServerSocket(
                        PORT,
                        Math.max(16, maxClients * 2),
                        InetAddress.getByName("0.0.0.0")
                );

                serverSocket = ss;
                startedAt = System.currentTimeMillis();
                rxBytes = 0L;
                txBytes = 0L;
                running = true;

                addLog("🟢 SERVER STARTED • " + currentUrl(this));
                updateNotification();

                serverPool.execute(() -> acceptLoop(ss));

            } catch (Exception e) {
                running = false;
                serverSocket = null;
                addLog("🔴 START ERROR • " + safeMessage(e));
                updateNotification();
            }
        }
    }

    public void stopServer() {
        getSharedPreferences(PREFS, MODE_PRIVATE)
                .edit().putBoolean("server_enabled", false).apply();
        stopServerInternal(true);
    }

    private void stopServerInternal(boolean stopService) {
        synchronized (SERVER_LOCK) {
            boolean wasRunning = running;
            running = false;

            ServerSocket ss = serverSocket;
            serverSocket = null;

            if (ss != null) {
                try {
                    ss.close();
                } catch (IOException ignored) {
                }
            }

            clients.clear();
            RATE.clear();

            if (wasRunning) {
                addLog("🔴 SERVER STOPPED");
            }

            updateNotification();

            if (stopService) {
                try {
                    stopForeground(STOP_FOREGROUND_REMOVE);
                } catch (Exception ignored) {
                }
                stopSelf();
            }
        }
    }

    private void acceptLoop(ServerSocket ss) {
        while (running && ss == serverSocket && !ss.isClosed()) {
            Socket socket = null;
            try {
                socket = ss.accept();
                socket.setSoTimeout(15000);

                final Socket accepted = socket;
                final String ip = accepted.getInetAddress() == null
                        ? "unknown"
                        : accepted.getInetAddress().getHostAddress();

                if (clients.size() >= maxClients) {
                    send(accepted, 503, "text/plain; charset=utf-8",
                            "Server busy: maximum clients reached.");
                    closeQuietly(accepted);
                    continue;
                }

                serverPool.execute(() -> handleClient(accepted));

            } catch (IOException e) {
                closeQuietly(socket);
                if (running) {
                    addLog("⚠️ ACCEPT ERROR • " + safeMessage(e));
                }
            } catch (Exception e) {
                closeQuietly(socket);
                if (running) {
                    addLog("⚠️ ACCEPT FAILURE • " + safeMessage(e));
                }
            }
        }
    }

    /* ============================================================
       HTTP ENGINE
       ============================================================ */

    private void handleClient(Socket socket) {
        String ip = "unknown";

        try {
            if (socket.getInetAddress() != null) {
                ip = socket.getInetAddress().getHostAddress();
            }

            clients.add(ip);
            requests.incrementAndGet();

            InputStream input = new BufferedInputStream(socket.getInputStream());
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(input, StandardCharsets.ISO_8859_1)
            );

            String requestLine = reader.readLine();
            if (requestLine == null || requestLine.trim().isEmpty()) {
                return;
            }

            String[] parts = requestLine.split(" ");
            if (parts.length < 2) {
                send(socket, 400, "text/plain; charset=utf-8", "Bad Request");
                return;
            }

            String method = parts[0].toUpperCase(Locale.US);
            String target = parts[1];

            Map<String, String> headers = new HashMap<>();
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty()) {
                    break;
                }

                int colon = line.indexOf(':');
                if (colon > 0) {
                    String key = line.substring(0, colon).trim().toLowerCase(Locale.US);
                    String value = line.substring(colon + 1).trim();
                    headers.put(key, value);
                }
            }

            String rawPath = target;
            String path = target.split("\\?", 2)[0];

            try {
                path = URLDecoder.decode(path, "UTF-8");
            } catch (Exception ignored) {
            }

            addLog(ip + " • " + method + " " + path);
            HISTORY.add(now() + " | " + ip + " | " + method + " " + path);
            trimHistory();

            if (BLOCK.contains(ip)) {
                send(socket, 403, "text/plain; charset=utf-8", "Blocked IP");
                return;
            }

            if (!ALLOW.isEmpty() && !ALLOW.contains(ip)) {
                send(socket, 403, "text/plain; charset=utf-8", "IP is not in the allowlist");
                return;
            }

            if (!allowRate(ip)) {
                send(socket, 429, "text/plain; charset=utf-8",
                        "Request rate limit exceeded");
                return;
            }

            if (!authorized(headers)) {
                authRequired(socket);
                return;
            }

            if ("GET".equals(method) || "HEAD".equals(method)) {
                handleGet(socket, method, path);
            } else if ("POST".equals(method)) {
                handlePost(socket, headers, path, input);
            } else {
                send(socket, 405, "text/plain; charset=utf-8",
                        "Method Not Allowed");
            }

        } catch (Exception e) {
            addLog("⚠️ CLIENT ERROR • " + ip + " • " + safeMessage(e));
        } finally {
            clients.remove(ip);
            closeQuietly(socket);
        }
    }

    private void handleGet(Socket socket, String method, String path) throws Exception {
        if ("/__salam__/api/info".equals(path)) {
            sendJson(socket, infoJson());
            return;
        }

        if ("/__salam__/api/logs".equals(path)) {
            sendJson(socket, listJson("logs", LOGS));
            return;
        }

        if ("/__salam__/api/history".equals(path)) {
            sendJson(socket, listJson("history", HISTORY));
            return;
        }

        if ("/__salam__/api/files".equals(path)) {
            sendJson(socket, filesJson(webRoot(this), "/"));
            return;
        }

        if ("/__salam__/api/settings".equals(path)) {
            sendJson(socket, settingsJson());
            return;
        }

        if ("/__salam__/api/unblock-all".equals(path)) {
            BLOCK.clear();
            saveListsOnly(this);
            send(socket, 200, "text/plain; charset=utf-8", "All blocked IPs cleared");
            return;
        }

        if (path.startsWith("/__salam__/")) {
            send(socket, 200, "text/html; charset=utf-8", controlPage());
            return;
        }

        serveFile(socket, method, path);
    }

    private void handlePost(
            Socket socket,
            Map<String, String> headers,
            String path,
            InputStream input
    ) throws Exception {

        if ("/__salam__/api/start".equals(path)) {
            getSharedPreferences(PREFS, MODE_PRIVATE)
                    .edit().putBoolean("server_enabled", true).apply();
            startServer();
            send(socket, 200, "text/plain; charset=utf-8", "Server started");
            return;
        }

        if ("/__salam__/api/stop".equals(path)) {
            send(socket, 200, "text/plain; charset=utf-8", "Server stopping");
            new Handler(Looper.getMainLooper()).postDelayed(this::stopServer, 80L);
            return;
        }

        if ("/__salam__/api/restart".equals(path)) {
            send(socket, 200, "text/plain; charset=utf-8", "Server restarting");
            new Thread(() -> {
                stopServerInternal(false);
                try {
                    Thread.sleep(250L);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
                getSharedPreferences(PREFS, MODE_PRIVATE)
                        .edit().putBoolean("server_enabled", true).apply();
                startServer();
            }, "salam-browser-restart").start();
            return;
        }

        if ("/__salam__/api/clear".equals(path)
                || "/__salam__/api/clear-logs".equals(path)) {
            LOGS.clear();
            HISTORY.clear();
            send(socket, 200, "text/plain; charset=utf-8", "Logs and history cleared");
            return;
        }

        if ("/__salam__/api/action".equals(path)) {
            String body = readBody(input, headers.get("content-length"));
            Map<String, String> form = parseUrlEncoded(body);
            handleBrowserAction(socket, form);
            return;
        }

        if ("/__salam__/api/upload".equals(path)) {
            String contentType = headers.get("content-type");
            String lengthText = headers.get("content-length");
            long length = parseLong(lengthText, 0L);

            if (length <= 0L) {
                send(socket, 400, "text/plain; charset=utf-8",
                        "Upload body is empty");
                return;
            }

            if (length > 100L * 1024L * 1024L) {
                send(socket, 413, "text/plain; charset=utf-8",
                        "Upload too large (100 MB limit)");
                return;
            }

            if (contentType != null
                    && contentType.toLowerCase(Locale.US).startsWith("multipart/form-data")) {

                String boundary = multipartBoundary(contentType);
                if (boundary == null) {
                    send(socket, 400, "text/plain; charset=utf-8",
                            "Multipart boundary missing");
                    return;
                }

                byte[] body = readBytes(input, length);
                String result = saveMultipart(body, boundary, webRoot(this));
                send(socket, 200, "text/plain; charset=utf-8", result);
                return;
            }

            send(socket, 415, "text/plain; charset=utf-8",
                    "Use multipart/form-data for browser upload");
            return;
        }

        send(socket, 404, "text/plain; charset=utf-8", "Unknown API action");
    }

    private void handleBrowserAction(Socket socket, Map<String, String> d) throws Exception {
        String action = d.getOrDefault("action", "");

        if ("mkdir".equals(action)) {
            File dir = safe(webRoot(this), d.getOrDefault("path", "/"));
            if (dir == null) {
                send(socket, 403, "text/plain; charset=utf-8", "Forbidden");
                return;
            }
            if (!dir.exists() && dir.mkdirs()) {
                send(socket, 200, "text/plain; charset=utf-8", "Folder created");
            } else {
                send(socket, 400, "text/plain; charset=utf-8", "Folder creation failed");
            }
            return;
        }

        if ("delete".equals(action)) {
            File f = safe(webRoot(this), d.getOrDefault("path", "/"));
            if (f == null || f.equals(webRoot(this))) {
                send(socket, 403, "text/plain; charset=utf-8", "Forbidden");
                return;
            }
            deleteRecursive(f);
            send(socket, 200, "text/plain; charset=utf-8", "Deleted");
            return;
        }

        if ("rename".equals(action)) {
            File f = safe(webRoot(this), d.getOrDefault("path", "/"));
            String name = cleanName(d.getOrDefault("name", ""));
            if (f == null || f.equals(webRoot(this)) || name.isEmpty()) {
                send(socket, 400, "text/plain; charset=utf-8", "Invalid rename");
                return;
            }
            File n = new File(f.getParentFile(), name);
            if (n.exists()) {
                send(socket, 409, "text/plain; charset=utf-8", "Target already exists");
                return;
            }
            if (f.renameTo(n)) {
                send(socket, 200, "text/plain; charset=utf-8", "Renamed");
            } else {
                send(socket, 400, "text/plain; charset=utf-8", "Rename failed");
            }
            return;
        }

        if ("copy".equals(action) || "move".equals(action)) {
            File src = safe(webRoot(this), d.getOrDefault("path", "/"));
            File dstDir = safe(webRoot(this), d.getOrDefault("to", "/"));

            if (src == null || dstDir == null || !dstDir.isDirectory()
                    || src.equals(webRoot(this))) {
                send(socket, 400, "text/plain; charset=utf-8", "Invalid copy/move");
                return;
            }

            File dst = new File(dstDir, src.getName());
            copyRecursive(src, dst);

            if ("move".equals(action)) {
                deleteRecursive(src);
            }

            send(socket, 200, "text/plain; charset=utf-8",
                    "move".equals(action) ? "Moved" : "Copied");
            return;
        }

        if ("zip".equals(action)) {
            File src = safe(webRoot(this), d.getOrDefault("path", "/"));
            if (src == null || src.equals(webRoot(this))) {
                send(socket, 400, "text/plain; charset=utf-8", "Invalid ZIP source");
                return;
            }

            File out = new File(src.getParentFile(), src.getName() + ".zip");
            zip(src, out);

            send(socket, 200, "text/plain; charset=utf-8",
                    "ZIP created: " + out.getName());
            return;
        }

        if ("unzip".equals(action)) {
            File src = safe(webRoot(this), d.getOrDefault("path", "/"));
            File dest = safe(webRoot(this), d.getOrDefault("to", "/"));

            if (src == null || dest == null || !src.isFile()
                    || !src.getName().toLowerCase(Locale.US).endsWith(".zip")) {
                send(socket, 400, "text/plain; charset=utf-8", "ZIP required");
                return;
            }

            unzip(src, dest);
            send(socket, 200, "text/plain; charset=utf-8", "ZIP extracted");
            return;
        }

        if ("block".equals(action)) {
            String ip = d.getOrDefault("ip", "").trim();
            if (!isValidIp(ip)) {
                send(socket, 400, "text/plain; charset=utf-8", "Invalid IP");
                return;
            }
            BLOCK.add(ip);
            saveListsOnly(this);
            send(socket, 200, "text/plain; charset=utf-8", "IP blocked");
            return;
        }

        if ("unblock".equals(action)) {
            String ip = d.getOrDefault("ip", "").trim();
            BLOCK.remove(ip);
            saveListsOnly(this);
            send(socket, 200, "text/plain; charset=utf-8", "IP unblocked");
            return;
        }

        if ("clear-block".equals(action)) {
            BLOCK.clear();
            saveListsOnly(this);
            send(socket, 200, "text/plain; charset=utf-8", "Blocklist cleared");
            return;
        }

        if ("settings".equals(action)) {
            saveBrowserSettings(d);
            send(socket, 200, "text/plain; charset=utf-8", "Settings saved");
            return;
        }

        send(socket, 400, "text/plain; charset=utf-8", "Unknown action");
    }

    private void serveFile(Socket socket, String method, String path) throws Exception {
        File root = webRoot(this);

        if ("/".equals(path) || path.isEmpty()) {
            send(socket, 200, "text/html; charset=utf-8", homePage());
            return;
        }

        File file = safe(root, path);

        if (file == null) {
            send(socket, 403, "text/plain; charset=utf-8", "Forbidden");
            return;
        }

        if (!file.exists()) {
            send(socket, 404, "text/plain; charset=utf-8", "Not Found");
            return;
        }

        if (file.isDirectory()) {
            send(socket, 200, "text/html; charset=utf-8", directoryPage(file));
            return;
        }

        long length = file.length();
        String name = file.getName().replace("\"", "");
        String headers =
                "HTTP/1.1 200 OK\r\n" +
                "Content-Type: " + mime(file.getName()) + "\r\n" +
                "Content-Length: " + length + "\r\n" +
                "Content-Disposition: inline; filename=\"" + name + "\"\r\n" +
                "Cache-Control: no-store\r\n" +
                "Connection: close\r\n\r\n";

        OutputStream out = socket.getOutputStream();
        out.write(headers.getBytes(StandardCharsets.UTF_8));

        if ("HEAD".equals(method)) {
            return;
        }

        try (FileInputStream in = new FileInputStream(file)) {
            byte[] buffer = new byte[16384];
            int n;
            while ((n = in.read(buffer)) > 0) {
                out.write(buffer, 0, n);
                txBytes += n;
            }
        }
    }

    /* ============================================================
       AUTH / RATE LIMIT / SECURITY
       ============================================================ */

    private boolean authorized(Map<String, String> headers) {
        if (password == null || password.isEmpty()) {
            return true;
        }

        String auth = headers.get("authorization");
        if (auth == null || !auth.startsWith("Basic ")) {
            return false;
        }

        try {
            byte[] decoded = Base64.decode(auth.substring(6), Base64.DEFAULT);
            String value = new String(decoded, StandardCharsets.UTF_8);
            int colon = value.indexOf(':');

            if (colon < 0) {
                return false;
            }

            String suppliedPassword = value.substring(colon + 1);
            return password.equals(suppliedPassword);

        } catch (Exception e) {
            return false;
        }
    }

    private void authRequired(Socket socket) throws IOException {
        String h =
                "HTTP/1.1 401 Unauthorized\r\n" +
                "WWW-Authenticate: Basic realm=\"Salam Web Server\"\r\n" +
                "Content-Length: 0\r\n" +
                "Connection: close\r\n\r\n";

        socket.getOutputStream().write(h.getBytes(StandardCharsets.UTF_8));
    }

    private boolean allowRate(String ip) {
        if (rateLimit <= 0) {
            return true;
        }

        long now = System.currentTimeMillis();

        ArrayDeque<Long> queue = RATE.computeIfAbsent(
                ip, key -> new ArrayDeque<>()
        );

        synchronized (queue) {
            while (!queue.isEmpty() && now - queue.peekFirst() >= 60000L) {
                queue.pollFirst();
            }

            if (queue.size() >= rateLimit) {
                return false;
            }

            queue.addLast(now);
            return true;
        }
    }

    /* ============================================================
       HTTP RESPONSES
       ============================================================ */

    private void send(
            Socket socket,
            int code,
            String contentType,
            String body
    ) throws IOException {

        byte[] data = body.getBytes(StandardCharsets.UTF_8);

        String status;
        switch (code) {
            case 200: status = "OK"; break;
            case 201: status = "Created"; break;
            case 400: status = "Bad Request"; break;
            case 401: status = "Unauthorized"; break;
            case 403: status = "Forbidden"; break;
            case 404: status = "Not Found"; break;
            case 405: status = "Method Not Allowed"; break;
            case 409: status = "Conflict"; break;
            case 413: status = "Payload Too Large"; break;
            case 415: status = "Unsupported Media Type"; break;
            case 429: status = "Too Many Requests"; break;
            case 503: status = "Service Unavailable"; break;
            default: status = "Error"; break;
        }

        String header =
                "HTTP/1.1 " + code + " " + status + "\r\n" +
                "Content-Type: " + contentType + "\r\n" +
                "Content-Length: " + data.length + "\r\n" +
                "Cache-Control: no-store\r\n" +
                "Connection: close\r\n\r\n";

        OutputStream out = socket.getOutputStream();
        out.write(header.getBytes(StandardCharsets.UTF_8));
        out.write(data);
        out.flush();

        txBytes += data.length;
    }

    private void sendJson(Socket socket, String json) throws IOException {
        send(socket, 200, "application/json; charset=utf-8", json);
    }

    /* ============================================================
       BROWSER CONTROL PANEL
       ============================================================ */

    private String controlPage() {
        return "<!doctype html>" +
                "<html><head>" +
                "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">" +
                "<meta name=\"theme-color\" content=\"#06172b\">" +
                "<title>Salam Web Server V10</title>" +
                "<style>" + panelCss() + "</style>" +
                "</head><body>" +
                "<header>" +
                "<div class=\"brand\"><span class=\"bolt\">⚡</span>" +
                "<div><b>Salam Web Server</b><small>V10 • Control Center</small></div></div>" +
                "<span id=\"dot\" class=\"dot\">●</span>" +
                "</header>" +

                "<main>" +
                "<section class=\"hero\">" +
                "<div class=\"leds\">" +
                "<i></i><i></i><i></i><i></i><i></i><i></i><i></i>" +
                "</div>" +
                "<h1 id=\"status\">SERVER</h1>" +
                "<div id=\"url\" class=\"url\">" + escHtml(currentUrl(this)) + "</div>" +
                "<div class=\"actions\">" +
                "<button onclick=\"post('/__salam__/api/start')\">▶ START</button>" +
                "<button onclick=\"post('/__salam__/api/stop')\">■ STOP</button>" +
                "<button onclick=\"post('/__salam__/api/restart')\">↻ RESTART</button>" +
                "</div>" +
                "</section>" +

                "<section class=\"grid\" id=\"stats\"></section>" +

                "<section class=\"panel\">" +
                "<h2>📁 FILE MANAGER</h2>" +
                "<div class=\"actions\">" +
                "<button onclick=\"mkdir()\">📂 NEW FOLDER</button>" +
                "<button onclick=\"upload()\">⬆️ UPLOAD</button>" +
                "<button onclick=\"zipCurrent()\">📦 ZIP</button>" +
                "</div>" +
                "<div id=\"files\"></div>" +
                "</section>" +

                "<section class=\"panel\">" +
                "<h2>🔐 SECURITY</h2>" +
                "<p>Blocked IPs: <b id=\"blocked\">0</b></p>" +
                "<div class=\"actions\">" +
                "<button onclick=\"blockIp()\">🚫 BLOCK IP</button>" +
                "<button onclick=\"unblockIp()\">🔓 UNBLOCK</button>" +
                "<button onclick=\"clearBlock()\">🧹 CLEAR BLOCKLIST</button>" +
                "</div>" +
                "</section>" +

                "<section class=\"panel\">" +
                "<h2>📋 LIVE REQUEST LOG</h2>" +
                "<pre id=\"logs\">Loading…</pre>" +
                "</section>" +

                "<section class=\"panel\">" +
                "<h2>🕘 ACCESS HISTORY</h2>" +
                "<pre id=\"history\">Loading…</pre>" +
                "</section>" +

                "<section class=\"panel\">" +
                "<h2>⚙️ SERVER SETTINGS</h2>" +
                "<div class=\"form\">" +
                "<label>Requests / minute<input id=\"rate\" type=\"number\" min=\"0\" value=\"" + rateLimit + "\"></label>" +
                "<label>Maximum clients<input id=\"max\" type=\"number\" min=\"1\" value=\"" + maxClients + "\"></label>" +
                "<label>Custom hostname<input id=\"host\" value=\"" + escHtml(customHost) + "\" placeholder=\"salam.local\"></label>" +
                "<label>Web password<input id=\"pass\" type=\"password\" placeholder=\"Leave empty to disable\"></label>" +
                "<button onclick=\"saveSettings()\">💾 SAVE SETTINGS</button>" +
                "</div>" +
                "</section>" +
                "</main>" +

                "<script>" + panelJs() + "</script>" +
                "</body></html>";
    }

    private String homePage() {
        return "<!doctype html><html><head>" +
                "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">" +
                "<meta name=\"theme-color\" content=\"#06172b\">" +
                "<title>Salam Web Server</title>" +
                "<style>" + panelCss() + "</style></head><body>" +
                "<header><div class=\"brand\"><span class=\"bolt\">⚡</span>" +
                "<div><b>Salam Web Server</b><small>Local Web Server V10</small></div></div></header>" +
                "<main><section class=\"hero\">" +
                "<div class=\"leds\"><i></i><i></i><i></i><i></i><i></i><i></i><i></i></div>" +
                "<h1>SERVER ONLINE</h1>" +
                "<div class=\"url\">" + escHtml(currentUrl(this)) + "</div>" +
                "<div class=\"actions\"><a class=\"btn\" href=\"/__salam__/\">🖥️ CONTROL PANEL</a></div>" +
                "</section></main></body></html>";
    }

    private String directoryPage(File directory) {
        StringBuilder html = new StringBuilder();
        html.append("<!doctype html><html><head>")
                .append("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">")
                .append("<title>Files • Salam Web Server</title>")
                .append("<style>").append(panelCss()).append("</style>")
                .append("</head><body>")
                .append("<header><div class=\"brand\"><span class=\"bolt\">📁</span>")
                .append("<div><b>Web Files</b><small>")
                .append(escHtml(directory.getName()))
                .append("</small></div></div></header><main>");

        File[] files = directory.listFiles();
        if (files == null) {
            html.append("<section class=\"panel\"><p>Unable to read folder.</p></section>");
        } else {
            Arrays.sort(files, (a, b) -> {
                if (a.isDirectory() != b.isDirectory()) {
                    return a.isDirectory() ? -1 : 1;
                }
                return a.getName().compareToIgnoreCase(b.getName());
            });

            for (File f : files) {
                String rel = relativeWebPath(webRoot(this), f);
                html.append("<section class=\"file\">")
                        .append(f.isDirectory() ? "📁 " : "📄 ")
                        .append("<a href=\"")
                        .append(escHtml(rel))
                        .append("\">")
                        .append(escHtml(f.getName()))
                        .append("</a>")
                        .append("<small>")
                        .append(f.isDirectory() ? "Folder" : sizeText(f.length()))
                        .append("</small></section>");
            }
        }

        html.append("</main></body></html>");
        return html.toString();
    }

    private String panelCss() {
        return
                "*{box-sizing:border-box}" +
                "body{margin:0;background:#020a15;color:#f7fbff;font-family:Arial,sans-serif}" +
                "header{position:sticky;top:0;z-index:10;padding:15px;background:#06172b;border-bottom:1px solid #12415f;display:flex;align-items:center;justify-content:space-between}" +
                ".brand{display:flex;gap:12px;align-items:center}.brand b{font-size:20px}.brand small{display:block;color:#8eabc5;margin-top:3px}" +
                ".bolt{font-size:31px}.dot{color:#21f59b;font-size:20px}" +
                "main{max-width:900px;margin:auto;padding:12px}" +
                ".hero{background:linear-gradient(145deg,#071c32,#05111f);border:1px solid #11486b;border-radius:26px;padding:22px;margin-bottom:14px;box-shadow:0 0 30px #001c2e}" +
                ".leds{display:flex;justify-content:center;gap:13px;margin:4px 0 22px}.leds i{width:18px;height:18px;border-radius:50%;display:block;background:#263849;box-shadow:0 0 5px #1b2d3d}.leds i:nth-child(1){background:#25ef9c;box-shadow:0 0 18px #25ef9c}.leds i:nth-child(2){background:#21d9ff;box-shadow:0 0 18px #21d9ff}.leds i:nth-child(3){background:#b44cff;box-shadow:0 0 18px #b44cff}.leds i:nth-child(4){background:#ffd52f;box-shadow:0 0 18px #ffd52f}.leds i:nth-child(5){background:#fff;box-shadow:0 0 18px #fff}.leds i:nth-child(6){background:#ff8a24;box-shadow:0 0 18px #ff8a24}.leds i:nth-child(7){background:#ff3658;box-shadow:0 0 18px #ff3658}" +
                "h1{text-align:center;font-size:24px;color:#21f59b;letter-spacing:.5px}.url{background:#020c19;border-radius:18px;padding:16px;text-align:center;color:#21d9ff;word-break:break-all;margin:15px 0}" +
                ".actions{display:flex;flex-wrap:wrap;gap:8px}.btn,button{border:0;border-radius:15px;padding:13px 15px;background:linear-gradient(100deg,#13d8ff,#4b4dff);color:#fff;font-weight:800;text-decoration:none;cursor:pointer}" +
                ".grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:10px;margin-bottom:14px}.stat{background:#071c32;border:1px solid #11486b;border-radius:20px;padding:15px}.stat b{font-size:22px;color:#21d9ff;display:block;margin-top:5px}" +
                ".panel{background:#071c32;border:1px solid #11486b;border-radius:22px;padding:16px;margin-bottom:14px}.panel h2{font-size:17px;margin:0 0 13px;color:#21d9ff}" +
                ".file{display:flex;align-items:center;gap:9px;background:#06172b;border:1px solid #123650;border-radius:15px;padding:12px;margin:7px 0}.file a{color:#fff;text-decoration:none;font-weight:700;flex:1}.file small{color:#89a7c0}" +
                "pre{max-height:340px;overflow:auto;white-space:pre-wrap;word-break:break-word;color:#a8c4db;background:#020c19;border-radius:14px;padding:12px}" +
                ".form label{display:block;color:#8eabc5;margin:10px 0}.form input{display:block;width:100%;margin-top:6px;padding:12px;border-radius:12px;border:1px solid #1a4b69;background:#020c19;color:#fff}" +
                "@media(min-width:700px){.grid{grid-template-columns:repeat(4,minmax(0,1fr))}}";
    }

    private String panelJs() {
        return
                "async function get(u){let r=await fetch(u);return await r.json()}" +
                "async function post(u,b=''){await fetch(u,{method:'POST',headers:{'Content-Type':'application/x-www-form-urlencoded'},body:b});setTimeout(load,150)}" +
                "async function load(){" +
                "try{" +
                "let x=await get('/__salam__/api/info');" +
                "document.getElementById('status').textContent=x.running?'SERVER ONLINE':'SERVER OFFLINE';" +
                "document.getElementById('status').style.color=x.running?'#21f59b':'#ff3658';" +
                "document.getElementById('url').textContent=x.url;" +
                "document.getElementById('stats').innerHTML=" +
                "'<div class=stat>🧠 CPU<b>'+x.cpu+'</b></div>'+" +
                "'<div class=stat>💾 RAM<b>'+x.ram+'</b></div>'+" +
                "'<div class=stat>💽 Storage<b>'+x.storage+'</b></div>'+" +
                "'<div class=stat>👥 Clients<b>'+x.clients+'</b></div>'+" +
                "'<div class=stat>📊 Requests<b>'+x.requests+'</b></div>'+" +
                "'<div class=stat>📡 Traffic<b>'+x.traffic+'</b></div>'+" +
                "'<div class=stat>⏱️ Uptime<b>'+x.uptime+'</b></div>'+" +
                "'<div class=stat>🌐 Interface<b>'+x.interface+'</b></div>';" +
                "let l=await get('/__salam__/api/logs');document.getElementById('logs').textContent=l.logs.join('\\n');" +
                "let h=await get('/__salam__/api/history');document.getElementById('history').textContent=h.history.join('\\n');" +
                "document.getElementById('blocked').textContent=x.blocked;" +
                "}catch(e){console.log(e)}" +
                "}" +
                "function mkdir(){let n=prompt('Folder name');if(n)post('/__salam__/api/action','action=mkdir&path='+encodeURIComponent('/'+n))}" +
                "function blockIp(){let n=prompt('IP to block');if(n)post('/__salam__/api/action','action=block&ip='+encodeURIComponent(n))}" +
                "function unblockIp(){let n=prompt('IP to unblock');if(n)post('/__salam__/api/action','action=unblock&ip='+encodeURIComponent(n))}" +
                "function clearBlock(){post('/__salam__/api/action','action=clear-block')}" +
                "function zipCurrent(){let n=prompt('Path to ZIP, e.g. /folder');if(n)post('/__salam__/api/action','action=zip&path='+encodeURIComponent(n))}" +
                "function upload(){alert('Use the Android app Upload button for selecting a local file. Browser upload endpoint is enabled for multipart/form-data clients.')}" +
                "function saveSettings(){let q='action=settings&rate='+encodeURIComponent(document.getElementById('rate').value)+'&max='+encodeURIComponent(document.getElementById('max').value)+'&host='+encodeURIComponent(document.getElementById('host').value)+'&password='+encodeURIComponent(document.getElementById('pass').value);post('/__salam__/api/action',q)}" +
                "load();setInterval(load,1000);";
    }

    /* ============================================================
       JSON / INFORMATION
       ============================================================ */

    private String infoJson() {
        return "{" +
                "\"running\":" + running + "," +
                "\"requests\":" + requests.get() + "," +
                "\"clients\":" + clients.size() + "," +
                "\"blocked\":" + BLOCK.size() + "," +
                "\"url\":\"" + esc(currentUrl(this)) + "\"," +
                "\"cpu\":\"" + esc(cpuText()) + "\"," +
                "\"ram\":\"" + esc(memoryText(this)) + "\"," +
                "\"storage\":\"" + esc(storageText(this)) + "\"," +
                "\"traffic\":\"" + esc(trafficText()) + "\"," +
                "\"uptime\":\"" + esc(uptime()) + "\"," +
                "\"interface\":\"" + esc(interfaceName(this)) + "\"" +
                "}";
    }

    private String settingsJson() {
        return "{" +
                "\"rateLimit\":" + rateLimit + "," +
                "\"maxClients\":" + maxClients + "," +
                "\"customHost\":\"" + esc(customHost) + "\"," +
                "\"passwordEnabled\":" + (password != null && !password.isEmpty()) +
                "}";
    }

    private String listJson(String key, List<String> list) {
        StringBuilder b = new StringBuilder();
        b.append("{\"").append(key).append("\":[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) b.append(',');
            b.append('"').append(esc(list.get(i))).append('"');
        }
        b.append("]}");
        return b.toString();
    }

    private String filesJson(File root, String path) {
        StringBuilder b = new StringBuilder("{\"path\":\"")
                .append(esc(path))
                .append("\",\"files\":[");

        File dir = safe(root, path);
        if (dir != null && dir.isDirectory()) {
            File[] fs = dir.listFiles();
            if (fs != null) {
                Arrays.sort(fs, Comparator.comparing(File::getName,
                        String.CASE_INSENSITIVE_ORDER));

                for (int i = 0; i < fs.length; i++) {
                    if (i > 0) b.append(',');
                    File f = fs[i];
                    b.append("{\"name\":\"")
                            .append(esc(f.getName()))
                            .append("\",\"directory\":")
                            .append(f.isDirectory())
                            .append(",\"size\":")
                            .append(f.length())
                            .append("}");
                }
            }
        }

        b.append("]}");
        return b.toString();
    }

    /* ============================================================
       SETTINGS
       ============================================================ */

    public static void saveSecurity(
            Context context,
            String pass,
            String allow,
            String block,
            String rate
    ) {
        synchronized (SETTINGS_LOCK) {
            password = pass == null ? "" : pass.trim();

            ALLOW.clear();
            BLOCK.clear();

            addListValues(ALLOW, allow);
            addListValues(BLOCK, block);

            try {
                int parsed = Integer.parseInt(rate == null ? "60" : rate.trim());
                rateLimit = Math.max(0, Math.min(parsed, 100000));
            } catch (Exception e) {
                rateLimit = 60;
            }

            SharedPreferences.Editor editor =
                    context.getSharedPreferences(PREFS, MODE_PRIVATE).edit();

            editor.putString("password", password);
            editor.putString("allow", joinSet(ALLOW));
            editor.putString("block", joinSet(BLOCK));
            editor.putInt("rate", rateLimit);
            editor.apply();
        }
    }

    public static void setCustomHost(Context context, String host) {
        customHost = cleanHost(host);
        context.getSharedPreferences(PREFS, MODE_PRIVATE)
                .edit().putString("host", customHost).apply();
    }

    public static void unblockIp(String ip) {
        if (ip == null) return;

        BLOCK.remove(ip.trim());

        // The Android Activity does not pass Context here, so persistence is
        // refreshed on the next save/load. Runtime state is immediately fixed.
    }

    public static void unblockIp(Context context, String ip) {
        if (ip == null) return;
        BLOCK.remove(ip.trim());
        context.getSharedPreferences(PREFS, MODE_PRIVATE)
                .edit().putString("block", joinSet(BLOCK)).apply();
    }

    private static void loadSettings(Context context) {
        synchronized (SETTINGS_LOCK) {
            SharedPreferences p = context.getSharedPreferences(PREFS, MODE_PRIVATE);

            password = p.getString("password", "");
            customHost = p.getString("host", "");
            rateLimit = Math.max(0, p.getInt("rate", 60));
            maxClients = Math.max(1, p.getInt("maxClients", 32));

            ALLOW.clear();
            BLOCK.clear();

            addListValues(ALLOW, p.getString("allow", ""));
            addListValues(BLOCK, p.getString("block", ""));
        }
    }

    private void saveListsOnly(Context context) {
        context.getSharedPreferences(PREFS, MODE_PRIVATE)
                .edit()
                .putString("allow", joinSet(ALLOW))
                .putString("block", joinSet(BLOCK))
                .apply();
    }

    private void saveBrowserSettings(Map<String, String> d) {
        try {
            int r = Integer.parseInt(d.getOrDefault("rate", "60"));
            rateLimit = Math.max(0, Math.min(r, 100000));
        } catch (Exception ignored) {
        }

        try {
            int m = Integer.parseInt(d.getOrDefault("max", "32"));
            maxClients = Math.max(1, Math.min(m, 512));
        } catch (Exception ignored) {
        }

        customHost = cleanHost(d.getOrDefault("host", ""));
        String newPassword = d.getOrDefault("password", "");

        if (!newPassword.isEmpty()) {
            password = newPassword;
        }

        getSharedPreferences(PREFS, MODE_PRIVATE)
                .edit()
                .putInt("rate", rateLimit)
                .putInt("maxClients", maxClients)
                .putString("host", customHost)
                .putString("password", password)
                .apply();

        updateNotification();
    }

    /* ============================================================
       NETWORK INFORMATION
       ============================================================ */

    public static String localIp(Context context) {
        String active = activeTransportIp(context);
        if (!"—".equals(active)) {
            return active;
        }

        try {
            Enumeration<NetworkInterface> interfaces =
                    NetworkInterface.getNetworkInterfaces();

            while (interfaces.hasMoreElements()) {
                NetworkInterface ni = interfaces.nextElement();

                if (!ni.isUp() || ni.isLoopback()) {
                    continue;
                }

                Enumeration<InetAddress> addresses = ni.getInetAddresses();

                while (addresses.hasMoreElements()) {
                    InetAddress address = addresses.nextElement();

                    if (address instanceof Inet4Address
                            && !address.isLoopbackAddress()) {
                        return address.getHostAddress();
                    }
                }
            }

        } catch (Exception ignored) {
        }

        return "127.0.0.1";
    }

    private static String activeTransportIp(Context context) {
        try {
            ConnectivityManager cm =
                    (ConnectivityManager) context.getSystemService(
                            Context.CONNECTIVITY_SERVICE
                    );

            Network active = cm.getActiveNetwork();
            if (active == null) return "—";

            LinkProperties lp = cm.getLinkProperties(active);
            if (lp == null) return "—";

            for (LinkAddress address : lp.getLinkAddresses()) {
                if (address.getAddress() instanceof Inet4Address
                        && !address.getAddress().isLoopbackAddress()) {
                    return address.getAddress().getHostAddress();
                }
            }
        } catch (Exception ignored) {
        }

        return "—";
    }

    public static String wifiIp(Context context) {
        return transportIp(context, NetworkCapabilities.TRANSPORT_WIFI);
    }

    public static String cellularIp(Context context) {
        return transportIp(context, NetworkCapabilities.TRANSPORT_CELLULAR);
    }

    private static String transportIp(Context context, int transport) {
        try {
            ConnectivityManager cm =
                    (ConnectivityManager) context.getSystemService(
                            Context.CONNECTIVITY_SERVICE
                    );

            for (Network network : cm.getAllNetworks()) {
                NetworkCapabilities nc = cm.getNetworkCapabilities(network);

                if (nc == null || !nc.hasTransport(transport)) {
                    continue;
                }

                LinkProperties lp = cm.getLinkProperties(network);
                if (lp == null) continue;

                for (LinkAddress address : lp.getLinkAddresses()) {
                    if (address.getAddress() instanceof Inet4Address
                            && !address.getAddress().isLoopbackAddress()) {
                        return address.getAddress().getHostAddress();
                    }
                }
            }
        } catch (Exception ignored) {
        }

        return "—";
    }

    public static String interfaceName(Context context) {
        try {
            ConnectivityManager cm =
                    (ConnectivityManager) context.getSystemService(
                            Context.CONNECTIVITY_SERVICE
                    );

            Network active = cm.getActiveNetwork();
            LinkProperties lp = cm.getLinkProperties(active);

            if (lp != null && lp.getInterfaceName() != null) {
                return lp.getInterfaceName();
            }
        } catch (Exception ignored) {
        }

        return "none";
    }

    public static String gateway(Context context) {
        try {
            ConnectivityManager cm =
                    (ConnectivityManager) context.getSystemService(
                            Context.CONNECTIVITY_SERVICE
                    );

            LinkProperties lp = cm.getLinkProperties(cm.getActiveNetwork());

            if (lp != null) {
                for (android.net.RouteInfo route : lp.getRoutes()) {
                    if (route.getGateway() != null) {
                        return route.getGateway().getHostAddress();
                    }
                }
            }
        } catch (Exception ignored) {
        }

        return "—";
    }

    public static String dns(Context context) {
        try {
            ConnectivityManager cm =
                    (ConnectivityManager) context.getSystemService(
                            Context.CONNECTIVITY_SERVICE
                    );

            LinkProperties lp = cm.getLinkProperties(cm.getActiveNetwork());

            if (lp != null && !lp.getDnsServers().isEmpty()) {
                return lp.getDnsServers().get(0).getHostAddress();
            }
        } catch (Exception ignored) {
        }

        return "—";
    }

    public static String networkInfo(Context context) {
        String iface = interfaceName(context);
        String wifi = wifiIp(context);
        String mobile = cellularIp(context);

        return "Interface: " + iface
                + " • Wi-Fi IP: " + wifi
                + " • Mobile IP: " + mobile;
    }

    public static String currentUrl(Context context) {
        String host = customHost == null ? "" : customHost.trim();

        if (host.isEmpty()) {
            host = localIp(context);
        }

        return "http://" + host + ":" + PORT;
    }

    /* ============================================================
       MONITORING
       ============================================================ */

    public static String memoryText(Context context) {
        try {
            ActivityManager am =
                    (ActivityManager) context.getSystemService(
                            Context.ACTIVITY_SERVICE
                    );

            ActivityManager.MemoryInfo info = new ActivityManager.MemoryInfo();
            am.getMemoryInfo(info);

            long used = Math.max(0L, info.totalMem - info.availMem);

            return (used / 1048576L)
                    + " / "
                    + (info.totalMem / 1048576L)
                    + " MB";
        } catch (Exception e) {
            return "n/a";
        }
    }

    public static String storageText(Context context) {
        try {
            StatFs fs = new StatFs(webRoot(context).getAbsolutePath());

            long total = fs.getTotalBytes();
            long available = fs.getAvailableBytes();
            long used = Math.max(0L, total - available);

            return (used / 1048576L)
                    + " / "
                    + (total / 1048576L)
                    + " MB";
        } catch (Exception e) {
            return "n/a";
        }
    }

    public static synchronized String cpuText() {
        try {
            BufferedReader reader = new BufferedReader(
                    new FileReader("/proc/stat")
            );

            String line = reader.readLine();
            reader.close();

            if (line == null || !line.startsWith("cpu")) {
                return "n/a";
            }

            String[] p = line.trim().split("\\s+");
            if (p.length < 5) {
                return "n/a";
            }

            long user = parseLong(p[1], 0L);
            long nice = parseLong(p[2], 0L);
            long system = parseLong(p[3], 0L);
            long idle = parseLong(p[4], 0L);
            long iowait = p.length > 5 ? parseLong(p[5], 0L) : 0L;
            long irq = p.length > 6 ? parseLong(p[6], 0L) : 0L;
            long softIrq = p.length > 7 ? parseLong(p[7], 0L) : 0L;
            long steal = p.length > 8 ? parseLong(p[8], 0L) : 0L;

            long idleAll = idle + iowait;
            long total = user + nice + system + idleAll
                    + irq + softIrq + steal;

            if (previousTotalCpu < 0L) {
                previousTotalCpu = total;
                previousIdleCpu = idleAll;
                return "sampling…";
            }

            long totalDelta = total - previousTotalCpu;
            long idleDelta = idleAll - previousIdleCpu;

            previousTotalCpu = total;
            previousIdleCpu = idleAll;

            if (totalDelta <= 0L) {
                return "0%";
            }

            long busy = Math.max(0L, totalDelta - idleDelta);
            int percent = (int) Math.round(
                    (busy * 100.0) / totalDelta
            );

            percent = Math.max(0, Math.min(100, percent));
            return percent + "%";

        } catch (Exception e) {
            return "n/a";
        }
    }

    public static String trafficText() {
        long total = Math.max(0L, rxBytes + txBytes);

        if (total < 1024L) {
            return total + " B";
        }

        if (total < 1024L * 1024L) {
            return (total / 1024L) + " KB";
        }

        if (total < 1024L * 1024L * 1024L) {
            return (total / (1024L * 1024L)) + " MB";
        }

        return String.format(
                Locale.US,
                "%.2f GB",
                total / (1024.0 * 1024.0 * 1024.0)
        );
    }

    public static String uptime() {
        if (!running || startedAt <= 0L) {
            return "00:00:00";
        }

        long seconds =
                Math.max(0L, (System.currentTimeMillis() - startedAt) / 1000L);

        long hours = seconds / 3600L;
        long minutes = (seconds / 60L) % 60L;
        long secs = seconds % 60L;

        return String.format(
                Locale.US,
                "%02d:%02d:%02d",
                hours,
                minutes,
                secs
        );
    }

    public static int getClientCount() {
        return clients.size();
    }

    public static int getRequestCount() {
        return requests.get();
    }

    /* ============================================================
       FILE MANAGER HELPERS
       ============================================================ */

    public static File webRoot(Context context) {
        File root = new File(context.getFilesDir(), "www");

        if (!root.exists()) {
            root.mkdirs();
        }

        return root;
    }

    private static File safe(File base, String path) {
        try {
            if (base == null) return null;

            String clean = path == null ? "/" : path;

            if (clean.startsWith("http://")
                    || clean.startsWith("https://")) {
                return null;
            }

            while (clean.startsWith("/")) {
                clean = clean.substring(1);
            }

            File result = new File(base, clean);

            String rootPath = base.getCanonicalPath();
            String resultPath = result.getCanonicalPath();

            if (resultPath.equals(rootPath)
                    || resultPath.startsWith(rootPath + File.separator)) {
                return result;
            }

        } catch (Exception ignored) {
        }

        return null;
    }

    public static String readText(File file) throws IOException {
        StringBuilder builder = new StringBuilder();

        try (BufferedReader reader = new BufferedReader(
                new FileReader(file)
        )) {
            String line;

            while ((line = reader.readLine()) != null) {
                builder.append(line).append('\n');
            }
        }

        return builder.toString();
    }

    public static void writeText(File file, String text) throws IOException {
        File parent = file.getParentFile();

        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }

        try (FileWriter writer = new FileWriter(file)) {
            writer.write(text == null ? "" : text);
        }
    }

    public static void deleteRecursive(File file) {
        if (file == null || !file.exists()) {
            return;
        }

        if (file.isDirectory()) {
            File[] children = file.listFiles();

            if (children != null) {
                for (File child : children) {
                    deleteRecursive(child);
                }
            }
        }

        file.delete();
    }

    public static void copyRecursive(File source, File destination)
            throws IOException {

        if (source == null || destination == null) {
            throw new IOException("Invalid source or destination");
        }

        if (source.equals(destination)) {
            throw new IOException("Source and destination are the same");
        }

        if (source.isDirectory()) {
            if (!destination.exists() && !destination.mkdirs()) {
                throw new IOException("Cannot create destination folder");
            }

            File[] children = source.listFiles();

            if (children != null) {
                for (File child : children) {
                    copyRecursive(
                            child,
                            new File(destination, child.getName())
                    );
                }
            }

            return;
        }

        File parent = destination.getParentFile();

        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }

        try (
                FileInputStream in = new FileInputStream(source);
                FileOutputStream out = new FileOutputStream(destination)
        ) {
            byte[] buffer = new byte[16384];
            int n;

            while ((n = in.read(buffer)) > 0) {
                out.write(buffer, 0, n);
            }
        }
    }

    /* ============================================================
       ZIP — ONE AND ONLY ONE IMPLEMENTATION
       ============================================================ */

    public static void zip(File source, File output)
            throws IOException {

        if (source == null || output == null) {
            throw new IOException("Invalid ZIP source");
        }

        if (source.equals(output)) {
            throw new IOException("ZIP output cannot equal source");
        }

        File parent = output.getParentFile();

        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }

        File base = source.getParentFile();

        try (ZipOutputStream zip = new ZipOutputStream(
                new FileOutputStream(output)
        )) {
            zipRec(source, base, zip);
        }
    }

    private static void zipRec(
            File file,
            File base,
            ZipOutputStream zip
    ) throws IOException {

        String name;

        try {
            name = base.toPath()
                    .relativize(file.toPath())
                    .toString()
                    .replace(File.separatorChar, '/');
        } catch (Exception e) {
            name = file.getName();
        }

        if (file.isDirectory()) {
            if (!name.endsWith("/")) {
                name += "/";
            }

            zip.putNextEntry(new ZipEntry(name));
            zip.closeEntry();

            File[] children = file.listFiles();

            if (children != null) {
                Arrays.sort(
                        children,
                        Comparator.comparing(
                                File::getName,
                                String.CASE_INSENSITIVE_ORDER
                        )
                );

                for (File child : children) {
                    zipRec(child, base, zip);
                }
            }

            return;
        }

        zip.putNextEntry(new ZipEntry(name));

        try (FileInputStream in = new FileInputStream(file)) {
            byte[] buffer = new byte[16384];
            int n;

            while ((n = in.read(buffer)) > 0) {
                zip.write(buffer, 0, n);
            }
        }

        zip.closeEntry();
    }

    public static void unzip(File zipFile, File destination)
            throws IOException {

        if (zipFile == null
                || destination == null
                || !zipFile.isFile()) {
            throw new IOException("Invalid ZIP file");
        }

        if (!destination.exists() && !destination.mkdirs()) {
            throw new IOException("Cannot create destination");
        }

        String root = destination.getCanonicalPath();

        try (
                ZipInputStream input = new ZipInputStream(
                        new FileInputStream(zipFile)
                )
        ) {
            ZipEntry entry;

            byte[] buffer = new byte[16384];

            while ((entry = input.getNextEntry()) != null) {
                File output = new File(destination, entry.getName());

                String canonical = output.getCanonicalPath();

                // ZIP-SLIP protection.
                if (!canonical.equals(root)
                        && !canonical.startsWith(root + File.separator)) {
                    throw new SecurityException(
                            "Unsafe ZIP entry: " + entry.getName()
                    );
                }

                if (entry.isDirectory()) {
                    output.mkdirs();
                    input.closeEntry();
                    continue;
                }

                File parent = output.getParentFile();

                if (parent != null && !parent.exists()) {
                    parent.mkdirs();
                }

                try (FileOutputStream out =
                             new FileOutputStream(output)) {

                    int n;

                    while ((n = input.read(buffer)) > 0) {
                        out.write(buffer, 0, n);
                    }
                }

                input.closeEntry();
            }
        }
    }

    /* ============================================================
       MULTIPART UPLOAD
       ============================================================ */

    private String saveMultipart(
            byte[] body,
            String boundary,
            File root
    ) throws IOException {

        String marker = "--" + boundary;
        String text = new String(body, StandardCharsets.ISO_8859_1);
        String[] parts = text.split(java.util.regex.Pattern.quote(marker));

        int saved = 0;

        for (String part : parts) {
            if (!part.contains("Content-Disposition")) {
                continue;
            }

            int headerEnd = part.indexOf("\r\n\r\n");

            if (headerEnd < 0) {
                continue;
            }

            String headerText = part.substring(0, headerEnd);
            String filename = multipartFilename(headerText);

            if (filename == null || filename.isEmpty()) {
                continue;
            }

            filename = cleanName(filename);

            if (filename.isEmpty()) {
                continue;
            }

            String content = part.substring(headerEnd + 4);

            while (content.endsWith("\r\n")) {
                content = content.substring(0, content.length() - 2);
            }

            while (content.endsWith("--")) {
                content = content.substring(0, content.length() - 2);
            }

            byte[] data = content.getBytes(
                    StandardCharsets.ISO_8859_1
            );

            File output = new File(root, filename);

            try (FileOutputStream out =
                         new FileOutputStream(output)) {
                out.write(data);
            }

            saved++;
        }

        return saved == 0
                ? "No file found in multipart request"
                : "Uploaded " + saved + " file(s)";
    }

    private String multipartFilename(String headers) {
        String lower = headers.toLowerCase(Locale.US);
        int index = lower.indexOf("filename=");

        if (index < 0) {
            return null;
        }

        String value = headers.substring(index + 9).trim();

        if (value.startsWith("\"")) {
            int end = value.indexOf('"', 1);

            if (end > 0) {
                return value.substring(1, end);
            }
        }

        int semicolon = value.indexOf(';');

        return semicolon >= 0
                ? value.substring(0, semicolon).trim()
                : value.trim();
    }

    private String multipartBoundary(String contentType) {
        String[] tokens = contentType.split(";");

        for (String token : tokens) {
            token = token.trim();

            if (token.toLowerCase(Locale.US).startsWith("boundary=")) {
                String value = token.substring(9).trim();

                if (value.startsWith("\"")
                        && value.endsWith("\"")
                        && value.length() >= 2) {
                    value = value.substring(1, value.length() - 1);
                }

                return value;
            }
        }

        return null;
    }

    /* ============================================================
       NOTIFICATION
       ============================================================ */

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Salam Web Server",
                    NotificationManager.IMPORTANCE_LOW
            );

            channel.setDescription(
                    "Persistent Salam Web Server status"
            );
            channel.setShowBadge(true);

            NotificationManager manager =
                    getSystemService(NotificationManager.class);

            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private Notification buildNotification() {
        Notification.Builder builder;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(this, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(this);
        }

        String title = running
                ? "🟢 Salam Web Server • ONLINE"
                : "🔴 Salam Web Server • OFFLINE";

        String text = running
                ? currentUrl(this) + " • " + requests.get() + " requests"
                : "Server stopped";

        return builder
                // Uses a platform-safe icon so the notification cannot fail
                // because a custom drawable is missing.
                .setSmallIcon(android.R.drawable.stat_sys_upload_done)
                .setContentTitle(title)
                .setContentText(text)
                .setOngoing(running)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .build();
    }

    private void updateNotification() {
        try {
            NotificationManager manager =
                    (NotificationManager) getSystemService(
                            Context.NOTIFICATION_SERVICE
                    );

            if (manager != null) {
                manager.notify(
                        NOTIFICATION_ID,
                        buildNotification()
                );
            }
        } catch (Exception ignored) {
        }
    }

    /* ============================================================
       UTILITY
       ============================================================ */

    private static String now() {
        return new SimpleDateFormat(
                "yyyy-MM-dd HH:mm:ss",
                Locale.getDefault()
        ).format(new Date());
    }

    private static void addLog(String message) {
        LOGS.add(
                new SimpleDateFormat(
                        "HH:mm:ss",
                        Locale.getDefault()
                ).format(new Date())
                        + "  "
                        + message
        );

        while (LOGS.size() > 1000) {
            LOGS.remove(0);
        }
    }

    private static void trimHistory() {
        while (HISTORY.size() > 1000) {
            HISTORY.remove(0);
        }
    }

    private static void addListValues(Set<String> target, String value) {
        if (value == null || value.trim().isEmpty()) {
            return;
        }

        String[] values = value.split("[,\\n;]+");

        for (String item : values) {
            String clean = item.trim();

            if (!clean.isEmpty()) {
                target.add(clean);
            }
        }
    }

    private static String joinSet(Set<String> values) {
        StringBuilder b = new StringBuilder();

        for (String value : values) {
            if (b.length() > 0) {
                b.append(',');
            }
            b.append(value);
        }

        return b.toString();
    }

    private static String esc(String value) {
        if (value == null) {
            return "";
        }

        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "")
                .replace("\n", "\\n");
    }

    private static String escHtml(String value) {
        if (value == null) {
            return "";
        }

        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private static String safeMessage(Exception e) {
        String message = e == null ? null : e.getMessage();
        return message == null || message.isEmpty()
                ? e.getClass().getSimpleName()
                : message;
    }

    private static void closeQuietly(Socket socket) {
        if (socket == null) return;

        try {
            socket.close();
        } catch (Exception ignored) {
        }
    }

    private static String mime(String name) {
        String n = name.toLowerCase(Locale.US);

        if (n.endsWith(".html") || n.endsWith(".htm")) return "text/html";
        if (n.endsWith(".css")) return "text/css";
        if (n.endsWith(".js")) return "application/javascript";
        if (n.endsWith(".json")) return "application/json";
        if (n.endsWith(".xml")) return "application/xml";
        if (n.endsWith(".txt")) return "text/plain";
        if (n.endsWith(".csv")) return "text/csv";
        if (n.endsWith(".md")) return "text/markdown";

        if (n.endsWith(".png")) return "image/png";
        if (n.endsWith(".jpg") || n.endsWith(".jpeg")) return "image/jpeg";
        if (n.endsWith(".gif")) return "image/gif";
        if (n.endsWith(".webp")) return "image/webp";
        if (n.endsWith(".svg")) return "image/svg+xml";
        if (n.endsWith(".ico")) return "image/x-icon";

        if (n.endsWith(".pdf")) return "application/pdf";
        if (n.endsWith(".zip")) return "application/zip";

        if (n.endsWith(".mp3")) return "audio/mpeg";
        if (n.endsWith(".mp4")) return "video/mp4";

        return "application/octet-stream";
    }

    private static String sizeText(long bytes) {
        if (bytes < 1024L) {
            return bytes + " B";
        }

        if (bytes < 1024L * 1024L) {
            return (bytes / 1024L) + " KB";
        }

        if (bytes < 1024L * 1024L * 1024L) {
            return (bytes / (1024L * 1024L)) + " MB";
        }

        return String.format(
                Locale.US,
                "%.1f GB",
                bytes / (1024.0 * 1024.0 * 1024.0)
        );
    }

    private static String relativeWebPath(File root, File file) {
        try {
            String rel = root.toPath()
                    .relativize(file.toPath())
                    .toString()
                    .replace(File.separatorChar, '/');

            return "/" + rel;
        } catch (Exception e) {
            return "/";
        }
    }

    private static String cleanName(String value) {
        if (value == null) return "";

        String name = value.trim();

        while (name.contains("/")) {
            name = name.replace("/", "_");
        }

        while (name.contains("\\")) {
            name = name.replace("\\", "_");
        }

        while (name.contains("..")) {
            name = name.replace("..", "_");
        }

        return name.replaceAll("[\\r\\n\\t]", "_");
    }

    private static String cleanHost(String value) {
        if (value == null) return "";

        String host = value.trim();

        host = host.replace("http://", "")
                .replace("https://", "");

        int slash = host.indexOf('/');
        if (slash >= 0) {
            host = host.substring(0, slash);
        }

        return host.replaceAll("[^a-zA-Z0-9.\\-:]", "");
    }

    private static boolean isValidIp(String ip) {
        if (ip == null || ip.trim().isEmpty()) {
            return false;
        }

        String[] parts = ip.trim().split("\\.");

        if (parts.length != 4) {
            return false;
        }

        try {
            for (String part : parts) {
                int n = Integer.parseInt(part);

                if (n < 0 || n > 255) {
                    return false;
                }
            }

            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static long parseLong(String value, long fallback) {
        try {
            return Long.parseLong(value);
        } catch (Exception e) {
            return fallback;
        }
    }

    private static String readBody(InputStream input, String lengthText)
            throws IOException {

        long length = parseLong(lengthText, 0L);

        if (length <= 0L) {
            return "";
        }

        if (length > 1024L * 1024L) {
            throw new IOException("Request body too large");
        }

        byte[] data = readBytes(input, length);

        return new String(
                data,
                StandardCharsets.UTF_8
        );
    }

    private static byte[] readBytes(
            InputStream input,
            long length
    ) throws IOException {

        if (length < 0L || length > Integer.MAX_VALUE) {
            throw new IOException("Invalid body length");
        }

        byte[] data = new byte[(int) length];

        int offset = 0;

        while (offset < data.length) {
            int n = input.read(
                    data,
                    offset,
                    data.length - offset
            );

            if (n < 0) {
                throw new IOException("Unexpected end of request");
            }

            offset += n;
        }

        rxBytes += data.length;

        return data;
    }

    private static Map<String, String> parseUrlEncoded(String body) {
        Map<String, String> result = new HashMap<>();

        if (body == null || body.isEmpty()) {
            return result;
        }

        String[] pairs = body.split("&");

        for (String pair : pairs) {
            int eq = pair.indexOf('=');

            if (eq < 0) {
                result.put(
                        decode(pair),
                        ""
                );
            } else {
                result.put(
                        decode(pair.substring(0, eq)),
                        decode(pair.substring(eq + 1))
                );
            }
        }

        return result;
    }

    private static String decode(String value) {
        try {
            return URLDecoder.decode(
                    value,
                    "UTF-8"
            );
        } catch (Exception e) {
            return value;
        }
    }
}
