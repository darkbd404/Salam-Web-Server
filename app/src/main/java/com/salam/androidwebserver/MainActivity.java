package com.salam.androidserver;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.view.Gravity;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {

    private static final int GREEN = Color.rgb(25, 230, 165);
    private static final int PURPLE = Color.rgb(108, 99, 255);
    private static final int BG = Color.rgb(9, 12, 20);
    private static final int CARD = Color.rgb(21, 26, 39);

    private LinearLayout content;
    private TextView status;
    private TextView url;
    private TextView stats;
    private SharedPreferences prefs;
    private final Handler handler = new Handler();
    private boolean screenReady = false;

    private int dp(int n) {
        return (int) (n * getResources().getDisplayMetrics().density + 0.5f);
    }

    private GradientDrawable box(int color, int radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radius));
        return g;
    }

    private TextView text(String value, float size, int color) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER_VERTICAL);
        return t;
    }

    private Button button(String value, int color) {
        Button b = new Button(this);
        b.setText(value);
        b.setTextColor(Color.WHITE);
        b.setTextSize(13);
        b.setAllCaps(false);
        b.setBackground(box(color, 18));
        return b;
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);

        try {
            prefs = getSharedPreferences("server", Context.MODE_PRIVATE);
            buildScreen();
            screenReady = true;
            safeRefresh();
            startRefreshLoop();
        } catch (Throwable e) {
            screenReady = false;
            showStartupError(e);
        }
    }

    private void startRefreshLoop() {
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!isFinishing() && screenReady) {
                    safeRefresh();
                    handler.postDelayed(this, 1000);
                }
            }
        }, 1000);
    }

    private void safeRefresh() {
        try {
            refresh();
        } catch (Throwable e) {
            // Keep the main Activity alive even if a metric/network value fails.
            if (stats != null) {
                stats.setText("📊 Live metrics temporarily unavailable");
            }
        }
    }

    private void showStartupError(Throwable e) {
        String msg = e.getClass().getSimpleName();
        if (e.getMessage() != null && !e.getMessage().trim().isEmpty()) {
            msg += "\n\n" + e.getMessage();
        }

        new AlertDialog.Builder(this)
                .setTitle("Salam SIP Server")
                .setMessage("App startup error:\n\n" + msg)
                .setPositiveButton("CLOSE", null)
                .show();
    }

    private void buildScreen() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);

        LinearLayout head = new LinearLayout(this);
        head.setPadding(dp(16), dp(12), dp(12), dp(10));
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setBackground(box(Color.rgb(15, 18, 30), 0));

        TextView logo = text("◈", 32, GREEN);
        logo.setGravity(Gravity.CENTER);
        head.addView(logo, new LinearLayout.LayoutParams(dp(48), dp(54)));

        LinearLayout titleBox = new LinearLayout(this);
        titleBox.setOrientation(LinearLayout.VERTICAL);
        titleBox.addView(text("Salam SIP Server", 21, Color.WHITE));
        titleBox.addView(text("VERSION 10 • LOCAL HTTP SERVER", 10, Color.LTGRAY));
        head.addView(titleBox, new LinearLayout.LayoutParams(0, dp(55), 1));

        Button settings = button("⚙ Settings", Color.rgb(50, 55, 75));
        settings.setOnClickListener(v -> settings());
        head.addView(settings, new LinearLayout.LayoutParams(dp(105), dp(48)));
        root.addView(head, new LinearLayout.LayoutParams(-1, dp(76)));

        ScrollView scroll = new ScrollView(this);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(14), dp(12), dp(14), dp(30));
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);

        LinearLayout hero = card();

        status = text("● OFFLINE", 15, Color.RED);
        hero.addView(status);

        url = text("Loading server URL...", 13, Color.WHITE);
        url.setPadding(dp(12), dp(9), dp(12), dp(9));
        url.setBackground(box(Color.rgb(10, 14, 23), 12));
        hero.addView(url);

        LinearLayout actions = new LinearLayout(this);

        Button start = button("▶ START", Color.rgb(22, 130, 85));
        Button stop = button("■ STOP", Color.rgb(150, 52, 72));
        Button open = button("🌐 OPEN", PURPLE);

        start.setOnClickListener(v -> service("START"));
        stop.setOnClickListener(v -> service("STOP"));
        open.setOnClickListener(v -> openAdmin());

        actions.addView(start, new LinearLayout.LayoutParams(0, dp(50), 1));
        actions.addView(stop, new LinearLayout.LayoutParams(0, dp(50), 1));
        actions.addView(open, new LinearLayout.LayoutParams(0, dp(50), 1));
        hero.addView(actions);

        content.addView(hero);

        stats = text("📊 Loading live metrics...", 14, Color.WHITE);
        stats.setPadding(dp(14), dp(14), dp(14), dp(14));
        stats.setBackground(box(CARD, 18));
        content.addView(stats, new LinearLayout.LayoutParams(-1, dp(92)));

        section("🧰 SERVER CONTROL");
        row("📊", "Dashboard", "Live browser dashboard with auto refresh", this::openAdmin);
        row("📁", "Advanced File Manager", "Upload, download, edit, rename, copy, move, delete", this::openAdmin);
        row("📦", "ZIP Manager", "Upload/extract ZIP safely", this::openAdmin);
        row("📝", "File Editor", "Edit HTML/CSS/JS/JSON/text files", this::openAdmin);
        row("📜", "Request & Access History", "Live logs and recent clients", this::openAdmin);

        section("🛡️ SECURITY");
        row("🔐", "Login & Password", "Basic web authentication", this::settings);
        row("🟢", "IP Allowlist", "Permit only selected addresses", this::settings);
        row("🔴", "IP Blocklist", "Block addresses and unblock later", this::settings);
        row("🚦", "Rate Limit", "Requests per IP per minute", this::settings);
        row("👥", "Client Limit", "Maximum concurrent connections", this::settings);

        section("🎨 THEMES");
        row("🌈", "Theme Engine", "Midnight, Ocean, Violet, Emerald, Sunset, Neon", this::theme);
        row("✨", "Animations", "Animated cards and live dashboard", this::theme);
        row("🔆", "Server Logo / Icon", "App identity and server branding", this::about);

        section("🌐 NETWORK");
        row("📡", "Network Interfaces", "Wi-Fi/LAN interface information", this::network);
        row("🔗", "Custom Server URL", "Display alias/hostname", this::settings);
        row("🔳", "QR Server Sharing", "Open QR sharing instructions", this::qrInfo);

        section("⚙ SYSTEM");
        row("🔔", "Background Server", "Foreground service + status notification", this::about);
        row("💾", "CPU / RAM / Storage", "Live device/server metrics", this::openAdmin);
        row("👨‍💻", "Developer", "Abdus Salam • 09696590864 • salam230864@gmail.com", this::about);
        row("💬", "Messenger", "Open developer Messenger", this::messenger);

        section("🚀 FUTURE MODULE CENTER");
        String[] future = {
                "Server profiles", "Multiple web roots", "Redirect rules", "MIME manager",
                "Custom 403/404", "Maintenance mode", "Security headers", "CORS control",
                "Cache control", "Compression", "Configuration backup/restore",
                "Traffic statistics", "Health check", "Heartbeat API",
                "Plugin-ready modules", "API testing tools"
        };

        for (String item : future) {
            row("🧩", item, "Module slot reserved in Version 10 architecture", this::about);
        }

        AlphaAnimation anim = new AlphaAnimation(0.45f, 1f);
        anim.setDuration(1000);
        anim.setRepeatMode(AlphaAnimation.REVERSE);
        anim.setRepeatCount(AlphaAnimation.INFINITE);
        logo.startAnimation(anim);
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(15), dp(14), dp(15), dp(14));
        c.setBackground(box(CARD, 20));

        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.setMargins(0, dp(5), 0, dp(8));
        c.setLayoutParams(p);
        return c;
    }

    private void section(String value) {
        TextView t = text(value, 12, Color.rgb(165, 172, 195));
        t.setTypeface(null, 1);
        t.setPadding(dp(5), dp(13), dp(5), dp(5));
        content.addView(t);
    }

    private void row(String icon, String title, String desc, final Runnable action) {
        LinearLayout c = card();
        c.setOrientation(LinearLayout.HORIZONTAL);

        TextView i = text(icon, 23, Color.WHITE);
        i.setGravity(Gravity.CENTER);
        c.addView(i, new LinearLayout.LayoutParams(dp(46), dp(58)));

        LinearLayout x = new LinearLayout(this);
        x.setOrientation(LinearLayout.VERTICAL);
        x.addView(text(title, 14, Color.WHITE));
        x.addView(text(desc, 11, Color.LTGRAY));
        c.addView(x, new LinearLayout.LayoutParams(0, dp(58), 1));

        TextView go = text("›", 28, GREEN);
        go.setGravity(Gravity.CENTER);
        c.addView(go, new LinearLayout.LayoutParams(dp(28), dp(58)));

        c.setOnClickListener(v -> {
            try {
                action.run();
            } catch (Throwable e) {
                Toast.makeText(this, "Feature error: " + e.getClass().getSimpleName(),
                        Toast.LENGTH_SHORT).show();
            }
        });

        content.addView(c);
    }

    private void service(String action) {
        try {
            Intent i = new Intent(this, WebServerService.class);
            i.setAction(action);

            if (Build.VERSION.SDK_INT >= 26 && "START".equals(action)) {
                startForegroundService(i);
            } else {
                startService(i);
            }

            handler.postDelayed(this::safeRefresh, 300);
        } catch (Throwable e) {
            Toast.makeText(this, "Server error: " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
        }
    }

    private void refresh() {
        boolean r = WebServerService.isRunning();

        status.setText(r ? "● SERVER ONLINE" : "● SERVER OFFLINE");
        status.setTextColor(r ? GREEN : Color.RED);

        String current;
        try {
            current = WebServerService.currentUrl(this);
        } catch (Throwable e) {
            current = "http://0.0.0.0:8080";
        }

        url.setText(current);

        String memory;
        try {
            memory = WebServerService.memoryText(this);
        } catch (Throwable e) {
            memory = "N/A";
        }

        String uptime;
        try {
            uptime = WebServerService.uptime();
        } catch (Throwable e) {
            uptime = "0s";
        }

        stats.setText(
                "📈 Requests: " + WebServerService.getRequestCount() +
                "    👥 Clients: " + WebServerService.getClientCount() +
                "\n⏱ Uptime: " + uptime +
                "    💾 " + memory
        );
    }

    private void openAdmin() {
        try {
            if (!WebServerService.isRunning()) {
                Toast.makeText(this, "Start server first", Toast.LENGTH_SHORT).show();
                return;
            }

            startActivity(new Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse(WebServerService.currentUrl(this) + "/__admin")
            ));
        } catch (Throwable e) {
            Toast.makeText(this, "Cannot open dashboard: " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
        }
    }

    private void settings() {
        final LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(18), dp(8), dp(18), 0);

        EditText port = edit("Port", String.valueOf(prefs.getInt("port", 8080)));
        EditText pass = edit("Web password (blank = off)", prefs.getString("password", ""));
        EditText allow = edit("Allowlist IPs (comma separated)", prefs.getString("allowIps", ""));
        EditText block = edit("Blocklist IPs (comma separated)", prefs.getString("blockIps", ""));
        EditText rate = edit("Rate limit per IP/minute", String.valueOf(prefs.getInt("rate", 120)));
        EditText max = edit("Max clients", String.valueOf(prefs.getInt("maxClients", 32)));

        for (EditText q : new EditText[]{port, pass, allow, block, rate, max}) {
            q.setTextColor(Color.WHITE);
            q.setHintTextColor(Color.GRAY);
            l.addView(q);
        }

        android.widget.Switch only = new android.widget.Switch(this);
        only.setText("🟢 Enforce allowlist");
        only.setTextColor(Color.WHITE);
        only.setChecked(prefs.getBoolean("allowOnly", false));
        l.addView(only);

        new AlertDialog.Builder(this)
                .setTitle("⚙ Advanced Server Settings")
                .setView(l)
                .setPositiveButton("SAVE", (d, w) -> {
                    try {
                        prefs.edit()
                                .putInt("port", Integer.parseInt(port.getText().toString().trim()))
                                .putString("password", pass.getText().toString())
                                .putString("allowIps", allow.getText().toString())
                                .putString("blockIps", block.getText().toString())
                                .putBoolean("allowOnly", only.isChecked())
                                .putInt("rate", Math.max(1, Integer.parseInt(rate.getText().toString().trim())))
                                .putInt("maxClients", Math.max(1, Integer.parseInt(max.getText().toString().trim())))
                                .apply();

                        Toast.makeText(this,
                                "Saved.\nRestart server to apply port changes.",
                                Toast.LENGTH_LONG).show();
                    } catch (Throwable e) {
                        Toast.makeText(this, "Invalid settings", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("CANCEL", null)
                .show();
    }

    private EditText edit(String hint, String value) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setText(value);
        e.setSingleLine(false);
        return e;
    }

    private void theme() {
        String[] themes = {
                "🌑 Midnight", "🌊 Ocean", "💜 Violet",
                "💚 Emerald", "🌅 Sunset", "⚡ Neon"
        };

        new AlertDialog.Builder(this)
                .setTitle("🌈 Theme Engine")
                .setItems(themes, (d, which) ->
                        prefs.edit().putString("theme", themes[which]).apply())
                .show();
    }

    private void network() {
        try {
            new AlertDialog.Builder(this)
                    .setTitle("🌐 Network Interfaces")
                    .setMessage(WebServerService.networkInfo(this))
                    .setPositiveButton("OK", null)
                    .show();
        } catch (Throwable e) {
            Toast.makeText(this, "Network info unavailable",
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void qrInfo() {
        String u;
        try {
            u = WebServerService.currentUrl(this);
        } catch (Throwable e) {
            u = "Unavailable";
        }

        new AlertDialog.Builder(this)
                .setTitle("📱 QR Server Sharing")
                .setMessage(
                        "Current server URL:\n\n" + u +
                        "\n\nOpen the server URL in a browser, or use your phone's QR sharing feature."
                )
                .setPositiveButton("OK", null)
                .show();
    }

    private void messenger() {
        try {
            startActivity(new Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://m.me/Salam.864")
            ));
        } catch (Throwable e) {
            Toast.makeText(this, "Messenger unavailable",
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void about() {
        new AlertDialog.Builder(this)
                .setTitle("👨‍💻 Salam SIP Server v10")
                .setMessage(
                        "Local HTTP server + browser control center\n\n" +
                        "Developer: Abdus Salam\n" +
                        "Phone: 09696590864\n" +
                        "Email: salam230864@gmail.com\n" +
                        "Messenger: m.me/Salam.864\n\n" +
                        "Version 10"
                )
                .setPositiveButton("OK", null)
                .show();
    }

    @Override
    protected void onDestroy() {
        screenReady = false;
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
