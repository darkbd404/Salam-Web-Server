package com.salam.androidwebserver;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Salam Web Server v10 - Public Internet Access.
 *
 * Random URL: Cloudflare Quick Tunnel.
 * Custom Domain: remotely-managed Cloudflare Tunnel token.
 *
 * The tunnel process runs inside the Android app's private files directory.
 * Android/ cloudflared compatibility is device/network dependent; failures are
 * surfaced as ERROR rather than being shown as a false ONLINE state.
 */
public final class PublicAccessManager {
    private static final String VERSION = "2026.9.1";
    private static final String BINARY_URL =
            "https://github.com/cloudflare/cloudflared/releases/download/" +
            VERSION + "/cloudflared-linux-arm64";
    private static final String BINARY_SHA256 =
            "98aca3173f73248fad6180fc75dade2d186a6e54fa807e088108cb4345de8efe";

    private static final Pattern QUICK_URL = Pattern.compile(
            "https://[A-Za-z0-9.-]+\\.trycloudflare\\.com/?");

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static volatile Process process;
    private static volatile String state = "OFFLINE";
    private static volatile String publicUrl = "";
    private static volatile String lastError = "";
    private static TextView homeState;
    private static TextView homeUrl;
    private static TextView homeInfo;
    private static Button homeStart;

    private PublicAccessManager() {}

    public static boolean isRunning() {
        Process p = process;
        return p != null && p.isAlive();
    }

    public static String getState() { return state; }
    public static String getPublicUrl() { return publicUrl; }

    public static void addHomeCard(MainActivity a, LinearLayout parent) {
        TextView title = a.text("🌐  PUBLIC INTERNET ACCESS", 12, MainActivity.CYAN);
        title.setTypeface(null, 1);
        a.add(parent, title, -1, 35);

        LinearLayout card = a.card();
        card.setPadding(a.dp(15), a.dp(14), a.dp(15), a.dp(14));

        TextView head = a.text("🌍  Public HTTPS Tunnel", 17, MainActivity.WHITE);
        head.setTypeface(null, 1);
        card.addView(head, new LinearLayout.LayoutParams(-1, a.dp(30)));

        TextView stateView = a.text("🔴  OFFLINE", 13, Color.rgb(255, 75, 95));
        stateView.setTypeface(null, 1);
        stateView.setGravity(Gravity.CENTER_VERTICAL);
        card.addView(stateView, new LinearLayout.LayoutParams(-1, a.dp(28)));
        homeState = stateView;

        TextView urlView = a.text("No public URL", 12, MainActivity.MUTED);
        urlView.setGravity(Gravity.CENTER_VERTICAL);
        urlView.setTextIsSelectable(true);
        card.addView(urlView, new LinearLayout.LayoutParams(-1, a.dp(42)));
        homeUrl = urlView;

        TextView info = a.text("Random URL creates a temporary public HTTPS address.\nCustom Domain uses your Cloudflare Tunnel token.", 10, MainActivity.MUTED);
        card.addView(info, new LinearLayout.LayoutParams(-1, a.dp(44)));
        homeInfo = info;

        LinearLayout row = new LinearLayout(a);
        Button random = a.blueButton("🌈  START RANDOM");
        Button custom = a.blueButton("🔗  CUSTOM DOMAIN");
        Button copy = a.blueButton("📋  COPY");
        random.setOnClickListener(v -> startRandom(a));
        custom.setOnClickListener(v -> customDialog(a));
        copy.setOnClickListener(v -> {
            if (publicUrl.isEmpty()) toast(a, "No public URL yet");
            else copyText(a, publicUrl);
        });
        row.addView(random, new LinearLayout.LayoutParams(0, a.dp(48), 1));
        row.addView(custom, new LinearLayout.LayoutParams(0, a.dp(48), 1));
        row.addView(copy, new LinearLayout.LayoutParams(0, a.dp(48), 0.75f));
        card.addView(row);
        parent.addView(card);
        homeStart = random;
        refreshUi();
    }

    public static void addSettingsItem(MainActivity a, LinearLayout parent) {
        a.section("🌐 PUBLIC ACCESS");
        a.setting("🌍", "Public Internet Access", "Random URL • Custom Domain • HTTPS tunnel", () -> settingsDialog(a));
    }

    private static void startRandom(MainActivity a) {
        if (!WebServerService.running) {
            toast(a, "Start the local server first");
            return;
        }
        stopProcess(false);
        publicUrl = "";
        lastError = "";
        state = "DOWNLOADING";
        refreshUi();
        final int port = a.pref.getInt("port", 8080);
        new Thread(() -> {
            try {
                File bin = ensureBinary(a);
                runProcess(a, new String[]{bin.getAbsolutePath(), "tunnel", "--url",
                        "http://127.0.0.1:" + port, "--no-autoupdate"}, null);
            } catch (Exception e) {
                fail(a, "Random tunnel failed: " + safe(e.getMessage()));
            }
        }, "Salam-Public-Random").start();
    }

