package com.salam.androidwebserver;

import android.app.*;
import android.content.*;
import android.os.*;
import android.net.*;
import android.net.wifi.*;
import java.io.*;
import java.net.*;
import java.text.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.*;

public class WebServerService extends Service {
    public static volatile boolean running=false;
    public static final AtomicInteger requests=new AtomicInteger(0);
    public static final Set<String> clients=ConcurrentHashMap.newKeySet();
    public static final CopyOnWriteArrayList<String> LOGS=new CopyOnWriteArrayList<>();
    public static final CopyOnWriteArrayList<String> HISTORY=new CopyOnWriteArrayList<>();
    public static volatile String password="",customHost="";
    public static volatile int rateLimit=60,maxClients=32;
    static final Set<String> ALLOW=ConcurrentHashMap.newKeySet(),BLOCK=ConcurrentHashMap.newKeySet();
    static final ConcurrentHashMap<String,ArrayDeque<Long>> RATE=new ConcurrentHashMap<>();
    static volatile long startedAt=0,rx=0,tx=0;
    static ServerSocket server; static ExecutorService pool=Executors.newCachedThreadPool();

    @Override public void onCreate(){super.onCreate();load(this);createChannel();startForeground(77,notification());}
    @Override public int onStartCommand(Intent i,int flags,int id){String a=i==null?null:i.getAction();if("STOP".equals(a))stopServer();else startServer();return START_STICKY;}
    void createChannel(){if(Build.VERSION.SDK_INT>=26){NotificationChannel c=new NotificationChannel("salam_v10","Salam Web Server",NotificationManager.IMPORTANCE_LOW);c.setDescription("Persistent server status");getSystemService(NotificationManager.class).createNotificationChannel(c);}}
    Notification notification(){return new Notification.Builder(this,"salam_v10").setContentTitle("Salam Web Server").setContentText(running?"ONLINE • "+currentUrl(this):"Server starting…").setSmallIcon(android.R.drawable.stat_sys_upload_done).setOngoing(true).setOnlyAlertOnce(true).build();}
    void notifyStatus(){if(running)getSystemService(NotificationManager.class).notify(77,notification());}
    void startServer(){if(running)return;try{server=new ServerSocket(8080,64,InetAddress.getByName("0.0.0.0"));startedAt=System.currentTimeMillis();running=true;addLog("SERVER STARTED • "+currentUrl(this));notifyStatus();pool.submit(()->{while(running){try{Socket s=server.accept();if(clients.size()>=maxClients){reject(s,"Server busy");continue;}pool.submit(()->handle(s));}catch(Exception e){if(running)addLog("ACCEPT ERROR • "+e.getMessage());}}});}catch(Exception e){addLog("START ERROR • "+e.getMessage());running=false;stopSelf();}}
    void stopServer(){running=false;try{if(server!=null)server.close();}catch(Exception ignored){}addLog("SERVER STOPPED");stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();}
    void reject(Socket s,String msg){try{send(s,503,"text/plain",msg);}catch(Exception ignored){}try{s.close();}catch(Exception ignored){}}
    void handle(Socket s){String ip=s.getInetAddress().getHostAddress();clients.add(ip);requests.incrementAndGet();try{s.setSoTimeout(12000);InputStream raw=s.getInputStream();BufferedReader r=new BufferedReader(new InputStreamReader(raw));String first=r.readLine();if(first==null)return;String[] z=first.split(" ");if(z.length<2)return;String method=z[0],target=z[1];Map<String,String> headers=new HashMap<>();String line;while((line=r.readLine())!=null&&!line.isEmpty()){int k=line.indexOf(':');if(k>0)headers.put(line.substring(0,k).trim().toLowerCase(Locale.US),line.substring(k+1).trim());}String path=URLDecoder.decode(target.split("\\?",2)[0],"UTF-8");rx+=first.length();addLog(ip+" • "+method+" "+path);HISTORY.add(now()+" | "+ip+" | "+method+" "+path);trimHistory();if(BLOCK.contains(ip)){send(s,403,"text/plain","Blocked IP");return;}if(!ALLOW.isEmpty()&&!ALLOW.contains(ip)){send(s,403,"text/plain","IP not allowed");return;}if(!allowRate(ip)){send(s,429,"text/plain","Rate limit exceeded");return;}if(!authorized(path,headers)){authRequired(s);return;}if(path.equals("/__salam__/api/info"))json(s,info());else if(path.equals("/__salam__/api/logs"))json(s,"{\"logs\":\""+esc(String.join("\n",LOGS))+"\"}");else if(path.equals("/__salam__/api/history"))json(s,"{\"history\":\""+esc(String.join("\n",HISTORY))+"\"}");else if(path.equals("/__salam__/api/clear")){LOGS.clear();HISTORY.clear();send(s,200,"text/plain","OK");}else if(path.equals("/__salam__/api/stop")){send(s,200,"text/plain","Stopping");new Handler(Looper.getMainLooper()).postDelayed(this::stopServer,100); }else if(path.equals("/__salam__/api/restart")){send(s,200,"text/plain","Restarting");new Handler(Looper.getMainLooper()).postDelayed(()->{stopServer();},100);}else if(path.startsWith("/__salam__/"))send(s,200,"text/html",controlPage());else serve(s,path);}catch(Exception e){addLog("CLIENT ERROR • "+ip+" • "+e.getMessage());}finally{clients.remove(ip);try{s.close();}catch(Exception ignored){}}}
    boolean authorized(String path,Map<String,String> h){
        if(password.isEmpty()) return true;
        String a=h.get("authorization");
        if(a==null||!a.startsWith("Basic ")) return false;
        try{
            String decoded=new String(android.util.Base64.decode(a.substring(6),android.util.Base64.DEFAULT),"UTF-8");
            int k=decoded.indexOf(':'); if(k<0)return false;
            return decoded.substring(k+1).equals(password);
        }catch(Exception e){return false;}
    }
    void authRequired(Socket s)throws Exception{String h="HTTP/1.1 401 Unauthorized\r\nWWW-Authenticate: Basic realm=\"Salam Web Server\"\r\nContent-Length: 0\r\nConnection: close\r\n\r\n";s.getOutputStream().write(h.getBytes());}
    boolean allowRate(String ip){if(rateLimit<=0)return true;long now=System.currentTimeMillis();ArrayDeque<Long> q=RATE.computeIfAbsent(ip,k->new ArrayDeque<>());synchronized(q){while(!q.isEmpty()&&now-q.peekFirst()>60000)q.pollFirst();if(q.size()>=rateLimit)return false;q.addLast(now);return true;}}
    void serve(Socket s,String path)throws Exception{File base=webRoot(this);if(path.equals("/")){send(s,200,"text/html",homePage());return;}File f=safe(base,path);if(f==null){send(s,403,"text/plain","Forbidden");return;}if(!f.exists()){send(s,404,"text/plain","Not found");return;}if(f.isDirectory()){send(s,200,"text/html",directory(f));return;}FileInputStream in=new FileInputStream(f);String h="HTTP/1.1 200 OK\r\nContent-Type:"+mime(f.getName())+"\r\nContent-Length:"+f.length()+"\r\nContent-Disposition:inline; filename=\""+f.getName().replace("\"","")+"\"\r\nConnection: close\r\n\r\n";byte[] hb=h.getBytes("UTF-8");s.getOutputStream().write(hb);byte[] b=new byte[16384];int n;while((n=in.read(b))>0){s.getOutputStream().write(b,0,n);tx+=n;}in.close();}
    File safe(File base,String path)throws Exception{String rel=path.startsWith("/")?path.substring(1):path;File f=new File(base,rel);String root=base.getCanonicalPath(),can=f.getCanonicalPath();return can.equals(root)||can.startsWith(root+File.separator)?f:null;}
    String homePage(){return htmlShell("Salam Web Server","<div class=card><h2>⚡ SERVER ONLINE</h2><p>"+currentUrl(this)+"</p><a href='/__salam__/'>Open Control Panel</a></div>");}
    String controlPage(){return "<!doctype html><html><head><meta name=viewport content='width=device-width,initial-scale=1'><style>"+css()+"</style></head><body><header>⚡ Salam Web Server <span>V10</span></header><div id=cards class=grid></div><div class=card><button onclick='act(\"/ __salam__/api/stop\")'>STOP</button><button onclick='act(\"/ __salam__/api/restart\")'>RESTART</button><button onclick='load()'>REFRESH</button><button onclick='act(\"/ __salam__/api/clear\")'>CLEAR LOGS</button></div><div class=card><h3>Live Logs</h3><pre id=log></pre></div><div class=card><h3>Access History</h3><pre id=his></pre></div><script>async function j(u){return await fetch(u).then(r=>r.json())}async function load(){let x=await j('/__salam__/api/info');cards.innerHTML='<div class=card>Status: '+(x.running?'ONLINE':'OFFLINE')+'</div><div class=card>URL: '+x.url+'</div><div class=card>Requests: '+x.requests+'</div><div class=card>Clients: '+x.clients+'</div><div class=card>Traffic: '+x.traffic+'</div>';let l=await j('/__salam__/api/logs');log.textContent=l.logs;let h=await j('/__salam__/api/history');his.textContent=h.history}async function act(u){u=u.replace(' ','');await fetch(u);setTimeout(load,300)}load();setInterval(load,1000)</script></body></html>";}
    String css(){return"body{font-family:Arial;background:#020a15;color:#fff;padding:12px;margin:0}header{font-size:22px;font-weight:800;padding:14px}header span{float:right;color:#1cd3ff}.grid{display:grid;grid-template-columns:1fr 1fr;gap:8px}.card{background:#092139;border:1px solid #103a5b;border-radius:20px;padding:16px;margin:7px 0}button,a{background:linear-gradient(90deg,#1cd3ff,#4c53ff);color:#fff;border:0;border-radius:14px;padding:12px;margin:3px;text-decoration:none}pre{white-space:pre-wrap;color:#a9c2dc}";}
    String htmlShell(String title,String body){return"<!doctype html><html><head><meta name=viewport content='width=device-width,initial-scale=1'><title>"+title+"</title><style>"+css()+"</style></head><body>"+body+"</body></html>";}
    String directory(File d){StringBuilder b=new StringBuilder(htmlShell("Files","<div class=card><h2>📁 "+d.getName()+"</h2>"));File[] fs=d.listFiles();if(fs!=null)for(File f:fs)b.append("<div class=card><a href='").append(encodePath(d,f)).append("'>").append(escHtml(f.getName())).append("</a></div>");return b.append("</body></html>").toString();}
    String encodePath(File d,File f){return f.getName();}
    String info(){return"{\"running\":"+running+",\"requests\":"+requests.get()+",\"clients\":"+clients.size()+",\"url\":\""+esc(currentUrl(this))+"\",\"traffic\":\""+esc(trafficText())+"\",\"uptime\":\""+uptime()+"\"}";}
    void send(Socket s,int code,String type,String body)throws Exception{byte[] b=body.getBytes("UTF-8");String h="HTTP/1.1 "+code+" "+(code==200?"OK":code==401?"Unauthorized":code==403?"Forbidden":code==404?"Not Found":code==429?"Too Many Requests":"Error")+"\r\nContent-Type:"+type+"; charset=utf-8\r\nContent-Length:"+b.length+"\r\nConnection: close\r\n\r\n";s.getOutputStream().write(h.getBytes("UTF-8"));s.getOutputStream().write(b);}
    void json(Socket s,String body)throws Exception{send(s,200,"application/json",body);}
    String mime(String n){String x=n.toLowerCase(Locale.US);if(x.endsWith(".html"))return"text/html";if(x.endsWith(".css"))return"text/css";if(x.endsWith(".js"))return"application/javascript";if(x.endsWith(".json"))return"application/json";if(x.endsWith(".png"))return"image/png";if(x.endsWith(".jpg")||x.endsWith(".jpeg"))return"image/jpeg";if(x.endsWith(".gif"))return"image/gif";if(x.endsWith(".svg"))return"image/svg+xml";if(x.endsWith(".pdf"))return"application/pdf";if(x.endsWith(".txt"))return"text/plain";return"application/octet-stream";}
    static File webRoot(Context c){File f=new File(c.getFilesDir(),"www");if(!f.exists())f.mkdirs();return f;}
    static String now(){return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss",Locale.getDefault()).format(new Date());}
    static void addLog(String x){LOGS.add(new SimpleDateFormat("HH:mm:ss",Locale.getDefault()).format(new Date())+"  "+x);while(LOGS.size()>1000)LOGS.remove(0);}
    static void trimHistory(){while(HISTORY.size()>1000)HISTORY.remove(0);}
    static String esc(String x){return x.replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n").replace("\r","");}
    static String escHtml(String x){return x.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;");}
    static void load(Context c){SharedPreferences p=c.getSharedPreferences("salam_security",0);password=p.getString("password","");customHost=p.getString("host","");String a=p.getString("allow","");String b=p.getString("block","");if(!a.isEmpty())ALLOW.add(a);if(!b.isEmpty())BLOCK.add(b);rateLimit=p.getInt("rate",60);}
    public static void saveSecurity(Context c,String pass,String allow,String block,String rate){password=pass;SharedPreferences.Editor e=c.getSharedPreferences("salam_security",0).edit().putString("password",pass).putString("allow",allow).putString("block",block);try{rateLimit=Integer.parseInt(rate);e.putInt("rate",rateLimit);}catch(Exception ignored){}e.apply();ALLOW.clear();BLOCK.clear();if(!allow.trim().isEmpty())ALLOW.add(allow.trim());if(!block.trim().isEmpty())BLOCK.add(block.trim());}
    public static void unblockIp(String ip){if(ip!=null)BLOCK.remove(ip.trim());}
    public static String localIp(Context c){try{ConnectivityManager cm=(ConnectivityManager)c.getSystemService(Context.CONNECTIVITY_SERVICE);Network n=cm.getActiveNetwork();LinkProperties lp=cm.getLinkProperties(n);if(lp!=null)for(LinkAddress a:lp.getLinkAddresses())if(a.getAddress() instanceof Inet4Address&&!a.getAddress().isLoopbackAddress())return a.getAddress().getHostAddress();}catch(Exception ignored){}try{Enumeration<NetworkInterface> e=NetworkInterface.getNetworkInterfaces();while(e.hasMoreElements()){for(Enumeration<InetAddress>a=e.nextElement().getInetAddresses();a.hasMoreElements();){InetAddress i=a.nextElement();if(i instanceof Inet4Address&&!i.isLoopbackAddress())return i.getHostAddress();}}}catch(Exception ignored){}return"127.0.0.1";}
    public static String wifiIp(Context c){try{ConnectivityManager cm=(ConnectivityManager)c.getSystemService(Context.CONNECTIVITY_SERVICE);for(Network n:cm.getAllNetworks()){NetworkCapabilities nc=cm.getNetworkCapabilities(n);if(nc!=null&&nc.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)){LinkProperties lp=cm.getLinkProperties(n);if(lp!=null)for(LinkAddress a:lp.getLinkAddresses())if(a.getAddress() instanceof Inet4Address)return a.getAddress().getHostAddress();}}}catch(Exception ignored){}return"—";}
    public static String cellularIp(Context c){try{ConnectivityManager cm=(ConnectivityManager)c.getSystemService(Context.CONNECTIVITY_SERVICE);for(Network n:cm.getAllNetworks()){NetworkCapabilities nc=cm.getNetworkCapabilities(n);if(nc!=null&&nc.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)){LinkProperties lp=cm.getLinkProperties(n);if(lp!=null)for(LinkAddress a:lp.getLinkAddresses())if(a.getAddress() instanceof Inet4Address)return a.getAddress().getHostAddress();}}}catch(Exception ignored){}return"—";}
    public static String interfaceName(Context c){try{ConnectivityManager cm=(ConnectivityManager)c.getSystemService(Context.CONNECTIVITY_SERVICE);Network n=cm.getActiveNetwork();LinkProperties lp=cm.getLinkProperties(n);return lp==null?"none":String.valueOf(lp.getInterfaceName());}catch(Exception e){return"none";}}
    public static String gateway(Context c){try{ConnectivityManager cm=(ConnectivityManager)c.getSystemService(Context.CONNECTIVITY_SERVICE);LinkProperties lp=cm.getLinkProperties(cm.getActiveNetwork());if(lp!=null&&!lp.getRoutes().isEmpty())return String.valueOf(lp.getRoutes().get(0).getGateway());}catch(Exception ignored){}return"—";}
    public static String dns(Context c){try{ConnectivityManager cm=(ConnectivityManager)c.getSystemService(Context.CONNECTIVITY_SERVICE);LinkProperties lp=cm.getLinkProperties(cm.getActiveNetwork());if(lp!=null&&!lp.getDnsServers().isEmpty())return String.valueOf(lp.getDnsServers().get(0).getHostAddress());}catch(Exception ignored){}return"—";}
    public static String networkInfo(Context c){return"Interface: "+interfaceName(c)+" • Wi-Fi IP: "+wifiIp(c)+" • Mobile IP: "+cellularIp(c);}
    public static String currentUrl(Context c){return"http://"+(customHost.isEmpty()?localIp(c):customHost)+":8080";}
    public static String memoryText(Context c){ActivityManager a=(ActivityManager)c.getSystemService(Context.ACTIVITY_SERVICE);ActivityManager.MemoryInfo m=new ActivityManager.MemoryInfo();a.getMemoryInfo(m);return((m.totalMem-m.availMem)/1048576)+" / "+(m.totalMem/1048576)+" MB";}
    public static String storageText(Context c){StatFs s=new StatFs(webRoot(c).getPath());return((s.getTotalBytes()-s.getAvailableBytes())/1048576)+" / "+(s.getTotalBytes()/1048576)+" MB";}
    static long prevIdle=-1,prevTotal=-1;
    public static synchronized String cpuText(){
        try{
            BufferedReader r=new BufferedReader(new FileReader("/proc/stat"));String l=r.readLine();r.close();
            if(l==null)return"n/a";String[] p=l.trim().split("\\s+");if(p.length<5)return"n/a";
            long user=Long.parseLong(p[1]),nice=Long.parseLong(p[2]),sys=Long.parseLong(p[3]),idle=Long.parseLong(p[4]);
            long total=user+nice+sys+idle; if(p.length>5)total+=Long.parseLong(p[5]); if(p.length>6)total+=Long.parseLong(p[6]); if(p.length>7)total+=Long.parseLong(p[7]);
            if(prevTotal<0){prevTotal=total;prevIdle=idle;return"sampling…";}
            long dt=total-prevTotal,di=idle-prevIdle;prevTotal=total;prevIdle=idle;
            if(dt<=0)return"0%";return Math.max(0,Math.min(100,Math.round((1.0-di/(double)dt)*100)))+"%";
        }catch(Exception e){return"n/a";}
    }
    public static String trafficText(){return((rx+tx)/1024)+" KB";}
    public static String uptime(){if(startedAt<=0)return"00:00:00";long s=(System.currentTimeMillis()-startedAt)/1000;return String.format(Locale.US,"%02d:%02d:%02d",s/3600,(s/60)%60,s%60);}
    public static String readText(File f)throws Exception{BufferedReader r=new BufferedReader(new FileReader(f));StringBuilder b=new StringBuilder();String x;while((x=r.readLine())!=null)b.append(x).append('\n');r.close();return b.toString();}
    public static void writeText(File f,String s)throws Exception{FileWriter w=new FileWriter(f);w.write(s);w.close();}
    public static void deleteRecursive(File f){if(f==null)return;if(f.isDirectory()){File[]x=f.listFiles();if(x!=null)for(File y:x)deleteRecursive(y);}f.delete();}
    public static void copyRecursive(File a,File b)throws Exception{if(a.isDirectory()){b.mkdirs();File[]x=a.listFiles();if(x!=null)for(File y:x)copyRecursive(y,new File(b,y.getName()));}else{FileInputStream i=new FileInputStream(a);FileOutputStream o=new FileOutputStream(b);byte[]z=new byte[8192];int n;while((n=i.read(z))>0)o.write(z,0,n);i.close();o.close();}}
    public static void zip(File src,File dest)throws Exception{ZipOutputStream z=new ZipOutputStream(new FileOutputStream(dest));zipRec(src,src.isDirectory()?src.getParentFile():src.getParentFile(),z);z.close();}
    public static void unzip(File zip,File dest)throws Exception{
        String root=dest.getCanonicalPath();
        ZipInputStream in=new ZipInputStream(new FileInputStream(zip));ZipEntry e;
        byte[] b=new byte[16384];
        while((e=in.getNextEntry())!=null){
            File out=new File(dest,e.getName());String can=out.getCanonicalPath();
            if(!can.equals(root)&&!can.startsWith(root+File.separator)){in.close();throw new SecurityException("Unsafe ZIP path");}
            if(e.isDirectory()){out.mkdirs();continue;}
            File par=out.getParentFile();if(par!=null)par.mkdirs();
            FileOutputStream o=new FileOutputStream(out);int n;while((n=in.read(b))>0)o.write(b,0,n);o.close();in.closeEntry();
        }in.close();
    }
    static void zipRec(File f,File base,ZipOutputStream z)throws Exception{if(f.isDirectory()){File[]x=f.listFiles();if(x!=null)for(File y:x)zipRec(y,base,z);return;}String name=base.toPath().relativize(f.toPath()).toString().replace('\\','/');z.putNextEntry(new ZipEntry(name));FileInputStream i=new FileInputStream(f);byte[]b=new byte[8192];int n;while((n=i.read(b))>0)z.write(b,0,n);i.close();z.closeEntry();}
    @Override public IBinder onBind(Intent i){return null;}
}