package com.salam.androidwebserver;

import java.util.ArrayList;
import java.util.List;

public final class FeatureCatalog {

    public static class ToolEntry {
        public final String icon;
        public final String title;
        public final String subtitle;
        public final String category;
        public final String actionKey;

        public ToolEntry(String icon, String title, String subtitle, String category, String actionKey) {
            this.icon = icon;
            this.title = title;
            this.subtitle = subtitle;
            this.category = category;
            this.actionKey = actionKey;
        }
    }

    public static final String CAT_ALL = "All Tools (100+)";
    public static final String CAT_NETWORK = "🌐 Network & Tunnel";
    public static final String CAT_SECURITY = "🛡️ Security & Firewall";
    public static final String CAT_SERVER = "⚙️ Server & 24/7 Core";
    public static final String CAT_MAINTENANCE = "📁 File & Maintenance";
    public static final String CAT_ANALYTICS = "📊 Visitor Analytics";
    public static final String CAT_DEV = "💻 Web Dev & API";
    public static final String CAT_ADMIN = "🛠️ System Diagnostics";

    public static final String[] CATEGORIES = {
        CAT_ALL, CAT_NETWORK, CAT_SECURITY, CAT_SERVER, CAT_MAINTENANCE, CAT_ANALYTICS, CAT_DEV, CAT_ADMIN
    };

    public static final List<ToolEntry> TOOLS = new ArrayList<>();

