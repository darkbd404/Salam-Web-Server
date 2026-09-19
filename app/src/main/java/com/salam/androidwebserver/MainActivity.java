package com.salam.androidwebserver;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.net.Uri;
import android.os.*;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.*;
import android.widget.*;
import com.google.zxing.BarcodeFormat;
import com.journeyapps.barcodescanner.BarcodeEncoder;

import java.io.*;
import java.util.*;

public class MainActivity extends Activity {
    static final int BG = Color.rgb(2, 9, 18), PANEL = Color.rgb(6, 23, 41), PANEL2 = Color.rgb(10, 34, 57);
    static final int WHITE = Color.WHITE, MUTED = Color.rgb(145, 174, 205), CYAN = Color.rgb(28, 220, 255), BLUE = Color.rgb(74, 82, 255);
    static final int GREEN = Color.rgb(35, 239, 132), RED = Color.rgb(255, 57, 82), PURPLE = Color.rgb(188, 75, 255), YELLOW = Color.rgb(255, 205, 45), ORANGE = Color.rgb(255, 135, 35);
    static final int[] LED = {GREEN, CYAN, PURPLE, YELLOW, WHITE, ORANGE, RED};

    Handler h = new Handler(Looper.getMainLooper());
    SharedPreferences p;
    LinearLayout body, bottom;
    TextView title;
    int page = 0, theme = 0;
    String cwd = "/";
    long sequence = 0;
    long lastReq = 0, lastReqAt = 0;
    int selectedToolCategory = 0; // 0=Diagnostics, 1=IP & Web, 2=Dev Tools, 3=Templates

    int accent() {
        return new int[]{CYAN, PURPLE, GREEN, ORANGE, Color.rgb(255, 72, 200), Color.rgb(80, 255, 235)}[theme % 6];
    }

    int accent2() {
        return new int[]{BLUE, Color.rgb(103, 55, 255), Color.rgb(0, 160, 130), RED, Color.rgb(255, 55, 125), Color.rgb(0, 115, 255)}[theme % 6];
    }

    int dp(float n) {
        return (int) (n * getResources().getDisplayMetrics().density + .5f);
    }

    TextView tv(String s, float z, int c) {
        TextView v = new TextView(this);
        v.setText(s);
        v.setTextSize(z);
        v.setTextColor(c);
        return v;
    }

