package com.salam.androidwebserver;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;

import java.nio.charset.StandardCharsets;

import java.text.SimpleDateFormat;

import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import java.util.concurrent.ConcurrentHashMap;


public class WebServerService extends Service {

    public static volatile boolean running = false;

    public static volatile long requests = 0;

    public static final Set<String> clients =
            ConcurrentHashMap.newKeySet();

    private static final List<String> LOGS =
            Collections.synchronizedList(new ArrayList<String>());

    private static final int MAX_LOGS = 2000;

    private static final Object SERVER_LOCK = new Object();

    private static ServerSocket serverSocket;

    private static long startedAt = 0;

    private Thread acceptThread;


    // ============================================================
    // ANDROID SERVICE
    // ============================================================

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        String action = intent == null ? null : intent.getAction();

        if ("STOP".equals(action)) {
            stopServer();
            stopSelf();
            return START_NOT_STICKY;
        }

        if ("START".equals(action) || !running) {

            int port = getPrefs().getInt("port", 8080);

            if (intent != null) {
                port = intent.getIntExtra("port", port);
            }

            startServer(port);
        }

        return START_STICKY;
    }

    private SharedPreferences getPrefs() {
        return getSharedPreferences("server", MODE_PRIVATE);
    }

    public static void applySettings(Context context) {
        // Settings are read directly from SharedPreferences.
    }


    // ============================================================
    // SERVER START
    // ============================================================

    private void startServer(int port) {

        synchronized (SERVER_LOCK) {

            if (running) {
                stopServer();
            }

            try {

                if (port < 1024 || port > 65535) {
                    port = 8080;
                }

                serverSocket = new ServerSocket();
                serverSocket.setReuseAddress(true);
                serverSocket.bind(new InetSocketAddress(port));

                running = true;
                requests = 0;
                clients.clear();
                startedAt = System.currentTimeMillis();

                log("SERVER STARTED | PORT=" + port);

                startForeground(
                        77,
                        createNotification(
                                "Server running on port " + port
                        )
                );

                final WebServerService self = this;

                acceptThread = new Thread(
                        new Runnable() {
                            @Override
                            public void run() {
                                acceptLoop(self);
                            }
                        },
                        "Salam-WebServer-Accept"
                );

                acceptThread.start();

            } catch (Exception e) {

                running = false;

                log(
                        "SERVER START ERROR | " +
                                e.getClass().getSimpleName() +
                                " | " +
                                e.getMessage()
                );
            }
        }
    }

    private void acceptLoop(final WebServerService service) {

        while (running) {

            try {

                Socket socket = serverSocket.accept();

                Thread clientThread = new Thread(
                        new Runnable() {
                            private final Socket client = socket;

                            @Override
                            public void run() {
                                handleClient(client);
                            }
                        },
                        "Salam-WebServer-Client"
                );

                clientThread.start();

            } catch (Exception e) {

                if (running) {
                    log("ACCEPT ERROR | " + e.getMessage());
                }
            }
        }
    }


    // ============================================================
    // CLIENT
    // ============================================================

    private void handleClient(Socket socket) {

        String ip = socket.getInetAddress().getHostAddress();

        clients.add(ip);

        try {

            socket.setSoTimeout(15000);

            BufferedInputStream input =
                    new BufferedInputStream(socket.getInputStream());

            BufferedOutputStream output =
                    new BufferedOutputStream(socket.getOutputStream());

            Request request = parseRequest(input);

            if (request == null) {

                respond(
                        output,
                        400,
                        "text/plain; charset=utf-8",
                        "Bad Request"
                );

                return;
            }

            requests++;

            String path = request.path;

            int status = 200;


            // ----------------------------------------------------
            // IP ACCESS CONTROL
            // ----------------------------------------------------

            if (!isIpAllowed(ip)) {

                status = 403;

                respond(
                        output,
                        403,
                        "text/html; charset=utf-8",
                        forbiddenPage(ip)
                );

                logRequest(ip, request, "IP_BLOCKED", status);

                return;
            }


            // ----------------------------------------------------
            // PASSWORD
            // ----------------------------------------------------

            if (passwordRequired(path) &&
                    !isAuthorized(request.headers)) {

                status = 401;

                sendAuthRequest(output);

                logRequest(
                        ip,
                        request,
                        "PASSWORD_REQUIRED",
                        status
                );

                return;
            }


            // ----------------------------------------------------
            // GET / HEAD
            // ----------------------------------------------------

            if ("GET".equals(request.method) ||
                    "HEAD".equals(request.method)) {

                if ("/__admin".equals(path)) {

                    respond(
                            output,
                            200,
                            "text/html; charset=utf-8",
                            adminHtml()
                    );

                } else if ("/__api/status".equals(path)) {

                    respond(
                            output,
                            200,
                            "application/json; charset=utf-8",
                            statusJson()
                    );

                } else if ("/__api/logs".equals(path)) {

                    respond(
                            output,
                            200,
                            "text/plain; charset=utf-8",
                            logsText()
                    );

                } else if ("/__api/files".equals(path)) {

                    respond(
                            output,
                            200,
                            "application/json; charset=utf-8",
                            filesJson()
                    );

                } else {

                    File file = safeFile(path);

                    if (file == null) {

                        status = 403;

                        respond(
                                output,
                                403,
                                "text/plain; charset=utf-8",
                                "Forbidden"
                        );

                    } else if (file.isDirectory()) {

                        respond(
                                output,
                                200,
                                "text/html; charset=utf-8",
                                directoryHtml(file, path)
                        );

                    } else if (!file.exists()) {

                        status = 404;

                        respond(
                                output,
                                404,
                                "text/plain; charset=utf-8",
                                "Not Found"
                        );

                    } else {

                        sendFile(
                                output,
                                file,
                                "HEAD".equals(request.method)
                        );
                    }
                }

            }

            // ----------------------------------------------------
            // POST
            // ----------------------------------------------------

            else if ("POST".equals(request.method)) {

                String contentType =
                        request.headers.get("content-type");

                if (contentType == null) {
                    contentType = "";
                }

                if ("/__api/action".equals(path)) {

                    String form =
                            new String(
                                    request.body,
                                    StandardCharsets.UTF_8
                            );

                    String result = processAction(form);

                    respond(
                            output,
                            200,
                            "application/json; charset=utf-8",
                            result
                    );

                } else if ("/__api/upload".equals(path)) {

                    String result =
                            uploadFiles(
                                    contentType,
                                    request.body
                            );

                    respond(
                            output,
                            200,
                            "application/json; charset=utf-8",
                            result
                    );

                } else {

                    status = 405;

                    respond(
                            output,
                            405,
                            "text/plain; charset=utf-8",
                            "Method Not Allowed"
                    );
                }

            } else {

                status = 405;

                respond(
                        output,
                        405,
                        "text/plain; charset=utf-8",
                        "Method Not Allowed"
                );
            }

            logRequest(ip, request, "", status);

        } catch (Exception e) {

            log(
                    "CLIENT ERROR | IP=" +
                            ip +
                            " | " +
                            e.getMessage()
            );

            try {

                respond(
                        new BufferedOutputStream(
                                socket.getOutputStream()
                        ),
                        500,
                        "text/plain; charset=utf-8",
                        "Internal Server Error"
                );

            } catch (Exception ignored) {
            }

        } finally {

            clients.remove(ip);

            try {
                socket.close();
            } catch (Exception ignored) {
            }
        }
    }


    // ============================================================
    // HTTP REQUEST PARSER
    // ============================================================

    private Request parseRequest(InputStream input) throws Exception {

        ByteArrayOutputStream headerBuffer =
                new ByteArrayOutputStream();

        int previous = 0;
        int current;

        while ((current = input.read()) != -1) {

            headerBuffer.write(current);

            if (previous == '\r' && current == '\n') {

                byte[] data = headerBuffer.toByteArray();

                int length = data.length;

                if (length >= 4 &&
                        data[length - 4] == '\r' &&
                        data[length - 3] == '\n' &&
                        data[length - 2] == '\r' &&
                        data[length - 1] == '\n') {

                    break;
                }
            }

            previous = current;

            if (headerBuffer.size() > 32768) {
                throw new IOException("HTTP headers too large");
            }
        }

        String headerText =
                headerBuffer.toString("ISO-8859-1");

        String[] lines = headerText.split("\\r\\n");

        if (lines.length == 0) {
            return null;
        }

        String[] firstLine = lines[0].split(" ", 3);

        if (firstLine.length < 2) {
            return null;
        }

        Request request = new Request();

        request.method = firstLine[0];

        String rawPath = firstLine[1];

        int queryIndex = rawPath.indexOf('?');

        String cleanPath =
                queryIndex >= 0
                        ? rawPath.substring(0, queryIndex)
                        : rawPath;

        request.path = decode(cleanPath);

        if (request.path == null || request.path.length() == 0) {
            request.path = "/";
        }

        for (int i = 1; i < lines.length; i++) {

            int separator = lines[i].indexOf(':');

            if (separator > 0) {

                String name =
                        lines[i]
                                .substring(0, separator)
                                .trim()
                                .toLowerCase(Locale.US);

                String value =
                        lines[i]
                                .substring(separator + 1)
                                .trim();

                request.headers.put(name, value);
            }
        }

        int bodyLength = 0;

        try {

            bodyLength =
                    Integer.parseInt(
                            request.headers.getOrDefault(
                                    "content-length",
                                    "0"
                            )
                    );

        } catch (Exception ignored) {
        }

        if (bodyLength > 50 * 1024 * 1024) {
            throw new IOException("Request body too large");
        }

        ByteArrayOutputStream body =
                new ByteArrayOutputStream();

        byte[] buffer = new byte[8192];

        int remaining = bodyLength;

        while (remaining > 0) {

            int count =
                    input.read(
                            buffer,
                            0,
                            Math.min(buffer.length, remaining)
                    );

            if (count < 0) {
                break;
            }

            body.write(buffer, 0, count);

            remaining -= count;
        }

        request.body = body.toByteArray();

        return request;
    }


    // ============================================================
    // PASSWORD
    // ============================================================

    private boolean passwordRequired(String path) {

        String password =
                getPrefs().getString("password", "");

        if (password.isEmpty()) {
            return false;
        }

        return !"/favicon.ico".equals(path);
    }

    private boolean isAuthorized(Map<String, String> headers) {

        String password =
                getPrefs().getString("password", "");

        if (password.isEmpty()) {
            return true;
        }

        String authorization =
                headers.get("authorization");

        if (authorization == null ||
                !authorization.startsWith("Basic ")) {

            return false;
        }

        try {

            String encoded = authorization.substring(6);

            String decoded =
                    new String(
                            Base64.getDecoder().decode(encoded),
                            StandardCharsets.UTF_8
                    );

            int separator = decoded.indexOf(':');

            if (separator < 0) {
                return false;
            }

            String username =
                    decoded.substring(0, separator);

            String suppliedPassword =
                    decoded.substring(separator + 1);

            return "admin".equals(username) &&
                    password.equals(suppliedPassword);

        } catch (Exception e) {

            return false;
        }
    }

    private void sendAuthRequest(OutputStream output)
            throws IOException {

        String response =
                "HTTP/1.1 401 Unauthorized\r\n" +
                "WWW-Authenticate: Basic realm=\"Salam Web Server\"\r\n" +
                "Content-Length: 0\r\n" +
                "Connection: close\r\n" +
                "\r\n";

        output.write(
                response.getBytes(
                        StandardCharsets.ISO_8859_1
                )
        );

        output.flush();
    }


    // ============================================================
    // IP ACCESS CONTROL
    // ============================================================

    private boolean isIpAllowed(String ip) {

        if ("127.0.0.1".equals(ip) || "::1".equals(ip)) {
            return true;
        }

        Set<String> blocked = getIpRules("blockIps");

        for (String rule : blocked) {

            if (ipMatches(ip, rule)) {
                return false;
            }
        }

        boolean allowOnly =
                getPrefs().getBoolean("allowOnly", false);

        if (!allowOnly) {
            return true;
        }

        Set<String> allowed = getIpRules("allowIps");

        for (String rule : allowed) {

            if (ipMatches(ip, rule)) {
                return true;
            }
        }

        return false;
    }

    private Set<String> getIpRules(String key) {

        String value =
                getPrefs().getString(key, "");

        Set<String> result = new HashSet<>();

        String[] rules = value.split(",");

        for (String rule : rules) {

            rule = rule.trim();

            if (!rule.isEmpty()) {
                result.add(rule);
            }
        }

        return result;
    }

    private boolean ipMatches(String ip, String rule) {

        try {

            if (rule.contains("/")) {

                String[] parts = rule.split("/");

                if (parts.length != 2) {
                    return false;
                }

                int bits = Integer.parseInt(parts[1]);

                byte[] address =
                        InetAddress.getByName(ip).getAddress();

                byte[] network =
                        InetAddress.getByName(parts[0]).getAddress();

                if (address.length != network.length) {
                    return false;
                }

                int maxBits = address.length * 8;

                if (bits < 0 || bits > maxBits) {
                    return false;
                }

                int fullBytes = bits / 8;

                int remainingBits = bits % 8;

                for (int i = 0; i < fullBytes; i++) {

                    if (address[i] != network[i]) {
                        return false;
                    }
                }

                if (remainingBits > 0) {

                    int mask = 0xff << (8 - remainingBits);

                    if ((address[fullBytes] & mask) !=
                            (network[fullBytes] & mask)) {

                        return false;
                    }
                }

                return true;
            }

            return ip.equals(rule);

        } catch (Exception e) {

            return false;
        }
    }


    // ============================================================
    // WEB ROOT
    // ============================================================

    private File root() {

        File directory =
                new File(getFilesDir(), "www");

        if (!directory.exists()) {
            directory.mkdirs();
        }

        return directory;
    }

    public static File webRoot(Context context) {

        File directory =
                new File(context.getFilesDir(), "www");

        if (!directory.exists()) {
            directory.mkdirs();
        }

        return directory;
    }

    public static String displayName(Context context, Uri uri) {

        String name = "upload.bin";

        try {

            android.database.Cursor cursor =
                    context.getContentResolver().query(
                            uri,
                            null,
                            null,
                            null,
                            null
                    );

            if (cursor != null && cursor.moveToFirst()) {

                int index =
                        cursor.getColumnIndex(
                                android.provider.OpenableColumns.DISPLAY_NAME
                        );

                if (index >= 0) {
                    name = cursor.getString(index);
                }

                cursor.close();
            }

        } catch (Exception ignored) {
        }

        return sanitizeFileName(name);
    }

    private static String sanitizeFileName(String name) {

        if (name == null || name.isEmpty()) {
            name = "file.bin";
        }

        name = new File(name).getName();

        return name.replaceAll(
                "[^A-Za-z0-9._ -]",
                "_"
        );
    }


    // ============================================================
    // SAFE FILE ACCESS
    // ============================================================

    private File safeFile(String path) {

        try {

            if (path == null || path.isEmpty()) {
                path = "/";
            }

            String customPath =
                    getPrefs().getString("customPath", "/");

            if (customPath == null || customPath.isEmpty()) {
                customPath = "/";
            }

            if (!customPath.startsWith("/")) {
                customPath = "/" + customPath;
            }

            if (!"/".equals(customPath) &&
                    !path.equals(customPath) &&
                    !path.startsWith(customPath + "/")) {

                return null;
            }

            String relative;

            if ("/".equals(customPath)) {

                relative = path;

            } else {

                relative =
                        path.substring(customPath.length());
            }

            if (relative.isEmpty()) {
                relative = "/";
            }

            File file = new File(root(), relative);

            String rootPath =
                    root().getCanonicalPath();

            String filePath =
                    file.getCanonicalPath();

            if (filePath.equals(rootPath) ||
                    filePath.startsWith(
                            rootPath + File.separator
                    )) {

                return file;
            }

            return null;

        } catch (Exception e) {

            return null;
        }
    }

    private File target(String path) {

        if (path == null || path.isEmpty()) {
            path = "/";
        }

        try {

            File file = new File(root(), path);

            String rootPath =
                    root().getCanonicalPath();

            String filePath =
                    file.getCanonicalPath();

            if (filePath.equals(rootPath) ||
                    filePath.startsWith(
                            rootPath + File.separator
                    )) {

                return file;
            }

        } catch (Exception ignored) {
        }

        return null;
    }

    private boolean safeChild(File directory, File child) {

        try {

            String parent =
                    directory.getCanonicalPath();

            String target =
                    child.getCanonicalPath();

            return target.startsWith(parent + File.separator);

        } catch (Exception e) {

            return false;
        }
    }


    // ============================================================
    // ACTION API
    // ============================================================

    private String processAction(String form) {

        Map<String, String> data = parseForm(form);

        String action =
                data.getOrDefault("action", "");

        try {

            if ("saveSettings".equals(action)) {

                int port =
                        Integer.parseInt(
                                data.getOrDefault("port", "8080")
                        );

                if (port < 1024 || port > 65535) {

                    return errorJson(
                            "Port must be between 1024 and 65535"
                    );
                }

                getPrefs()
                        .edit()
                        .putInt("port", port)
                        .putBoolean(
                                "allowOnly",
                                "1".equals(data.get("allowOnly"))
                        )
                        .putString(
                                "allowIps",
                                data.getOrDefault("allowIps", "")
                        )
                        .putString(
                                "blockIps",
                                data.getOrDefault("blockIps", "")
                        )
                        .putString(
                                "password",
                                data.getOrDefault("password", "")
                        )
                        .putString(
                                "customPath",
                                data.getOrDefault("customPath", "/")
                        )
                        .apply();

                return successJson("Settings saved");
            }

            if ("mkdir".equals(action)) {

                File directory =
                        target(data.get("path"));

                if (directory == null) {
                    return errorJson("Invalid path");
                }

                if (!directory.mkdirs() &&
                        !directory.exists()) {

                    return errorJson("Folder creation failed");
                }

                return successJson("Folder created");
            }

            if ("touch".equals(action)) {

                File file =
                        target(data.get("path"));

                if (file == null) {
                    return errorJson("Invalid path");
                }

                File parent = file.getParentFile();

                if (parent != null && !parent.exists()) {
                    parent.mkdirs();
                }

                if (!file.exists()) {
                    file.createNewFile();
                }

                return successJson("File created");
            }

            if ("delete".equals(action)) {

                File file =
                        target(data.get("path"));

                if (file == null || file.equals(root())) {
                    return errorJson("Invalid path");
                }

                deleteRecursive(file);

                return successJson("Deleted");
            }

            if ("rename".equals(action)) {

                File oldFile =
                        target(data.get("path"));

                if (oldFile == null) {
                    return errorJson("Invalid source");
                }

                String newName =
                        sanitizeFileName(data.get("name"));

                File parent = oldFile.getParentFile();

                if (parent == null) {
                    return errorJson("Invalid destination");
                }

                File newFile =
                        new File(parent, newName);

                if (!safeChild(parent, newFile)) {
                    return errorJson("Invalid destination");
                }

                if (!oldFile.renameTo(newFile)) {
                    return errorJson("Rename failed");
                }

                return successJson("Renamed");
            }

            if ("clearLogs".equals(action)) {

                synchronized (LOGS) {
                    LOGS.clear();
                }

                return successJson("Logs cleared");
            }

            return errorJson("Unknown action");

        } catch (Exception e) {

            return errorJson(e.getMessage());
        }
    }

    private void deleteRecursive(File file) {

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


    // ============================================================
    // UPLOAD
    // ============================================================

    private String uploadFiles(
            String contentType,
            byte[] body
    ) {

        try {

            if (contentType == null ||
                    !contentType.contains("boundary=")) {

                return errorJson(
                        "Multipart boundary missing"
                );
            }

            String boundary =
                    contentType.substring(
                            contentType.indexOf("boundary=") + 9
                    ).trim();

            if (boundary.startsWith("\"") &&
                    boundary.endsWith("\"")) {

                boundary =
                        boundary.substring(
                                1,
                                boundary.length() - 1
                        );
            }

            byte[] marker =
                    ("--" + boundary).getBytes(
                            StandardCharsets.ISO_8859_1
                    );

            byte[] headerSeparator =
                    "\r\n\r\n".getBytes(
                            StandardCharsets.ISO_8859_1
                    );

            int position = 0;
            int uploaded = 0;

            while (true) {

                int markerPosition =
                        indexOf(body, marker, position);

                if (markerPosition < 0) {
                    break;
                }

                int headerStart =
                        markerPosition + marker.length;

                if (headerStart + 2 >= body.length) {
                    break;
                }

                int nextMarker =
                        indexOf(body, marker, headerStart);

                if (nextMarker < 0) {
                    break;
                }

                int headerEnd =
                        indexOf(
                                body,
                                headerSeparator,
                                headerStart
                        );

                if (headerEnd < 0 ||
                        headerEnd > nextMarker) {

                    break;
                }

                String headers =
                        new String(
                                body,
                                headerStart,
                                headerEnd - headerStart,
                                StandardCharsets.ISO_8859_1
                        );

                String filename = null;

                for (String line : headers.split("\\r\\n")) {

                    String lower =
                            line.toLowerCase(Locale.US);

                    if (lower.contains("filename=")) {

                        int index =
                                line.indexOf("filename=");

                        filename =
                                line.substring(index + 9)
                                        .trim()
                                        .replace("\"", "");

                        break;
                    }
                }

                if (filename != null &&
                        !filename.isEmpty()) {

                    filename =
                            sanitizeFileName(filename);

                    int dataStart = headerEnd + 4;
                    int dataEnd = nextMarker - 2;

                    if (dataEnd >= dataStart) {

                        File outputFile =
                                new File(root(), filename);

                        try (
                                FileOutputStream output =
                                        new FileOutputStream(outputFile)
                        ) {

                            output.write(
                                    body,
                                    dataStart,
                                    dataEnd - dataStart
                            );
                        }

                        uploaded++;
                    }
                }

                position =
                        nextMarker + marker.length;
            }

            return successJson(
                    "Uploaded " + uploaded + " file(s)"
            );

        } catch (Exception e) {

            return errorJson(e.getMessage());
        }
    }

    private int indexOf(
            byte[] source,
            byte[] target,
            int from
    ) {

        if (target.length == 0) {
            return from;
        }

        outer:

        for (
                int i = Math.max(0, from);
                i <= source.length - target.length;
                i++
        ) {

            for (int j = 0; j < target.length; j++) {

                if (source[i + j] != target[j]) {
                    continue outer;
                }
            }

            return i;
        }

        return -1;
    }


    // ============================================================
    // FILE LIST
    // ============================================================

    private String filesJson() {

        StringBuilder json =
                new StringBuilder();

        json.append("[");

        File[] files = root().listFiles();

        if (files != null) {

            for (int i = 0; i < files.length; i++) {

                if (i > 0) {
                    json.append(",");
                }

                File file = files[i];

                json.append("{");

                json.append("\"name\":\"");
                json.append(escapeJson(file.getName()));
                json.append("\",");

                json.append("\"dir\":");
                json.append(file.isDirectory());
                json.append(",");

                json.append("\"size\":");
                json.append(file.length());

                json.append("}");
            }
        }

        json.append("]");

        return json.toString();
    }


    // ============================================================
    // DIRECTORY
    // ============================================================

    private String directoryHtml(
            File directory,
            String path
    ) {

        StringBuilder html =
                new StringBuilder();

        html.append("<!doctype html>");
        html.append("<html><head>");

        html.append(
                "<meta name=\"viewport\" " +
                        "content=\"width=device-width,initial-scale=1\">"
        );

        html.append(
                "<style>" +
                        "body{font-family:system-ui;" +
                        "background:#080b10;color:#fff;padding:20px}" +
                        "a{display:block;padding:12px;margin:6px 0;" +
                        "background:#151b22;color:#62e6a5;" +
                        "border-radius:10px;text-decoration:none}" +
                        "</style>"
        );

        html.append("</head><body>");

        html.append("<h1>Salam Web Server</h1>");

        html.append("<p>Directory: ");
        html.append(escapeHtml(path));
        html.append("</p>");

        File[] files = directory.listFiles();

        if (files != null) {

            for (File file : files) {

                String childPath;

                if (path.endsWith("/")) {

                    childPath =
                            path + file.getName();

                } else {

                    childPath =
                            path + "/" + file.getName();
                }

                html.append("<a href=\"");
                html.append(escapeHtml(childPath));
                html.append("\">");

                if (file.isDirectory()) {
                    html.append("📁 ");
                } else {
                    html.append("📄 ");
                }

                html.append(escapeHtml(file.getName()));
                html.append("</a>");
            }
        }

        html.append("</body></html>");

        return html.toString();
    }


    // ============================================================
    // ADMIN DASHBOARD
    // ============================================================

    private String adminHtml() {

        StringBuilder html =
                new StringBuilder();

        html.append("<!doctype html>");
        html.append("<html>");
        html.append("<head>");

        html.append(
                "<meta name=\"viewport\" " +
                        "content=\"width=device-width,initial-scale=1\">"
        );

        html.append(
                "<meta name=\"theme-color\" " +
                        "content=\"#080b10\">"
        );

        html.append("<title>Salam Web Server Pro</title>");

        html.append(
                "<style>" +
                        "*{box-sizing:border-box}" +
                        "body{margin:0;background:#080b10;color:#f5f7fa;" +
                        "font-family:system-ui,-apple-system,BlinkMacSystemFont,Segoe UI,Arial;" +
                        "padding:12px}" +
                        ".top{background:#111820;border:1px solid #27313b;" +
                        "border-radius:18px;padding:18px;margin-bottom:12px}" +
                        ".brand{font-size:24px;font-weight:900}" +
                        ".sub{color:#8793a1;font-size:12px;margin-top:4px}" +
                        ".status{display:inline-flex;align-items:center;gap:7px;" +
                        "margin-top:12px;padding:8px 12px;background:#10261d;" +
                        "color:#62e6a5;border-radius:999px;font-weight:800}" +
                        ".dot{width:9px;height:9px;border-radius:50%;background:#62e6a5}" +
                        ".grid{display:grid;grid-template-columns:repeat(2,1fr);gap:10px}" +
                        ".card{background:#111820;border:1px solid #27313b;" +
                        "border-radius:16px;padding:14px;margin-bottom:10px}" +
                        ".label{font-size:11px;color:#8793a1;font-weight:700;letter-spacing:.5px}" +
                        ".value{font-size:22px;font-weight:900;margin-top:5px}" +
                        "h2{font-size:16px;margin:0 0 12px 0}" +
                        "input{width:100%;padding:12px;border-radius:11px;" +
                        "border:1px solid #303b47;background:#0b1016;color:#fff;" +
                        "margin:5px 0;outline:none}" +
                        "button{width:100%;padding:12px;border:0;border-radius:11px;" +
                        "background:#202a35;color:#fff;font-weight:800;margin:5px 0}" +
                        "button:active{transform:scale(.98)}" +
                        ".green{background:#1b6f4d}" +
                        ".danger{background:#6f2730}" +
                        ".row{display:grid;grid-template-columns:1fr 1fr;gap:8px}" +
                        ".file{padding:10px;border-bottom:1px solid #27313b}" +
                        ".file button{width:auto;display:inline-block;padding:7px 10px;margin:5px 3px 0 0}" +
                        "pre{white-space:pre-wrap;word-break:break-word;background:#080b10;" +
                        "border:1px solid #27313b;border-radius:12px;padding:12px;" +
                        "max-height:400px;overflow:auto;font-size:11px}" +
                        ".url{word-break:break-all;color:#62e6a5;padding:10px;" +
                        "background:#0b1016;border-radius:10px;margin-top:10px}" +
                        "</style>"
        );

        html.append("</head><body>");

        html.append("<div class=\"top\">");

        html.append("<div class=\"brand\">");
        html.append("Salam Web Server Pro");
        html.append("</div>");

        html.append("<div class=\"sub\">");
        html.append("ADVANCED LAN SERVER CONTROL PANEL");
        html.append("</div>");

        html.append(
                "<div class=\"status\">" +
                        "<span class=\"dot\"></span>" +
                        "SERVER RUNNING" +
                        "</div>"
        );

        html.append(
                "<div class=\"url\" id=\"url\">Loading URL...</div>"
        );

        html.append(
                "<button onclick=\"copyUrl()\">COPY SERVER URL</button>"
        );

        html.append("</div>");


        // STATISTICS

        html.append("<div class=\"grid\">");

        html.append(
                "<div class=\"card\">" +
                        "<div class=\"label\">REQUESTS</div>" +
                        "<div class=\"value\" id=\"requests\">0</div>" +
                        "</div>"
        );

        html.append(
                "<div class=\"card\">" +
                        "<div class=\"label\">ACTIVE CLIENTS</div>" +
                        "<div class=\"value\" id=\"clients\">0</div>" +
                        "</div>"
        );

        html.append(
                "<div class=\"card\">" +
                        "<div class=\"label\">UPTIME</div>" +
                        "<div class=\"value\" id=\"uptime\">00:00:00</div>" +
                        "</div>"
        );

        html.append(
                "<div class=\"card\">" +
                        "<div class=\"label\">SERVER STATUS</div>" +
                        "<div class=\"value\" id=\"serverStatus\">RUNNING</div>" +
                        "</div>"
        );

        html.append("</div>");


        // FILE UPLOAD

        html.append("<div class=\"card\">");
        html.append("<h2>FILE UPLOAD</h2>");
        html.append(
                "<input id=\"uploadFiles\" type=\"file\" multiple>"
        );
        html.append(
                "<button class=\"green\" onclick=\"uploadFiles()\">" +
                        "UPLOAD FILES" +
                        "</button>"
        );
        html.append("</div>");


        // FILE MANAGER

        html.append("<div class=\"card\">");
        html.append("<h2>FILE MANAGER</h2>");
        html.append(
                "<div id=\"fileList\">Loading...</div>"
        );

        html.append("<div class=\"row\">");

        html.append(
                "<button onclick=\"newFolder()\">NEW FOLDER</button>"
        );

        html.append(
                "<button onclick=\"newFile()\">NEW FILE</button>"
        );

        html.append("</div>");
        html.append("</div>");


        // IP ACCESS CONTROL

        html.append("<div class=\"card\">");
        html.append("<h2>IP ACCESS CONTROL</h2>");

        html.append(
                "<input id=\"allowIps\" " +
                        "placeholder=\"Allowed IPs: 192.168.0.10, 192.168.0.20\">"
        );

        html.append(
                "<input id=\"blockIps\" " +
                        "placeholder=\"Blocked IPs: 192.168.0.50\">"
        );

        html.append(
                "<button onclick=\"saveAccess()\">SAVE IP RULES</button>"
        );

        html.append(
                "<small>Use comma-separated IPs or CIDR rules.</small>"
        );

        html.append("</div>");


        // PASSWORD

        html.append("<div class=\"card\">");
        html.append("<h2>WEB PASSWORD</h2>");

        html.append(
                "<input id=\"password\" type=\"password\" " +
                        "placeholder=\"New admin password\">"
        );

        html.append(
                "<button onclick=\"savePassword()\">SAVE PASSWORD</button>"
        );

        html.append("</div>");


        // LIVE LOGS

        html.append("<div class=\"card\">");
        html.append("<h2>LIVE SERVER LOGS</h2>");

        html.append(
                "<button class=\"danger\" onclick=\"clearLogs()\">" +
                        "CLEAR LOGS" +
                        "</button>"
        );

        html.append(
                "<pre id=\"logs\">Loading...</pre>"
        );

        html.append("</div>");


        // JAVASCRIPT

        html.append("<script>");

        html.append(
                "const $=id=>document.getElementById(id);"
        );

        html.append(
                "async function api(url,opt){" +
                        "const r=await fetch(url,opt);" +
                        "return r.json();" +
                        "}"
        );

        html.append(
                "async function refresh(){" +
                        "try{" +
                        "const s=await api('/__api/status');" +
                        "$('requests').textContent=s.requests;" +
                        "$('clients').textContent=s.clients;" +
                        "$('uptime').textContent=s.uptime;" +
                        "$('serverStatus').textContent=s.running?'RUNNING':'STOPPED';" +
                        "$('url').textContent=s.url;" +
                        "const files=await api('/__api/files');" +
                        "let box=$('fileList');" +
                        "box.innerHTML='';" +
                        "files.forEach(f=>{" +
                        "let div=document.createElement('div');" +
                        "div.className='file';" +
                        "div.textContent=(f.dir?'📁 ':'📄 ')+f.name;" +
                        "let r=document.createElement('button');" +
                        "r.textContent='RENAME';" +
                        "r.onclick=()=>renameFile(f.name);" +
                        "let d=document.createElement('button');" +
                        "d.textContent='DELETE';" +
                        "d.onclick=()=>deleteFile(f.name);" +
                        "div.appendChild(r);" +
                        "div.appendChild(d);" +
                        "box.appendChild(div);" +
                        "});" +
                        "const logs=await fetch('/__api/logs');" +
                        "$('logs').textContent=await logs.text();" +
                        "}catch(e){console.log(e)}" +
                        "}"
        );

        html.append(
                "async function uploadFiles(){" +
                        "const input=$('uploadFiles');" +
                        "if(!input.files.length){" +
                        "alert('Select files first');return;}" +
                        "const data=new FormData();" +
                        "for(const f of input.files){data.append('file',f);}" +
                        "const r=await fetch('/__api/upload',{method:'POST',body:data});" +
                        "const j=await r.json();" +
                        "alert(j.message||j.error);" +
                        "refresh();" +
                        "}"
        );

        html.append(
                "async function deleteFile(name){" +
                        "if(!confirm('Delete '+name+'?'))return;" +
                        "await api('/__api/action',{method:'POST'," +
                        "headers:{'Content-Type':'application/x-www-form-urlencoded'}," +
                        "body:'action=delete&path='+encodeURIComponent(name)});" +
                        "refresh();" +
                        "}"
        );

        html.append(
                "async function renameFile(name){" +
                        "const newName=prompt('New name',name);" +
                        "if(!newName)return;" +
                        "await api('/__api/action',{method:'POST'," +
                        "headers:{'Content-Type':'application/x-www-form-urlencoded'}," +
                        "body:'action=rename&path='+encodeURIComponent(name)+'&name='+encodeURIComponent(newName)});" +
                        "refresh();" +
                        "}"
        );

        html.append(
                "async function newFolder(){" +
                        "const name=prompt('Folder name');" +
                        "if(!name)return;" +
                        "await api('/__api/action',{method:'POST'," +
                        "headers:{'Content-Type':'application/x-www-form-urlencoded'}," +
                        "body:'action=mkdir&path='+encodeURIComponent(name)});" +
                        "refresh();" +
                        "}"
        );

        html.append(
                "async function newFile(){" +
                        "const name=prompt('File name');" +
                        "if(!name)return;" +
                        "await api('/__api/action',{method:'POST'," +
                        "headers:{'Content-Type':'application/x-www-form-urlencoded'}," +
                        "body:'action=touch&path='+encodeURIComponent(name)});" +
                        "refresh();" +
                        "}"
        );

        html.append(
                "async function saveAccess(){" +
                        "const allow=$('allowIps').value;" +
                        "const block=$('blockIps').value;" +
                        "await api('/__api/action',{method:'POST'," +
                        "headers:{'Content-Type':'application/x-www-form-urlencoded'}," +
                        "body:'action=saveSettings&allowIps='+encodeURIComponent(allow)+'&blockIps='+encodeURIComponent(block)});" +
                        "alert('IP rules saved');" +
                        "}"
        );

        html.append(
                "async function savePassword(){" +
                        "const p=$('password').value;" +
                        "await api('/__api/action',{method:'POST'," +
                        "headers:{'Content-Type':'application/x-www-form-urlencoded'}," +
                        "body:'action=saveSettings&password='+encodeURIComponent(p)});" +
                        "alert('Password saved');" +
                        "}"
        );

        html.append(
                "async function clearLogs(){" +
                        "await api('/__api/action',{method:'POST'," +
                        "headers:{'Content-Type':'application/x-www-form-urlencoded'}," +
                        "body:'action=clearLogs'});" +
                        "refresh();" +
                        "}"
        );

        html.append(
                "async function copyUrl(){" +
                        "try{" +
                        "await navigator.clipboard.writeText($('url').textContent);" +
                        "alert('URL copied');" +
                        "}catch(e){" +
                        "alert($('url').textContent);" +
                        "}" +
                        "}"
        );

        html.append("refresh();");
        html.append("setInterval(refresh,2000);");

        html.append("</script>");
        html.append("</body></html>");

        return html.toString();
    }


    // ============================================================
    // HTTP RESPONSE
    // ============================================================

    private void respond(
            OutputStream output,
            int code,
            String contentType,
            String body
    ) throws IOException {

        byte[] bytes =
                body.getBytes(StandardCharsets.UTF_8);

        String header =
                "HTTP/1.1 " +
                        code +
                        " " +
                        reason(code) +
                        "\r\n" +
                        "Content-Type: " +
                        contentType +
                        "\r\n" +
                        "Content-Length: " +
                        bytes.length +
                        "\r\n" +
                        "Cache-Control: no-store\r\n" +
                        "Connection: close\r\n" +
                        "\r\n";

        output.write(
                header.getBytes(
                        StandardCharsets.ISO_8859_1
                )
        );

        output.write(bytes);
        output.flush();
    }

    private void sendFile(
            OutputStream output,
            File file,
            boolean head
    ) throws Exception {

        String type = mimeType(file.getName());

        String header =
                "HTTP/1.1 200 OK\r\n" +
                        "Content-Type: " +
                        type +
                        "\r\n" +
                        "Content-Length: " +
                        file.length() +
                        "\r\n" +
                        "Connection: close\r\n" +
                        "\r\n";

        output.write(
                header.getBytes(
                        StandardCharsets.ISO_8859_1
                )
        );

        if (!head) {

            FileInputStream input =
                    new FileInputStream(file);

            try {

                byte[] buffer = new byte[16384];

                int count;

                while ((count = input.read(buffer)) > 0) {

                    output.write(
                            buffer,
                            0,
                            count
                    );
                }

            } finally {

                input.close();
            }
        }

        output.flush();
    }

    private String reason(int code) {

        if (code == 200) return "OK";
        if (code == 201) return "Created";
        if (code == 400) return "Bad Request";
        if (code == 401) return "Unauthorized";
        if (code == 403) return "Forbidden";
        if (code == 404) return "Not Found";
        if (code == 405) return "Method Not Allowed";

        return "Error";
    }


    // ============================================================
    // MIME
    // ============================================================

    private String mimeType(String name) {

        String lower =
                name.toLowerCase(Locale.US);

        if (lower.endsWith(".html") ||
                lower.endsWith(".htm")) {

            return "text/html; charset=utf-8";
        }

        if (lower.endsWith(".css")) {
            return "text/css; charset=utf-8";
        }

        if (lower.endsWith(".js")) {
            return "application/javascript; charset=utf-8";
        }

        if (lower.endsWith(".json")) {
            return "application/json; charset=utf-8";
        }

        if (lower.endsWith(".xml")) {
            return "application/xml; charset=utf-8";
        }

        if (lower.endsWith(".txt")) {
            return "text/plain; charset=utf-8";
        }

        if (lower.endsWith(".png")) {
            return "image/png";
        }

        if (lower.endsWith(".jpg") ||
                lower.endsWith(".jpeg")) {

            return "image/jpeg";
        }

        if (lower.endsWith(".gif")) {
            return "image/gif";
        }

        if (lower.endsWith(".svg")) {
            return "image/svg+xml";
        }

        if (lower.endsWith(".webp")) {
            return "image/webp";
        }

        if (lower.endsWith(".pdf")) {
            return "application/pdf";
        }

        if (lower.endsWith(".zip")) {
            return "application/zip";
        }

        if (lower.endsWith(".mp4")) {
            return "video/mp4";
        }

        if (lower.endsWith(".mp3")) {
            return "audio/mpeg";
        }

        return "application/octet-stream";
    }


    // ============================================================
    // JSON
    // ============================================================

    private String statusJson() {

        StringBuilder json =
                new StringBuilder();

        json.append("{");

        json.append("\"running\":");
        json.append(running);
        json.append(",");

        json.append("\"requests\":");
        json.append(requests);
        json.append(",");

        json.append("\"clients\":");
        json.append(clients.size());
        json.append(",");

        json.append("\"uptime\":\"");
        json.append(escapeJson(uptime()));
        json.append("\",");

        json.append("\"url\":\"");
        json.append(escapeJson(currentUrl(this)));
        json.append("\"");

        json.append("}");

        return json.toString();
    }

    private String successJson(String message) {

        return "{\"ok\":true,\"message\":\"" +
                escapeJson(message) +
                "\"}";
    }

    private String errorJson(String message) {

        if (message == null) {
            message = "Unknown error";
        }

        return "{\"ok\":false,\"error\":\"" +
                escapeJson(message) +
                "\"}";
    }


    // ============================================================
    // FORM
    // ============================================================

    private Map<String, String> parseForm(String value) {

        Map<String, String> result =
                new HashMap<>();

        if (value == null) {
            return result;
        }

        String[] parts = value.split("&");

        for (String part : parts) {

            int index = part.indexOf('=');

            if (index > 0) {

                String key =
                        decode(
                                part.substring(0, index)
                        );

                String val =
                        decode(
                                part.substring(index + 1)
                        );

                result.put(key, val);
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


    // ============================================================
    // LOGS
    // ============================================================

    private void logRequest(
            String ip,
            Request request,
            String note,
            int status
    ) {

        String userAgent =
                request.headers.getOrDefault(
                        "user-agent",
                        "-"
                );

        String line =
                ip +
                        " | " +
                        request.method +
                        " | " +
                        request.path +
                        " | STATUS=" +
                        status +
                        " | " +
                        (note.isEmpty() ? "" : note + " | ") +
                        "UA=" +
                        userAgent;

        log(line);
    }

    private static void log(String message) {

        String timestamp =
                new SimpleDateFormat(
                        "yyyy-MM-dd HH:mm:ss",
                        Locale.US
                ).format(new Date());

        synchronized (LOGS) {

            LOGS.add(
                    timestamp +
                            " | " +
                            message
            );

            while (LOGS.size() > MAX_LOGS) {
                LOGS.remove(0);
            }
        }
    }

    public static String logsText() {

        StringBuilder text =
                new StringBuilder();

        synchronized (LOGS) {

            for (String line : LOGS) {

                text.append(line);
                text.append('\n');
            }
        }

        return text.toString();
    }


    // ============================================================
    // MAIN ACTIVITY API
    // ============================================================

    public static boolean isRunning() {
        return running;
    }

    public static long getRequestCount() {
        return requests;
    }

    public static int getClientCount() {
        return clients.size();
    }


    // ============================================================
    // URL / IP
    // ============================================================

    public static String currentUrl(Context context) {

        int port =
                context
                        .getSharedPreferences(
                                "server",
                                MODE_PRIVATE
                        )
                        .getInt(
                                "port",
                                8080
                        );

        return "http://" +
                localIp(context) +
                ":" +
                port;
    }

    private static String localIp(Context context) {

        try {

            ConnectivityManager manager =
                    (ConnectivityManager)
                            context.getSystemService(
                                    CONNECTIVITY_SERVICE
                            );

            Network[] networks =
                    manager.getAllNetworks();

            for (Network network : networks) {

                LinkProperties properties =
                        manager.getLinkProperties(network);

                if (properties == null) {
                    continue;
                }

                for (
                        LinkAddress address :
                        properties.getLinkAddresses()
                ) {

                    String ip =
                            address
                                    .getAddress()
                                    .getHostAddress();

                    if (ip != null &&
                            !ip.contains(":") &&
                            !ip.startsWith("127.")) {

                        return ip;
                    }
                }
            }

        } catch (Exception ignored) {
        }

        try {

            Enumeration<NetworkInterface> interfaces =
                    NetworkInterface.getNetworkInterfaces();

            while (interfaces.hasMoreElements()) {

                NetworkInterface network =
                        interfaces.nextElement();

                Enumeration<InetAddress> addresses =
                        network.getInetAddresses();

                while (addresses.hasMoreElements()) {

                    String ip =
                            addresses
                                    .nextElement()
                                    .getHostAddress();

                    if (ip != null &&
                            !ip.contains(":") &&
                            !ip.startsWith("127.")) {

                        return ip;
                    }
                }
            }

        } catch (Exception ignored) {
        }

        return "0.0.0.0";
    }

    public static String uptime() {

        if (!running) {
            return "00:00:00";
        }

        long seconds =
                (
                        System.currentTimeMillis() -
                                startedAt
                ) / 1000;

        long hours = seconds / 3600;

        long minutes =
                (seconds % 3600) / 60;

        long secs = seconds % 60;

        return String.format(
                Locale.US,
                "%02d:%02d:%02d",
                hours,
                minutes,
                secs
        );
    }


    // ============================================================
    // ESCAPE
    // ============================================================

    private String escapeHtml(String value) {

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

    private String escapeJson(String value) {

        if (value == null) {
            return "";
        }

        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "")
                .replace("\n", "\\n");
    }


    // ============================================================
    // ERROR PAGE
    // ============================================================

    private String forbiddenPage(String ip) {

        return
                "<!doctype html>" +
                        "<html><head>" +
                        "<meta name=\"viewport\" " +
                        "content=\"width=device-width,initial-scale=1\">" +
                        "<style>" +
                        "body{font-family:system-ui;" +
                        "background:#080b10;color:#fff;padding:30px}" +
                        ".box{background:#151b22;" +
                        "border-radius:16px;padding:20px}" +
                        "</style>" +
                        "</head><body>" +
                        "<div class=\"box\">" +
                        "<h1>403 Forbidden</h1>" +
                        "<p>Your IP is not allowed.</p>" +
                        "<p>IP: " +
                        escapeHtml(ip) +
                        "</p>" +
                        "</div>" +
                        "</body></html>";
    }


    // ============================================================
    // NOTIFICATION
    // ============================================================

    private Notification createNotification(String text) {

        Notification.Builder builder;

        if (Build.VERSION.SDK_INT >= 26) {

            builder =
                    new Notification.Builder(
                            this,
                            "salams-web-server"
                    );

        } else {

            builder =
                    new Notification.Builder(this);
        }

        builder
                .setContentTitle(
                        "Salam Web Server Pro"
                )
                .setContentText(text)
                .setSmallIcon(
                        com.salam.androidwebserver
                                .R.drawable.ic_server
                )
                .setOngoing(true);

        return builder.build();
    }

    private void createNotificationChannel() {

        if (Build.VERSION.SDK_INT >= 26) {

            NotificationChannel channel =
                    new NotificationChannel(
                            "salams-web-server",
                            "Salam Web Server",
                            NotificationManager.IMPORTANCE_LOW
                    );

            NotificationManager manager =
                    getSystemService(
                            NotificationManager.class
                    );

            if (manager != null) {

                manager.createNotificationChannel(channel);
            }
        }
    }


    // ============================================================
    // STOP SERVER
    // ============================================================

    private void stopServer() {

        synchronized (SERVER_LOCK) {

            running = false;

            try {

                if (serverSocket != null) {
                    serverSocket.close();
                }

            } catch (Exception ignored) {
            }

            serverSocket = null;
            clients.clear();

            log("SERVER STOPPED");

            try {

                if (Build.VERSION.SDK_INT >= 24) {

                    stopForeground(
                            STOP_FOREGROUND_REMOVE
                    );

                } else {

                    stopForeground(true);
                }

            } catch (Exception ignored) {
            }
        }
    }

    @Override
    public void onDestroy() {

        stopServer();

        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }


    // ============================================================
    // REQUEST MODEL
    // ============================================================

    static class Request {

        String method;

        String path;

        Map<String, String> headers =
                new HashMap<>();

        byte[] body;
    }
}
