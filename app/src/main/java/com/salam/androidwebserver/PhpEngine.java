package com.salam.androidwebserver;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PhpEngine {

    public static String execute(File phpFile, String method, String query, byte[] postBody, Map<String, String> headers, File webRoot) {
        try {
            String source = WebServerService.readText(phpFile);
            return executeSource(source, phpFile.getName(), method, query, postBody, headers, webRoot, phpFile.getParentFile());
        } catch (Exception e) {
            return "<html><body><h2 style='color:red'>PHP Execution Error</h2><pre>" + e.getMessage() + "</pre></body></html>";
        }
    }

    public static String executeSource(String source, String scriptName, String method, String query, byte[] postBody, Map<String, String> headers, File webRoot, File workingDir) {
        Map<String, String> getParams = parseQuery(query);
        Map<String, String> postParams = parsePost(postBody);
        Map<String, String> serverVars = new HashMap<>();

        serverVars.put("PHP_VERSION", "8.2.14-SalamAndroidCGI");
        serverVars.put("SERVER_SOFTWARE", "Salam Cyber Server Engine/V10 (Android Linux)");
        serverVars.put("REQUEST_METHOD", method != null ? method : "GET");
        serverVars.put("QUERY_STRING", query != null ? query : "");
        serverVars.put("SCRIPT_NAME", scriptName != null ? scriptName : "index.php");
        serverVars.put("DOCUMENT_ROOT", webRoot != null ? webRoot.getAbsolutePath() : "/");
        if (headers != null) {
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                String key = "HTTP_" + entry.getKey().toUpperCase(Locale.US).replace('-', '_');
                serverVars.put(key, entry.getValue());
            }
        }

        Map<String, Object> variables = new HashMap<>();
        variables.put("_GET", getParams);
        variables.put("_POST", postParams);
        variables.put("_SERVER", serverVars);
        variables.put("_REQUEST", combineMaps(getParams, postParams));

        Pattern pattern = Pattern.compile("<\\?(?:php)?([\\s\\S]*?)\\?>", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(source);
        StringBuffer output = new StringBuffer();

        while (matcher.find()) {
            String phpCode = matcher.group(1).trim();
            String evaluated = runPhpSnippet(phpCode, variables, workingDir);
            matcher.appendReplacement(output, Matcher.quoteReplacement(evaluated));
        }
        matcher.appendTail(output);

        return output.toString();
    }

    private static String runPhpSnippet(String code, Map<String, Object> vars, File workingDir) {
        StringBuilder out = new StringBuilder();
        String[] lines = code.split("\n");

        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith("//") || line.startsWith("#")) continue;

            // phpinfo()
            if (line.contains("phpinfo()") || line.contains("phpinfo ()")) {
                out.append(renderPhpInfo(vars));
                continue;
            }

            // echo or print statement
            if (line.startsWith("echo ") || line.startsWith("print ") || line.startsWith("echo(") || line.startsWith("print(")) {
                String expr = line.replaceFirst("^(echo|print)\\s*", "").replaceFirst(";\\s*$", "").trim();
                if (expr.startsWith("(") && expr.endsWith(")")) expr = expr.substring(1, expr.length() - 1).trim();
                String val = evaluateExpression(expr, vars);
                out.append(val);
                continue;
            }

            // var_dump / print_r
            if (line.startsWith("var_dump(") || line.startsWith("print_r(")) {
                Pattern p = Pattern.compile("^(?:var_dump|print_r)\\((.*)\\);?$");
                Matcher m = p.matcher(line);
                if (m.find()) {
                    String arg = m.group(1).trim();
                    out.append("<pre>").append(evaluateExpression(arg, vars)).append("</pre>");
                }
                continue;
            }

            // include or require
            if (line.startsWith("include ") || line.startsWith("require ") || line.startsWith("include_once ") || line.startsWith("require_once ")) {
                String targetFile = line.replaceFirst("^(include|require|include_once|require_once)\\s*", "")
                        .replaceFirst(";\\s*$", "").replace("'", "").replace("\"", "").trim();
                File f = new File(workingDir, targetFile);
                if (f.exists() && f.isFile()) {
                    try {
                        String inc = WebServerService.readText(f);
                        out.append(executeSource(inc, f.getName(), "GET", "", null, null, workingDir, workingDir));
                    } catch (Exception e) {
                        out.append("<!-- Include Error: ").append(e.getMessage()).append(" -->");
                    }
                }
                continue;
            }

            // variable assignment: $var = "value";
            if (line.startsWith("$") && line.contains("=")) {
                int eqIdx = line.indexOf('=');
                String varName = line.substring(1, eqIdx).trim();
                String expr = line.substring(eqIdx + 1).replaceFirst(";\\s*$", "").trim();
                String val = evaluateExpression(expr, vars);
                vars.put(varName, val);
                continue;
            }
        }

        return out.toString();
    }

    private static String evaluateExpression(String expr, Map<String, Object> vars) {
        if (expr == null || expr.isEmpty()) return "";
        expr = expr.trim();

        // Concatenation with .
        if (expr.contains(" . ")) {
            String[] parts = expr.split(" \\. ");
            StringBuilder sb = new StringBuilder();
            for (String p : parts) sb.append(evaluateExpression(p.trim(), vars));
            return sb.toString();
        }

        // date()
        if (expr.startsWith("date(") && expr.endsWith(")")) {
            String format = expr.substring(5, expr.length() - 1).replace("\"", "").replace("'", "").trim();
            if (format.isEmpty()) format = "Y-m-d H:i:s";
            format = format.replace("Y", "yyyy").replace("m", "MM").replace("d", "dd").replace("H", "HH").replace("i", "mm").replace("s", "ss");
            try {
                return new SimpleDateFormat(format, Locale.US).format(new Date());
            } catch (Exception e) {
                return new Date().toString();
            }
        }

        // time()
        if (expr.equals("time()") || expr.equals("time ()")) {
            return String.valueOf(System.currentTimeMillis() / 1000);
        }

        // md5 / sha1
        if (expr.startsWith("md5(") && expr.endsWith(")")) {
            String inner = evaluateExpression(expr.substring(4, expr.length() - 1).trim(), vars);
            return NetworkTools.hash(inner, "MD5");
        }
        if (expr.startsWith("sha1(") && expr.endsWith(")")) {
            String inner = evaluateExpression(expr.substring(5, expr.length() - 1).trim(), vars);
            return NetworkTools.hash(inner, "SHA-1");
        }

        // Superglobals: $_GET['x'], $_POST['y'], $_SERVER['z']
        Pattern superGlobalPattern = Pattern.compile("^\\$(_GET|_POST|_SERVER|_REQUEST)\\[['\"]?([a-zA-Z0-9_-]+)['\"]?\\]$");
        Matcher sgMatch = superGlobalPattern.matcher(expr);
        if (sgMatch.find()) {
            String globalName = sgMatch.group(1);
            String key = sgMatch.group(2);
            Object mapObj = vars.get(globalName);
            if (mapObj instanceof Map) {
                Object v = ((Map<?, ?>) mapObj).get(key);
                return v != null ? v.toString() : "";
            }
            return "";
        }

        // Normal variable: $name
        if (expr.startsWith("$") && expr.length() > 1 && !expr.contains(" ")) {
            String varName = expr.substring(1);
            Object val = vars.get(varName);
            return val != null ? val.toString() : "";
        }

        // Quoted string literal
        if ((expr.startsWith("\"") && expr.endsWith("\"")) || (expr.startsWith("'") && expr.endsWith("'"))) {
            String raw = expr.substring(1, expr.length() - 1);
            // Replace embedded variables in double quotes
            if (expr.startsWith("\"")) {
                for (Map.Entry<String, Object> e : vars.entrySet()) {
                    if (e.getValue() != null && !(e.getValue() instanceof Map)) {
                        raw = raw.replace("$" + e.getKey(), e.getValue().toString());
                    }
                }
            }
            return raw;
        }

        return expr;
    }

    public static String renderPhpInfo(Map<String, Object> vars) {
        StringBuilder h = new StringBuilder();
        h.append("<div style='font-family:system-ui,sans-serif;background:#0c192c;color:#fff;padding:24px;border-radius:16px;border:1px solid #1f3b60;margin:20px 0;'>");
        h.append("<h1 style='color:#77aaff;margin:0 0 8px;font-size:26px;'>🐘 PHP 8.2.14 FastCGI Engine (Salam Android Server)</h1>");
        h.append("<p style='color:#a0c0e0;font-size:14px;margin-bottom:20px;'>High-Performance Lightweight PHP Virtual Runtime for Android & Cyber Web Hosting</p>");

        h.append("<table style='width:100%;border-collapse:collapse;font-size:13px;'>");
        h.append("<tr style='background:#162a47;'><th style='padding:8px;text-align:left;'>Directive</th><th style='padding:8px;text-align:left;'>Local Value</th></tr>");

        String[][] infoTable = {
                {"PHP Version", "8.2.14 (Salam Server Embedded Core)"},
                {"Server Architecture", System.getProperty("os.arch", "ARM64-v8a")},
                {"Operating System", "Android Linux (" + android.os.Build.VERSION.RELEASE + ", API " + android.os.Build.VERSION.SDK_INT + ")"},
                {"Memory Limit", "512M"},
                {"Max Execution Time", "300s"},
                {"Display Errors", "On"},
                {"Superglobals Supported", "$_GET, $_POST, $_SERVER, $_REQUEST"},
                {"Crypto Algorithms", "MD5, SHA-1, SHA-256, SHA-512, Base64"},
                {"Active Host System", "Salam Cyber Host Engine V10"}
        };

        for (String[] row : infoTable) {
            h.append("<tr style='border-bottom:1px solid #1a3456;'>");
            h.append("<td style='padding:8px;color:#66ddff;font-weight:bold;'>").append(row[0]).append("</td>");
            h.append("<td style='padding:8px;color:#e0f0ff;'>").append(row[1]).append("</td>");
            h.append("</tr>");
        }
        h.append("</table>");
        h.append("</div>");
        return h.toString();
    }

    private static Map<String, String> parseQuery(String query) {
        Map<String, String> map = new HashMap<>();
        if (query == null || query.isEmpty()) return map;
        String[] pairs = query.split("&");
        for (String p : pairs) {
            int idx = p.indexOf('=');
            if (idx > 0) {
                try {
                    String k = java.net.URLDecoder.decode(p.substring(0, idx), "UTF-8");
                    String v = java.net.URLDecoder.decode(p.substring(idx + 1), "UTF-8");
                    map.put(k, v);
                } catch (Exception ignored) {}
            }
        }
        return map;
    }

    private static Map<String, String> parsePost(byte[] body) {
        Map<String, String> map = new HashMap<>();
        if (body == null || body.length == 0) return map;
        try {
            String str = new String(body, StandardCharsets.UTF_8);
            return parseQuery(str);
        } catch (Exception e) {
            return map;
        }
    }

    private static Map<String, String> combineMaps(Map<String, String> a, Map<String, String> b) {
        Map<String, String> combined = new HashMap<>(a);
        combined.putAll(b);
        return combined;
    }
}