    GradientDrawable bg(int c, float r) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(c);
        g.setCornerRadius(dp(r));
        return g;
    }

    GradientDrawable grad(float r) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{accent(), accent2()});
        g.setCornerRadius(dp(r));
        return g;
    }

    GradientDrawable cardBg() {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{PANEL, PANEL2});
        g.setCornerRadius(dp(20));
        g.setStroke(dp(1), Color.rgb(18, 67, 99));
        return g;
    }

    TextView button(String s) {
        TextView v = tv(s, 13, WHITE);
        v.setGravity(Gravity.CENTER);
        v.setTypeface(null, Typeface.BOLD);
        v.setBackground(grad(15));
        v.setPadding(dp(10), 0, dp(10), 0);
        return v;
    }

    ImageView iconView(int res, int size) {
        ImageView v = new ImageView(this);
        v.setImageResource(res);
        v.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        v.setPadding(dp(4), dp(4), dp(4), dp(4));
        return v;
    }

    void add(ViewGroup g, View v, int w, int h) {
        g.addView(v, new LinearLayout.LayoutParams(w, dp(h)));
    }

    @Override
    public void onCreate(Bundle b) {
        super.onCreate(b);
        p = getSharedPreferences("server", MODE_PRIVATE);
        theme = p.getInt("theme", 0);
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != 0) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 9);
        }
        buildShell();
    }

    void buildShell() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);

        // Top App Bar
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(12), dp(6), dp(10), dp(4));

        ImageView logo = iconView(R.drawable.ic_server, 38);
        add(top, logo, dp(46), 52);

        title = tv("Salam Web Server", 20, WHITE);
        title.setTypeface(null, Typeface.BOLD);
        top.addView(title, new LinearLayout.LayoutParams(0, -2, 1));

        TextView qrBtn = tv("📱", 24, accent());
        qrBtn.setGravity(Gravity.CENTER);
        qrBtn.setPadding(dp(6), 0, dp(6), 0);
        qrBtn.setOnClickListener(v -> showQrDialog(displayUrl()));
        top.addView(qrBtn, new LinearLayout.LayoutParams(dp(42), dp(48)));

        TextView menu = tv("☰", 28, accent());
        menu.setGravity(Gravity.CENTER);
        menu.setOnClickListener(v -> menu());
        top.addView(menu, new LinearLayout.LayoutParams(dp(42), dp(48)));
        root.addView(top);

        // Scrollable Body
        ScrollView sc = new ScrollView(this);
        sc.setFillViewport(true);
        body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(8), dp(4), dp(8), dp(16));
        sc.addView(body);
        root.addView(sc, new LinearLayout.LayoutParams(-1, 0, 1));

        // Bottom Navigation (5 Tabs)
        bottom = new LinearLayout(this);
        bottom.setGravity(Gravity.CENTER);
        bottom.setPadding(dp(2), dp(2), dp(2), dp(4));
        bottom.setBackground(bg(Color.rgb(4, 21, 38), 24));
        nav(R.drawable.ic_home, "Home", 0);
        nav(R.drawable.ic_dashboard, "Tools", 1);
        nav(R.drawable.ic_files, "Files", 2);
        nav(R.drawable.ic_monitor, "Traffic", 3);
        nav(R.drawable.ic_settings, "Settings", 4);
        root.addView(bottom, new LinearLayout.LayoutParams(-1, dp(72)));

        setContentView(root);
        show(0);
    }

    void nav(int res, String name, int pg) {
        LinearLayout x = new LinearLayout(this);
        x.setOrientation(LinearLayout.VERTICAL);
        x.setGravity(Gravity.CENTER);
        ImageView a = iconView(res, 24);
        a.setAlpha(pg == page ? 1f : .55f);
        TextView b = tv(name, 10, pg == page ? accent() : MUTED);
        b.setGravity(Gravity.CENTER);
        x.addView(a, new LinearLayout.LayoutParams(-1, dp(34)));
        x.addView(b, new LinearLayout.LayoutParams(-1, dp(18)));
        x.setOnClickListener(v -> show(pg));
        bottom.addView(x, new LinearLayout.LayoutParams(0, dp(62), 1));
    }

    void show(int pg) {
        page = pg;
        body.removeAllViews();
        if (pg == 0) home();
        else if (pg == 1) tools();
        else if (pg == 2) files();
        else if (pg == 3) logs();
        else settings();

        title.setText(pg == 0 ? "Salam Server" : pg == 1 ? "Network Tools" : pg == 2 ? "Web Files" : pg == 3 ? "Live Traffic" : "Settings");
        refreshNav();
    }

    void refreshNav() {
        for (int i = 0; i < 5; i++) {
            LinearLayout x = (LinearLayout) bottom.getChildAt(i);
            ((ImageView) x.getChildAt(0)).setAlpha(i == page ? 1f : .55f);
            ((TextView) x.getChildAt(1)).setTextColor(i == page ? accent() : MUTED);
        }
    }

    // ==========================================
    // TAB 0: HOME SCREEN
    // ==========================================
    void home() {
        // Main Server Control Panel
        ServerPanel sp = new ServerPanel(this);
        body.addView(sp, new LinearLayout.LayoutParams(-1, dp(310)));

        // Scope Switcher Card: Public Internet (Global) vs Local Wi-Fi (LAN)
        section("🌍  SERVER ACCESS SCOPE");
        LinearLayout modeCard = card();
        boolean isPublic = p.getBoolean("publicMode", true);

        LinearLayout modeHeader = new LinearLayout(this);
        modeHeader.setOrientation(LinearLayout.HORIZONTAL);
        modeHeader.setGravity(Gravity.CENTER_VERTICAL);
        TextView modeTitle = tv(isPublic ? "🌍 PUBLIC INTERNET TUNNEL (ACTIVE)" : "🏠 LOCAL WI-FI ONLY (PRIVATE LAN)", 14, isPublic ? GREEN : CYAN);
        modeTitle.setTypeface(null, Typeface.BOLD);
        modeHeader.addView(modeTitle, new LinearLayout.LayoutParams(0, -2, 1));

        TextView switchBtn = button(isPublic ? "SWITCH TO LOCAL" : "SWITCH TO PUBLIC");
        switchBtn.setTextSize(10);
        switchBtn.setOnClickListener(v -> {
            boolean nextMode = !isPublic;
            p.edit().putBoolean("publicMode", nextMode).apply();
            toast(nextMode ? "Switched to Public Internet Tunnel Mode" : "Switched to Local Wi-Fi Private Mode");
            if (WebServerService.running) {
                stopServer();
                h.postDelayed(this::startServer, 600);
            }
            show(0);
        });
        modeHeader.addView(switchBtn, new LinearLayout.LayoutParams(dp(130), dp(34)));
        modeCard.addView(modeHeader);

        TextView modeDesc = tv(isPublic ?
                "✓ Global Access: Friends on any mobile data or external Wi-Fi can open your URL!\n✓ Kill-Switch: Stopping the server in this app immediately cuts public access." :
                "✓ Private Access: Accessible only to devices connected to your same local Wi-Fi router.", 11, MUTED);
        modeDesc.setPadding(0, dp(8), 0, dp(6));
        modeCard.addView(modeDesc);

        // Tunnel Provider Selector
        if (isPublic) {
            String currentProv = p.getString("tunnelProvider", TunnelManager.PROVIDER_LOCALHOST_RUN);
            LinearLayout provRow = new LinearLayout(this);
            provRow.setOrientation(LinearLayout.HORIZONTAL);
            provRow.setGravity(Gravity.CENTER_VERTICAL);
            provRow.setPadding(0, dp(4), 0, 0);

            TextView provLabel = tv("Tunnel: " + currentProv, 11, accent());
            provLabel.setTypeface(null, Typeface.BOLD);
            provRow.addView(provLabel, new LinearLayout.LayoutParams(0, -2, 1));

            TextView changeProvBtn = tv("CHANGE", 11, CYAN);
            changeProvBtn.setPadding(dp(8), dp(4), dp(8), dp(4));
            changeProvBtn.setBackground(bg(Color.rgb(2, 20, 36), 10));
            changeProvBtn.setOnClickListener(v -> chooseTunnelProvider());
            provRow.addView(changeProvBtn);
            modeCard.addView(provRow);
        }
        body.addView(modeCard);

        // One-Click Website Templates
        section("🛍️  WEBSITE HOSTING TEMPLATES");
        LinearLayout tplCard = card();
        TextView tplTitle = tv("HOST AN E-COMMERCE STORE OR WEBSITE", 13, accent());
        tplTitle.setTypeface(null, Typeface.BOLD);
        tplCard.addView(tplTitle);

        TextView tplSub = tv("Deploy ready-made websites to your server root in 1 click:", 11, MUTED);
        tplSub.setPadding(0, dp(4), 0, dp(8));
        tplCard.addView(tplSub);

        LinearLayout tplRow = new LinearLayout(this);
        tplRow.setOrientation(LinearLayout.HORIZONTAL);

        TextView ecomBtn = button("🛍️ DEPLOY E-COMMERCE SHOP");
        ecomBtn.setTextSize(11);
        ecomBtn.setOnClickListener(v -> deployEcommerceDialog());
        LinearLayout.LayoutParams p1 = new LinearLayout.LayoutParams(0, dp(42), 1);
        p1.setMargins(0, 0, dp(4), 0);
        tplRow.addView(ecomBtn, p1);

        TextView defBtn = button("🌐 DEFAULT PORTAL");
        defBtn.setTextSize(11);
        defBtn.setBackground(bg(Color.rgb(10, 40, 65), 14));
        defBtn.setOnClickListener(v -> deployDefaultDialog());
        LinearLayout.LayoutParams p2 = new LinearLayout.LayoutParams(0, dp(42), 1);
        p2.setMargins(dp(4), 0, 0, 0);
        tplRow.addView(defBtn, p2);

        tplCard.addView(tplRow);
        body.addView(tplCard);

        // Quick Tools Row
        section("⚡  QUICK SUITE SHORTCUTS");
        LinearLayout r1 = new LinearLayout(this), r2 = new LinearLayout(this);
        addTile(r1, R.drawable.ic_dashboard, "Network Tools", "Ping • Ports • DNS • LAN", () -> show(1));
        addTile(r1, R.drawable.ic_files, "Web Files", "Upload • Edit • ZIP", () -> show(2));
        addTile(r2, R.drawable.ic_monitor, "Traffic & Flow", "Speeds • Bandwidth", () -> show(3));
        addTile(r2, R.drawable.ic_security, "IP Security", "Blocklist • Passwords", () -> security());
        body.addView(r1);
        body.addView(r2);

        // Live Network Flow Speed Card
        section("🌊  LIVE NETWORK FLOW");
        LinearLayout flowCard = card();
        LinearLayout flowHead = new LinearLayout(this);
        flowHead.setOrientation(LinearLayout.HORIZONTAL);
        flowHead.setGravity(Gravity.CENTER_VERTICAL);
        TextView flowTitle = tv("⚡ DATA TRANSFER & BANDWIDTH", 14, accent());
        flowTitle.setTypeface(null, Typeface.BOLD);
        flowHead.addView(flowTitle, new LinearLayout.LayoutParams(0, -2, 1));

        TextView flowDetailBtn = button("STREAMS");
        flowDetailBtn.setTextSize(10);
        flowDetailBtn.setOnClickListener(v -> networkFlowDialog());
        flowHead.addView(flowDetailBtn, new LinearLayout.LayoutParams(dp(76), dp(32)));
        flowCard.addView(flowHead);

        LinearLayout speedRow = new LinearLayout(this);
        speedRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout rxBox = new LinearLayout(this);
        rxBox.setOrientation(LinearLayout.VERTICAL);
        rxBox.setPadding(dp(10), dp(8), dp(10), dp(8));
        rxBox.setBackground(bg(Color.rgb(2, 20, 35), 16));
        TextView rxLab = tv("↓ INBOUND SPEED (RX)", 10, CYAN);
        rxLab.setTypeface(null, Typeface.BOLD);
        TextView rxVal = tv("0 B/s", 17, WHITE);
        rxVal.setTypeface(null, Typeface.BOLD);
        rxBox.addView(rxLab);
        rxBox.addView(rxVal);

        LinearLayout txBox = new LinearLayout(this);
        txBox.setOrientation(LinearLayout.VERTICAL);
        txBox.setPadding(dp(10), dp(8), dp(10), dp(8));
        txBox.setBackground(bg(Color.rgb(18, 10, 36), 16));
        TextView txLab = tv("↑ OUTBOUND SPEED (TX)", 10, PURPLE);
        txLab.setTypeface(null, Typeface.BOLD);
        TextView txVal = tv("0 B/s", 17, WHITE);
        txVal.setTypeface(null, Typeface.BOLD);
        txBox.addView(txLab);
        txBox.addView(txVal);

        LinearLayout.LayoutParams spL = new LinearLayout.LayoutParams(0, -2, 1);
        spL.setMargins(dp(2), dp(6), dp(2), dp(6));
        speedRow.addView(rxBox, spL);
        speedRow.addView(txBox, spL);
        flowCard.addView(speedRow);

        TextView flowSub = tv("📥 Inbound: 0 B  •  📤 Outbound: 0 B  •  ⚡ Peak: 0 B/s", 10, MUTED);
        flowSub.setPadding(dp(2), dp(4), dp(2), dp(4));
        flowCard.addView(flowSub);
        body.addView(flowCard);

        // Telemetry Grid
        section("📊  LIVE SERVER TELEMETRY");
        LinearLayout monitor = card();
        TextView hd = tv("📡 REAL-TIME SYSTEM TELEMETRY", 15, accent());
        hd.setTypeface(null, Typeface.BOLD);
        monitor.addView(hd);

        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        monitor.addView(grid);
        body.addView(monitor);

        TextView net = tv("", 11, MUTED);
        net.setPadding(dp(10), dp(4), dp(10), dp(8));
        body.addView(net);

        TextView[] cells = new TextView[10];
        String[] labels = {"🧠 CPU", "💾 RAM", "💽 Storage", "🔋 Battery", "👥 Clients", "📈 Requests", "⚡ Req/min", "📡 Traffic", "⏱️ Uptime", "🌐 Interface"};
        for (int row = 0; row < 5; row++) {
            LinearLayout rr = new LinearLayout(this);
            for (int col = 0; col < 2; col++) {
                int idx = row * 2 + col;
                LinearLayout c = new LinearLayout(this);
                c.setOrientation(LinearLayout.VERTICAL);
                c.setPadding(dp(10), dp(8), dp(8), dp(8));
                c.setBackground(bg(Color.rgb(2, 16, 29), 16));
                TextView lab = tv(labels[idx], 11, MUTED);
                lab.setTypeface(null, Typeface.BOLD);
                TextView val = tv("—", 14, WHITE);
                val.setTypeface(null, Typeface.BOLD);
                c.addView(lab, new LinearLayout.LayoutParams(-1, dp(22)));
                c.addView(val, new LinearLayout.LayoutParams(-1, dp(26)));
                cells[idx] = val;
                LinearLayout.LayoutParams q = new LinearLayout.LayoutParams(0, dp(62), 1);
                q.setMargins(dp(3), dp(3), dp(3), dp(3));
                rr.addView(c, q);
            }
            grid.addView(rr);
        }

        Runnable rr = new Runnable() {
            public void run() {
                if (monitor.getParent() == null) return;
                long now = System.currentTimeMillis();
                long req = WebServerService.getRequestCount();
                long perMin = lastReqAt == 0 ? 0 : Math.max(0, (req - lastReq) * 60000 / Math.max(1, now - lastReqAt));
                lastReq = req;
                lastReqAt = now;

                cells[0].setText(WebServerService.cpuText());
                cells[1].setText(WebServerService.memoryText(MainActivity.this));
                cells[2].setText(WebServerService.storageText(MainActivity.this));
                cells[3].setText(batteryText());
                cells[4].setText("" + WebServerService.getClientCount());
                cells[5].setText("" + req);
                cells[6].setText("" + perMin);
                cells[7].setText(WebServerService.trafficText());
                cells[8].setText(uptime());
                cells[9].setText(interfaceText());

                rxVal.setText(WebServerService.rxSpeedText());
                txVal.setText(WebServerService.txSpeedText());
                flowSub.setText("📥 Inbound: " + WebServerService.fmt(WebServerService.getRxBytes()) + "  •  📤 Outbound: " + WebServerService.fmt(WebServerService.getTxBytes()) + "  •  ⚡ Peak: " + WebServerService.peakSpeedText());
                net.setText("📶 " + WebServerService.networkSummary(MainActivity.this) + "\n🔗 Server URL: " + displayUrl());
                h.postDelayed(this, 1000);
            }
        };
        rr.run();
    }

    // ==========================================
    // TAB 1: CATEGORIZED NETWORK & DEV TOOLS
    // ==========================================
    void tools() {
        section("🛠️  INTERNET & NETWORK TOOLS SUITE");

        // Category Selector Chips
        HorizontalScrollView catScroll = new HorizontalScrollView(this);
        catScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout catRow = new LinearLayout(this);
        catRow.setOrientation(LinearLayout.HORIZONTAL);
        catRow.setPadding(dp(2), dp(4), dp(2), dp(10));

        String[] cats = {"⚡ Diagnostics", "🔍 IP & Web", "💻 Dev Utilities", "🛍️ Web Templates"};
        for (int i = 0; i < cats.length; i++) {
            final int catIdx = i;
            TextView chip = tv(cats[i], 12, catIdx == selectedToolCategory ? WHITE : MUTED);
            chip.setTypeface(null, Typeface.BOLD);
            chip.setBackground(catIdx == selectedToolCategory ? grad(16) : bg(Color.rgb(6, 22, 38), 16));
            chip.setPadding(dp(14), dp(8), dp(14), dp(8));
            chip.setOnClickListener(v -> {
                selectedToolCategory = catIdx;
                show(1);
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.setMargins(dp(3), 0, dp(3), 0);
            catRow.addView(chip, lp);
        }
        catScroll.addView(catRow);
        body.addView(catScroll);

        if (selectedToolCategory == 0) {
            // Category 0: Diagnostics
            toolCard("🏓", "Ping / Latency Tester", "Measure real-time packet latency, jitter and loss to any host", () -> pingDialog());
            toolCard("🚪", "Multi-Threaded Port Scanner", "Scan 22 common ports or target services on any IP or domain", () -> portScanDialog());
            toolCard("🌐", "DNS Record Query (DoH)", "Lookup A, AAAA, MX, TXT, CNAME records via Cloudflare DoH", () -> dnsDialog());
            toolCard("📶", "Wi-Fi LAN Device Scanner", "Scan active local devices and IP addresses connected to your Wi-Fi", () -> lanScanDialog());
            toolCard("🧮", "Subnet & CIDR Calculator", "Calculate network address, broadcast, netmask, and host range", () -> subnetDialog());
        } else if (selectedToolCategory == 1) {
            // Category 1: IP & Web
            toolCard("🗺️", "Public IP & Geo-Location", "Lookup your external public IP, ISP provider, Country, City & ASN", () -> publicIpDialog());
            toolCard("🔍", "HTTP & SSL Header Inspector", "Inspect response headers, status codes, server signatures & TLS", () -> httpInspectDialog());
            toolCard("💓", "Website Health & Uptime", "Test responsiveness, response time and HTTP status of any website", () -> webHealthDialog());
            toolCard("🛡️", "IP Security & Blocklist", "Block/allow client IPs and configure anti-DDoS rate limits", () -> security());
            toolCard("👁️", "Upload Privacy Guard", "Hide uploaded directory listing so visitors only see your web homepage", () -> privacyDialog());
        } else if (selectedToolCategory == 2) {
            // Category 2: Developer Utilities
            toolCard("🔤", "Base64 Encoder / Decoder", "Convert text strings to Base64 and decode Base64 in real-time", () -> base64Dialog());
            toolCard("🔗", "URL Percent Encoder / Decoder", "Sanitize and decode URL-encoded parameter strings", () -> urlEncodeDialog());
            toolCard("🔐", "Cryptographic Hash Generator", "Generate MD5, SHA-1, SHA-256, and SHA-512 hashes", () -> hashDialog());
            toolCard("📋", "JSON Formatter & Validator", "Beautify, format, validate and minify JSON data structures", () -> jsonDialog());
            toolCard("🔑", "Secure Password / Token Generator", "Generate cryptographically secure random passwords and API tokens", () -> passwordGenDialog());
            toolCard("🆔", "UUID / GUID v4 Generator", "Instantly generate unique UUID version 4 strings", () -> uuidDialog());
        } else {
            // Category 3: Templates
            toolCard("🛍️", "Deploy E-Commerce Online Store", "One-click deploy a responsive e-commerce web app with shopping cart", () -> deployEcommerceDialog());
            toolCard("🌐", "Deploy Default Server Portal", "One-click restore default Salam server greeting & files portal", () -> deployDefaultDialog());
            toolCard("📱", "Generate QR Code for Server", "Create a scannable QR code for instant mobile phone sharing", () -> showQrDialog(displayUrl()));
            toolCard("🖥️", "Open in Browser", "Open your active hosted website in external mobile browser", () -> openBrowser(displayUrl()));
        }
    }

    void toolCard(String icon, String title, String subtitle, Runnable onClick) {
        LinearLayout c = card();
        c.setOrientation(LinearLayout.HORIZONTAL);
        c.setGravity(Gravity.CENTER_VERTICAL);

        TextView ic = tv(icon, 28, WHITE);
        ic.setGravity(Gravity.CENTER);
        c.addView(ic, new LinearLayout.LayoutParams(dp(48), dp(48)));

        LinearLayout m = new LinearLayout(this);
        m.setOrientation(LinearLayout.VERTICAL);
        m.setPadding(dp(8), 0, dp(4), 0);

        TextView t = tv(title, 14, WHITE);
        t.setTypeface(null, Typeface.BOLD);
        m.addView(t);

        TextView sub = tv(subtitle, 11, MUTED);
        sub.setPadding(0, dp(2), 0, 0);
        m.addView(sub);

        c.addView(m, new LinearLayout.LayoutParams(0, -2, 1));

        TextView btn = button("OPEN");
        btn.setTextSize(10);
        c.addView(btn, new LinearLayout.LayoutParams(dp(62), dp(32)));

        c.setOnClickListener(v -> onClick.run());
        body.addView(c);
    }

    // ==========================================
    // TOOL DIALOGS IMPLEMENTATION
    // ==========================================
    void pingDialog() {
        LinearLayout b = new LinearLayout(this);
        b.setOrientation(LinearLayout.VERTICAL);
        b.setPadding(dp(16), dp(10), dp(16), dp(10));

        EditText hostInput = input("google.com", "Host or IP (e.g. 1.1.1.1 or google.com)");
        b.addView(hostInput);

        TextView resultView = tv("Tap 'START PING' to test socket latency...", 12, MUTED);
        resultView.setBackground(bg(Color.rgb(2, 12, 22), 12));
        resultView.setPadding(dp(10), dp(10), dp(10), dp(10));
        b.addView(resultView, new LinearLayout.LayoutParams(-1, dp(140)));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("🏓 PING / LATENCY TESTER")
                .setView(b)
                .setNegativeButton("CLOSE", null)
                .setPositiveButton("START PING", null)
                .create();

        dialog.setOnShowListener(d -> {
            Button btn = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            btn.setOnClickListener(v -> {
                String host = hostInput.getText().toString().trim();
                if (host.isEmpty()) return;
                resultView.setText("Pinging " + host + " (4 packets)...");
                NetworkTools.runPing(host, 4, 1500, new NetworkTools.ToolCallback<NetworkTools.PingResult>() {
                    @Override
                    public void onSuccess(NetworkTools.PingResult res) {
                        h.post(() -> {
                            StringBuilder sb = new StringBuilder();
                            sb.append("Host: ").append(res.host).append(" (").append(res.resolvedIp).append(")\n");
                            sb.append("Packets: Sent = ").append(res.packetsSent).append(", Received = ").append(res.packetsReceived);
                            sb.append(" (").append((res.packetsSent - res.packetsReceived) * 25).append("% loss)\n\n");
                            if (res.packetsReceived > 0) {
                                sb.append("Min latency: ").append(res.minLatencyMs).append(" ms\n");
                                sb.append("Avg latency: ").append(res.avgLatencyMs).append(" ms\n");
                                sb.append("Max latency: ").append(res.maxLatencyMs).append(" ms\n");
                                sb.append("Round trips: ").append(res.latencies).append(" ms");
                            } else {
                                sb.append("Host unreachable or ICMP/Port 80 blocked.");
                            }
                            resultView.setText(sb.toString());
                        });
                    }

                    @Override
                    public void onError(String error) {
                        h.post(() -> resultView.setText("❌ " + error));
                    }
                });
            });
        });
        dialog.show();
    }

    void portScanDialog() {
        LinearLayout b = new LinearLayout(this);
        b.setOrientation(LinearLayout.VERTICAL);
        b.setPadding(dp(16), dp(10), dp(16), dp(10));

        EditText hostInput = input("127.0.0.1", "Target Host or IP (e.g. 127.0.0.1 or scanme.nmap.org)");
        b.addView(hostInput);

        TextView resultView = tv("Tap 'SCAN' to scan 22 common service ports...", 12, MUTED);
        resultView.setBackground(bg(Color.rgb(2, 12, 22), 12));
        resultView.setPadding(dp(10), dp(10), dp(10), dp(10));
        b.addView(resultView, new LinearLayout.LayoutParams(-1, dp(180)));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("🚪 MULTI-THREADED PORT SCANNER")
                .setView(b)
                .setNegativeButton("CLOSE", null)
                .setPositiveButton("SCAN", null)
                .create();

        dialog.setOnShowListener(d -> {
            Button btn = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            btn.setOnClickListener(v -> {
                String host = hostInput.getText().toString().trim();
                if (host.isEmpty()) return;
                resultView.setText("Scanning common ports on " + host + " in parallel...");
                NetworkTools.scanCommonPorts(host, new NetworkTools.ToolCallback<NetworkTools.PortScanResult>() {
                    @Override
                    public void onSuccess(NetworkTools.PortScanResult res) {
                        h.post(() -> {
                            StringBuilder sb = new StringBuilder();
                            sb.append("Target: ").append(res.host).append(" (").append(res.ip).append(")\n");
                            sb.append("Open Ports Found: ").append(res.openPorts.size()).append("\n\n");
                            if (res.openPorts.isEmpty()) {
                                sb.append("No common ports open (Firewall/filtered).");
                            } else {
                                for (Map.Entry<Integer, String> e : res.openPorts.entrySet()) {
                                    sb.append("✓ Port ").append(e.getKey()).append(" [").append(e.getValue()).append("] - OPEN\n");
                                }
                            }
                            resultView.setText(sb.toString());
                        });
                    }

                    @Override
                    public void onError(String error) {
                        h.post(() -> resultView.setText("❌ " + error));
                    }
                });
            });
        });
        dialog.show();
    }

    void dnsDialog() {
        LinearLayout b = new LinearLayout(this);
        b.setOrientation(LinearLayout.VERTICAL);
        b.setPadding(dp(16), dp(10), dp(16), dp(10));

        EditText hostInput = input("google.com", "Domain (e.g. google.com or github.com)");
        b.addView(hostInput);

        Spinner typeSpinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, new String[]{"A", "AAAA", "MX", "TXT", "NS", "CNAME"});
        typeSpinner.setAdapter(adapter);
        b.addView(typeSpinner);

        TextView resultView = tv("Tap 'LOOKUP' to query Cloudflare DNS-over-HTTPS...", 12, MUTED);
        resultView.setBackground(bg(Color.rgb(2, 12, 22), 12));
        resultView.setPadding(dp(10), dp(10), dp(10), dp(10));
        b.addView(resultView, new LinearLayout.LayoutParams(-1, dp(160)));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("🌐 DNS RECORD LOOKUP (DoH)")
                .setView(b)
                .setNegativeButton("CLOSE", null)
                .setPositiveButton("LOOKUP", null)
                .create();

        dialog.setOnShowListener(d -> {
            Button btn = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            btn.setOnClickListener(v -> {
                String domain = hostInput.getText().toString().trim();
                String type = (String) typeSpinner.getSelectedItem();
                if (domain.isEmpty()) return;
                resultView.setText("Querying Cloudflare 1.1.1.1 DoH for " + type + " records...");
                NetworkTools.lookupDns(domain, type, new NetworkTools.ToolCallback<NetworkTools.DnsResult>() {
                    @Override
                    public void onSuccess(NetworkTools.DnsResult res) {
                        h.post(() -> {
                            StringBuilder sb = new StringBuilder();
                            sb.append("Domain: ").append(res.domain).append(" (").append(type).append(")\n\n");
                            if (res.records.isEmpty()) {
                                sb.append("No records found.");
                            } else {
                                for (String r : res.records) {
                                    sb.append("• ").append(r).append("\n");
                                }
                            }
                            resultView.setText(sb.toString());
                        });
                    }

                    @Override
                    public void onError(String error) {
                        h.post(() -> resultView.setText("❌ " + error));
                    }
                });
            });
        });
        dialog.show();
    }

    void lanScanDialog() {
        LinearLayout b = new LinearLayout(this);
        b.setOrientation(LinearLayout.VERTICAL);
        b.setPadding(dp(16), dp(10), dp(16), dp(10));

        TextView info = tv("Scans the current local Wi-Fi /24 subnet for active devices.", 12, MUTED);
        info.setPadding(0, 0, 0, dp(8));
        b.addView(info);

        TextView resultView = tv("Tap 'SCAN WI-FI' to discover active LAN devices...", 12, MUTED);
        resultView.setBackground(bg(Color.rgb(2, 12, 22), 12));
        resultView.setPadding(dp(10), dp(10), dp(10), dp(10));
        b.addView(resultView, new LinearLayout.LayoutParams(-1, dp(180)));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("📶 WI-FI LAN DEVICE SCANNER")
                .setView(b)
                .setNegativeButton("CLOSE", null)
                .setPositiveButton("SCAN WI-FI", null)
                .create();

        dialog.setOnShowListener(d -> {
            Button btn = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            btn.setOnClickListener(v -> {
                resultView.setText("Scanning 254 subnet hosts concurrently...");
                NetworkTools.scanLocalLan(MainActivity.this, new NetworkTools.ToolCallback<List<String>>() {
                    @Override
                    public void onSuccess(List<String> res) {
                        h.post(() -> {
                            StringBuilder sb = new StringBuilder();
                            sb.append("Active LAN Devices Found: ").append(res.size()).append("\n\n");
                            for (String host : res) {
                                sb.append("🟢 ").append(host).append("\n");
                            }
                            resultView.setText(sb.toString());
                        });
                    }

                    @Override
                    public void onError(String error) {
                        h.post(() -> resultView.setText("❌ " + error));
                    }
                });
            });
        });
        dialog.show();
    }

    void subnetDialog() {
        LinearLayout b = new LinearLayout(this);
        b.setOrientation(LinearLayout.VERTICAL);
        b.setPadding(dp(16), dp(10), dp(16), dp(10));

        EditText cidrInput = input("192.168.1.50/24", "IP/Prefix (e.g. 192.168.1.50/24)");
        b.addView(cidrInput);

        TextView resultView = tv("Tap 'CALCULATE' to compute subnet metrics...", 12, MUTED);
        resultView.setBackground(bg(Color.rgb(2, 12, 22), 12));
        resultView.setPadding(dp(10), dp(10), dp(10), dp(10));
        b.addView(resultView, new LinearLayout.LayoutParams(-1, dp(170)));

        new AlertDialog.Builder(this)
                .setTitle("🧮 SUBNET & CIDR CALCULATOR")
                .setView(b)
                .setNegativeButton("CLOSE", null)
                .setPositiveButton("CALCULATE", (d, w) -> {
                    try {
                        NetworkTools.SubnetInfo info = NetworkTools.calculateSubnet(cidrInput.getText().toString());
                        String res = "IP: " + info.ip + "/" + info.prefix + "\n" +
                                "Network: " + info.networkAddress + "\n" +
                                "Broadcast: " + info.broadcastAddress + "\n" +
                                "Netmask: " + info.netmask + "\n" +
                                "Usable Range: " + info.firstHost + " - " + info.lastHost + "\n" +
                                "Usable Hosts: " + info.usableHosts + " (Total: " + info.totalHosts + ")";
                        resultView.setText(res);
                    } catch (Exception ex) {
                        resultView.setText("❌ " + ex.getMessage());
                    }
                })
                .show();
    }

    void publicIpDialog() {
        LinearLayout b = new LinearLayout(this);
        b.setOrientation(LinearLayout.VERTICAL);
        b.setPadding(dp(16), dp(10), dp(16), dp(10));

        TextView resultView = tv("Fetching public IP and Geo-Location data...", 12, MUTED);
        resultView.setBackground(bg(Color.rgb(2, 12, 22), 12));
        resultView.setPadding(dp(10), dp(10), dp(10), dp(10));
        b.addView(resultView, new LinearLayout.LayoutParams(-1, dp(170)));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("🗺️ PUBLIC IP & GEO-LOCATION")
                .setView(b)
                .setNegativeButton("CLOSE", null)
                .setPositiveButton("REFRESH", null)
                .create();

        dialog.setOnShowListener(d -> {
            Button btn = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            Runnable fetch = () -> {
                resultView.setText("Querying IP lookup APIs...");
                NetworkTools.lookupPublicIp(new NetworkTools.ToolCallback<NetworkTools.GeoIpResult>() {
                    @Override
                    public void onSuccess(NetworkTools.GeoIpResult res) {
                        h.post(() -> {
                            String out = "🌐 Public IP: " + res.ip + "\n" +
                                    "🌍 Country: " + res.country + "\n" +
                                    "📍 City / Region: " + res.city + ", " + res.region + "\n" +
                                    "🏢 ISP / Org: " + res.org + "\n" +
                                    "🔢 ASN: " + res.asn + "\n" +
                                    "⏰ Timezone: " + res.timezone;
                            resultView.setText(out);
                        });
                    }

                    @Override
                    public void onError(String error) {
                        h.post(() -> resultView.setText("❌ " + error));
                    }
                });
            };
            btn.setOnClickListener(v -> fetch.run());
            fetch.run();
        });
        dialog.show();
    }

    void httpInspectDialog() {
        LinearLayout b = new LinearLayout(this);
        b.setOrientation(LinearLayout.VERTICAL);
        b.setPadding(dp(16), dp(10), dp(16), dp(10));

        EditText urlInput = input(displayUrl(), "URL to inspect (e.g. https://google.com)");
        b.addView(urlInput);

        TextView resultView = tv("Tap 'INSPECT' to fetch response headers and SSL details...", 12, MUTED);
        resultView.setBackground(bg(Color.rgb(2, 12, 22), 12));
        resultView.setPadding(dp(10), dp(10), dp(10), dp(10));
        b.addView(resultView, new LinearLayout.LayoutParams(-1, dp(180)));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("🔍 HTTP & SSL HEADER INSPECTOR")
                .setView(b)
                .setNegativeButton("CLOSE", null)
                .setPositiveButton("INSPECT", null)
                .create();

        dialog.setOnShowListener(d -> {
            Button btn = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            btn.setOnClickListener(v -> {
                String u = urlInput.getText().toString().trim();
                resultView.setText("Connecting to " + u + "...");
                NetworkTools.inspectHttp(u, new NetworkTools.ToolCallback<NetworkTools.HttpInspection>() {
                    @Override
                    public void onSuccess(NetworkTools.HttpInspection res) {
                        h.post(() -> {
                            StringBuilder sb = new StringBuilder();
                            sb.append("Status: ").append(res.statusCode).append(" ").append(res.statusMessage).append("\n");
                            sb.append("Response Time: ").append(res.responseTimeMs).append(" ms\n\n");
                            sb.append("--- HEADERS ---\n");
                            for (Map.Entry<String, List<String>> e : res.headers.entrySet()) {
                                if (e.getKey() != null) {
                                    sb.append(e.getKey()).append(": ").append(String.join(", ", e.getValue())).append("\n");
                                }
                            }
                            resultView.setText(sb.toString());
                        });
                    }

                    @Override
                    public void onError(String error) {
                        h.post(() -> resultView.setText("❌ " + error));
                    }
                });
            });
        });
        dialog.show();
    }

    void webHealthDialog() {
        LinearLayout b = new LinearLayout(this);
        b.setOrientation(LinearLayout.VERTICAL);
        b.setPadding(dp(16), dp(10), dp(16), dp(10));

        EditText urlInput = input(displayUrl(), "URL to probe (e.g. https://google.com)");
        b.addView(urlInput);

        TextView resultView = tv("Tap 'CHECK HEALTH' to test endpoint response...", 12, MUTED);
        resultView.setBackground(bg(Color.rgb(2, 12, 22), 12));
        resultView.setPadding(dp(10), dp(10), dp(10), dp(10));
        b.addView(resultView, new LinearLayout.LayoutParams(-1, dp(140)));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("💓 WEBSITE HEALTH & UPTIME")
                .setView(b)
                .setNegativeButton("CLOSE", null)
                .setPositiveButton("CHECK HEALTH", null)
                .create();

        dialog.setOnShowListener(d -> {
            Button btn = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            btn.setOnClickListener(v -> {
                String u = urlInput.getText().toString().trim();
                resultView.setText("Probing " + u + "...");
                NetworkTools.inspectHttp(u, new NetworkTools.ToolCallback<NetworkTools.HttpInspection>() {
                    @Override
                    public void onSuccess(NetworkTools.HttpInspection res) {
                        h.post(() -> {
                            boolean ok = res.statusCode >= 200 && res.statusCode < 400;
                            String out = (ok ? "🟢 STATUS: HEALTHY & ONLINE\n\n" : "🔴 STATUS: WARNING / ERROR\n\n") +
                                    "HTTP Code: " + res.statusCode + " (" + res.statusMessage + ")\n" +
                                    "Latency: " + res.responseTimeMs + " ms\n" +
                                    "Target: " + u;
                            resultView.setText(out);
                        });
                    }

                    @Override
                    public void onError(String error) {
                        h.post(() -> resultView.setText("🔴 DOWN: " + error));
                    }
                });
            });
        });
        dialog.show();
    }

    void base64Dialog() {
        LinearLayout b = new LinearLayout(this);
        b.setOrientation(LinearLayout.VERTICAL);
        b.setPadding(dp(16), dp(10), dp(16), dp(10));

        EditText textInput = input("Salam Web Server 2026", "Text to encode or Base64 to decode");
        b.addView(textInput);

        TextView resultView = tv("Result will appear here...", 12, WHITE);
        resultView.setBackground(bg(Color.rgb(2, 12, 22), 12));
        resultView.setPadding(dp(10), dp(10), dp(10), dp(10));
        b.addView(resultView, new LinearLayout.LayoutParams(-1, dp(120)));

        LinearLayout actRow = new LinearLayout(this);
        actRow.setOrientation(LinearLayout.HORIZONTAL);
        actRow.setPadding(0, dp(8), 0, 0);

        TextView encBtn = button("ENCODE");
        encBtn.setOnClickListener(v -> resultView.setText(NetworkTools.base64Encode(textInput.getText().toString())));
        actRow.addView(encBtn, new LinearLayout.LayoutParams(0, dp(40), 1));

        TextView decBtn = button("DECODE");
        decBtn.setOnClickListener(v -> resultView.setText(NetworkTools.base64Decode(textInput.getText().toString())));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(40), 1);
        lp.setMargins(dp(6), 0, 0, 0);
        actRow.addView(decBtn, lp);

        b.addView(actRow);

        new AlertDialog.Builder(this)
                .setTitle("🔤 BASE64 ENCODER / DECODER")
                .setView(b)
                .setNegativeButton("CLOSE", null)
                .setNeutralButton("COPY RESULT", (d, w) -> copy(resultView.getText().toString()))
                .show();
    }

    void urlEncodeDialog() {
        LinearLayout b = new LinearLayout(this);
        b.setOrientation(LinearLayout.VERTICAL);
        b.setPadding(dp(16), dp(10), dp(16), dp(10));

        EditText textInput = input("hello world & test=1", "Text or URL to encode/decode");
        b.addView(textInput);

        TextView resultView = tv("Result will appear here...", 12, WHITE);
        resultView.setBackground(bg(Color.rgb(2, 12, 22), 12));
        resultView.setPadding(dp(10), dp(10), dp(10), dp(10));
        b.addView(resultView, new LinearLayout.LayoutParams(-1, dp(120)));

        LinearLayout actRow = new LinearLayout(this);
        actRow.setOrientation(LinearLayout.HORIZONTAL);
        actRow.setPadding(0, dp(8), 0, 0);

        TextView encBtn = button("ENCODE URL");
        encBtn.setOnClickListener(v -> resultView.setText(NetworkTools.urlEncode(textInput.getText().toString())));
        actRow.addView(encBtn, new LinearLayout.LayoutParams(0, dp(40), 1));

        TextView decBtn = button("DECODE URL");
        decBtn.setOnClickListener(v -> resultView.setText(NetworkTools.urlDecode(textInput.getText().toString())));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(40), 1);
        lp.setMargins(dp(6), 0, 0, 0);
        actRow.addView(decBtn, lp);

        b.addView(actRow);

        new AlertDialog.Builder(this)
                .setTitle("🔗 URL PERCENT ENCODER / DECODER")
                .setView(b)
                .setNegativeButton("CLOSE", null)
                .setNeutralButton("COPY RESULT", (d, w) -> copy(resultView.getText().toString()))
                .show();
    }

    void hashDialog() {
        LinearLayout b = new LinearLayout(this);
        b.setOrientation(LinearLayout.VERTICAL);
        b.setPadding(dp(16), dp(10), dp(16), dp(10));

        EditText textInput = input("SalamWebServer", "Text to hash");
        b.addView(textInput);

        Spinner algoSpinner = new Spinner(this);
        algoSpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, new String[]{"MD5", "SHA-1", "SHA-256", "SHA-512"}));
        b.addView(algoSpinner);

        TextView resultView = tv("Tap 'HASH' to generate checksum...", 12, WHITE);
        resultView.setBackground(bg(Color.rgb(2, 12, 22), 12));
        resultView.setPadding(dp(10), dp(10), dp(10), dp(10));
        b.addView(resultView, new LinearLayout.LayoutParams(-1, dp(120)));

        new AlertDialog.Builder(this)
                .setTitle("🔐 CRYPTOGRAPHIC HASHER")
                .setView(b)
                .setNegativeButton("CLOSE", null)
                .setNeutralButton("COPY HASH", (d, w) -> copy(resultView.getText().toString()))
                .setPositiveButton("HASH", (d, w) -> {
                    String str = textInput.getText().toString();
                    String algo = (String) algoSpinner.getSelectedItem();
                    resultView.setText(NetworkTools.hash(str, algo));
                })
                .show();
    }

    void jsonDialog() {
        LinearLayout b = new LinearLayout(this);
        b.setOrientation(LinearLayout.VERTICAL);
        b.setPadding(dp(16), dp(10), dp(16), dp(10));

        EditText jsonInput = input("{\"server\":\"salam\",\"status\":\"online\",\"version\":10}", "Paste JSON string here");
        jsonInput.setMinLines(4);
        b.addView(jsonInput);

        TextView resultView = tv("Beautified JSON will appear here...", 12, WHITE);
        resultView.setBackground(bg(Color.rgb(2, 12, 22), 12));
        resultView.setPadding(dp(10), dp(10), dp(10), dp(10));
        b.addView(resultView, new LinearLayout.LayoutParams(-1, dp(150)));

        new AlertDialog.Builder(this)
                .setTitle("📋 JSON FORMATTER & VALIDATOR")
                .setView(b)
                .setNegativeButton("CLOSE", null)
                .setNeutralButton("COPY FORMATTED", (d, w) -> copy(resultView.getText().toString()))
                .setPositiveButton("BEAUTIFY", (d, w) -> {
                    resultView.setText(NetworkTools.formatJson(jsonInput.getText().toString()));
                })
                .show();
    }

    void passwordGenDialog() {
        LinearLayout b = new LinearLayout(this);
        b.setOrientation(LinearLayout.VERTICAL);
        b.setPadding(dp(16), dp(10), dp(16), dp(10));

        TextView resultView = tv("Tap 'GENERATE' for secure token...", 15, CYAN);
        resultView.setTypeface(null, Typeface.BOLD);
        resultView.setBackground(bg(Color.rgb(2, 12, 22), 12));
        resultView.setPadding(dp(12), dp(12), dp(12), dp(12));
        b.addView(resultView);

        CheckBox symCb = new CheckBox(this);
        symCb.setText("Include Special Symbols (!@#$%)");
        symCb.setTextColor(WHITE);
        symCb.setChecked(true);
        b.addView(symCb);

        CheckBox numCb = new CheckBox(this);
        numCb.setText("Include Numbers (0-9)");
        numCb.setTextColor(WHITE);
        numCb.setChecked(true);
        b.addView(numCb);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("🔑 SECURE PASSWORD / TOKEN GENERATOR")
                .setView(b)
                .setNegativeButton("CLOSE", null)
                .setNeutralButton("COPY TOKEN", (d, w) -> copy(resultView.getText().toString()))
                .setPositiveButton("GENERATE", null)
                .create();

        dialog.setOnShowListener(d -> {
            Button btn = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            Runnable gen = () -> {
                String pwd = NetworkTools.generatePassword(20, symCb.isChecked(), numCb.isChecked());
                resultView.setText(pwd);
            };
            btn.setOnClickListener(v -> gen.run());
            gen.run();
        });
        dialog.show();
    }

    void uuidDialog() {
        LinearLayout b = new LinearLayout(this);
        b.setOrientation(LinearLayout.VERTICAL);
        b.setPadding(dp(16), dp(10), dp(16), dp(10));

        TextView resultView = tv(UUID.randomUUID().toString(), 14, CYAN);
        resultView.setTypeface(null, Typeface.BOLD);
        resultView.setBackground(bg(Color.rgb(2, 12, 22), 12));
        resultView.setPadding(dp(12), dp(12), dp(12), dp(12));
        b.addView(resultView);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("🆔 UUID / GUID v4 GENERATOR")
                .setView(b)
                .setNegativeButton("CLOSE", null)
                .setNeutralButton("COPY UUID", (d, w) -> copy(resultView.getText().toString()))
                .setPositiveButton("NEW UUID", null)
                .create();

        dialog.setOnShowListener(d -> {
            Button btn = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            btn.setOnClickListener(v -> resultView.setText(UUID.randomUUID().toString()));
        });
        dialog.show();
    }

    void deployEcommerceDialog() {
        new AlertDialog.Builder(this)
                .setTitle("🛍️ DEPLOY E-COMMERCE ONLINE STORE")
                .setMessage("This will install the modern online shop template to your /www folder.\n\n✓ Product showcase (Phones, Laptops, Audio, Watches)\n✓ Interactive Shopping Cart\n✓ Checkout & Order Submission\n✓ Mobile & Desktop Responsive\n✓ Ready to share worldwide with friends!")
                .setPositiveButton("DEPLOY NOW 🚀", (d, w) -> {
                    try {
                        WebServerService.deployTemplate(this, "ecommerce");
                        toast("🎉 E-Commerce Store Deployed Successfully!");
                        new AlertDialog.Builder(this)
                                .setTitle("✅ STORE READY")
                                .setMessage("Your e-commerce website is live on your server!\n\nURL: " + displayUrl())
                                .setPositiveButton("OPEN IN BROWSER", (d2, w2) -> openBrowser(displayUrl()))
                                .setNeutralButton("COPY LINK", (d2, w2) -> copy(displayUrl()))
                                .setNegativeButton("CLOSE", null)
                                .show();
                    } catch (Exception e) {
                        toast("Deploy error: " + e.getMessage());
                    }
                })
                .setNegativeButton("CANCEL", null)
                .show();
    }

    void deployDefaultDialog() {
        new AlertDialog.Builder(this)
                .setTitle("🌐 DEPLOY DEFAULT SERVER PORTAL")
                .setMessage("Restore the standard Salam Web Server control portal to /www?")
                .setPositiveButton("RESTORE", (d, w) -> {
                    try {
                        WebServerService.deployTemplate(this, "web");
                        toast("Default portal restored");
                    } catch (Exception e) {
                        toast("Deploy error: " + e.getMessage());
                    }
                })
                .setNegativeButton("CANCEL", null)
                .show();
    }

    void chooseTunnelProvider() {
        String[] provs = {
                TunnelManager.PROVIDER_LOCALHOST_RUN,
                TunnelManager.PROVIDER_PINGGY,
                TunnelManager.PROVIDER_SERVEO,
                TunnelManager.PROVIDER_CUSTOM
        };
        new AlertDialog.Builder(this)
                .setTitle("🌍 CHOOSE TUNNEL PROVIDER")
                .setItems(provs, (d, w) -> {
                    p.edit().putString("tunnelProvider", provs[w]).apply();
                    toast("Tunnel set to: " + provs[w]);
                    if (WebServerService.running) {
                        stopServer();
                        h.postDelayed(this::startServer, 600);
                    }
                    show(0);
                })
                .show();
    }

    void showQrDialog(String url) {
        LinearLayout b = new LinearLayout(this);
        b.setOrientation(LinearLayout.VERTICAL);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(16), dp(16), dp(16), dp(16));

        ImageView qrView = new ImageView(this);
        qrView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        try {
            BarcodeEncoder encoder = new BarcodeEncoder();
            Bitmap qr = encoder.encodeBitmap(url, BarcodeFormat.QR_CODE, dp(220), dp(220));
            qrView.setImageBitmap(qr);
        } catch (Exception e) {
            qrView.setBackgroundColor(Color.GRAY);
        }
        b.addView(qrView, new LinearLayout.LayoutParams(dp(220), dp(220)));

        TextView urlTv = tv(url, 12, CYAN);
        urlTv.setGravity(Gravity.CENTER);
        urlTv.setPadding(0, dp(12), 0, 0);
        b.addView(urlTv);

        new AlertDialog.Builder(this)
                .setTitle("📱 SCAN SERVER QR CODE")
                .setView(b)
                .setPositiveButton("SHARE LINK", (d, w) -> share())
                .setNeutralButton("COPY URL", (d, w) -> copy(url))
                .setNegativeButton("CLOSE", null)
                .show();
    }

    // ==========================================
    // TAB 2: FILES MANAGER
    // ==========================================
    void files() {
        section("📁  CPANEL-STYLE FILE MANAGER");
        LinearLayout head = card();
        head.setPadding(dp(13), dp(10), dp(13), dp(10));
        TextView root = tv("🏠  /  Web Root (" + cwd + ")", 14, WHITE);
        root.setTypeface(null, Typeface.BOLD);
        head.addView(root);
        TextView info = tv("💽 " + WebServerService.storageText(this) + "   •   📂 " + fileCount(new File(WebServerService.webRoot(this), cwd)) + " items", 10, MUTED);
        head.addView(info);
        body.addView(head);

        EditText search = input("", "🔎 Search files and folders");
        search.setSingleLine(true);
        body.addView(search, new LinearLayout.LayoutParams(-1, dp(46)));

        LinearLayout b = new LinearLayout(this);
        String[] names = {"📄 FILE", "📂 FOLDER", "⬆️ UPLOAD", "📦 ZIP"};
        for (String s : names) {
            TextView x = button(s);
            b.addView(x, new LinearLayout.LayoutParams(0, dp(44), 1));
            if (s.contains("FILE")) x.setOnClickListener(v -> newName(false));
            if (s.contains("FOLDER")) x.setOnClickListener(v -> newName(true));
            if (s.contains("UPLOAD")) x.setOnClickListener(v -> pick(22));
            if (s.contains("ZIP")) x.setOnClickListener(v -> zipMenu());
        }
        body.addView(b);

        File d = new File(WebServerService.webRoot(this), cwd);
        File[] fs = d.listFiles();
        if (fs == null) {
            body.addView(tv("No files or folder unavailable", 13, RED));
            return;
        }
        Arrays.sort(fs, (a, c) -> a.isDirectory() != c.isDirectory() ? (a.isDirectory() ? -1 : 1) : a.getName().compareToIgnoreCase(c.getName()));

        if (!cwd.equals("/")) {
            fileRow("↩️", "..", "Parent folder", () -> {
                int k = cwd.lastIndexOf('/');
                cwd = k <= 0 ? "/" : cwd.substring(0, k);
                show(2);
            });
        }
        for (File f : fs) {
            final File ff = f;
            String nm = f.getName();
            LinearLayout row = fileRowView(f, () -> fileMenu(ff));
            row.setTag(nm.toLowerCase(Locale.US));
            body.addView(row);
        }

        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int c, int d) {}
            public void onTextChanged(CharSequence s, int a, int b, int c) {
                String q = s.toString().toLowerCase(Locale.US);
                for (int i = body.getChildCount() - 1; i >= 0; i--) {
                    View v = body.getChildAt(i);
                    if (v.getTag() != null) {
                        v.setVisibility(String.valueOf(v.getTag()).contains(q) ? View.VISIBLE : View.GONE);
                    }
                }
            }
            public void afterTextChanged(Editable e) {}
        });
    }

    int fileCount(File d) {
        File[] x = d.listFiles();
        return x == null ? 0 : x.length;
    }

    LinearLayout fileRowView(File f, Runnable r) {
        LinearLayout c = card();
        c.setOrientation(LinearLayout.HORIZONTAL);
        int res = f.isDirectory() ? R.drawable.ic_files : R.drawable.ic_upload;
        ImageView i = iconView(res, 34);
        c.addView(i, new LinearLayout.LayoutParams(dp(44), dp(54)));

        LinearLayout m = new LinearLayout(this);
        m.setOrientation(LinearLayout.VERTICAL);
        TextView n = tv(f.getName(), 14, WHITE);
        n.setTypeface(null, Typeface.BOLD);
        m.addView(n, new LinearLayout.LayoutParams(-1, dp(28)));
        String meta = f.isDirectory() ? "📂 Folder • " + fileCount(f) + " items" : "📄 File • " + size(f.length());
        m.addView(tv(meta, 10, MUTED), new LinearLayout.LayoutParams(-1, dp(22)));
        c.addView(m, new LinearLayout.LayoutParams(0, dp(54), 1));

        TextView ar = tv("›", 28, accent());
        ar.setGravity(Gravity.CENTER);
        c.addView(ar, new LinearLayout.LayoutParams(dp(30), dp(54)));
        c.setOnClickListener(v -> r.run());
        return c;
    }

    void fileRow(String ic, String name, String sub, Runnable r) {
        LinearLayout c = card();
        c.setOrientation(LinearLayout.HORIZONTAL);
        TextView i = tv(ic, 25, icon(name));
        i.setGravity(Gravity.CENTER);
        c.addView(i, new LinearLayout.LayoutParams(dp(44), dp(54)));

        LinearLayout m = new LinearLayout(this);
        m.setOrientation(LinearLayout.VERTICAL);
        TextView n = tv(name, 14, WHITE);
        n.setTypeface(null, Typeface.BOLD);
        m.addView(n, new LinearLayout.LayoutParams(-1, dp(28)));
        m.addView(tv(sub, 10, MUTED), new LinearLayout.LayoutParams(-1, dp(22)));
        c.addView(m, new LinearLayout.LayoutParams(0, dp(54), 1));

        TextView ar = tv("›", 28, accent());
        ar.setGravity(Gravity.CENTER);
        c.addView(ar, new LinearLayout.LayoutParams(dp(30), dp(54)));
        c.setOnClickListener(v -> r.run());
        body.addView(c);
    }

    String size(long n) {
        if (n < 1024) return n + " B";
        if (n < 1048576) return n / 1024 + " KB";
        if (n < 1073741824L) return n / 1048576 + " MB";
        return String.format(Locale.US, "%.1f GB", n / 1073741824d);
    }

    void fileMenu(File f) {
        String[] a = f.isDirectory() ?
                new String[]{"📂 Open Folder", "✏️ Rename", "📋 Copy", "↔️ Move", "📦 Create ZIP", "🗑️ Delete"} :
                new String[]{"✏️ Edit in Code Editor", "✏️ Rename", "📋 Copy", "↔️ Move", "📦 Create ZIP", "⬇️ Copy Download URL", "🗑️ Delete"};

        new AlertDialog.Builder(this)
                .setTitle("⚙️ " + f.getName())
                .setItems(a, (d, w) -> {
                    String s = a[w];
                    if (s.contains("Open")) {
                        cwd = (cwd.equals("/") ? "/" : cwd + "/") + f.getName();
                        show(2);
                    } else if (s.contains("Edit")) edit(f);
                    else if (s.contains("Rename")) rename(f);
                    else if (s.contains("Copy")) copyMove(f, false);
                    else if (s.contains("Move")) copyMove(f, true);
                    else if (s.contains("Create ZIP")) zipOne(f);
                    else if (s.contains("Download")) copy(displayUrl() + pathOf(f));
                    else deleteConfirm(f);
                })
                .show();
    }

    String pathOf(File f) {
        return "/" + WebServerService.webRoot(this).toURI().relativize(f.toURI()).getPath().replace('\\', '/');
    }

    void newName(boolean dir) {
        EditText e = input("", dir ? "Folder name" : "File name (e.g. store.html)");
        new AlertDialog.Builder(this)
                .setTitle(dir ? "📂 New Folder" : "📄 New File")
                .setView(e)
                .setNegativeButton("CANCEL", null)
                .setPositiveButton("CREATE", (d, w) -> {
                    try {
                        File f = new File(WebServerService.webRoot(this), cwd + "/" + e.getText().toString().replace("/", "_"));
                        if (dir) f.mkdirs();
                        else f.createNewFile();
                        show(2);
                    } catch (Exception x) {
                        toast(x.getMessage());
                    }
                })
                .show();
    }

    void edit(File f) {
        EditText e = new EditText(this);
        e.setMinLines(16);
        e.setGravity(Gravity.TOP);
        e.setTextColor(WHITE);
        e.setBackgroundColor(Color.rgb(2, 12, 22));
        e.setPadding(dp(12), dp(12), dp(12), dp(12));
        try {
            e.setText(WebServerService.readText(f));
        } catch (Exception x) {
            toast(x.getMessage());
        }

        new AlertDialog.Builder(this)
                .setTitle("✏️ Code Editor • " + f.getName())
                .setView(e)
                .setNegativeButton("CANCEL", null)
                .setPositiveButton("SAVE", (d, w) -> {
                    try {
                        WebServerService.writeText(f, e.getText().toString());
                        toast("File saved successfully");
                    } catch (Exception x) {
                        toast(x.getMessage());
                    }
                })
                .show();
    }

    void rename(File f) {
        EditText e = input(f.getName(), "New name");
        new AlertDialog.Builder(this)
                .setTitle("Rename")
                .setView(e)
                .setNegativeButton("CANCEL", null)
                .setPositiveButton("SAVE", (d, w) -> {
                    File n = new File(f.getParent(), e.getText().toString().replace("/", "_"));
                    if (!f.renameTo(n)) toast("Rename failed");
                    show(2);
                })
                .show();
    }

    void copyMove(File f, boolean move) {
        EditText e = input(cwd, "Destination folder path");
        new AlertDialog.Builder(this)
                .setTitle(move ? "Move to folder" : "Copy to folder")
                .setView(e)
                .setNegativeButton("CANCEL", null)
                .setPositiveButton("OK", (d, w) -> {
                    try {
                        File dst = new File(WebServerService.webRoot(this), e.getText().toString());
                        dst.mkdirs();
                        WebServerService.copyRecursive(f, new File(dst, f.getName()));
                        if (move) WebServerService.deleteRecursive(f);
                        show(2);
                    } catch (Exception x) {
                        toast(x.getMessage());
                    }
                })
                .show();
    }

    void deleteConfirm(File f) {
        new AlertDialog.Builder(this)
                .setTitle("🗑️ Delete?")
                .setMessage(f.getName() + " will be permanently removed.")
                .setNegativeButton("CANCEL", null)
                .setPositiveButton("DELETE", (d, w) -> {
                    WebServerService.deleteRecursive(f);
                    show(2);
                })
                .show();
    }

    void pick(int req) {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.setType("*/*");
        i.addCategory(Intent.CATEGORY_OPENABLE);
        if (req == 23) i.setType("application/zip");
        startActivityForResult(i, req);
    }

    @Override
    protected void onActivityResult(int r, int c, Intent d) {
        super.onActivityResult(r, c, d);
        if (c != RESULT_OK || d == null) return;
        try {
            InputStream in = getContentResolver().openInputStream(d.getData());
            String n = String.valueOf(d.getData().getLastPathSegment()).replaceAll("[^A-Za-z0-9._ -]", "_");
            File out = new File(WebServerService.webRoot(this), cwd + "/" + n);
            FileOutputStream o = new FileOutputStream(out);
            byte[] b = new byte[16384];
            int k;
            while ((k = in.read(b)) > 0) o.write(b, 0, k);
            in.close();
            o.close();
            if (r == 23) {
                WebServerService.unzip(out, new File(WebServerService.webRoot(this), cwd));
                toast("ZIP extracted");
                show(2);
            } else {
                show(2);
                showUploadedUrl(out);
            }
        } catch (Exception e) {
            toast(e.getMessage());
        }
    }

    void showUploadedUrl(File f) {
        String u = displayUrl() + pathOf(f);
        new AlertDialog.Builder(this)
                .setTitle("✅ FILE UPLOADED")
                .setMessage("Live URL ready:\n\n" + u)
                .setNegativeButton("CLOSE", null)
                .setNeutralButton("🔗 COPY URL", (d, w) -> copy(u))
                .setPositiveButton("🌐 OPEN", (d, w) -> openBrowser(u))
                .show();
    }

    void zipMenu() {
        new AlertDialog.Builder(this)
                .setTitle("📦 ZIP TOOLS")
                .setItems(new String[]{"📦 Create ZIP from current folder", "🗜️ Extract ZIP here"}, (d, w) -> {
                    if (w == 0) zipOne(new File(WebServerService.webRoot(this), cwd));
                    else pick(23);
                })
                .show();
    }

    void zipOne(File f) {
        try {
            File z = new File(f.getParent(), f.getName() + ".zip");
            WebServerService.zip(f, z);
            toast("ZIP created: " + z.getName());
            show(2);
        } catch (Exception e) {
            toast(e.getMessage());
        }
    }

    // ==========================================
    // TAB 3: LIVE TRAFFIC & LOGS
    // ==========================================
    void logs() {
        section("🌊  LIVE NETWORK FLOW & SPEED");
        LinearLayout flowCard = card();
        TextView spTxt = tv("↓ Rx: " + WebServerService.rxSpeedText() + "   ↑ Tx: " + WebServerService.txSpeedText() + "   ⚡ Peak: " + WebServerService.peakSpeedText(), 13, CYAN);
        spTxt.setTypeface(null, Typeface.BOLD);
        flowCard.addView(spTxt);

        TextView streamBtn = button("VIEW CLIENT DATA STREAMS");
        streamBtn.setOnClickListener(v -> networkFlowDialog());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(40));
        lp.setMargins(0, dp(8), 0, 0);
        flowCard.addView(streamBtn, lp);
        body.addView(flowCard);

        section("📝  LIVE REQUEST LOG");
        TextView l = tv("", 11, Color.rgb(190, 225, 245));
        l.setBackground(bg(Color.rgb(2, 12, 22), 16));
        l.setPadding(dp(10), dp(10), dp(10), dp(10));
        body.addView(l);

        TextView hist = tv("", 11, MUTED);
        hist.setPadding(dp(4), dp(10), dp(4), dp(10));
        body.addView(hist);

        TextView clear = button("🧹  CLEAR LOGS + FLOW COUNTERS");
        body.addView(clear, new LinearLayout.LayoutParams(-1, dp(46)));
        clear.setOnClickListener(v -> {
            WebServerService.LOGS.clear();
            WebServerService.HISTORY.clear();
            WebServerService.resetFlow();
            toast("Logs & flow cleared");
        });

        Runnable r = new Runnable() {
            public void run() {
                if (l.getParent() == null) return;
                l.setText(join(WebServerService.LOGS));
                hist.setText("🧾 ACCESS HISTORY\n\n" + join(WebServerService.HISTORY));
                h.postDelayed(this, 800);
            }
        };
        r.run();
    }

    String join(List<String> a) {
        StringBuilder b = new StringBuilder();
        for (String x : a) b.append(x).append('\n');
        return b.length() == 0 ? "No events yet" : b.toString();
    }

    // ==========================================
    // TAB 4: SETTINGS
    // ==========================================
    void settings() {
        section("⚙️  CONTROL CENTER & CONFIGURATION");
        setting("🌍", "Public / Private Server Scope", "Toggle Global Public URL or Local Wi-Fi only", () -> scopeDialog());
        setting("🛰️", "Tunnel Provider", "Localhost.run, Pinggy, Serveo, or Custom Domain", () -> chooseTunnelProvider());
        setting("🛍️", "Website Templates", "Deploy E-Commerce Online Store or Web Portal", () -> templateDialog());
        setting("🖥️", "Server Port & Engine", "Change HTTP port (8080, 8000, 3000) • restart", () -> engineDialog());
        setting("🔐", "Web Password Protection", "Require login username & password for visitors", () -> security());
        setting("🛡️", "Upload Privacy (Hide Files)", "Only website homepage opens • Directory listing hidden", () -> privacyDialog());
        setting("🚦", "Rate Limits & Clients", "Maximum simultaneous clients & requests/minute", () -> limits());
        setting("🚫", "IP Blacklist / Allowlist", "Block malicious client IPs or enforce allowlist", () -> security());
        setting("🔓", "Unblock Client IP", "Remove an IP address from blocklist", () -> unblock());
        setting("🎨", "Themes & Visuals", "Ocean Neon, Purple Night, Emerald, Sunset, Arctic", () -> themes());
        setting("📱", "Share Server / QR Code", "Share URL with friends on WhatsApp, Messenger, QR", () -> showQrDialog(displayUrl()));
        setting("💡", "LED Visualizer Engine", "7-color pulsing lights & mathematical wave glow", () -> statusDialog());
        setting("👨‍💻", "Developer Center", "About Abdus Salam • Contact • Messenger • Version", () -> about());
    }

    void scopeDialog() {
        boolean isPublic = p.getBoolean("publicMode", true);
        new AlertDialog.Builder(this)
                .setTitle("🌍 SERVER ACCESS SCOPE")
                .setMessage("Current: " + (isPublic ? "Public Internet Tunnel (Accessible worldwide on any network)" : "Local Wi-Fi Only (LAN)"))
                .setPositiveButton(isPublic ? "SWITCH TO LOCAL WI-FI" : "SWITCH TO PUBLIC INTERNET", (d, w) -> {
                    p.edit().putBoolean("publicMode", !isPublic).apply();
                    toast(!isPublic ? "Switched to Public Internet Tunnel" : "Switched to Local Wi-Fi");
                    if (WebServerService.running) {
                        stopServer();
                        h.postDelayed(this::startServer, 600);
                    }
                    show(4);
                })
                .setNegativeButton("CLOSE", null)
                .show();
    }

    void templateDialog() {
        new AlertDialog.Builder(this)
                .setTitle("🛍️ WEBSITE TEMPLATES")
                .setItems(new String[]{"🛍️ Deploy Modern E-Commerce Store", "🌐 Deploy Default Portal", "📂 Open File Manager to Upload Custom"}, (d, w) -> {
                    if (w == 0) deployEcommerceDialog();
                    else if (w == 1) deployDefaultDialog();
                    else show(2);
                })
                .show();
    }

    void privacyDialog() {
        boolean allow = p.getBoolean("allowDirListing", false);
        new AlertDialog.Builder(this)
                .setTitle("🛡️ UPLOAD PRIVACY")
                .setMessage("Status: " + (allow ? "Files visible in browser (Directory Listing Enabled)" : "Uploaded files HIDDEN and PROTECTED (Only URL opens)") + "\n\nWhen hidden, opening the server URL loads your website homepage (index.html). Visitors cannot see or list your uploaded files.")
                .setPositiveButton(allow ? "HIDE FILES (RECOMMENDED)" : "SHOW FILES IN BROWSER", (d, w) -> {
                    p.edit().putBoolean("allowDirListing", !allow).apply();
                    toast(!allow ? "Uploaded files are now hidden in browser" : "Directory listing enabled");
                })
                .setNegativeButton("CLOSE", null)
                .show();
    }

    void setting(String ic, String name, String sub, Runnable r) {
        LinearLayout c = card();
        c.setOrientation(LinearLayout.HORIZONTAL);
        TextView i = tv(ic, 25, icon(name));
        i.setGravity(Gravity.CENTER);
        c.addView(i, new LinearLayout.LayoutParams(dp(48), dp(58)));

        LinearLayout m = new LinearLayout(this);
        m.setOrientation(LinearLayout.VERTICAL);
        TextView n = tv(name, 14, WHITE);
        n.setTypeface(null, Typeface.BOLD);
        m.addView(n, new LinearLayout.LayoutParams(-1, dp(28)));
        m.addView(tv(sub, 10, MUTED), new LinearLayout.LayoutParams(-1, dp(24)));
        c.addView(m, new LinearLayout.LayoutParams(0, dp(58), 1));

        TextView ar = tv("›", 28, accent());
        ar.setGravity(Gravity.CENTER);
        c.addView(ar, new LinearLayout.LayoutParams(dp(28), dp(58)));
        c.setOnClickListener(v -> r.run());
        body.addView(c);
    }

    void engineDialog() {
        new AlertDialog.Builder(this)
                .setTitle("🖥️ SERVER ENGINE")
                .setItems(new String[]{WebServerService.running ? "⏹️ Stop Server" : "▶️ Start Server", "🔄 Restart Server", "⚙️ Port Settings"}, (d, w) -> {
                    if (w == 0) {
                        if (WebServerService.running) stopServer();
                        else startServer();
                    } else if (w == 1) {
                        if (WebServerService.running) stopServer();
                        h.postDelayed(this::startServer, 500);
                    } else host();
                })
                .show();
    }

    void statusDialog() {
        new AlertDialog.Builder(this)
                .setTitle("💡 SERVER STATUS ENGINE")
                .setMessage("🟢 Startup sequence: 7 LEDs light one-by-one\n✨ Online: continuous sine-wave glow & pulse\n🔴 Offline: red indicator\n🔄 Status is updated live on the dashboard.")
                .setPositiveButton("OK", null)
                .show();
    }

    void themes() {
        String[] a = {"🌊 Ocean Neon", "💜 Purple Night", "💚 Emerald Matrix", "🌅 Sunset", "💗 Neon Pink", "🧊 Arctic Cyan"};
        new AlertDialog.Builder(this)
                .setTitle("🎨 CHOOSE COMPLETE THEME")
                .setItems(a, (d, w) -> {
                    theme = w;
                    p.edit().putInt("theme", w).apply();
                    buildShell();
                    toast(a[w] + " applied to full UI");
                })
                .show();
    }

    void security() {
        LinearLayout b = new LinearLayout(this);
        b.setOrientation(LinearLayout.VERTICAL);
        b.setPadding(dp(14), dp(8), dp(14), dp(8));
        EditText pass = input(p.getString("password", ""), "🔑 Web password (leave blank for open access)");
        EditText allow = input(p.getString("allowIps", ""), "✅ Allowlist IPs (comma separated)");
        EditText block = input(p.getString("blockIps", ""), "⛔ Blocklist IPs (comma separated)");
        CheckBox only = new CheckBox(this);
        only.setText("Enforce allowlist only");
        only.setTextColor(WHITE);
        only.setChecked(p.getBoolean("allowOnly", false));

        b.addView(pass);
        b.addView(allow);
        b.addView(block);
        b.addView(only);

        new AlertDialog.Builder(this)
                .setTitle("🛡️ SECURITY CENTER")
                .setView(b)
                .setNeutralButton("🔓 UNBLOCK IP", (d, w) -> unblock())
                .setNegativeButton("CANCEL", null)
                .setPositiveButton("SAVE", (d, w) -> {
                    p.edit().putString("password", pass.getText().toString())
                            .putString("allowIps", allow.getText().toString())
                            .putString("blockIps", block.getText().toString())
                            .putBoolean("allowOnly", only.isChecked()).apply();
                    WebServerService.rateLimit = p.getInt("rate", 120);
                    toast("Security state synchronized");
                })
                .show();
    }

    EditText input(String s, String hint) {
        EditText e = new EditText(this);
        e.setText(s);
        e.setHint(hint);
        e.setTextColor(WHITE);
        e.setHintTextColor(MUTED);
        e.setSingleLine(false);
        return e;
    }

    void unblock() {
        EditText e = input("", "IP address to unblock (e.g. 192.168.0.10)");
        new AlertDialog.Builder(this)
                .setTitle("🔓 Unblock IP")
                .setView(e)
                .setNegativeButton("CANCEL", null)
                .setPositiveButton("UNBLOCK", (d, w) -> {
                    String ip = e.getText().toString().trim();
                    Set<String> s = new LinkedHashSet<>(Arrays.asList(p.getString("blockIps", "").split(",")));
                    s.remove(ip);
                    s.remove("");
                    p.edit().putString("blockIps", String.join(",", s)).apply();
                    toast("IP unblocked: " + ip);
                })
                .show();
    }

    void limits() {
        LinearLayout b = new LinearLayout(this);
        b.setOrientation(LinearLayout.VERTICAL);
        b.setPadding(dp(14), dp(8), dp(14), dp(8));
        EditText rate = input("" + p.getInt("rate", 120), "Requests / IP / minute");
        EditText max = input("" + p.getInt("maxClients", 32), "Maximum simultaneous clients");
        b.addView(rate);
        b.addView(max);

        new AlertDialog.Builder(this)
                .setTitle("🚦 LIMIT CONTROL")
                .setView(b)
                .setNegativeButton("CANCEL", null)
                .setPositiveButton("SAVE", (d, w) -> {
                    p.edit().putInt("rate", num(rate, 120)).putInt("maxClients", num(max, 32)).apply();
                    WebServerService.rateLimit = num(rate, 120);
                    WebServerService.maxClients = num(max, 32);
                    toast("Limits applied");
                })
                .show();
    }

    int num(EditText e, int d) {
        try {
            return Math.max(1, Integer.parseInt(e.getText().toString().trim()));
        } catch (Exception x) {
            return d;
        }
    }

    void host() {
        EditText e = input("" + p.getInt("port", 8080), "Server Port (e.g. 8080, 8000)");
        e.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        new AlertDialog.Builder(this)
                .setTitle("⚙️ SERVER PORT")
                .setMessage("Default port is 8080. Changing port requires server restart.")
                .setView(e)
                .setNegativeButton("CANCEL", null)
                .setPositiveButton("SAVE", (d, w) -> {
                    int pt = num(e, 8080);
                    p.edit().putInt("port", pt).apply();
                    toast("Port set to " + pt);
                    if (WebServerService.running) {
                        stopServer();
                        h.postDelayed(this::startServer, 600);
                    }
                    show(page);
                })
                .show();
    }

    void networkFlowDialog() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14), dp(10), dp(14), dp(10));

        TextView rates = tv("⚡ LIVE TRANSFER SPEEDS:\n• Inbound Speed (Rx): " + WebServerService.rxSpeedText() + "\n• Outbound Speed (Tx): " + WebServerService.txSpeedText() + "\n• Peak Flow Rate: " + WebServerService.peakSpeedText() + "\n\n📦 DATA BANDWIDTH:\n• Inbound Total: " + WebServerService.fmt(WebServerService.getRxBytes()) + "\n• Outbound Total: " + WebServerService.fmt(WebServerService.getTxBytes()) + "\n• Combined Flow: " + WebServerService.totalFlowText() + "\n• Total Requests: " + WebServerService.getRequestCount(), 13, WHITE);
        root.addView(rates);

        TextView streamHead = tv("\n👥 CLIENT DATA STREAMS (" + WebServerService.CLIENT_FLOW.size() + "):", 13, accent());
        streamHead.setTypeface(null, Typeface.BOLD);
        root.addView(streamHead);

        ScrollView sc = new ScrollView(this);
        LinearLayout streamList = new LinearLayout(this);
        streamList.setOrientation(LinearLayout.VERTICAL);
        streamList.setPadding(0, dp(6), 0, dp(6));

        if (WebServerService.CLIENT_FLOW.isEmpty()) {
            streamList.addView(tv("No active client streams yet.\nIncoming requests will track per-client flow here.", 12, MUTED));
        } else {
            for (Map.Entry<String, Long> e : WebServerService.CLIENT_FLOW.entrySet()) {
                String ip = e.getKey();
                long bytes = e.getValue();
                long reqs = WebServerService.CLIENT_REQS.getOrDefault(ip, 1L);
                TextView row = tv("• " + ip + "  →  " + WebServerService.fmt(bytes) + " (" + reqs + " reqs)", 12, CYAN);
                row.setPadding(0, dp(3), 0, dp(3));
                streamList.addView(row);
            }
        }
        sc.addView(streamList);
        root.addView(sc, new LinearLayout.LayoutParams(-1, dp(130)));

        new AlertDialog.Builder(this)
                .setTitle("🌊 NETWORK FLOW MONITOR")
                .setView(root)
                .setPositiveButton("OK", null)
                .setNeutralButton("🧹 RESET FLOW", (d, w) -> {
                    WebServerService.resetFlow();
                    toast("Flow counters reset");
                })
                .show();
    }

    void menu() {
        new AlertDialog.Builder(this)
                .setItems(new String[]{"🔄 Restart Server", "📱 Share Server / QR Code", "🛍️ Deploy E-Commerce Shop", "🛠️ Network Tools", "👨‍💻 Developer"}, (d, w) -> {
                    if (w == 0) {
                        stopServer();
                        h.postDelayed(this::startServer, 700);
                    } else if (w == 1) showQrDialog(displayUrl());
                    else if (w == 2) deployEcommerceDialog();
                    else if (w == 3) show(1);
                    else about();
                })
                .show();
    }

    void share() {
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("text/plain");
        i.putExtra(Intent.EXTRA_TEXT, "Visit my online web server: " + displayUrl());
        startActivity(Intent.createChooser(i, "Share Server URL"));
    }

    void copy(String s) {
        ((android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("Server URL", s));
        toast("Copied to clipboard: " + s);
    }

    void about() {
        new AlertDialog.Builder(this)
                .setTitle("⚡ Salam Web Server v10.0")
                .setMessage("Premium Android Web Server & Network Tools Suite\n\n👨‍💻 Abdus Salam\n📞 09696590864\n📧 salam230864@gmail.com\n💬 Messenger: m.me/Salam.864\n\n✓ Public Internet Tunnel (Global Access)\n✓ One-Click E-Commerce Hosting\n✓ Network Tools (Ping, Ports, DNS, LAN, Geolocation)\n✓ Instant Kill-Switch Protection")
                .setPositiveButton("MESSENGER", (d, w) -> {
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://m.me/Salam.864")));
                    } catch (Exception ignored) {}
                })
                .setNegativeButton("CLOSE", null)
                .show();
    }

    void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    void openBrowser(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) {
            toast("No browser available");
        }
    }

    void startServer() {
        toast("Starting Server & Public Engine...");
        Intent i = new Intent(this, WebServerService.class).setAction("START");
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(i);
        else startService(i);
    }

    void stopServer() {
        toast("Stopping Server & Killing Public Tunnel...");
        startService(new Intent(this, WebServerService.class).setAction("STOP"));
    }

    String displayUrl() {
        return WebServerService.activeUrl(this);
    }

    String batteryText() {
        try {
            Intent i = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            int l = i == null ? 0 : i.getIntExtra(BatteryManager.EXTRA_LEVEL, 0), sc = i == null ? 100 : i.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
            int pct = sc > 0 ? (l * 100 / sc) : 0;
            int st = i == null ? 0 : i.getIntExtra(BatteryManager.EXTRA_STATUS, 0);
            return pct + "% " + (st == BatteryManager.BATTERY_STATUS_CHARGING ? "⚡ Charging" : "• Battery");
        } catch (Exception e) {
            return "—";
        }
    }

    String interfaceText() {
        try {
            android.net.ConnectivityManager m = (android.net.ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
            android.net.Network n = m.getActiveNetwork();
            android.net.LinkProperties lp = n == null ? null : m.getLinkProperties(n);
            return lp == null || lp.getInterfaceName() == null ? "—" : lp.getInterfaceName();
        } catch (Exception e) {
            return "—";
        }
    }

    String uptime() {
        return WebServerService.running ? getUp() : "00:00:00";
    }

    String getUp() {
        long s = (System.currentTimeMillis() - getStart()) / 1000;
        return String.format(Locale.US, "%02d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60);
    }

    long getStart() {
        try {
            java.lang.reflect.Field f = WebServerService.class.getDeclaredField("startedAt");
            f.setAccessible(true);
            return f.getLong(null);
        } catch (Exception e) {
            return System.currentTimeMillis();
        }
    }

    void section(String s) {
        TextView v = tv(s, 12, accent());
        v.setTypeface(null, Typeface.BOLD);
        v.setPadding(dp(4), dp(10), 0, dp(4));
        body.addView(v);
    }

    LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(14), dp(12), dp(14), dp(12));
        c.setBackground(cardBg());
        LinearLayout.LayoutParams q = new LinearLayout.LayoutParams(-1, -2);
        q.setMargins(dp(2), dp(4), dp(2), dp(4));
        c.setLayoutParams(q);
        return c;
    }

    void addTile(LinearLayout row, int res, String name, String sub, Runnable run) {
        LinearLayout x = new LinearLayout(this);
        x.setOrientation(LinearLayout.VERTICAL);
        x.setGravity(Gravity.CENTER);
        x.setBackground(cardBg());
        ImageView i = iconView(res, 32);
        TextView n = tv(name, 12, WHITE);
        n.setTypeface(null, Typeface.BOLD);
        n.setGravity(Gravity.CENTER);
        TextView s = tv(sub, 9, MUTED);
        s.setGravity(Gravity.CENTER);
        x.addView(i, new LinearLayout.LayoutParams(-1, dp(40)));
        x.addView(n, new LinearLayout.LayoutParams(-1, dp(24)));
        x.addView(s, new LinearLayout.LayoutParams(-1, dp(18)));
        x.setOnClickListener(v -> run.run());
        LinearLayout.LayoutParams q = new LinearLayout.LayoutParams(0, dp(96), 1);
        q.setMargins(dp(2), dp(2), dp(2), dp(2));
        row.addView(x, q);
    }

    int icon(String n) {
        if (n.contains("Files")) return YELLOW;
        if (n.contains("Tools") || n.contains("Network")) return CYAN;
        if (n.contains("Traffic") || n.contains("Logs")) return ORANGE;
        if (n.contains("Security")) return RED;
        return accent();
    }

    // ==========================================
    // SERVER PANEL WITH LED ANIMATIONS
    // ==========================================
    class ServerPanel extends ViewGroup {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        TextView state, url, startBtn, webBtn, qrBtn;
        boolean running;

        ServerPanel(Context c) {
            super(c);
            setWillNotDraw(false);
            setBackground(cardBg());

            state = tv("SERVER OFFLINE", 17, RED);
            state.setGravity(Gravity.CENTER);
            state.setTypeface(null, Typeface.BOLD);
            addView(state);

            url = tv(displayUrl() + "\n🔗 Tap URL to copy or scan QR", 12, accent());
            url.setGravity(Gravity.CENTER);
            url.setPadding(dp(8), 0, dp(8), 0);
            url.setBackground(bg(Color.rgb(2, 17, 31), 16));
            url.setOnClickListener(v -> copy(displayUrl()));
            addView(url);

            LinearLayout controls = new LinearLayout(c);
            controls.setGravity(Gravity.CENTER);
            controls.setPadding(0, 0, 0, 0);

            startBtn = button("▶  START SERVER");
            webBtn = button("🌐 OPEN WEB");
            qrBtn = button("📱 QR CODE");

            controls.addView(startBtn, new LinearLayout.LayoutParams(0, dp(50), 1));
            controls.addView(webBtn, new LinearLayout.LayoutParams(0, dp(50), 1));
            controls.addView(qrBtn, new LinearLayout.LayoutParams(0, dp(50), 1));
            addView(controls);

            startBtn.setOnClickListener(v -> {
                if (WebServerService.running) stopServer();
                else startServer();
            });
            webBtn.setOnClickListener(v -> openBrowser(displayUrl()));
            qrBtn.setOnClickListener(v -> showQrDialog(displayUrl()));

            post(this::tick);
        }

        void tick() {
            boolean on = WebServerService.running;
            if (on && !running) sequence = System.currentTimeMillis();
            running = on;

            boolean isPublic = p.getBoolean("publicMode", true);
            boolean tunnelActive = TunnelManager.getInstance().isTunnelRunning();

            if (on) {
                if (isPublic && tunnelActive) {
                    state.setText("🟢  PUBLIC SERVER ONLINE (GLOBAL)");
                    state.setTextColor(GREEN);
                } else if (isPublic) {
                    state.setText("🟡  CONNECTING PUBLIC TUNNEL...");
                    state.setTextColor(YELLOW);
                } else {
                    state.setText("🟢  LOCAL WI-FI SERVER ONLINE");
                    state.setTextColor(CYAN);
                }
            } else {
                state.setText("🔴  SERVER OFFLINE");
                state.setTextColor(RED);
            }

            url.setText(displayUrl() + "\n🔗 Tap URL to copy or share");
            startBtn.setText(on ? "■  STOP SERVER" : "▶  START SERVER");
            startBtn.setBackground(on ? bg(Color.rgb(87, 18, 34), 15) : grad(15));
            webBtn.setBackground(bg(Color.rgb(4, 68, 88), 15));
            qrBtn.setBackground(grad(15));
            invalidate();
            postDelayed(this::tick, 150);
        }

        protected void onMeasure(int ws, int hs) {
            int w = MeasureSpec.getSize(ws);
            state.measure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(dp(44), MeasureSpec.EXACTLY));
            url.measure(MeasureSpec.makeMeasureSpec(w - dp(24), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(dp(56), MeasureSpec.EXACTLY));
            ViewGroup controls = (ViewGroup) getChildAt(2);
            controls.measure(MeasureSpec.makeMeasureSpec(w - dp(24), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(dp(50), MeasureSpec.EXACTLY));
            setMeasuredDimension(w, dp(310));
        }

        protected void onLayout(boolean c, int l, int t, int r, int b) {
            int w = r - l;
            state.layout(0, dp(70), w, dp(114));
            url.layout(dp(12), dp(128), w - dp(12), dp(184));
            View controls = getChildAt(2);
            controls.layout(dp(12), dp(200), w - dp(12), dp(250));
        }

        protected void onDraw(Canvas c) {
            super.onDraw(c);
            float y = dp(40), gap = getWidth() / 8f;
            long age = System.currentTimeMillis() - sequence;
            int active = running ? Math.min(7, (int) (age / 200) + 1) : 0;
            for (int i = 0; i < 7; i++) {
                float x = gap * (i + 1);
                int col = running ? (i < active ? LED[i] : Color.rgb(28, 46, 62)) : (i == 6 ? RED : Color.rgb(26, 36, 48));
                paint.setColor(col);
                if (running && i < active) {
                    float pulse = (float) (0.55 + 0.45 * Math.sin(System.currentTimeMillis() / 200.0 + i * 0.8));
                    paint.setShadowLayer(dp(6 + 8 * pulse), 0, 0, col);
                } else if (!running && i == 6) {
                    paint.setShadowLayer(dp(4), 0, 0, RED);
                }
                c.drawCircle(x, y, dp(9), paint);
                paint.clearShadowLayer();
            }
            if (running) postInvalidateDelayed(60);
        }
    }

    @Override
    protected void onDestroy() {
        h.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
