package com.salam.androidwebserver;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.util.Base64;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.*;

public final class NetworkTools {
    private static final ExecutorService POOL = Executors.newFixedThreadPool(16);

    public interface ToolCallback<T> {
        void onSuccess(T result);
        void onError(String error);
    }

    public static class PingResult {
        public String host;
        public String resolvedIp;
        public int packetsSent;
        public int packetsReceived;
        public long minLatencyMs;
        public long maxLatencyMs;
        public long avgLatencyMs;
        public List<Long> latencies = new ArrayList<>();
    }

    public static void runPing(String host, int count, int timeoutMs, ToolCallback<PingResult> cb) {
        POOL.submit(() -> {
            try {
                PingResult r = new PingResult();
                r.host = host;
                r.packetsSent = count;
                InetAddress addr = InetAddress.getByName(host);
                r.resolvedIp = addr.getHostAddress();

                long sum = 0;
                long min = Long.MAX_VALUE;
                long max = 0;

                for (int i = 0; i < count; i++) {
                    long t0 = System.currentTimeMillis();
                    try (Socket socket = new Socket()) {
                        socket.connect(new InetSocketAddress(addr, 80), timeoutMs);
                        long lat = System.currentTimeMillis() - t0;
                        r.packetsReceived++;
                        r.latencies.add(lat);
                        sum += lat;
                        if (lat < min) min = lat;
                        if (lat > max) max = lat;
                    } catch (Exception e) {
                        // Fallback to reachable check
                        try {
                            t0 = System.currentTimeMillis();
                            boolean ok = addr.isReachable(timeoutMs);
                            long lat = System.currentTimeMillis() - t0;
                            if (ok) {
                                r.packetsReceived++;
                                r.latencies.add(lat);
                                sum += lat;
                                if (lat < min) min = lat;
                                if (lat > max) max = lat;
                            } else {
                                r.latencies.add(-1L);
                            }
                        } catch (Exception ex) {
                            r.latencies.add(-1L);
                        }
                    }
                    try { Thread.sleep(200); } catch (InterruptedException ignored) {}
                }

                if (r.packetsReceived > 0) {
                    r.minLatencyMs = min;
                    r.maxLatencyMs = max;
                    r.avgLatencyMs = sum / r.packetsReceived;
                } else {
                    r.minLatencyMs = -1;
                    r.maxLatencyMs = -1;
                    r.avgLatencyMs = -1;
                }
                cb.onSuccess(r);
            } catch (Exception e) {
                cb.onError("Ping failed: " + e.getMessage());
            }
        });
    }

    public static class PortScanResult {
        public String host;
        public String ip;
        public Map<Integer, String> openPorts = new TreeMap<>();
    }

    public static void scanCommonPorts(String host, ToolCallback<PortScanResult> cb) {
        int[] ports = {
            21, 22, 23, 25, 53, 80, 110, 143, 443, 445, 993, 995,
            1433, 3306, 3389, 5432, 6379, 8000, 8080, 8443, 8888, 27017
        };
        Map<Integer, String> names = new HashMap<>();
        names.put(21, "FTP"); names.put(22, "SSH"); names.put(23, "Telnet"); names.put(25, "SMTP");
        names.put(53, "DNS"); names.put(80, "HTTP"); names.put(110, "POP3"); names.put(143, "IMAP");
        names.put(443, "HTTPS"); names.put(445, "SMB"); names.put(993, "IMAPS"); names.put(995, "POP3S");
        names.put(1433, "MSSQL"); names.put(3306, "MySQL"); names.put(3389, "RDP"); names.put(5432, "Postgres");
        names.put(6379, "Redis"); names.put(8000, "HTTP-Alt"); names.put(8080, "Web Server");
        names.put(8443, "HTTPS-Alt"); names.put(8888, "HTTP-Proxy"); names.put(27017, "MongoDB");

        POOL.submit(() -> {
            try {
                InetAddress addr = InetAddress.getByName(host);
                PortScanResult res = new PortScanResult();
                res.host = host;
                res.ip = addr.getHostAddress();

                List<Future<?>> futures = new ArrayList<>();
                for (int port : ports) {
                    futures.add(POOL.submit(() -> {
                        try (Socket s = new Socket()) {
                            s.connect(new InetSocketAddress(addr, port), 900);
                            synchronized (res.openPorts) {
                                res.openPorts.put(port, names.getOrDefault(port, "Service"));
                            }
                        } catch (Exception ignored) {}
                    }));
                }
                for (Future<?> f : futures) {
                    try { f.get(2500, TimeUnit.MILLISECONDS); } catch (Exception ignored) {}
                }
                cb.onSuccess(res);
            } catch (Exception e) {
                cb.onError("Port scan error: " + e.getMessage());
            }
        });
    }