    static {
        // --- 1. NETWORK & TUNNELING (16 tools) ---
        add("🌍", "Public Internet Tunnel", "Expose server to any network / Wi-Fi globally via HTTPS", CAT_NETWORK, "tunnel");
        add("🛰️", "Multi-Provider Tunnel Switcher", "Auto switch between Localhost.run, Pinggy, and Serveo", CAT_NETWORK, "provider");
        add("🏓", "Ping & Socket Latency Tester", "Real-time ICMP/TCP ping diagnostics with jitter calculation", CAT_NETWORK, "ping");
        add("🚪", "Multi-Threaded Port Scanner", "Scan 22 common services & custom port ranges on any host", CAT_NETWORK, "portscan");
        add("🌐", "DNS Record Query (DoH)", "Resolve A, AAAA, MX, TXT, NS, CNAME via Cloudflare DoH", CAT_NETWORK, "dns");
        add("🗺️", "Public IP & Geo-Location", "External IP, ISP, ASN, City, and Country detection", CAT_NETWORK, "publicip");
        add("📶", "Wi-Fi LAN Device Scanner", "Discover active hosts and servers on local network", CAT_NETWORK, "lanscan");
        add("🧮", "Subnet & CIDR Calculator", "Netmask, broadcast, and usable IP range calculator", CAT_NETWORK, "subnet");
        add("📡", "Network Interface Detective", "Detect Wi-Fi, Cellular Data, VPN, and Ethernet interfaces", CAT_NETWORK, "interfaces");
        add("🦆", "Dynamic DNS Updater (DuckDNS)", "Synchronize dynamic IP with free DuckDNS / No-IP subdomains", CAT_NETWORK, "ddns");
        add("🛣️", "Traceroute & Hop Visualizer", "Trace network route hops to identify connectivity bottlenecks", CAT_NETWORK, "traceroute");
        add("⚡", "Socket Speed & Throughput Test", "Measure download/upload socket transfer throughput", CAT_NETWORK, "speedtest");
        add("🧭", "Default Gateway Detective", "Inspect default gateway IP and router web configuration", CAT_NETWORK, "gateway");
        add("🔌", "Wake-on-LAN (WoL) Packet Broadcaster", "Send magic packets to wake sleep-state LAN computers", CAT_NETWORK, "wol");
        add("🔍", "WHOIS Domain & Registrar Lookup", "Query domain registration dates, nameservers, and registrar", CAT_NETWORK, "whois");
        add("🏷️", "DNS Propagation Inspector", "Verify DNS propagation across international name servers", CAT_NETWORK, "dnsprop");

        // --- 2. SECURITY & FIREWALL (16 tools) ---
        add("🔒", "Instant Kill-Switch", "Stopping server in app instantly cuts public URL access worldwide", CAT_SECURITY, "killswitch");
        add("🚫", "IP Blacklist & Access Blocker", "Instantly deny traffic from suspicious or malicious IPs", CAT_SECURITY, "blacklist");
        add("✅", "IP Allowlist Enforcement", "Restrict web server access exclusively to approved IP addresses", CAT_SECURITY, "allowlist");
        add("🚦", "Anti-DDoS Rate Limiter", "Cap max requests per minute to prevent resource exhaustion", CAT_SECURITY, "ratelimit");
        add("🔐", "Cryptographic Hasher (MD5/SHA)", "Generate MD5, SHA-1, SHA-256, and SHA-512 hashes", CAT_SECURITY, "hash");
        add("🔑", "Secure Password / Token Generator", "Cryptographic token and complex 32-character password generator", CAT_SECURITY, "passwordgen");
        add("🛡️", "HTTP Security Headers Injector", "Inspect and enforce CSP, HSTS, X-Frame, and XSS headers", CAT_SECURITY, "headers");
        add("📜", "SSL/TLS Certificate Inspector", "Verify SSL certificate validity, expiry, and TLS cipher suites", CAT_SECURITY, "ssl");
        add("🚪", "Brute-Force Shield", "Automatically block IP after multiple failed authentication attempts", CAT_SECURITY, "bruteforce");
        add("🔏", "Web Authentication Vault", "Enforce HTTP Basic Auth username & password for all visitors", CAT_SECURITY, "basicauth");
        add("🛡️", "Directory Traversal Protection", "Enforce strict root boundary checks to prevent path escapes", CAT_SECURITY, "traversal");
        add("🧩", "JWT Token Decoder & Inspector", "Decode JWT headers and claims payload with timestamp checks", CAT_DEV, "jwt");
        add("🗝️", "Base64 Cryptographic Coder", "Standard, URL-safe, and UTF-8 Base64 encode and decode", CAT_SECURITY, "base64");
        add("🔢", "Password Strength & Entropy Meter", "Calculate zxcvbn entropy bits and crack-time estimation", CAT_SECURITY, "pwdstrength");
        add("🛡️", "CORS Policy Controller", "Configure Cross-Origin Resource Sharing headers for web APIs", CAT_SECURITY, "cors");
        add("👁️", "Upload Directory Privacy Guard", "Hide uploaded directory file listings from public visitors", CAT_SECURITY, "privacy");

        // --- 3. SERVER & 24/7 CORE (16 tools) ---
        add("⚡", "24/7 WakeLock Engine", "Prevents Android OS from sleeping or throttling background server", CAT_SERVER, "wakelock");
        add("🔄", "Auto-Restart on Network Change", "Automatically re-binds sockets when switching Wi-Fi to Data", CAT_SERVER, "autorestart");
        add("🖥️", "Custom HTTP Port Selector", "Run server on custom ports (8080, 8000, 3000, 5000, 8888)", CAT_SERVER, "portchange");
        add("👥", "Concurrent Connection Manager", "Configure pool limits for maximum simultaneous socket threads", CAT_SERVER, "maxclients");
        add("🔋", "Battery Optimization Exemption", "Guide to whitelist app from Android OEM battery killers", CAT_SERVER, "battery");
        add("📱", "Native QR Code Generator", "ZXing powered scannable QR code for instant phone access", CAT_SERVER, "qrcode");
        add("🔔", "Server Status Notification Bar", "Foreground notification with one-tap stop and live public URL", CAT_SERVER, "notification");
        add("🛍️", "Deploy E-Commerce Online Store", "One-click deployable online shop with shopping cart & checkout", CAT_SERVER, "ecomtemplate");
        add("🌐", "Deploy Default Web Portal", "Restore default clean Salam web server index homepage", CAT_SERVER, "defaulttemplate");
        add("🗜️", "GZIP Compression Simulator", "Analyze compression ratio for served HTML, CSS, and JS files", CAT_SERVER, "gzip");
        add("📑", "Custom 404 & 500 Error Pages", "Serve branded, user-friendly error templates to visitors", CAT_SERVER, "customerrors");
        add("🗂️", "MIME Type Auto-Resolver", "Extensive mapping for 100+ audio, video, image, and font types", CAT_SERVER, "mimetypes");
        add("🎛️", "Live Server Configuration Dump", "Export entire server parameters to JSON configuration file", CAT_SERVER, "configdump");
        add("💡", "Animated 7-Color LED Array", "Dynamic pulsing status visualizer with mathematical wave glow", CAT_SERVER, "ledarray");
        add("🎨", "Cyber Neon Theme Engine", "Ocean Neon, Purple Night, Emerald Matrix, Sunset, Cyber Pink", CAT_SERVER, "themes");
        add("🌐", "Open in Native Browser", "Instantly open the active server URL in default Android browser", CAT_SERVER, "openbrowser");

        // --- 4. FILE & WEB MAINTENANCE (16 tools) ---
        add("🚧", "Per-File Maintenance Switch", "Toggle Maintenance Mode individually on specific HTML or code files", CAT_MAINTENANCE, "filemaint");
        add("🛑", "Global Server Maintenance Mode", "Put entire server into maintenance with custom animated 503 banner", CAT_MAINTENANCE, "globalmaint");
        add("📢", "Visitor Broadcast Notification", "Display custom alert banner message across hosted web pages", CAT_MAINTENANCE, "broadcast");
        add("✏️", "In-App Live Code Editor", "Edit HTML, CSS, JavaScript, and JSON with syntax highlights", CAT_MAINTENANCE, "editor");
        add("📂", "Create New Folder / Directory", "Organize multi-page websites and modular project structures", CAT_MAINTENANCE, "newdir");
        add("📄", "Create New Blank Web File", "Quickly create index.html, styles.css, app.js, or data.json", CAT_MAINTENANCE, "newfile");
        add("📦", "ZIP Archive Extractor", "Upload and unzip complete static websites and web templates", CAT_MAINTENANCE, "unzip");
        add("🗜️", "Compress Folder to ZIP", "Package entire web directory into downloadable ZIP backup", CAT_MAINTENANCE, "zipfolder");
        add("🖼️", "In-App Image & Media Preview", "Inspect PNG, JPG, GIF, SVG, and WebP assets directly in app", CAT_MAINTENANCE, "imgpreview");
        add("🔍", "File Search & Filter Engine", "Fast real-time substring filtering across web directory items", CAT_MAINTENANCE, "filesearch");
        add("📋", "File Duplicator & Cloner", "Clone web files for quick A/B testing and revision history", CAT_MAINTENANCE, "duplicate");
        add("🏷️", "File & Folder Renamer", "Safely rename web assets and update file extensions", CAT_MAINTENANCE, "rename");
        add("🚚", "File Move & Relocator", "Relocate files and assets between subfolders easily", CAT_MAINTENANCE, "move");
        add("🗑️", "Safe File / Folder Deletion", "Remove obsolete web files with safety confirmation prompt", CAT_MAINTENANCE, "delete");
        add("📊", "Web Storage Quota & Usage", "Inspect total megabytes consumed by web root folder", CAT_MAINTENANCE, "storagequota");
        add("🔒", "File Permissions (Chmod) Inspector", "Check read, write, and execute permissions on server files", CAT_MAINTENANCE, "fileperm");

        // --- 5. VISITOR ANALYTICS & MONITORING (16 tools) ---
        add("👥", "Real-Time Active Visitors", "Live count of connected client IP addresses and sessions", CAT_ANALYTICS, "visitors");
        add("📈", "Requests Per Minute (RPM) Meter", "Live calculated request frequency and burst monitoring", CAT_ANALYTICS, "rpm");
        add("🌊", "Live Bandwidth Flow (Rx / Tx)", "Real-time transfer speedometer (KB/s, MB/s) and total bytes", CAT_ANALYTICS, "bandwidth");
        add("🧾", "Access History & Request Stream", "Full timestamped log of incoming URLs, IPs, and HTTP methods", CAT_ANALYTICS, "history");
        add("📊", "HTTP Status Code Breakdown", "Track proportions of 200 OK, 304 Cache, 404 Missing, 503 Maint", CAT_ANALYTICS, "statuscodes");
        add("🔝", "Top Requested URLs Ranking", "Identify the most popular web pages and media assets on server", CAT_ANALYTICS, "topurls");
        add("📱", "Visitor Device & User-Agent Radar", "Parse visitor browsers (Chrome, Safari, Firefox) and OS devices", CAT_ANALYTICS, "useragent");
        add("⏱️", "Server Uptime & Session Timer", "Track exact duration since last server boot without downtime", CAT_ANALYTICS, "uptime");
        add("💓", "CPU & Memory Utilization Monitor", "Real-time RAM consumption and JVM garbage collection stats", CAT_ANALYTICS, "systemhealth");
        add("🗺️", "Visitor Geolocation Map Log", "Resolve country and city of visiting external IP addresses", CAT_ANALYTICS, "visitorgeo");
        add("🔄", "Live Request Pulse Visualizer", "Pulsing neon visual waves on each incoming HTTP hit", CAT_ANALYTICS, "pulse");
        add("📥", "Export Access Logs to CSV", "Download request history formatted for Excel or database import", CAT_ANALYTICS, "exportcsv");
        add("🧹", "Log & Traffic Flow Purger", "Reset access logs, traffic counters, and bandwidth statistics", CAT_ANALYTICS, "clearlogs");
        add("🚨", "Visitor Spike Alert System", "Triggers toast notifications when visitor thresholds are reached", CAT_ANALYTICS, "spikealert");
        add("🔍", "Active Client IP Inspector", "Detailed view of each connected client IP and byte consumption", CAT_ANALYTICS, "clientinspect");
        add("⚡", "Peak Traffic Speed Record", "Historical high-water mark for maximum bandwidth throughput", CAT_ANALYTICS, "peakspeed");

        // --- 6. WEB DEV & API UTILITIES (16 tools) ---
        add("📋", "JSON Formatter & Validator", "Beautify, format, validate and minify JSON data structures", CAT_DEV, "json");
        add("🔗", "URL Percent Encoder / Decoder", "Sanitize and decode URL-encoded parameter strings", CAT_DEV, "urlencode");
        add("🆔", "UUID / GUID v4 Generator", "Instantly generate unique UUID version 4 strings", CAT_DEV, "uuid");
        add("🔤", "HTML Minifier & Optimizer", "Strip whitespace and comments to accelerate mobile load times", CAT_DEV, "htmlmin");
        add("🎨", "CSS Beautifier & Cleaner", "Format minified stylesheet files into readable CSS blocks", CAT_DEV, "cssclean");
        add("📜", "JavaScript Syntax Validator", "Check basic JS syntax and script closure integrity", CAT_DEV, "jsvalidate");
        add("🧪", "Regex Regular Expression Tester", "Test pattern matching against custom input strings", CAT_DEV, "regex");
        add("📝", "Markdown to HTML Converter", "Compile README.md syntax directly to formatted HTML snippet", CAT_DEV, "markdown");
        add("🍪", "HTTP Cookie Inspector & Builder", "Generate Set-Cookie headers with SameSite and Secure flags", CAT_DEV, "cookies");
        add("🌐", "cURL Command Generator", "Generate copyable cURL commands to test your active server endpoints", CAT_DEV, "curlgen");
        add("🏷️", "HTML Meta Tags & SEO Builder", "Generate Open Graph, Twitter Cards, and viewport meta tags", CAT_DEV, "metatags");
        add("📱", "Favicon & App Icon Helper", "Generate HTML links for mobile web app icons and manifest", CAT_DEV, "favicon");
        add("📡", "Webhook Trigger Simulator", "Send custom HTTP POST webhooks to test remote endpoints", CAT_DEV, "webhook");
        add("⏱️", "Epoch Unix Timestamp Converter", "Convert epoch milliseconds to human-readable date & time", CAT_DEV, "timestamp");
        add("🔢", "Color Hex / RGB / HSL Converter", "Convert between CSS color formats with live preview swatch", CAT_DEV, "colorconvert");
        add("🔠", "Lorem Ipsum Dummy Text Gen", "Generate sample paragraphs and headers for web design testing", CAT_DEV, "loremipsum");

        // --- 7. SYSTEM DIAGNOSTICS & ADMIN (16 tools) ---
        add("💓", "Website Health & Uptime Checker", "Test responsiveness, response time and HTTP status of any URL", CAT_ADMIN, "webhealth");
        add("👨‍💻", "Developer Center & About", "About Abdus Salam • Contact • Messenger • Version", CAT_ADMIN, "about");
        add("📱", "Share Server Link Multi-Platform", "Share URL with friends via WhatsApp, Messenger, Telegram, SMS", CAT_ADMIN, "sharelink");
        add("⚙️", "Admin Control Panel Interface", "Direct access to internal web admin control center at /__salam__", CAT_ADMIN, "webadmin");
        add("🛡️", "Root Directory Path Inspector", "Verify exact internal storage directory location of web files", CAT_ADMIN, "rootpath");
        add("🔋", "Thermal & Power State Sensor", "Check device battery temperature, charge state, and voltage", CAT_ADMIN, "batteryhealth");
        add("💾", "JVM Heap Memory Garbage Collector", "Trigger runtime GC to reclaim server memory and optimize RAM", CAT_ADMIN, "run_gc");
        add("📦", "Complete Server Backup Engine", "Export entire website, logs, and settings into one backup package", CAT_ADMIN, "fullbackup");
        add("📥", "Restore Server from Backup", "Restore web content and configuration from previously saved package", CAT_ADMIN, "restorebackup");
        add("🔒", "Android Keystore Security Info", "Inspect cryptographic provider and SHA signature validity", CAT_ADMIN, "keystore");
        add("📋", "System Information Diagnostic", "Display Android OS version, SDK API level, architecture and SoC", CAT_ADMIN, "sysinfo");
        add("🧹", "Purge Web Browser App Cache", "Clear internal WebView cache and temporary cache storage", CAT_ADMIN, "clearcache");
        add("🔔", "Test Push Notification Alert", "Trigger test system notification to verify alerts are working", CAT_ADMIN, "testnotification");
        add("🧭", "Localhost Fallback URL Checker", "Validate 127.0.0.1 and loopback socket connectivity", CAT_ADMIN, "loopbackcheck");
        add("💡", "Toggle Status LED Effects", "Configure animation glow speed and visual rhythm settings", CAT_ADMIN, "ledconfig");
        add("🔄", "Factory Reset Web Server", "Reset all server configurations and restore starter web template", CAT_ADMIN, "factoryreset");
    }

    private static void add(String icon, String title, String subtitle, String category, String actionKey) {
        TOOLS.add(new ToolEntry(icon, title, subtitle, category, actionKey));
    }

    public static List<ToolEntry> getToolsByCategory(String category, String query) {
        List<ToolEntry> list = new ArrayList<>();
        String q = query == null ? "" : query.trim().toLowerCase(java.util.Locale.US);
        for (ToolEntry t : TOOLS) {
            boolean catMatch = CAT_ALL.equals(category) || t.category.equals(category);
            if (!catMatch) continue;
            if (q.isEmpty()) {
                list.add(t);
            } else {
                if (t.title.toLowerCase(java.util.Locale.US).contains(q) ||
                    t.subtitle.toLowerCase(java.util.Locale.US).contains(q) ||
                    t.category.toLowerCase(java.util.Locale.US).contains(q)) {
                    list.add(t);
                }
            }
        }
        return list;
    }

    private FeatureCatalog() {}
}
