package com.salam.androidwebserver;

import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Salam Web Server - Public HTTPS Access.
 *
 * Uses Cloudflare Quick Tunnel:
 * cloudflared tunnel --url http://127.0.0.1:<port> --no-autoupdate
 *
 * This creates a temporary https://*.trycloudflare.com URL.
 * A permanent custom domain requires a named Cloudflare Tunnel.
 */
public final class PublicAccessManager {
    public interface Listener {
        void onState(String state, String publicUrl);
        void onError(String message);
    }

    private static final String VERSION = "2026.9.1";
    private static final String ARM64_URL =
            "https://github.com/cloudflare/cloudflared/releases/download/" +
            VERSION + "/cloudflared-linux-arm64";

    private static final Pattern URL_PATTERN = Pattern.compile(
            "https://[a-zA-Z0-9.-]+\\.trycloudflare\\.com/?"
    );

    private static volatile Process process;
    private static volatile String publicUrl = "";
    private static volatile String state = "OFFLINE";
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private PublicAccessManager() {}

    public static boolean isRunning() {
        Process p = process;
        return p != null && p.isAlive();
    }

    public static String getPublicUrl() {
        return publicUrl;
    }

    public static String getState() {
        return state;
    }

    public static void start(Context context, int port, Listener listener) {
        final Context app = context.getApplicationContext();

        if (isRunning()) {
            post(listener, state, publicUrl);
            return;
        }

        boolean arm64 = false;
        for (String abi : Build.SUPPORTED_ABIS) {
            if ("arm64-v8a".equals(abi) || "aarch64".equalsIgnoreCase(abi)) {
                arm64 = true;
                break;
            }
        }

        if (!arm64) {
            error(listener, "This public tunnel build currently supports ARM64 Android only.");
            return;
        }

        state = "DOWNLOADING";
        post(listener, state, "");

        new Thread(() -> {
            try {
                File bin = ensureBinary(app);
                if (bin == null) {
                    error(listener, "cloudflared binary could not be prepared.");
                    return;
                }

                state = "CONNECTING";
                post(listener, state, "");

                ProcessBuilder pb = new ProcessBuilder(
                        bin.getAbsolutePath(),
                        "tunnel",
                        "--url", "http://127.0.0.1:" + port,
                        "--no-autoupdate"
                );
                pb.redirectErrorStream(true);
                process = pb.start();

                try (BufferedReader br = new BufferedReader(
                        new InputStreamReader(process.getInputStream(), "UTF-8"))) {

                    String line;
                    while ((line = br.readLine()) != null) {
                        Matcher m = URL_PATTERN.matcher(line);
                        if (m.find()) {
                            publicUrl = m.group().replaceAll("/$", "");
                            state = "ONLINE";
                            post(listener, state, publicUrl);
                        }
                    }
                }

                int exit = process.waitFor();
                process = null;

                if (!"ERROR".equals(state)) {
                    state = "OFFLINE";
                    post(listener, state, publicUrl);
                }

                if (exit != 0 && publicUrl.isEmpty()) {
                    error(listener, "cloudflared stopped with exit code " + exit);
                }
            } catch (Exception e) {
                process = null;
                state = "ERROR";
                error(listener, "Public access failed: " + e.getMessage());
            }
        }, "Salam-Public-Tunnel").start();
    }

    private static File ensureBinary(Context context) throws Exception {
        File dir = new File(context.getFilesDir(), "public_tunnel");
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("Cannot create tunnel directory");
        }

        File bin = new File(dir, "cloudflared-arm64");

        if (!bin.exists() || bin.length() < 1024 * 1024) {
            download(ARM64_URL, bin);
        }

        if (!bin.setExecutable(true, true)) {
            try {
                Process p = Runtime.getRuntime().exec(
                        new String[]{"chmod", "700", bin.getAbsolutePath()}
                );
                p.waitFor();
            } catch (Exception ignored) {}
        }

        if (!bin.canExecute()) {
            throw new IOException("Android did not allow the tunnel binary to execute");
        }

        return bin;
    }

    private static void download(String url, File out) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(20000);
        c.setReadTimeout(60000);
        c.setInstanceFollowRedirects(true);
        c.setRequestProperty("User-Agent", "Salam-Web-Server/10.0");

        int code = c.getResponseCode();
        if (code < 200 || code >= 400) {
            throw new IOException("Download HTTP " + code);
        }

        File tmp = new File(out.getParentFile(), out.getName() + ".tmp");

        try (InputStream in = c.getInputStream();
             OutputStream os = new BufferedOutputStream(new FileOutputStream(tmp))) {

            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) != -1) {
                os.write(buf, 0, n);
            }
        } finally {
            c.disconnect();
        }

        if (out.exists()) out.delete();

        if (!tmp.renameTo(out)) {
            throw new IOException("Cannot save cloudflared binary");
        }
    }

    public static void stop(Listener listener) {
        Process p = process;
        process = null;
        publicUrl = "";
        state = "OFFLINE";

        if (p != null) {
            try { p.destroy(); } catch (Exception ignored) {}
            try {
                if (p.isAlive()) p.destroyForcibly();
            } catch (Exception ignored) {}
        }

        post(listener, state, "");
    }

    private static void post(Listener l, String s, String u) {
        if (l == null) return;
        MAIN.post(() -> l.onState(s, u));
    }

    private static void error(Listener l, String msg) {
        state = "ERROR";
        MAIN.post(() -> {
            if (l != null) l.onError(msg);
        });
    }
}
