package com.salam.androidwebserver;

import android.app.*;
import android.content.*;
import android.net.*;
import android.os.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.*;

public class WebServerService extends Service {
    public static volatile boolean running=false;
    public static final AtomicLong requests=new AtomicLong(0);
    public static final Set<String> clients=ConcurrentHashMap.newKeySet();
    public static final CopyOnWriteArrayList<String> LOGS=new CopyOnWriteArrayList<>();
    public static final CopyOnWriteArrayList<String> HISTORY=new CopyOnWriteArrayList<>();
    public static final Set<String> MAINTENANCE_FILES = ConcurrentHashMap.newKeySet();
    public static volatile boolean GLOBAL_MAINTENANCE = false;
    public static volatile int maxClients=32, rateLimit=120;
    public static volatile String customHost="";
    private static final ConcurrentHashMap<String,Long> RATE=new ConcurrentHashMap<>();
    private static final Object LOCK=new Object();
    private static final int MAX_LOGS=3000, MAX_HISTORY=3000;
    private static volatile long startedAt,rxBytes,txBytes;
    public static final ConcurrentHashMap<String,Long> CLIENT_FLOW=new ConcurrentHashMap<>();
    public static final ConcurrentHashMap<String,Long> CLIENT_REQS=new ConcurrentHashMap<>();
    public static final ConcurrentHashMap<String,Long> VISITOR_LAST_SEEN = new ConcurrentHashMap<>();
    private static volatile long lastSpeedCalc=0, prevRx=0, prevTx=0, curRxSpeed=0, curTxSpeed=0, peakSpeed=0;
    private PowerManager.WakeLock wakeLock;
    private static volatile ServerSocket server;
    private static ExecutorService pool=Executors.newCachedThreadPool();

    private SharedPreferences pref(){return getSharedPreferences("server",MODE_PRIVATE);}
    public static File webRoot(Context c){
        File f=new File(c.getFilesDir(),"www");
        if(!f.exists())f.mkdirs();
        File i=new File(f,"index.html");
        boolean needsExtract=!i.exists()||i.length()<20;
        if(needsExtract){
            try{
                String[] list=c.getAssets().list("web");
                if(list!=null&&list.length>0){
                    for(String file:list){
                        try(InputStream in=c.getAssets().open("web/"+file);
                            FileOutputStream out=new FileOutputStream(new File(f,file))){
                            byte[]b=new byte[8192];
                            int n;
                            while((n=in.read(b))>0)out.write(b,0,n);
                        }catch(Exception ignored){}
                    }
                }
            }catch(Exception ignored){}
            if(!i.exists())try(FileWriter w=new FileWriter(i)){
                w.write("<!doctype html><html><head><meta name='viewport' content='width=device-width,initial-scale=1'><title>Salam Web Server</title></head><body><h1>Salam Web Server</h1><p>Local server is online.</p></body></html>");
            }catch(Exception ignored){}
        }
        return f;
    }
    private File root(){return webRoot(this);}

    @Override public void onCreate(){super.onCreate();load();createChannel();}
    @Override public int onStartCommand(Intent i,int flags,int id){load();if(i!=null&&"STOP".equals(i.getAction())){stopServer();stopSelf();return START_NOT_STICKY;}if(i!=null&&"RESTART".equals(i.getAction())){stopServer();}startServer();return START_STICKY;}
    @Override public IBinder onBind(Intent i){return null;}
    @Override public void onDestroy(){stopServer();super.onDestroy();}

    private void load(){SharedPreferences p=pref();maxClients=Math.max(1,p.getInt("maxClients",32));rateLimit=Math.max(1,p.getInt("rate",120));customHost=p.getString("customUrl","").trim();}
    private void createChannel(){if(Build.VERSION.SDK_INT>=26){NotificationChannel c=new NotificationChannel("salam-server-v2","Server Status",NotificationManager.IMPORTANCE_LOW);c.setDescription("Real-time Salam Web Server Status & Address");c.setShowBadge(false);getSystemService(NotificationManager.class).createNotificationChannel(c);}}
    private Notification notification(){String url=displayUrl();boolean isPub=pref().getBoolean("publicMode",true)&&TunnelManager.getInstance().isTunnelRunning();String serverType=isPub?"Salam Public Server":(running?"Salam Local Private Server":"Salam Web Server Offline");String addrText="Server address: "+(url!=null&&!url.isEmpty()?url:"http://0.0.0.0:8080");Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(this,"salam-server-v2"):new Notification.Builder(this);Intent open=new Intent(this,MainActivity.class);PendingIntent pi=PendingIntent.getActivity(this,78,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);Intent stop=new Intent(this,WebServerService.class).setAction("STOP");PendingIntent stopPi=PendingIntent.getService(this,79,stop,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);b.setContentTitle("Salam Web Server").setContentText(addrText).setSubText(serverType).setSmallIcon(R.drawable.ic_server).setContentIntent(pi).setOngoing(true).setOnlyAlertOnce(true).setCategory(Notification.CATEGORY_SERVICE);if(Build.VERSION.SDK_INT>=24){b.setStyle(new Notification.BigTextStyle().setBigContentTitle("Salam Web Server").setSummaryText(serverType).bigText(addrText+"\n"+(isPub?"🌍 Global Public Cloud Access":"🔒 Local Wi-Fi / Private LAN Network")));}b.addAction(new Notification.Action.Builder(null,"STOP SERVER",stopPi).build());return b.build();}
    private void notifyStatus(){try{getSystemService(NotificationManager.class).notify(77,notification());}catch(Exception ignored){}}