    private static void customDialog(MainActivity a) {
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(a.dp(8), 0, a.dp(8), 0);
        EditText host = a.input(a.pref.getString("publicHost", ""));
        host.setHint("server.example.com");
        EditText token = a.input("");
        token.setHint("Cloudflare Tunnel Token");
        token.setInputType(0x00000081); // password
        box.addView(host);
        box.addView(token);
        new AlertDialog.Builder(a)
                .setTitle("🔗 CUSTOM DOMAIN")
                .setMessage("Create/configure the named tunnel in Cloudflare first. Then paste its tunnel token here and enter the published hostname.")
                .setView(box)
                .setNegativeButton("CANCEL", null)
                .setPositiveButton("START CUSTOM", (d, w) -> {
                    String h = normalizeHost(host.getText().toString());
                    String t = token.getText().toString().trim();
                    if (h.isEmpty() || t.isEmpty()) {
                        toast(a, "Hostname and token are required");
                        return;
                    }
                    a.pref.edit().putString("publicHost", h).apply();
                    startCustom(a, h, t);
                }).show();
    }

    private static void startCustom(MainActivity a, String host, String token) {
        if (!WebServerService.running) {
            toast(a, "Start the local server first");
            return;
        }
        stopProcess(false);
        publicUrl = "https://" + host;
        lastError = "";
        state = "DOWNLOADING";
        refreshUi();
        new Thread(() -> {
            try {
                File bin = ensureBinary(a);
                // Token is passed via the environment, not as a command-line argument.
                runProcess(a, new String[]{bin.getAbsolutePath(), "tunnel", "--no-autoupdate", "run"}, token);
            } catch (Exception e) {
                fail(a, "Custom tunnel failed: " + safe(e.getMessage()));
            }
        }, "Salam-Public-Custom").start();
    }

    private static void settingsDialog(MainActivity a) {
        String host = a.pref.getString("publicHost", "");
        String msg = "State: " + state + "\n\n" +
                (publicUrl.isEmpty() ? "No public URL" : publicUrl) +
                (lastError.isEmpty() ? "" : "\n\nError: " + lastError);
        new AlertDialog.Builder(a)
                .setTitle("🌐 PUBLIC ACCESS")
                .setMessage(msg + "\n\nSaved hostname: " + (host.isEmpty() ? "Not configured" : host))
                .setNegativeButton("STOP", (d, w) -> stop(a))
                .setNeutralButton("CUSTOM DOMAIN", (d, w) -> customDialog(a))
                .setPositiveButton("START RANDOM", (d, w) -> startRandom(a))
                .show();
    }

    public static void stop(MainActivity a) {
        stopProcess(true);
        refreshUi();
        toast(a, "Public tunnel stopped");
    }

    private static void runProcess(MainActivity a, String[] command, String token) throws Exception {
        File dir = new File(a.getFilesDir(), "public_tunnel");
        File home = new File(dir, "home");
        File tmp = new File(dir, "tmp");
        home.mkdirs();
        tmp.mkdirs();

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        pb.directory(dir);
        pb.environment().put("HOME", home.getAbsolutePath());
        pb.environment().put("TMPDIR", tmp.getAbsolutePath());
        if (token != null) pb.environment().put("TUNNEL_TOKEN", token);

        process = pb.start();
        state = "CONNECTING";
        refreshUi();

        try (BufferedReader br = new BufferedReader(new InputStreamReader(process.getInputStream(), "UTF-8"))) {
            String line;
            while ((line = br.readLine()) != null) {
                Matcher m = QUICK_URL.matcher(line);
                if (m.find()) {
                    publicUrl = m.group().replaceAll("/$", "");
                    state = "ONLINE";
                    refreshUi();
                }
                String low = line.toLowerCase(Locale.US);
                if (low.contains("lookup") && low.contains("dns")) {
                    lastError = "Android DNS lookup failed. Try another network/DNS.";
                }
                if (low.contains("failed") || low.contains("fatal") || low.contains("error")) {
                    lastError = line.trim();
                }
            }
        }
        int exit = process.waitFor();
        process = null;
        if (exit == 0 && !publicUrl.isEmpty()) state = "OFFLINE";
        else if (!"ONLINE".equals(state)) state = "ERROR";
        if (exit != 0 && lastError.isEmpty()) lastError = "cloudflared exited with code " + exit;
        refreshUi();
    }