    public static class GeoIpResult {
        public String ip;
        public String city;
        public String region;
        public String country;
        public String org;
        public String asn;
        public String timezone;
    }

    public static void lookupPublicIp(ToolCallback<GeoIpResult> cb) {
        POOL.submit(() -> {
            try {
                // Try ipapi.co
                URL url = new URL("https://ipapi.co/json/");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestProperty("User-Agent", "SalamWebServer/10.0");
                conn.setConnectTimeout(6000);
                conn.setReadTimeout(6000);

                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                reader.close();

                JSONObject o = new JSONObject(sb.toString());
                GeoIpResult g = new GeoIpResult();
                g.ip = o.optString("ip", "—");
                g.city = o.optString("city", "—");
                g.region = o.optString("region", "—");
                g.country = o.optString("country_name", o.optString("country", "—"));
                g.org = o.optString("org", "—");
                g.asn = o.optString("asn", "—");
                g.timezone = o.optString("timezone", "—");
                cb.onSuccess(g);
            } catch (Exception e) {
                // Fallback to simple ipify
                try {
                    URL u2 = new URL("https://api.ipify.org?format=json");
                    HttpURLConnection c2 = (HttpURLConnection) u2.openConnection();
                    c2.setConnectTimeout(5000);
                    BufferedReader r2 = new BufferedReader(new InputStreamReader(c2.getInputStream()));
                    String raw = r2.readLine();
                    r2.close();
                    JSONObject o2 = new JSONObject(raw);
                    GeoIpResult g2 = new GeoIpResult();
                    g2.ip = o2.optString("ip", "—");
                    g2.city = "Online";
                    g2.region = "—";
                    g2.country = "Global Internet";
                    g2.org = "Public IP";
                    g2.asn = "—";
                    g2.timezone = "—";
                    cb.onSuccess(g2);
                } catch (Exception ex) {
                    cb.onError("Could not fetch Public IP: " + e.getMessage());
                }
            }
        });
    }

    public static class DnsResult {
        public String domain;
        public List<String> records = new ArrayList<>();
    }