    private void startServer(){synchronized(LOCK){if(running)return;load();int port=pref().getInt("port",8080);if(port<1024||port>65535)port=8080;try{server=new ServerSocket();server.setReuseAddress(true);server.bind(new InetSocketAddress("0.0.0.0",port),64);running=true;startedAt=System.currentTimeMillis();acquireKeepAlive();resetFlow();requests.set(0);clients.clear();RATE.clear();log("SERVER STARTED | "+displayUrl());startForeground(77,notification());pool.submit(this::acceptLoop);
        if(pref().getBoolean("publicMode",true)){
            final int p=port;
            TunnelManager.getInstance().setListener(new TunnelManager.TunnelListener(){
                @Override public void onTunnelStarting(String msg){log("TUNNEL | "+msg);notifyStatus();}
                @Override public void onTunnelActive(String url,String provider){log("PUBLIC TUNNEL ONLINE | "+url+" ("+provider+")");notifyStatus();}
                @Override public void onTunnelError(String error){log("TUNNEL ERROR | "+error);notifyStatus();}
                @Override public void onTunnelStopped(){log("TUNNEL STOPPED");notifyStatus();}
            });
            String prov=pref().getString("tunnelProvider",TunnelManager.PROVIDER_LOCALHOST_RUN);
            TunnelManager.getInstance().startTunnel(this,p,prov);
        }
    }catch(Exception e){running=false;log("START ERROR | "+e.getClass().getSimpleName()+" | "+e.getMessage());stopSelf();}}}
    private void acquireKeepAlive(){try{if(wakeLock==null){PowerManager pm=(PowerManager)getSystemService(POWER_SERVICE);wakeLock=pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"SalamWebServer:KeepAlive");wakeLock.setReferenceCounted(false);}if(!wakeLock.isHeld())wakeLock.acquire();}catch(Exception ignored){}}
    private void releaseKeepAlive(){try{if(wakeLock!=null&&wakeLock.isHeld())wakeLock.release();}catch(Exception ignored){}}
    private void stopServer(){synchronized(LOCK){if(!running){releaseKeepAlive();return;}running=false;try{TunnelManager.getInstance().stopTunnel();}catch(Exception ignored){}try{if(server!=null)server.close();}catch(Exception ignored){}server=null;clients.clear();RATE.clear();releaseKeepAlive();log("SERVER STOPPED");try{stopForeground(STOP_FOREGROUND_REMOVE);}catch(Exception ignored){}}}
    private void acceptLoop(){while(running){try{Socket s=server.accept();String ip=s.getInetAddress().getHostAddress();if(clients.size()>=maxClients){respond(s,503,"text/plain","Server busy");close(s);continue;}pool.submit(()->handle(s));}catch(Exception e){if(running)log("ACCEPT ERROR | "+e.getMessage());}}}

    private boolean isLocalIp(String ip){return "127.0.0.1".equals(ip)||"::1".equals(ip)||"localhost".equalsIgnoreCase(ip);}

    private void handle(Socket s){
        String ip=s.getInetAddress().getHostAddress();
        clients.add(ip);
        try{
            s.setSoTimeout(15000);
            Req r=parse(s.getInputStream());
            if(r==null){respond(s,400,"text/plain","Bad Request");return;}
            requests.incrementAndGet();
            long inBytes=(r.body==null?0:r.body.length)+r.headerBytes;
            rxBytes+=inBytes;
            trackRx(ip,inBytes);
            VISITOR_LAST_SEEN.put(ip, System.currentTimeMillis());
            if(!allowed(ip)){respond(s,403,"text/plain","IP blocked");logReq(ip,r,403,"IP_BLOCKED");return;}
            boolean controlRequest=r.path.startsWith("/__salam__/")||r.path.equals("/__salam__");
            if(controlRequest&&!isControlAuthorized(r,ip)){
                respond(s,401,"text/html; charset=utf-8",adminLoginPage());
                logReq(ip,r,401,"ADMIN_AUTH_REQUIRED");
                return;
            }
            if(!controlRequest&&!rateOk(ip)){respond(s,429,"text/plain","Rate limit exceeded");logReq(ip,r,429,"RATE_LIMIT");return;}
            if(authRequired(r.path)&&!authorized(r.headers)){auth(s);logReq(ip,r,401,"AUTH_REQUIRED");return;}
            int st=route(r,s);
            logReq(ip,r,st,"");
        }catch(Exception e){
            log("CLIENT ERROR | "+ip+" | "+e.getMessage());
            try{respond(s,500,"text/plain","Internal Server Error");}catch(Exception ignored){}
        }finally{clients.remove(ip);close(s);}
    }

    private int redirect(Socket s, String location) throws IOException {
        OutputStream o = s.getOutputStream();
        String h = "HTTP/1.1 301 Moved Permanently\r\nLocation: " + location + "\r\nContent-Length: 0\r\nAccess-Control-Allow-Origin: *\r\nConnection: close\r\n\r\n";
        byte[] hb = h.getBytes(StandardCharsets.ISO_8859_1);
        o.write(hb);
        o.flush();
        long out = hb.length;
        txBytes += out;
        trackTx(out);
        return 301;
    }

    private File resolveFile(Req r) {
        if (r == null || r.path == null) return null;
        String path = r.path;

        // 1. Direct safe path
        File f = safe(path);
        if (f != null && f.exists()) return f;

        // 2. Decode variations (spaces, percent encoding, plus signs)
        try {
            String alt = URLDecoder.decode(path.replace("+", " "), "UTF-8");
            File fAlt = safe(alt);
            if (fAlt != null && fAlt.exists()) return fAlt;
        } catch (Exception ignored) {}

        // 3. Referer header resolution (resolves relative assets called from subfolders)
        String ref = r.headers.get("referer");
        if (ref != null && !ref.isEmpty()) {
            try {
                URI refUri = new URI(ref);
                String refPath = refUri.getPath();
                if (refPath != null && !refPath.isEmpty()) {
                    int lastSlash = refPath.lastIndexOf('/');
                    if (lastSlash > 0) {
                        String parentDir = refPath.substring(0, lastSlash);
                        File fRef = safe(parentDir + "/" + (path.startsWith("/") ? path.substring(1) : path));
                        if (fRef != null && fRef.exists()) return fRef;
                    }
                }
            } catch (Exception ignored) {}
        }

        // 4. Smart filename matcher across directory tree
        // (Handles prefix uploads e.g. Primary_www_falkamuri_female.json -> falkamuri_female.json)
        String reqFileName = new File(path).getName();
        if (!reqFileName.isEmpty()) {
            File matched = findMatchingFile(root(), reqFileName);
            if (matched != null && matched.exists()) return matched;
        }

        return f != null ? f : new File(root(), path.startsWith("/") ? path.substring(1) : path);
    }

    private File findMatchingFile(File dir, String targetName) {
        if (dir == null || !dir.exists()) return null;
        File direct = new File(dir, targetName);
        if (direct.exists()) return direct;

        File[] files = dir.listFiles();
        if (files == null) return null;

        String tn = targetName.toLowerCase(Locale.US);
        // Check direct children for match or suffix
        for (File f : files) {
            if (f.isFile()) {
                String fn = f.getName().toLowerCase(Locale.US);
                if (fn.equals(tn) || fn.endsWith("_" + tn) || fn.endsWith("___" + tn) || fn.endsWith(tn)) {
                    return f;
                }
            }
        }

        // Check subdirectories
        for (File f : files) {
            if (f.isDirectory() && !f.getName().startsWith(".")) {
                File found = findMatchingFile(f, targetName);
                if (found != null) return found;
            }
        }
        return null;
    }

    private int route(Req r,Socket s)throws Exception{
        File f=resolveFile(r);

        // Check maintenance mode for specific file or root
        String checkPath = r.path.startsWith("/") ? r.path : "/" + r.path;
        if(pref().getBoolean("globalMaintenance", false) || MAINTENANCE_FILES.contains(checkPath) || (f!=null && f.isDirectory() && (MAINTENANCE_FILES.contains(checkPath) || MAINTENANCE_FILES.contains(checkPath + "/index.html")))){
            return respond(s, 503, "text/html; charset=utf-8", maintenancePage(f != null ? f.getName() : "Resource"));
        }

        if("OPTIONS".equals(r.method)){
            return respond(s, 204, "text/plain", "");
        }

        if("GET".equals(r.method)||"HEAD".equals(r.method)){
            if("/__salam__/".equals(r.path)||"/__salam__".equals(r.path)){
                String adminKey = getAdminKey(this);
                return respondWithCookie(s,200,"text/html; charset=utf-8",panel(),"salam_admin_key="+adminKey+"; Path=/; Max-Age=86400; SameSite=Lax");
            }
            if(r.path.startsWith("/__salam__/api/"))return api(r,s);
            if(f==null)return respond(s,403,"text/plain","Forbidden");

            if(f.isDirectory()){
                // Universal directory redirect: if URL doesn't end with slash, redirect to dirname/
                if(!r.path.endsWith("/") && !r.path.isEmpty()){
                    return redirect(s, r.path + "/" + (r.query != null && !r.query.isEmpty() ? "?" + r.query : ""));
                }

                File indexPhp = new File(f, "index.php");
                if (indexPhp.exists() && indexPhp.isFile()) {
                    String phpOutput = PhpEngine.execute(indexPhp, r.method, r.query, r.body, r.headers, root());
                    return respond(s, 200, "text/html; charset=utf-8", phpOutput);
                }
                File index=new File(f,"index.html");
                if(!index.exists())index=new File(f,"index.htm");
                if(!index.exists()){
                    // Check any html file in the directory
                    File[] dirFiles = f.listFiles();
                    if(dirFiles != null){
                        for(File df : dirFiles){
                            if(df.isFile() && (df.getName().toLowerCase(Locale.US).endsWith(".html") || df.getName().toLowerCase(Locale.US).endsWith(".htm"))){
                                index = df;
                                break;
                            }
                        }
                    }
                }
                if(!index.exists()&&f.equals(root()))index=new File(root(),"index.html");
                if(index.exists()&&index.isFile()){
                    String indexPath = rel(index);
                    if(MAINTENANCE_FILES.contains(indexPath)) {
                        return respond(s, 503, "text/html; charset=utf-8", maintenancePage(index.getName()));
                    }
                    return sendFile(s,index,"HEAD".equals(r.method));
                }
                boolean allowDir=pref().getBoolean("allowDirListing",false);
                if(allowDir){
                    return respond(s,200,"text/html; charset=utf-8",directory(f,r.path));
                }
                return respond(s,403,"text/html; charset=utf-8",forbiddenPage());
            }

            if(!f.exists())return respond(s,404,"text/plain","Not Found");

            // Direct PHP execution
            if(f.isFile() && f.getName().toLowerCase(Locale.US).endsWith(".php")){
                String phpOutput = PhpEngine.execute(f, r.method, r.query, r.body, r.headers, root());
                return respond(s, 200, "text/html; charset=utf-8", phpOutput);
            }

            return sendFile(s,f,"HEAD".equals(r.method));
        }

        if("POST".equals(r.method)){
            if(r.path.startsWith("/__salam__/api/"))return apiPost(r,s);
            if(f!=null && f.exists() && f.isFile() && f.getName().toLowerCase(Locale.US).endsWith(".php")){
                String phpOutput = PhpEngine.execute(f, r.method, r.query, r.body, r.headers, root());
                return respond(s, 200, "text/html; charset=utf-8", phpOutput);
            }
        }

        return respond(s,405,"text/plain","Method Not Allowed");
    }
    private int api(Req r,Socket s)throws Exception{if("/__salam__/api/status".equals(r.path))return respond(s,200,"application/json",statusJson());if("/__salam__/api/flow".equals(r.path))return respond(s,200,"application/json",flowJson());if("/__salam__/api/visitors".equals(r.path))return respond(s,200,"application/json",visitorsJson());if("/__salam__/api/logs".equals(r.path))return respond(s,200,"text/plain; charset=utf-8",join(LOGS));if("/__salam__/api/history".equals(r.path))return respond(s,200,"text/plain; charset=utf-8",join(HISTORY));if("/__salam__/api/files".equals(r.path))return respond(s,200,"application/json",filesJson("/"));if("/__salam__/api/settings".equals(r.path))return respond(s,200,"application/json",settingsJson());return respond(s,404,"text/plain","Not Found");}
    private int apiPost(Req r,Socket s)throws Exception{String ct=r.headers.getOrDefault("content-type","");if("/__salam__/api/upload".equals(r.path))return respond(s,200,"application/json",multipartUpload(ct,r.body));String form=new String(r.body==null?new byte[0]:r.body,StandardCharsets.UTF_8);Map<String,String>d=form(form);if("/__salam__/api/action".equals(r.path))return respond(s,200,"application/json",action(d));return respond(s,404,"text/plain","Not Found");}

    private Req parse(InputStream in)throws Exception{ByteArrayOutputStream h=new ByteArrayOutputStream();int prev=-1,b;while((b=in.read())!=-1){h.write(b);if(prev=='\r'&&b=='\n'){byte[]a=h.toByteArray();int n=a.length;if(n>=4&&a[n-4]=='\r'&&a[n-3]=='\n'&&a[n-2]=='\r'&&a[n-1]=='\n')break;}prev=b;if(h.size()>65536)throw new IOException("Header too large");}String[] lines=h.toString("ISO-8859-1").split("\\r\\n");if(lines.length==0)return null;String[] first=lines[0].split(" ",3);if(first.length<2)return null;Req r=new Req();r.headerBytes=h.size();r.method=first[0].toUpperCase(Locale.US);String raw=first[1];int q=raw.indexOf('?');r.path=decode(q>=0?raw.substring(0,q):raw);r.query=q>=0?raw.substring(q+1):"";if(r.path.isEmpty())r.path="/";for(int i=1;i<lines.length;i++){int x=lines[i].indexOf(':');if(x>0)r.headers.put(lines[i].substring(0,x).trim().toLowerCase(Locale.US),lines[i].substring(x+1).trim());}int len=0;try{len=Integer.parseInt(r.headers.getOrDefault("content-length","0"));}catch(Exception ignored){}if(len>50*1024*1024)throw new IOException("Request body too large");r.body=new byte[len];int off=0;while(off<len){int n=in.read(r.body,off,len-off);if(n<0)break;off+=n;}if(off<len)r.body=Arrays.copyOf(r.body,off);return r;}

    private boolean authRequired(String path){return !pref().getString("password","").isEmpty()&&!path.startsWith("/__salam__/api/status")&&!path.equals("/favicon.ico");}
    private boolean authorized(Map<String,String> h){String pass=pref().getString("password","");if(pass.isEmpty())return true;String a=h.get("authorization");if(a==null||!a.startsWith("Basic "))return false;try{String d=new String(Base64.getDecoder().decode(a.substring(6)),StandardCharsets.UTF_8);int x=d.indexOf(':');return x>0&&"admin".equals(d.substring(0,x))&&pass.equals(d.substring(x+1));}catch(Exception e){return false;}}
    private void auth(Socket s)throws IOException{String h="HTTP/1.1 401 Unauthorized\r\nWWW-Authenticate: Basic realm=\"Salam Web Server\"\r\nContent-Length: 0\r\nConnection: close\r\n\r\n";OutputStream o=s.getOutputStream();o.write(h.getBytes(StandardCharsets.ISO_8859_1));o.flush();}
    private boolean allowed(String ip){if("127.0.0.1".equals(ip)||"::1".equals(ip))return true;for(String x:rules("blockIps"))if(match(ip,x))return false;if(!pref().getBoolean("allowOnly",false))return true;for(String x:rules("allowIps"))if(match(ip,x))return true;return false;}
    private boolean rateOk(String ip){int lim=Math.max(1,pref().getInt("rate",120));long now=System.currentTimeMillis();String k=ip;Long old=RATE.get(k);if(old!=null&&now-old<60000){String countKey=k+":count";Long c=RATE.get(countKey);long n=c==null?1:c+1;if(n>lim)return false;RATE.put(countKey,n);return true;}RATE.put(k,now);RATE.put(k+":count",1L);return true;}
    private Set<String> rules(String key){Set<String>s=new HashSet<>();for(String x:pref().getString(key,"").split(",")){x=x.trim();if(!x.isEmpty())s.add(x);}return s;}
    private boolean match(String ip,String rule){try{if(!rule.contains("/"))return ip.equals(rule);String[]z=rule.split("/");byte[]a=InetAddress.getByName(ip).getAddress(),n=InetAddress.getByName(z[0]).getAddress();int bits=Integer.parseInt(z[1]);if(a.length!=n.length||bits<0||bits>a.length*8)return false;int full=bits/8,rem=bits%8;for(int i=0;i<full;i++)if(a[i]!=n[i])return false;if(rem>0){int mask=0xff<<(8-rem);if((a[full]&mask)!=(n[full]&mask))return false;}return true;}catch(Exception e){return false;}}

    private File safe(String path){
        try{
            if(path==null)return null;
            while(path.startsWith("/"))path=path.substring(1);
            File f=new File(root(),path);
            String a=root().getCanonicalPath(),b=f.getCanonicalPath();
            return b.equals(a)||b.startsWith(a+File.separator)?f:null;
        }catch(Exception e){
            return null;
        }
    }
    private String clean(String s){
        if(s==null||s.trim().isEmpty())return "file_"+System.currentTimeMillis();
        String n=new File(s).getName().trim();
        n=n.replace("..","").replace("/","").replace("\\","").replace(":","");
        return n.isEmpty()?"file_"+System.currentTimeMillis():n;
    }
    private String action(Map<String,String>d)throws Exception{String a=d.getOrDefault("action","");if("restart".equals(a)){new Thread(()->{stopServer();new Handler(Looper.getMainLooper()).postDelayed(this::startServer,300);}).start();return ok("Restart requested");}if("stop".equals(a)){stopServer();return ok("Server stopped");}if("start".equals(a)){startServer();return ok("Server started");}if("resetFlow".equals(a)){resetFlow();return ok("Network flow counters reset");}if("saveSettings".equals(a)){int port=parseInt(d.get("port"),8080);if(port<1024||port>65535)port=8080;pref().edit().putInt("port",port).putString("password",d.getOrDefault("password","")).putString("allowIps",d.getOrDefault("allowIps","")).putString("blockIps",d.getOrDefault("blockIps","")).putBoolean("allowOnly","1".equals(d.get("allowOnly"))).putInt("rate",Math.max(1,parseInt(d.get("rate"),120))).putInt("maxClients",Math.max(1,parseInt(d.get("maxClients"),32))).putString("customUrl",d.getOrDefault("customUrl","")).apply();load();return ok("Settings saved");}if("block".equals(a)){String ip=d.getOrDefault("ip","").trim();if(ip.isEmpty())return err("IP required");Set<String>s=rules("blockIps");s.add(ip);pref().edit().putString("blockIps",String.join(",",s)).apply();return ok("IP blocked");}if("clear-block".equals(a)){pref().edit().putString("blockIps","").apply();return ok("Blocklist cleared");}if("unblock".equals(a)){String ip=d.getOrDefault("ip","").trim();Set<String>s=rules("blockIps");s.remove(ip);pref().edit().putString("blockIps",String.join(",",s)).apply();return ok("IP unblocked");}File f=safe(d.getOrDefault("path","/"));if("mkdir".equals(a)){if(f==null)return err("Invalid path");return f.mkdirs()||f.exists()?ok("Folder created"):err("Create failed");}if("touch".equals(a)){if(f==null)return err("Invalid path");File par=f.getParentFile();if(par!=null)par.mkdirs();return f.exists()?ok("Already exists"):f.createNewFile()?ok("File created"):err("Create failed");}if("delete".equals(a)){if(f==null||f.equals(root()))return err("Invalid path");delete(f);return ok("Deleted");}if("rename".equals(a)){if(f==null)return err("Invalid path");File n=new File(f.getParentFile(),clean(d.get("name")));return f.renameTo(n)?ok("Renamed"):err("Rename failed");}if("copy".equals(a)||"move".equals(a)){File to=safe(d.getOrDefault("to","/"));if(f==null||to==null)return err("Invalid path");copy(f,to);if("move".equals(a))delete(f);return ok("move".equals(a)?"Moved":"Copied");}if("save".equals(a)){if(f==null)return err("Invalid path");File par=f.getParentFile();if(par!=null)par.mkdirs();try(FileOutputStream o=new FileOutputStream(f)){o.write(d.getOrDefault("content","").getBytes(StandardCharsets.UTF_8));}return ok("Saved");}if("zip".equals(a)){if(f==null||!f.exists())return err("Invalid source");File z=new File(f.getParentFile(),f.getName()+".zip");zip(f,z);return ok("ZIP created");}if("extract".equals(a)){if(f==null||!f.getName().toLowerCase(Locale.US).endsWith(".zip"))return err("ZIP required");File to=safe(d.getOrDefault("to","/"));extract(f,to);return ok("ZIP extracted");}if("toggleMaintenance".equals(a)){String p=d.getOrDefault("path","/");if(p.isEmpty())return err("Path required");if(MAINTENANCE_FILES.contains(p)){MAINTENANCE_FILES.remove(p);return ok("Maintenance mode DISABLED for "+p);}else{MAINTENANCE_FILES.add(p);return ok("Maintenance mode ENABLED for "+p);}}if("toggleGlobalMaintenance".equals(a)){boolean curr=pref().getBoolean("globalMaintenance",false);pref().edit().putBoolean("globalMaintenance",!curr).apply();return ok("Global maintenance "+(!curr?"ENABLED":"DISABLED"));}if("clearLogs".equals(a)){LOGS.clear();HISTORY.clear();return ok("Logs cleared");}return err("Unknown action");}
    private int parseInt(String s,int d){try{return Integer.parseInt(s);}catch(Exception e){return d;}}
    private void delete(File f){if(f.isDirectory()){File[]a=f.listFiles();if(a!=null)for(File x:a)delete(x);}f.delete();}
    private void copy(File a,File b)throws IOException{if(a.isDirectory()){b.mkdirs();File[]x=a.listFiles();if(x!=null)for(File q:x)copy(q,new File(b,q.getName()));}else{File p=b.getParentFile();if(p!=null)p.mkdirs();try(InputStream i=new FileInputStream(a);OutputStream o=new FileOutputStream(b)){byte[]z=new byte[16384];int n;while((n=i.read(z))>0)o.write(z,0,n);}}}
    private void extract(File zip,File dest)throws IOException{if(dest==null)throw new IOException("Invalid destination");String base=dest.getCanonicalPath();try(ZipInputStream in=new ZipInputStream(new FileInputStream(zip))){ZipEntry e;while((e=in.getNextEntry())!=null){File o=new File(dest,e.getName());String cp=o.getCanonicalPath();if(!cp.equals(base)&&!cp.startsWith(base+File.separator))throw new IOException("Unsafe ZIP entry");if(e.isDirectory())o.mkdirs();else{File p=o.getParentFile();if(p!=null)p.mkdirs();try(OutputStream out=new FileOutputStream(o)){byte[]b=new byte[16384];int n;while((n=in.read(b))>0)out.write(b,0,n);}}}}}
    private String multipartUpload(String ct,byte[]body)throws Exception{int bi=ct.indexOf("boundary=");if(bi<0)return err("Boundary missing");String boundary=ct.substring(bi+9).trim();if(boundary.startsWith("\""))boundary=boundary.substring(1,boundary.length()-1);byte[]sep=("--"+boundary).getBytes(StandardCharsets.ISO_8859_1);int pos=0,count=0;while(true){int a=index(body,sep,pos);if(a<0)break;int st=a+sep.length;if(st+2<=body.length&&body[st]=='-'&&body[st+1]=='-')break;int he=index(body,"\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1),st);if(he<0)break;int ds=he+4,nxt=index(body,sep,ds);if(nxt<0)break;int de=Math.max(ds,nxt-2);String name=filename(new String(body,st,he-st,StandardCharsets.ISO_8859_1));if(name!=null){File o=new File(root(),clean(name));try(FileOutputStream out=new FileOutputStream(o)){out.write(body,ds,de-ds);}count++;}pos=nxt;}return ok(count+" file(s) uploaded");}
    private int index(byte[]a,byte[]b,int from){outer:for(int i=Math.max(0,from);i<=a.length-b.length;i++){for(int j=0;j<b.length;j++)if(a[i+j]!=b[j])continue outer;return i;}return -1;}
    private String filename(String h){int p=h.indexOf("filename=\"");if(p<0)return null;int q=h.indexOf('"',p+10);return q<0?null:h.substring(p+10,q);}
    private Map<String,String> form(String s){Map<String,String>m=new HashMap<>();for(String x:s.split("&")){int i=x.indexOf('=');if(i>0)m.put(decode(x.substring(0,i)),decode(x.substring(i+1)));}return m;}

    private String statusJson(){updateFlowSpeed();return "{\"running\":"+running+",\"requests\":"+requests.get()+",\"clients\":"+clients.size()+",\"uptime\":\""+json(uptime())+"\",\"url\":\""+json(displayUrl())+"\",\"cpu\":\""+json(cpuText())+"\",\"ram\":\""+json(memoryText(this))+"\",\"storage\":\""+json(storageText())+"\",\"traffic\":\""+json(trafficText())+"\",\"interface\":\""+json(interfaceName(this))+"\",\"rx\":"+rxBytes+",\"tx\":"+txBytes+",\"rxSpeed\":\""+json(rxSpeedText())+"\",\"txSpeed\":\""+json(txSpeedText())+"\",\"peakSpeed\":\""+json(peakSpeedText())+"\",\"network\":\""+json(networkSummary(this))+"\"}";}
    private String flowJson(){updateFlowSpeed();StringBuilder b=new StringBuilder("{");b.append("\"rxBytes\":").append(rxBytes).append(",\"txBytes\":").append(txBytes).append(",\"totalBytes\":").append(rxBytes+txBytes).append(",\"rxSpeed\":\"").append(json(rxSpeedText())).append("\"").append(",\"txSpeed\":\"").append(json(txSpeedText())).append("\"").append(",\"peakSpeed\":\"").append(json(peakSpeedText())).append("\"").append(",\"clients\":").append(clients.size()).append(",\"requests\":").append(requests.get()).append(",\"interface\":\"").append(json(interfaceName(this))).append("\"").append(",\"streams\":[");int count=0;for(Map.Entry<String,Long> e:CLIENT_FLOW.entrySet()){if(count++>0)b.append(',');long reqs=CLIENT_REQS.getOrDefault(e.getKey(),1L);b.append("{\"ip\":\"").append(json(e.getKey())).append("\",\"bytes\":").append(e.getValue()).append(",\"reqs\":").append(reqs).append("}");}b.append("]}");return b.toString();}
    private String settingsJson(){return "{\"port\":"+pref().getInt("port",8080)+",\"password\":"+(!pref().getString("password","").isEmpty())+",\"allowOnly\":"+pref().getBoolean("allowOnly",false)+",\"allowIps\":\""+json(pref().getString("allowIps",""))+"\",\"blockIps\":\""+json(pref().getString("blockIps",""))+"\",\"rate\":"+pref().getInt("rate",120)+",\"maxClients\":"+pref().getInt("maxClients",32)+",\"customUrl\":\""+json(pref().getString("customUrl",""))+"\"}";}
    private String filesJson(String path){File d=safe(path);if(d==null||!d.isDirectory())return "[]";File[]fs=d.listFiles();StringBuilder b=new StringBuilder("[");if(fs!=null)for(int i=0;i<fs.length;i++){if(i>0)b.append(',');File f=fs[i];String rPath=rel(f);boolean isMaint=MAINTENANCE_FILES.contains(rPath);b.append("{\"name\":\"").append(json(f.getName())).append("\",\"dir\":").append(f.isDirectory()).append(",\"size\":").append(f.length()).append(",\"path\":\"").append(json(rPath)).append("\",\"maintenance\":").append(isMaint).append("}");}return b.append(']').toString();}
    private String rel(File f){return "/"+root().toURI().relativize(f.toURI()).getPath().replace('\\','/');}
    private String visitorsJson(){
        long now=System.currentTimeMillis();
        StringBuilder b=new StringBuilder("{\"totalVisitors\":").append(VISITOR_LAST_SEEN.size()).append(",\"visitors\":[");
        int count=0;
        for(Map.Entry<String,Long> e : VISITOR_LAST_SEEN.entrySet()){
            if(count++>0) b.append(',');
            long agoSec = Math.max(0, (now - e.getValue()) / 1000);
            long reqs = CLIENT_REQS.getOrDefault(e.getKey(), 1L);
            long bytes = CLIENT_FLOW.getOrDefault(e.getKey(), 0L);
            b.append("{\"ip\":\"").append(json(e.getKey())).append("\",\"lastSeenAgo\":").append(agoSec).append(",\"reqs\":").append(reqs).append(",\"bytes\":").append(bytes).append("}");
        }
        b.append("]}");
        return b.toString();
    }
    private String forbiddenPage(){
        return "<!doctype html><html><head><meta name='viewport' content='width=device-width,initial-scale=1'><title>403 Forbidden - Salam Cyber Server</title><style>*{box-sizing:border-box}body{margin:0;padding:40px 16px;background:radial-gradient(circle at 50% 0,#10223d 0,#010712 60%);color:#fff;font-family:system-ui,-apple-system,sans-serif;text-align:center;min-height:100vh;display:flex;align-items:center;justify-content:center}.card{max-width:520px;width:100%;background:linear-gradient(145deg,#07172b,#030d1a);border:1px solid #144975;border-radius:24px;padding:36px 24px;box-shadow:0 20px 50px rgba(0,0,0,0.8),0 0 30px rgba(0,217,255,0.15)}.badge{display:inline-block;padding:6px 16px;background:rgba(255,54,88,0.15);border:1px solid #ff3658;color:#ff3658;border-radius:20px;font-size:12px;font-weight:800;letter-spacing:1px;margin-bottom:18px}h1{color:#ff3658;font-size:30px;margin:0 0 12px;font-weight:900}p{color:#94b6d9;font-size:15px;line-height:1.7;margin:0 0 24px}a{display:inline-block;padding:12px 28px;background:linear-gradient(135deg,#00f0ff,#4b4dff);color:#fff;text-decoration:none;border-radius:14px;font-weight:800;font-size:14px;box-shadow:0 8px 24px rgba(0,240,255,0.3)}</style></head><body><div class='card'><div class='badge'>SECURITY FIREWALL ACTIVE</div><h1>🔒 403 Forbidden</h1><p>Directory indexing has been restricted to secure hosted files.<br>Only direct file links and the homepage are publicly accessible.</p><a href='/'>⚡ Enter Homepage</a></div></body></html>";
    }
    public static String defaultMaintenancePage(String path){
        return "<!doctype html><html><head><meta name='viewport' content='width=device-width,initial-scale=1'><title>503 Cyber Maintenance • Salam Server</title><style>*{box-sizing:border-box}body{margin:0;padding:30px 16px;background:radial-gradient(circle at 50% 0,#1c1803 0,#020710 65%);color:#f2f6fc;font-family:system-ui,-apple-system,sans-serif;text-align:center;min-height:100vh;display:flex;align-items:center;justify-content:center}.box{max-width:560px;width:100%;background:linear-gradient(145deg,#0f1522,#060b13);border:1px solid #ffaa00;border-radius:28px;padding:36px 24px;box-shadow:0 24px 60px rgba(0,0,0,0.85),0 0 40px rgba(255,170,0,0.2);position:relative;overflow:hidden}.radar{width:64px;height:64px;margin:0 auto 18px;border-radius:50%;border:3px solid #ffaa00;position:relative;display:flex;align-items:center;justify-content:center;animation:pulse 2s infinite}.radar::after{content:'';position:absolute;width:100%;height:100%;border-radius:50%;border:2px solid #ffaa00;opacity:0;animation:ripple 2s infinite}@keyframes pulse{0%{box-shadow:0 0 0 0 rgba(255,170,0,0.6)}70%{box-shadow:0 0 0 20px rgba(255,170,0,0)}100%{box-shadow:0 0 0 0 rgba(255,170,0,0)}}@keyframes ripple{0%{transform:scale(0.8);opacity:1}100%{transform:scale(2.2);opacity:0}}.badge{display:inline-block;padding:5px 14px;background:rgba(255,170,0,0.15);border:1px solid #ffaa00;color:#ffaa00;border-radius:20px;font-size:11px;font-weight:800;letter-spacing:1px;margin-bottom:14px}h1{color:#ffaa00;font-size:26px;margin:0 0 10px;font-weight:900}p{color:#a2c4e2;font-size:14px;line-height:1.6;margin:0 0 20px}.card-info{background:#02060e;border:1px solid #162f48;border-radius:14px;padding:12px;margin:16px 0;font-family:monospace;font-size:12px;color:#00f0ff;text-align:left;word-break:break-all}.btn{display:inline-block;margin-top:12px;padding:11px 24px;background:linear-gradient(135deg,#ffaa00,#ff5500);color:#fff;text-decoration:none;border-radius:12px;font-weight:800;font-size:13px;border:none;cursor:pointer}</style></head><body><div class='box'><div class='radar'><span style='font-size:26px'>🚧</span></div><div class='badge'>DATACENTER MAINTENANCE ENGINE</div><h1>System Maintenance</h1><p>This resource is temporarily undergoing scheduled updates and infrastructure maintenance. Please refresh in a moment.</p><div class='card-info'><div><b>Target Resource:</b> "+path+"</div><div><b>Server Engine:</b> Salam High-Performance Web Core</div><div><b>Status Code:</b> HTTP 503 Service Unavailable</div></div><button class='btn' onclick='location.reload()'>🔄 Retry Connection</button></div></body></html>";
    }
    private String maintenancePage(String target){
        try{
            File mf = new File(root(), "maintenance.html");
            if(mf.exists() && mf.isFile()) return readText(mf);
        }catch(Exception ignored){}
        return defaultMaintenancePage(target);
    }
    private String directory(File d,String path){StringBuilder h=new StringBuilder("<!doctype html><html><meta name=viewport content=width=device-width><body style='font-family:system-ui;background:#06101f;color:white;padding:20px'><h2>📁 ").append(html(path)).append("</h2>");File[]fs=d.listFiles();if(fs!=null)for(File f:fs){String p=(path.endsWith("/")?path:path+"/")+f.getName();h.append("<p><a style='color:#19dfff' href='").append(html(p)).append("'>").append(f.isDirectory()?"📂 ":"📄 ").append(html(f.getName())).append("</a></p>");}return h.append("</body></html>").toString();}

    private String panel(){return "<!doctype html><html><head><meta name='viewport' content='width=device-width,initial-scale=1'><meta name='theme-color' content='#06172b'><title>Salam Web Server V10</title><style>"+css()+"</style></head><body><header><div class='brand'><span class='bolt'>⚡</span><div><b>Salam Web Server</b><small>V10 • Control Center</small></div></div><span id='dot' class='dot'>●</span></header><main><section class='hero'><div class='leds'><i></i><i></i><i></i><i></i><i></i><i></i><i></i></div><h1 id='status'>SERVER</h1><div id='url' class='url'>Loading…</div><div class='actions'><button onclick='serverAction(&quot;start&quot;)'>▶ START</button><button onclick='serverAction(&quot;stop&quot;)'>■ STOP</button><button onclick='serverAction(&quot;restart&quot;)'>↻ RESTART</button><button onclick='copyUrl()'>🔗 COPY URL</button></div></section><section class='grid' id='stats'></section><section class='panel'><h2>📁 FILE MANAGER</h2><div class='actions'><button onclick='mkdir()'>📂 NEW FOLDER</button><button onclick='upload()'>⬆️ UPLOAD</button><button onclick='touch()'>📄 NEW FILE</button><button onclick='zipCurrent()'>📦 ZIP</button><button onclick='extract()'>🗜️ EXTRACT</button></div><input id='up' type='file' multiple><input id='q' placeholder='🔎 Search files' oninput='render()'><div id='files'></div></section><section class='panel'><h2>🔐 SECURITY</h2><p>Blocked IPs: <b id='blocked'>0</b></p><div class='actions'><button onclick='blockIp()'>🚫 BLOCK IP</button><button onclick='unblockIp()'>🔓 UNBLOCK</button><button onclick='clearBlock()'>🧹 CLEAR BLOCKLIST</button></div></section><section class='panel'><h2>📋 LIVE REQUEST LOG</h2><pre id='logs'>Loading…</pre></section><section class='panel'><h2>🕘 ACCESS HISTORY</h2><pre id='history'>Loading…</pre></section><section class='panel'><h2>⚙️ SERVER SETTINGS</h2><div class='form'><label>Requests / minute<input id='rate' type='number' min='1'></label><label>Maximum clients<input id='max' type='number' min='1'></label><label>Custom hostname<input id='host' placeholder='salam.local'></label><label>Web password<input id='pass' type='password' placeholder='Leave empty to disable'></label><button onclick='saveSettings()'>💾 SAVE SETTINGS</button></div></section></main><script>"+js()+"</script></body></html>";}
    private String css(){return "*{box-sizing:border-box}body{margin:0;padding:14px;background:radial-gradient(circle at 15% 0,#123b62 0,#030b17 42%);color:#f7fbff;font:14px system-ui,sans-serif}header,.card,.panel{background:linear-gradient(145deg,#0a2038,#0e2d4c);border:1px solid #164b72;border-radius:24px;padding:16px;margin-bottom:12px;box-shadow:0 14px 40px #0008}.brand{display:flex;gap:12px;align-items:center}.brand b{font-size:24px;font-weight:900}.brand small{display:block;font-size:11px;color:#8eabc9;margin-top:4px}.bolt{font-size:31px}.dot{color:#21f59b;font-size:22px}main{max-width:900px;margin:auto}.hero{background:linear-gradient(145deg,#071c32,#05111f);border:1px solid #11486b;border-radius:26px;padding:22px;margin-bottom:14px;box-shadow:0 0 30px #001c2e}.leds{display:flex;justify-content:center;gap:13px;margin:4px 0 22px}.leds i{width:18px;height:18px;border-radius:50%;display:block;background:#263849;box-shadow:0 0 5px #1b2d3d}.leds i:nth-child(1){background:#25ef9c;box-shadow:0 0 18px #25ef9c}.leds i:nth-child(2){background:#21d9ff;box-shadow:0 0 18px #21d9ff}.leds i:nth-child(3){background:#b44cff;box-shadow:0 0 18px #b44cff}.leds i:nth-child(4){background:#ffd52f;box-shadow:0 0 18px #ffd52f}.leds i:nth-child(5){background:#fff;box-shadow:0 0 18px #fff}.leds i:nth-child(6){background:#ff8a24;box-shadow:0 0 18px #ff8a24}.leds i:nth-child(7){background:#ff3658;box-shadow:0 0 18px #ff3658}h1{text-align:center;font-size:24px;color:#21f59b;letter-spacing:.5px}.url{background:#020c19;border-radius:18px;padding:16px;text-align:center;color:#21d9ff;word-break:break-all;margin:15px 0}.actions{display:flex;flex-wrap:wrap;gap:8px}button{border:0;border-radius:15px;padding:13px 15px;background:linear-gradient(100deg,#13d8ff,#4b4dff);color:#fff;font-weight:800;cursor:pointer;flex:1;min-width:115px}.grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:10px;margin-bottom:14px}.stat{background:#071c32;border:1px solid #11486b;border-radius:20px;padding:15px}.stat b{font-size:22px;color:#21d9ff;display:block;margin-top:5px}.stat small{color:#8eabc9}.file{display:flex;align-items:center;gap:9px;background:#06172b;border:1px solid #123650;border-radius:15px;padding:12px;margin:7px 0}.file a{color:#fff;text-decoration:none;font-weight:700;flex:1}.file small{color:#89a7c0;display:block}.form label{display:block;color:#8eabc5;margin:10px 0}.form input{display:block;width:100%;margin-top:6px;padding:12px;border-radius:12px;border:1px solid #1a4b69;background:#020c19;color:#fff}pre{max-height:340px;overflow:auto;white-space:pre-wrap;word-break:break-word;color:#a8c4db;background:#020c19;border-radius:14px;padding:12px}@media(min-width:700px){.grid{grid-template-columns:repeat(4,minmax(0,1fr))}}";}
    private String js(){return "const $=x=>document.getElementById(x);let data=[];let busy=false;async function j(u,o){let r=await fetch(u,o);if(!r.ok)throw new Error(r.status+' '+r.statusText);return r.json()}async function t(u){let r=await fetch(u);if(!r.ok)throw new Error(r.status+' '+r.statusText);return r.text()}async function refresh(){if(busy)return;busy=true;try{let s=await j('/__salam__/api/status');$('status').textContent=s.running?'SERVER ONLINE':'SERVER OFFLINE';$('status').style.color=s.running?'#21f59b':'#ff3658';$('dot').style.color=s.running?'#21f59b':'#ff3658';$('url').textContent=s.url;let v=[['🧠 CPU',s.cpu],['💾 RAM',s.ram],['💽 Storage',s.storage],['👥 Clients',s.clients],['📊 Requests',s.requests],['📡 Traffic',s.traffic||((s.rx+s.tx)+' bytes')],['⚡ Flow Speed','↓ '+(s.rxSpeed||'0 B/s')+'  ↑ '+(s.txSpeed||'0 B/s')],['⏱️ Uptime',s.uptime],['🌐 Network',s.network]];$('stats').innerHTML=v.map(x=>'<div class=stat>'+x[0]+'<b>'+x[1]+'</b></div>').join('');data=await j('/__salam__/api/files');render();$('logs').textContent=await t('/__salam__/api/logs');$('history').textContent=await t('/__salam__/api/history');let q=await j('/__salam__/api/settings');$('rate').value=q.rate;$('max').value=q.maxClients;$('host').value=q.customUrl||'';$('blocked').textContent=(q.blockIps||'').split(',').filter(Boolean).length}catch(e){$('logs').textContent='Control panel error: '+e}finally{busy=false}}function render(){let q=($('q').value||'').toLowerCase();$('files').innerHTML=data.filter(x=>String(x.name).toLowerCase().includes(q)).map(x=>'<div class=file><a href=\"'+x.path+'\">'+(x.dir?'📂':'📄')+' '+x.name+'<small>'+x.size+' bytes</small></a><a href=\"'+x.path+'\" download>⬇️</a></div>').join('')}async function act(b){return j('/__salam__/api/action',{method:'POST',headers:{'Content-Type':'application/x-www-form-urlencoded'},body:b})}function serverAction(a){act('action='+a).then(refresh)}function copyUrl(){if(navigator.clipboard)navigator.clipboard.writeText($('url').textContent);else alert($('url').textContent)}function mkdir(){let n=prompt('Folder name');if(n)act('action=mkdir&path='+encodeURIComponent('/'+n)).then(refresh)}function touch(){let n=prompt('File name');if(n)act('action=touch&path='+encodeURIComponent('/'+n)).then(refresh)}function blockIp(){let n=prompt('IP to block');if(n)act('action=block&ip='+encodeURIComponent(n)).then(refresh)}function unblockIp(){let n=prompt('IP to unblock');if(n)act('action=unblock&ip='+encodeURIComponent(n)).then(refresh)}function clearBlock(){act('action=clear-block').then(refresh)}function zipCurrent(){let n=prompt('Path to ZIP, e.g. /folder');if(n)act('action=zip&path='+encodeURIComponent(n)).then(refresh)}function extract(){let n=prompt('ZIP path');if(n)act('action=extract&path='+encodeURIComponent(n)+'&to=%2F').then(refresh)}async function upload(){let f=$('up').files;if(!f.length)return alert('Select files');let m=new FormData();for(let x of f)m.append('file',x);let r=await fetch('/__salam__/api/upload',{method:'POST',body:m});let z=await r.json();alert(z.message||'Upload complete');refresh()}async function saveSettings(){let b='action=saveSettings&rate='+encodeURIComponent($('rate').value||120)+'&maxClients='+encodeURIComponent($('max').value||32)+'&customUrl='+encodeURIComponent($('host').value)+'&password='+encodeURIComponent($('pass').value);await act(b);alert('Settings saved');refresh()}refresh();setInterval(refresh,2500);";}

    private int respondWithCookie(Socket s,int code,String type,String body,String cookie)throws IOException{
        byte[]b=body.getBytes(StandardCharsets.UTF_8);
        OutputStream o=s.getOutputStream();
        String h="HTTP/1.1 "+code+" "+reason(code)+"\r\nContent-Type: "+type+"\r\nContent-Length: "+b.length+"\r\nSet-Cookie: "+cookie+"\r\nAccess-Control-Allow-Origin: *\r\nAccess-Control-Allow-Methods: GET, POST, PUT, DELETE, OPTIONS, HEAD, PATCH\r\nAccess-Control-Allow-Headers: *\r\nAccess-Control-Expose-Headers: *\r\nCache-Control: no-store\r\nX-Content-Type-Options: nosniff\r\nConnection: close\r\n\r\n";
        byte[]hb=h.getBytes(StandardCharsets.ISO_8859_1);
        o.write(hb);
        o.write(b);
        o.flush();
        long out=b.length+hb.length;
        txBytes+=out;
        trackTx(out);
        return code;
    }

    public static synchronized String getAdminKey(Context c){
        SharedPreferences sp=c.getSharedPreferences("server",Context.MODE_PRIVATE);
        String key=sp.getString("adminKey","");
        if(key.isEmpty()){
            key=UUID.randomUUID().toString().replace("-","").substring(0,16);
            sp.edit().putString("adminKey",key).apply();
        }
        return key;
    }

    private boolean isControlAuthorized(Req r,String ip){
        String validKey=getAdminKey(this);
        String pass=pref().getString("password","");
        if(r.query!=null&&!r.query.isEmpty()){
            Map<String,String> qm=form(r.query);
            String k=qm.get("key");
            if(k==null)k=qm.get("token");
            if(k!=null&&(k.equals(validKey)||(!pass.isEmpty()&&k.equals(pass))))return true;
            String p=qm.get("password");
            if(p!=null&&(p.equals(validKey)||(!pass.isEmpty()&&p.equals(pass))))return true;
        }
        String cookie=r.headers.get("cookie");
        if(cookie!=null){
            if(cookie.contains("salam_admin_key="+validKey))return true;
            if(!pass.isEmpty()&&cookie.contains("salam_admin_key="+pass))return true;
            if(!pass.isEmpty()&&cookie.contains("salam_admin_pass="+pass))return true;
        }
        String auth=r.headers.get("authorization");
        if(auth!=null&&auth.startsWith("Basic ")){
            try{
                String d=new String(Base64.getDecoder().decode(auth.substring(6)),StandardCharsets.UTF_8);
                int x=d.indexOf(':');
                if(x>=0){
                    String credential=d.substring(x+1);
                    if(credential.equals(validKey)||(!pass.isEmpty()&&credential.equals(pass)))return true;
                }
            }catch(Exception ignored){}
        }
        return false;
    }

    private String adminLoginPage(){
        return "<!doctype html><html><head><meta name='viewport' content='width=device-width,initial-scale=1'><meta name='theme-color' content='#020712'><title>Admin Security Gateway • Salam Web Server</title><style>*{box-sizing:border-box}body{margin:0;padding:24px 16px;background:radial-gradient(circle at 50% 0,#10223d 0,#010712 60%);color:#fff;font-family:system-ui,-apple-system,sans-serif;min-height:100vh;display:flex;align-items:center;justify-content:center}.card{max-width:440px;width:100%;background:linear-gradient(145deg,#07172b,#030d1a);border:1px solid #144975;border-radius:24px;padding:32px 24px;box-shadow:0 20px 50px rgba(0,0,0,0.8),0 0 30px rgba(0,217,255,0.15);text-align:center}.icon{font-size:42px;margin-bottom:12px}.badge{display:inline-block;padding:5px 14px;background:rgba(0,229,255,0.12);border:1px solid #00e5ff;color:#00e5ff;border-radius:20px;font-size:11px;font-weight:800;letter-spacing:1px;margin-bottom:14px}h1{font-size:22px;color:#fff;margin:0 0 8px;font-weight:800}p{font-size:13px;color:#94b6d9;line-height:1.6;margin:0 0 20px}input{width:100%;padding:14px;border-radius:14px;background:#020b17;border:1px solid #1a4d7c;color:#00f0ff;font-size:15px;margin-bottom:16px;outline:none;text-align:center;letter-spacing:2px}input:focus{border-color:#00f0ff;box-shadow:0 0 15px rgba(0,240,255,0.3)}button{width:100%;padding:14px;border-radius:14px;border:none;background:linear-gradient(135deg,#00f0ff,#4b4dff);color:#fff;font-weight:800;font-size:14px;cursor:pointer;box-shadow:0 6px 20px rgba(0,240,255,0.35)}button:hover{opacity:0.9}.home-link{display:inline-block;margin-top:16px;color:#6b8aa8;font-size:12px;text-decoration:none}</style></head><body><div class='card'><div class='icon'>🛡️</div><div class='badge'>AUTHENTICATION GATEWAY</div><h1>Admin Control Center</h1><p>Access to this server management console is restricted.<br>Please enter the <b>Admin Key</b> or <b>Web Password</b>.</p><form onsubmit='doLogin(event)'><input id='pwd' type='password' placeholder='Enter Admin Key / Password' required autofocus><button type='submit'>🔓 UNLOCK CPANEL</button></form><a class='home-link' href='/'>← Back to Website Homepage</a></div><script>function doLogin(e){e.preventDefault();var val=document.getElementById('pwd').value.trim();if(!val)return;document.cookie='salam_admin_key='+encodeURIComponent(val)+'; path=/; max-age=86400';window.location.href='/__salam__?key='+encodeURIComponent(val);}</script></body></html>";
    }

    private int respond(Socket s,int code,String type,String body)throws IOException{
        byte[]b=body.getBytes(StandardCharsets.UTF_8);
        OutputStream o=s.getOutputStream();
        String h="HTTP/1.1 "+code+" "+reason(code)+"\r\nContent-Type: "+type+"\r\nContent-Length: "+b.length+"\r\nAccess-Control-Allow-Origin: *\r\nAccess-Control-Allow-Methods: GET, POST, PUT, DELETE, OPTIONS, HEAD, PATCH\r\nAccess-Control-Allow-Headers: *\r\nAccess-Control-Expose-Headers: *\r\nCache-Control: no-store\r\nX-Content-Type-Options: nosniff\r\nConnection: close\r\n\r\n";
        byte[]hb=h.getBytes(StandardCharsets.ISO_8859_1);
        o.write(hb);
        o.write(b);
        o.flush();
        long out=b.length+hb.length;
        txBytes+=out;
        trackTx(out);
        return code;
    }
    private int sendFile(Socket s,File f,boolean head)throws IOException{
        OutputStream o=s.getOutputStream();
        String h="HTTP/1.1 200 OK\r\nContent-Type: "+mime(f.getName())+"\r\nContent-Length: "+f.length()+"\r\nAccess-Control-Allow-Origin: *\r\nAccess-Control-Allow-Methods: GET, POST, PUT, DELETE, OPTIONS, HEAD, PATCH\r\nAccess-Control-Allow-Headers: *\r\nAccess-Control-Expose-Headers: *\r\nCache-Control: no-store\r\nX-Content-Type-Options: nosniff\r\nConnection: close\r\n\r\n";
        byte[]hb=h.getBytes(StandardCharsets.ISO_8859_1);
        o.write(hb);
        long out=hb.length;
        if(!head)try(InputStream i=new FileInputStream(f)){
            byte[]b=new byte[16384];
            int n;
            while((n=i.read(b))>0){
                o.write(b,0,n);
                out+=n;
            }
        }
        o.flush();
        txBytes+=out;
        trackTx(out);
        return 200;
    }
    private String mime(String n){
        String x=n.toLowerCase(Locale.US);
        if(x.endsWith(".html")||x.endsWith(".htm"))return "text/html; charset=utf-8";
        if(x.endsWith(".css"))return "text/css; charset=utf-8";
        if(x.endsWith(".js")||x.endsWith(".mjs"))return "application/javascript; charset=utf-8";
        if(x.endsWith(".json"))return "application/json; charset=utf-8";
        if(x.endsWith(".png"))return "image/png";
        if(x.endsWith(".jpg")||x.endsWith(".jpeg"))return "image/jpeg";
        if(x.endsWith(".gif"))return "image/gif";
        if(x.endsWith(".svg"))return "image/svg+xml";
        if(x.endsWith(".webp"))return "image/webp";
        if(x.endsWith(".ico"))return "image/x-icon";
        if(x.endsWith(".pdf"))return "application/pdf";
        if(x.endsWith(".zip"))return "application/zip";
        if(x.endsWith(".mp4"))return "video/mp4";
        if(x.endsWith(".mp3"))return "audio/mpeg";
        if(x.endsWith(".wav"))return "audio/wav";
        if(x.endsWith(".ogg"))return "audio/ogg";
        if(x.endsWith(".webm"))return "video/webm";
        if(x.endsWith(".woff"))return "font/woff";
        if(x.endsWith(".woff2"))return "font/woff2";
        if(x.endsWith(".ttf"))return "font/ttf";
        if(x.endsWith(".otf"))return "font/otf";
        if(x.endsWith(".eot"))return "application/vnd.ms-fontobject";
        if(x.endsWith(".xml"))return "application/xml; charset=utf-8";
        if(x.endsWith(".txt"))return "text/plain; charset=utf-8";
        if(x.endsWith(".csv"))return "text/csv; charset=utf-8";
        if(x.endsWith(".map"))return "application/json; charset=utf-8";
        return "application/octet-stream";
    }
    private String reason(int c){switch(c){case 200:return "OK";case 400:return "Bad Request";case 401:return "Unauthorized";case 403:return "Forbidden";case 404:return "Not Found";case 405:return "Method Not Allowed";case 429:return "Too Many Requests";case 503:return "Service Unavailable";default:return "Error";}}
    private void close(Socket s){try{s.close();}catch(Exception ignored){}}
    private void log(String x){String s=now()+" | "+x;LOGS.add(s);while(LOGS.size()>MAX_LOGS)LOGS.remove(0);}
    private void logReq(String ip,Req r,int status,String note){String s=ip+" | "+r.method+" | "+r.path+" | "+status+(note.isEmpty()?"":" | "+note);log(s);HISTORY.add(now()+" | "+s);while(HISTORY.size()>MAX_HISTORY)HISTORY.remove(0);}
    private String join(List<String> a){StringBuilder b=new StringBuilder();for(String x:a)b.append(x).append('\n');return b.toString();}
    private String now(){return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss",Locale.US).format(new Date());}
    private String uptime(){if(!running)return "00:00:00";long s=(System.currentTimeMillis()-startedAt)/1000;return String.format(Locale.US,"%02d:%02d:%02d",s/3600,(s%3600)/60,s%60);}
    private String displayUrl(){
        if(pref().getBoolean("publicMode",true)&&TunnelManager.getInstance().isTunnelRunning()){
            String pub=TunnelManager.getInstance().getActivePublicUrl();
            if(pub!=null&&!pub.isEmpty())return pub;
        }
        String u=pref().getString("customUrl","").trim();
        return u.isEmpty()?currentUrl(this):u;
    }
    public static String activeUrl(Context c){
        SharedPreferences p=c.getSharedPreferences("server",Context.MODE_PRIVATE);
        if(p.getBoolean("publicMode",true)&&TunnelManager.getInstance().isTunnelRunning()){
            String pub=TunnelManager.getInstance().getActivePublicUrl();
            if(pub!=null&&!pub.isEmpty())return pub;
        }
        String u=p.getString("customUrl","").trim();
        return u.isEmpty()?currentUrl(c):u;
    }
    public static void deployTemplate(Context c,String templateName)throws IOException{
        File f=new File(c.getFilesDir(),"www");
        if(!f.exists())f.mkdirs();
        String assetDir="ecommerce".equals(templateName)?"ecommerce":"web";
        String[] list=c.getAssets().list(assetDir);
        if(list!=null&&list.length>0){
            for(String file:list){
                try(InputStream in=c.getAssets().open(assetDir+"/"+file);
                    FileOutputStream out=new FileOutputStream(new File(f,file))){
                    byte[]b=new byte[8192];
                    int n;
                    while((n=in.read(b))>0)out.write(b,0,n);
                }
            }
        }
    }
    public static String currentUrl(Context c){int p=c.getSharedPreferences("server",Context.MODE_PRIVATE).getInt("port",8080);return "http://"+localIp(c)+":"+p;}
    public static String localIp(Context c){try{ConnectivityManager m=(ConnectivityManager)c.getSystemService(Context.CONNECTIVITY_SERVICE);Network active=m.getActiveNetwork();if(active!=null){LinkProperties lp=m.getLinkProperties(active);if(lp!=null)for(LinkAddress a:lp.getLinkAddresses()){String x=a.getAddress().getHostAddress();if(x!=null&&!x.contains(":" )&&!x.startsWith("127."))return x;}}for(Network n:m.getAllNetworks()){LinkProperties lp=m.getLinkProperties(n);if(lp!=null)for(LinkAddress a:lp.getLinkAddresses()){String x=a.getAddress().getHostAddress();if(x!=null&&!x.contains(":" )&&!x.startsWith("127."))return x;}}}catch(Exception ignored){}return "0.0.0.0";}
    public static String wifiIp(Context c){return interfaceIp(c,"wlan");}
    public static String cellularIp(Context c){String x=interfaceIp(c,"rmnet");return "—".equals(x)?interfaceIp(c,"ccmni"):x;}
    private static String interfaceIp(Context c,String prefix){try{ConnectivityManager m=(ConnectivityManager)c.getSystemService(Context.CONNECTIVITY_SERVICE);for(Network n:m.getAllNetworks()){LinkProperties lp=m.getLinkProperties(n);if(lp!=null&&lp.getInterfaceName()!=null&&lp.getInterfaceName().startsWith(prefix))for(LinkAddress a:lp.getLinkAddresses()){String x=a.getAddress().getHostAddress();if(x!=null&&!x.contains(":" )&&!x.startsWith("127."))return x;}}}catch(Exception ignored){}return "—";}
    public static String interfaceName(Context c){
        try{
            ConnectivityManager m=(ConnectivityManager)c.getSystemService(Context.CONNECTIVITY_SERVICE);
            Network n=m.getActiveNetwork();
            if(n!=null){
                LinkProperties lp=m.getLinkProperties(n);
                if(lp!=null && lp.getInterfaceName()!=null) return lp.getInterfaceName();
            }
        }catch(Exception ignored){}
        return "—";
    }
    public static String networkSummary(Context c){try{ConnectivityManager m=(ConnectivityManager)c.getSystemService(Context.CONNECTIVITY_SERVICE);Network n=m.getActiveNetwork();LinkProperties lp=n==null?null:m.getLinkProperties(n);return "Interface: "+(lp==null?"—":String.valueOf(lp.getInterfaceName()))+" • Wi-Fi IP: "+wifiIp(c)+" • Mobile IP: "+cellularIp(c);}catch(Exception e){return "Network unavailable";}}
    public static String networkInfo(Context c){return networkSummary(c)+"\nGateway: "+gateway(c)+"\nDNS: "+dns(c);}
    public static String gateway(Context c){try{ConnectivityManager m=(ConnectivityManager)c.getSystemService(Context.CONNECTIVITY_SERVICE);Network n=m.getActiveNetwork();LinkProperties lp=n==null?null:m.getLinkProperties(n);if(lp!=null&&!lp.getRoutes().isEmpty())for(RouteInfo r:lp.getRoutes())if(r.isDefaultRoute()&&r.getGateway()!=null)return r.getGateway().getHostAddress();}catch(Exception ignored){}return "—";}
    public static String dns(Context c){try{ConnectivityManager m=(ConnectivityManager)c.getSystemService(Context.CONNECTIVITY_SERVICE);Network n=m.getActiveNetwork();LinkProperties lp=n==null?null:m.getLinkProperties(n);if(lp!=null&&!lp.getDnsServers().isEmpty())return lp.getDnsServers().get(0).getHostAddress();}catch(Exception ignored){}return "—";}
    public static String memoryText(Context c){ActivityManager a=(ActivityManager)c.getSystemService(Context.ACTIVITY_SERVICE);ActivityManager.MemoryInfo m=new ActivityManager.MemoryInfo();a.getMemoryInfo(m);return fmt(m.totalMem-m.availMem)+" / "+fmt(m.totalMem);}
    public static String storageText(Context c){StatFs f=new StatFs(webRoot(c).getAbsolutePath());long t=f.getTotalBytes(),a=f.getAvailableBytes();return fmt(t-a)+" / "+fmt(t);}
    private String storageText(){return storageText(this);}
    public static String cpuText(){try{long[] a=readCpu();Thread.sleep(80);long[] b=readCpu();long total=b[0]-a[0],idle=b[1]-a[1];if(total>0){long busy=total-idle;int pct=(int)Math.round((busy*100.0)/total);return Math.max(0,Math.min(100,pct))+"%";} }catch(Exception ignored){}try{BufferedReader r=new BufferedReader(new FileReader("/proc/loadavg"));String line=r.readLine();r.close();if(line!=null){String[] v=line.trim().split("\\s+");if(v.length>0){double load=Double.parseDouble(v[0]);int cores=Math.max(1,Runtime.getRuntime().availableProcessors());int pct=(int)Math.round(Math.min(100d,(load/cores)*100d));return Math.max(0,pct)+"% (load)";}}}catch(Exception ignored){}return "N/A";}
    private static long[] readCpu()throws Exception{try(BufferedReader r=new BufferedReader(new FileReader("/proc/stat"))){String line=r.readLine();if(line==null||!line.startsWith("cpu"))throw new IOException("cpu stat unavailable");String[] v=line.trim().split("\\s+");if(v.length<5)throw new IOException("cpu stat incomplete");long total=0;for(int i=1;i<v.length;i++)total+=Long.parseLong(v[i]);long idle=Long.parseLong(v[4]);if(v.length>5)idle+=Long.parseLong(v[5]);return new long[]{total,idle};}}
    public static void trackRx(String ip,long bytes){if(ip!=null){CLIENT_FLOW.compute(ip,(k,v)->v==null?bytes:v+bytes);CLIENT_REQS.compute(ip,(k,v)->v==null?1L:v+1L);}}
    public static void trackTx(long bytes){}
    public static synchronized void updateFlowSpeed(){long now=System.currentTimeMillis();if(lastSpeedCalc==0){lastSpeedCalc=now;prevRx=rxBytes;prevTx=txBytes;return;}long dt=now-lastSpeedCalc;if(dt>=400){long drx=Math.max(0,rxBytes-prevRx), dtx=Math.max(0,txBytes-prevTx);curRxSpeed=(drx*1000)/dt;curTxSpeed=(dtx*1000)/dt;long tot=curRxSpeed+curTxSpeed;if(tot>peakSpeed)peakSpeed=tot;prevRx=rxBytes;prevTx=txBytes;lastSpeedCalc=now;}}
    public static long getRxBytes(){return rxBytes;}
    public static long getTxBytes(){return txBytes;}
    public static long getRxSpeed(){updateFlowSpeed();return curRxSpeed;}
    public static long getTxSpeed(){updateFlowSpeed();return curTxSpeed;}
    public static String rxSpeedText(){updateFlowSpeed();return fmt(curRxSpeed)+"/s";}
    public static String txSpeedText(){updateFlowSpeed();return fmt(curTxSpeed)+"/s";}
    public static String peakSpeedText(){return fmt(peakSpeed)+"/s";}
    public static String totalFlowText(){return fmt(rxBytes+txBytes);}
    public static void resetFlow(){rxBytes=0;txBytes=0;prevRx=0;prevTx=0;curRxSpeed=0;curTxSpeed=0;peakSpeed=0;lastSpeedCalc=System.currentTimeMillis();CLIENT_FLOW.clear();CLIENT_REQS.clear();}
    public static String fmt(long b){if(b<1024)return b+" B";if(b<1048576)return b/1024+" KB";if(b<1073741824L)return b/1048576+" MB";return String.format(Locale.US,"%.1f GB",b/1073741824d);}
    public static String trafficText(){long b=rxBytes+txBytes;return fmt(b);}
    public static boolean isRunning(){return running;}
    public static long getRequestCount(){return requests.get();}
    public static int getClientCount(){return clients.size();}
    public static int getVisitorCount(){return VISITOR_LAST_SEEN.size();}
    public static boolean isPathInMaintenance(String path){return MAINTENANCE_FILES.contains(path);}
    public static boolean toggleMaintenancePath(String path){if(MAINTENANCE_FILES.contains(path)){MAINTENANCE_FILES.remove(path);return false;}else{MAINTENANCE_FILES.add(path);return true;}}
    public static void saveSecurity(Context c,String pass,String allow,String block,String rate){int r=120;try{r=Integer.parseInt(rate);}catch(Exception ignored){}c.getSharedPreferences("server",Context.MODE_PRIVATE).edit().putString("password",pass==null?"":pass).putString("allowIps",allow==null?"":allow).putString("blockIps",block==null?"":block).putInt("rate",Math.max(1,r)).apply();rateLimit=Math.max(1,r);}
    public static void unblockIp(String ip){/* UI calls save below when service is active; retained for API compatibility. */}
    public static String readText(File f)throws IOException{return new String(readAll(f),StandardCharsets.UTF_8);}
    public static void writeText(File f,String s)throws IOException{try(FileOutputStream o=new FileOutputStream(f)){o.write(s.getBytes(StandardCharsets.UTF_8));}}
    public static void copyRecursive(File a,File b)throws IOException{if(a.isDirectory()){b.mkdirs();File[]x=a.listFiles();if(x!=null)for(File q:x)copyRecursive(q,new File(b,q.getName()));}else{File p=b.getParentFile();if(p!=null)p.mkdirs();try(InputStream i=new FileInputStream(a);OutputStream o=new FileOutputStream(b)){byte[]z=new byte[16384];int n;while((n=i.read(z))>0)o.write(z,0,n);}}}
    public static void deleteRecursive(File f){if(f.isDirectory()){File[]x=f.listFiles();if(x!=null)for(File q:x)deleteRecursive(q);}f.delete();}
    public static void zip(File src,File out)throws IOException{new WebServerService().zipStandalone(src,out);}
    private void zipStandalone(File src,File out)throws IOException{try(ZipOutputStream z=new ZipOutputStream(new FileOutputStream(out))){File base=src.isDirectory()?src.getParentFile():src.getParentFile();zipRec(src,base,z);}}
    public static void unzip(File z,File dest)throws IOException{if(dest==null)throw new IOException("Invalid destination");String base=dest.getCanonicalPath();try(ZipInputStream in=new ZipInputStream(new FileInputStream(z))){ZipEntry e;while((e=in.getNextEntry())!=null){File o=new File(dest,e.getName());String cp=o.getCanonicalPath();if(!cp.equals(base)&&!cp.startsWith(base+File.separator))throw new IOException("Unsafe ZIP entry");if(e.isDirectory())o.mkdirs();else{File p=o.getParentFile();if(p!=null)p.mkdirs();try(OutputStream out=new FileOutputStream(o)){byte[]b=new byte[16384];int n;while((n=in.read(b))>0)out.write(b,0,n);}}}}}
    private static byte[] readAll(File f)throws IOException{try(InputStream i=new FileInputStream(f);ByteArrayOutputStream o=new ByteArrayOutputStream()){byte[]b=new byte[16384];int n;while((n=i.read(b))>0)o.write(b,0,n);return o.toByteArray();}}
    private String json(String s){return(s==null?"":s).replace("\\","\\\\").replace("\"","\\\"").replace("\r","").replace("\n","\\n");}
    private String html(String s){return(s==null?"":s).replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&#39;");}
    private static String decode(String s){try{return URLDecoder.decode(s,StandardCharsets.UTF_8.name());}catch(Exception e){return s;}}
    private String ok(String s){return "{\"ok\":true,\"message\":\""+json(s)+"\"}";}
    private String err(String s){return "{\"ok\":false,\"error\":\""+json(s==null?"Unknown error":s)+"\"}";}

    static class Req{String method,path,query="";Map<String,String>headers=new HashMap<>();byte[]body;int headerBytes;}
    private static void zipRec(File f,File base,ZipOutputStream z)throws IOException{String name=base.toPath().relativize(f.toPath()).toString().replace('\\','/');if(f.isDirectory()){if(!name.endsWith("/"))name+="/";z.putNextEntry(new ZipEntry(name));z.closeEntry();File[]a=f.listFiles();if(a!=null)for(File x:a)zipRec(x,base,z);}else{z.putNextEntry(new ZipEntry(name));try(InputStream i=new FileInputStream(f)){byte[]b=new byte[16384];int n;while((n=i.read(b))>0)z.write(b,0,n);}z.closeEntry();}}
}