    private static File ensureBinary(Context c) throws Exception {
        boolean arm64 = false;
        for (String abi : Build.SUPPORTED_ABIS) {
            if ("arm64-v8a".equals(abi) || "aarch64".equalsIgnoreCase(abi)) { arm64 = true; break; }
        }
        if (!arm64) throw new IllegalStateException("This module currently supports ARM64 Android only");

        File dir = new File(c.getFilesDir(), "public_tunnel");
        if (!dir.exists() && !dir.mkdirs()) throw new IllegalStateException("Cannot create tunnel directory");
        File bin = new File(dir, "cloudflared-arm64");
        if (!bin.exists() || !verifySha256(bin)) download(BINARY_URL, bin);
        if (!verifySha256(bin)) throw new IllegalStateException("cloudflared checksum verification failed");
        if (!bin.setExecutable(true, true)) {
            try { Runtime.getRuntime().exec(new String[]{"chmod", "700", bin.getAbsolutePath()}).waitFor(); }
            catch (Exception ignored) {}
        }
        if (!bin.canExecute()) throw new IllegalStateException("Android did not allow cloudflared to execute");
        return bin;
    }

    private static void download(String url, File out) throws Exception {
        HttpURLConnection c = (HttpURLConnection)new URL(url).openConnection();
        c.setConnectTimeout(20000); c.setReadTimeout(120000); c.setInstanceFollowRedirects(true);
        c.setRequestProperty("User-Agent", "Salam-Web-Server/10.0");
        int code = c.getResponseCode();
        if (code < 200 || code >= 400) throw new IllegalStateException("Download HTTP " + code);
        File tmp = new File(out.getParentFile(), out.getName() + ".tmp");
        try (InputStream in = new BufferedInputStream(c.getInputStream()); OutputStream os = new BufferedOutputStream(new FileOutputStream(tmp))) {
            byte[] buf = new byte[32768]; int n;
            while ((n = in.read(buf)) != -1) os.write(buf, 0, n);
        } finally { c.disconnect(); }
        if (out.exists()) out.delete();
        if (!tmp.renameTo(out)) throw new IllegalStateException("Cannot save cloudflared binary");
    }

    private static boolean verifySha256(File f) {
        if (!f.exists() || f.length() < 1000000) return false;
        try (InputStream in = new BufferedInputStream(new FileInputStream(f))) {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] b = new byte[32768]; int n;
            while ((n = in.read(b)) != -1) md.update(b, 0, n);
            StringBuilder s = new StringBuilder();
            for (byte x : md.digest()) s.append(String.format(Locale.US, "%02x", x));
            return BINARY_SHA256.equalsIgnoreCase(s.toString());
        } catch (Exception e) { return false; }
    }

    private static String normalizeHost(String s) {
        s = s.trim();
        s = s.replaceFirst("^https?://", "");
        int slash = s.indexOf('/'); if (slash >= 0) s = s.substring(0, slash);
        return s.toLowerCase(Locale.US);
    }

    private static void stopProcess(boolean clearUrl) {
        Process p = process;
        process = null;
        if (p != null) {
            try { p.destroy(); } catch (Exception ignored) {}
            try { if (p.isAlive()) p.destroyForcibly(); } catch (Exception ignored) {}
        }
        state = "OFFLINE";
        lastError = "";
        if (clearUrl) publicUrl = "";
    }

    private static void fail(MainActivity a, String msg) {
        process = null;
        state = "ERROR";
        lastError = msg;
        refreshUi();
        MAIN.post(() -> Toast.makeText(a, msg, Toast.LENGTH_LONG).show());
    }

    private static void refreshUi() {
        MAIN.post(() -> {
            if (homeState != null) {
                String s = state;
                homeState.setText(("ONLINE".equals(s) ? "🟢  ONLINE" :
                        "CONNECTING".equals(s) || "DOWNLOADING".equals(s) ? "🟡  " + s :
                        "ERROR".equals(s) ? "🔴  ERROR" : "🔴  OFFLINE"));
                homeState.setTextColor("ONLINE".equals(s) ? MainActivity.GREEN :
                        "ERROR".equals(s) ? Color.rgb(255,70,90) : MainActivity.MUTED);
            }
            if (homeUrl != null) homeUrl.setText(publicUrl.isEmpty() ? "No public URL" : publicUrl);
            if (homeInfo != null) homeInfo.setText(lastError.isEmpty() ?
                    "Random URL creates a temporary public HTTPS address.\nCustom Domain uses your Cloudflare Tunnel token." :
                    "⚠ " + lastError);
            if (homeStart != null) homeStart.setText(isRunning() ? "⏹  STOP" : "🌈  START RANDOM");
        });
    }

    private static void copyText(Context c, String s) {
        android.content.ClipboardManager cm = (android.content.ClipboardManager)c.getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(android.content.ClipData.newPlainText("Public URL", s));
        toast(c, "Public URL copied");
    }

    private static void toast(Context c, String s) { MAIN.post(() -> Toast.makeText(c, s, Toast.LENGTH_SHORT).show()); }
}