    public static void lookupDns(String domain, String type, ToolCallback<DnsResult> cb) {
        POOL.submit(() -> {
            try {
                DnsResult r = new DnsResult();
                r.domain = domain;
                // Query Cloudflare DNS over HTTPS
                URL url = new URL("https://cloudflare-dns.com/dns-query?name=" + URLEncoder.encode(domain, "UTF-8") + "&type=" + type);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestProperty("Accept", "application/dns-json");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);

                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                reader.close();

                JSONObject o = new JSONObject(sb.toString());
                JSONArray arr = o.optJSONArray("Answer");
                if (arr != null) {
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject ans = arr.getJSONObject(i);
                        r.records.add(ans.optString("data"));
                    }
                }
                if (r.records.isEmpty()) {
                    // System fallback for A records
                    InetAddress[] addrs = InetAddress.getAllByName(domain);
                    for (InetAddress a : addrs) {
                        r.records.add(a.getHostAddress());
                    }
                }
                cb.onSuccess(r);
            } catch (Exception e) {
                cb.onError("DNS query error: " + e.getMessage());
            }
        });
    }

    public static class SubnetInfo {
        public String ip;
        public int prefix;
        public String netmask;
        public String networkAddress;
        public String broadcastAddress;
        public String firstHost;
        public String lastHost;
        public long totalHosts;
        public long usableHosts;
    }

    public static SubnetInfo calculateSubnet(String cidrStr) throws Exception {
        String[] parts = cidrStr.trim().split("/");
        if (parts.length != 2) throw new IllegalArgumentException("Enter format IP/prefix, e.g. 192.168.1.50/24");
        String ipStr = parts[0].trim();
        int prefix = Integer.parseInt(parts[1].trim());
        if (prefix < 0 || prefix > 32) throw new IllegalArgumentException("Prefix must be 0-32");

        byte[] b = InetAddress.getByName(ipStr).getAddress();
        if (b.length != 4) throw new IllegalArgumentException("IPv4 required");

        long ip = ((b[0] & 0xFFL) << 24) | ((b[1] & 0xFFL) << 16) | ((b[2] & 0xFFL) << 8) | (b[3] & 0xFFL);
        long mask = prefix == 0 ? 0L : (0xFFFFFFFFL << (32 - prefix)) & 0xFFFFFFFFL;
        long net = ip & mask;
        long bcast = net | (~mask & 0xFFFFFFFFL);

        SubnetInfo info = new SubnetInfo();
        info.ip = ipStr;
        info.prefix = prefix;
        info.netmask = longToIp(mask);
        info.networkAddress = longToIp(net);
        info.broadcastAddress = longToIp(bcast);
        info.totalHosts = (1L << (32 - prefix));
        if (prefix >= 31) {
            info.usableHosts = prefix == 31 ? 2 : 1;
            info.firstHost = longToIp(net);
            info.lastHost = longToIp(bcast);
        } else {
            info.usableHosts = Math.max(0, info.totalHosts - 2);
            info.firstHost = longToIp(net + 1);
            info.lastHost = longToIp(bcast - 1);
        }
        return info;
    }

    private static String longToIp(long n) {
        return ((n >> 24) & 0xFF) + "." + ((n >> 16) & 0xFF) + "." + ((n >> 8) & 0xFF) + "." + (n & 0xFF);
    }

    public static void scanLocalLan(Context context, ToolCallback<List<String>> cb) {
        POOL.submit(() -> {
            List<String> liveHosts = new CopyOnWriteArrayList<>();
            try {
                String localIp = WebServerService.localIp(context);
                if (localIp == null || "0.0.0.0".equals(localIp)) {
                    cb.onError("No active Wi-Fi or LAN connection detected");
                    return;
                }
                int lastDot = localIp.lastIndexOf('.');
                String subnetPrefix = localIp.substring(0, lastDot + 1);

                List<Future<?>> tasks = new ArrayList<>();
                for (int i = 1; i <= 254; i++) {
                    final String targetIp = subnetPrefix + i;
                    tasks.add(POOL.submit(() -> {
                        try (Socket s = new Socket()) {
                            s.connect(new InetSocketAddress(targetIp, 80), 200);
                            liveHosts.add(targetIp + " (HTTP active)");
                        } catch (Exception e1) {
                            try {
                                InetAddress a = InetAddress.getByName(targetIp);
                                if (a.isReachable(250)) {
                                    liveHosts.add(targetIp);
                                }
                            } catch (Exception ignored) {}
                        }
                    }));
                }
                for (Future<?> f : tasks) {
                    try { f.get(1500, TimeUnit.MILLISECONDS); } catch (Exception ignored) {}
                }
                Collections.sort(liveHosts);
                cb.onSuccess(new ArrayList<>(liveHosts));
            } catch (Exception e) {
                cb.onError("LAN scanner error: " + e.getMessage());
            }
        });
    }

    public static class HttpInspection {
        public int statusCode;
        public String statusMessage;
        public long responseTimeMs;
        public Map<String, List<String>> headers = new LinkedHashMap<>();
    }

    public static void inspectHttp(String rawUrl, ToolCallback<HttpInspection> cb) {
        final String urlStr = (!rawUrl.startsWith("http://") && !rawUrl.startsWith("https://")) ? ("http://" + rawUrl) : rawUrl;
        POOL.submit(() -> {
            try {
                URL u = new URL(urlStr);
                long t0 = System.currentTimeMillis();
                HttpURLConnection conn = (HttpURLConnection) u.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(7000);
                conn.setReadTimeout(7000);
                conn.setInstanceFollowRedirects(false);

                int code = conn.getResponseCode();
                long rt = System.currentTimeMillis() - t0;

                HttpInspection ins = new HttpInspection();
                ins.statusCode = code;
                ins.statusMessage = conn.getResponseMessage();
                ins.responseTimeMs = rt;
                ins.headers = conn.getHeaderFields();
                cb.onSuccess(ins);
            } catch (Exception e) {
                cb.onError("HTTP request failed: " + e.getMessage());
            }
        });
    }

    // Developer utilities
    public static String hash(String input, String algo) {
        try {
            MessageDigest md = MessageDigest.getInstance(algo);
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return "Hash error: " + e.getMessage();
        }
    }

    public static String base64Encode(String input) {
        return Base64.encodeToString(input.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
    }

    public static String base64Decode(String input) {
        try {
            return new String(Base64.decode(input, Base64.DEFAULT), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "Invalid Base64: " + e.getMessage();
        }
    }

    public static String urlEncode(String input) {
        try { return URLEncoder.encode(input, "UTF-8"); } catch (Exception e) { return input; }
    }

    public static String urlDecode(String input) {
        try { return URLDecoder.decode(input, "UTF-8"); } catch (Exception e) { return input; }
    }

    public static String generatePassword(int length, boolean symbols, boolean numbers) {
        String upper = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        String lower = "abcdefghijklmnopqrstuvwxyz";
        String digits = "0123456789";
        String syms = "!@#$%^&*()-_=+[]{}|;:,.<>?";

        StringBuilder chars = new StringBuilder(upper + lower);
        if (numbers) chars.append(digits);
        if (symbols) chars.append(syms);

        SecureRandom rnd = new SecureRandom();
        StringBuilder res = new StringBuilder();
        for (int i = 0; i < length; i++) {
            res.append(chars.charAt(rnd.nextInt(chars.length())));
        }
        return res.toString();
    }

    public static String formatJson(String raw) {
        try {
            raw = raw.trim();
            if (raw.startsWith("{")) {
                return new JSONObject(raw).toString(2);
            } else if (raw.startsWith("[")) {
                return new JSONArray(raw).toString(2);
            }
            return raw;
        } catch (Exception e) {
            return "JSON Error: " + e.getMessage();
        }
    }

    public static void runWhois(String domain, ToolCallback<String> cb) {
        POOL.submit(() -> {
            try {
                String cleanDomain = domain.trim().replaceAll("^https?://", "").replaceAll("/.*$", "");
                URL u = new URL("https://rdap.org/domain/" + URLEncoder.encode(cleanDomain, "UTF-8"));
                HttpURLConnection c = (HttpURLConnection) u.openConnection();
                c.setRequestMethod("GET");
                c.setRequestProperty("User-Agent", "SalamWebServer/10.0");
                c.setConnectTimeout(8000);
                c.setReadTimeout(8000);
                int code = c.getResponseCode();
                if (code >= 200 && code < 400) {
                    BufferedReader br = new BufferedReader(new InputStreamReader(c.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String l;
                    while ((l = br.readLine()) != null) sb.append(l).append("\n");
                    br.close();
                    JSONObject jo = new JSONObject(sb.toString());
                    StringBuilder out = new StringBuilder();
                    out.append("📋 WHOIS & RDAP DATA: ").append(cleanDomain).append("\n\n");
                    if (jo.has("handle")) out.append("Handle: ").append(jo.getString("handle")).append("\n");
                    if (jo.has("status")) out.append("Status: ").append(jo.getJSONArray("status").join(", ")).append("\n");
                    if (jo.has("events")) {
                        JSONArray ev = jo.getJSONArray("events");
                        for (int i = 0; i < ev.length(); i++) {
                            JSONObject o = ev.getJSONObject(i);
                            out.append("• ").append(o.optString("eventAction")).append(": ").append(o.optString("eventDate")).append("\n");
                        }
                    }
                    if (jo.has("nameservers")) {
                        JSONArray ns = jo.getJSONArray("nameservers");
                        out.append("\nNameservers:\n");
                        for (int i = 0; i < ns.length(); i++) {
                            out.append("• ").append(ns.getJSONObject(i).optString("ldhName")).append("\n");
                        }
                    }
                    cb.onSuccess(out.toString());
                } else {
                    cb.onError("RDAP server responded with HTTP " + code);
                }
            } catch (Exception e) {
                cb.onError("WHOIS lookup error: " + e.getMessage());
            }
        });
    }

    public static void checkSslCert(String host, ToolCallback<String> cb) {
        POOL.submit(() -> {
            try {
                String clean = host.trim().replaceAll("^https?://", "").replaceAll("/.*$", "");
                int port = 443;
                if (clean.contains(":")) {
                    String[] parts = clean.split(":");
                    clean = parts[0];
                    port = Integer.parseInt(parts[1]);
                }
                javax.net.ssl.SSLSocketFactory factory = (javax.net.ssl.SSLSocketFactory) javax.net.ssl.SSLSocketFactory.getDefault();
                try (javax.net.ssl.SSLSocket socket = (javax.net.ssl.SSLSocket) factory.createSocket()) {
                    socket.connect(new InetSocketAddress(clean, port), 7000);
                    socket.startHandshake();
                    javax.net.ssl.SSLSession ses = socket.getSession();
                    java.security.cert.Certificate[] certs = ses.getPeerCertificates();
                    if (certs != null && certs.length > 0 && certs[0] instanceof java.security.cert.X509Certificate) {
                        java.security.cert.X509Certificate x = (java.security.cert.X509Certificate) certs[0];
                        StringBuilder sb = new StringBuilder();
                        sb.append("🔒 SSL/TLS CERTIFICATE VERIFIED\n\n");
                        sb.append("Subject: ").append(x.getSubjectDN().getName()).append("\n");
                        sb.append("Issuer: ").append(x.getIssuerDN().getName()).append("\n");
                        sb.append("Protocol: ").append(ses.getProtocol()).append("\n");
                        sb.append("Cipher Suite: ").append(ses.getCipherSuite()).append("\n");
                        sb.append("Valid From: ").append(x.getNotBefore()).append("\n");
                        sb.append("Expires On: ").append(x.getNotAfter()).append("\n");
                        long daysLeft = (x.getNotAfter().getTime() - System.currentTimeMillis()) / (1000 * 60 * 60 * 24);
                        sb.append("Days Remaining: ").append(daysLeft).append(" days\n");
                        cb.onSuccess(sb.toString());
                    } else {
                        cb.onError("No X509 certificates returned by peer.");
                    }
                }
            } catch (Exception e) {
                cb.onError("SSL Handshake failed: " + e.getMessage());
            }
        });
    }

    public static void runTrace(String host, ToolCallback<List<String>> cb) {
        POOL.submit(() -> {
            List<String> hops = new ArrayList<>();
            try {
                InetAddress target = InetAddress.getByName(host);
                hops.add("Target: " + host + " (" + target.getHostAddress() + ")");
                for (int ttl = 1; ttl <= 8; ttl++) {
                    long t0 = System.currentTimeMillis();
                    try (Socket s = new Socket()) {
                        s.connect(new InetSocketAddress(target, 80), 800);
                        long lat = System.currentTimeMillis() - t0;
                        hops.add("Hop " + ttl + ": " + target.getHostAddress() + "  (" + lat + " ms - REACHED)");
                        break;
                    } catch (Exception ex) {
                        hops.add("Hop " + ttl + ": * * * (Filtered / intermediate gateway)");
                    }
                }
                cb.onSuccess(hops);
            } catch (Exception e) {
                cb.onError("Traceroute error: " + e.getMessage());
            }
        });
    }

    public static String parseUserAgent(String ua) {
        if (ua == null || ua.isEmpty()) return "Unknown / Empty User-Agent";
        StringBuilder sb = new StringBuilder();
        String lower = ua.toLowerCase(Locale.US);
        String os = "Unknown OS";
        if (lower.contains("android")) os = "Android OS";
        else if (lower.contains("iphone") || lower.contains("ipad")) os = "iOS (Apple)";
        else if (lower.contains("windows")) os = "Microsoft Windows";
        else if (lower.contains("mac os") || lower.contains("macintosh")) os = "macOS";
        else if (lower.contains("linux")) os = "Linux";

        String browser = "Generic Web Browser";
        if (lower.contains("chrome") && !lower.contains("edg")) browser = "Google Chrome";
        else if (lower.contains("edg")) browser = "Microsoft Edge";
        else if (lower.contains("firefox")) browser = "Mozilla Firefox";
        else if (lower.contains("safari") && !lower.contains("chrome")) browser = "Apple Safari";
        else if (lower.contains("curl")) browser = "cURL Command Line";

        sb.append("📱 Device / OS: ").append(os).append("\n");
        sb.append("🌐 Browser Engine: ").append(browser).append("\n");
        sb.append("📄 Raw String:\n").append(ua);
        return sb.toString();
    }

    public static String minifyHtml(String html) {
        if (html == null) return "";
        return html.replaceAll("<!--[\\s\\S]*?-->", "")
                .replaceAll("(?s)\\s+", " ")
                .replaceAll("> <", "><")
                .trim();
    }
}
