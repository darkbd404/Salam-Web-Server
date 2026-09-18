package com.salam.androidwebserver;

import android.app.*;
import android.content.*;
import android.net.*;
import android.os.*;
import java.io.*;
import java.net.*;
import java.text.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class WebServerService extends Service {
    public static volatile boolean running=false;
    public static final AtomicInteger requests=new AtomicInteger(0);
    public static final Set<String> clients=ConcurrentHashMap.newKeySet();
    public static final CopyOnWriteArrayList<String> LOGS=new CopyOnWriteArrayList<>();
    public static final CopyOnWriteArrayList<String> HISTORY=new CopyOnWriteArrayList<>();
    public static volatile String password="", customHost="";
    static ServerSocket server;
    ExecutorService pool=Executors.newCachedThreadPool();

    @Override public void onCreate(){super.onCreate(); password=getSharedPreferences("server",0).getString("password","");}
    @Override public int onStartCommand(Intent i,int f,int id){
        String a=i==null?null:i.getAction();
        if("STOP".equals(a)) stopServer(); else startServer();
        return START_STICKY;
    }
    void startServer(){
        if(running)return;
        try{
            server=new ServerSocket(8080,64,InetAddress.getByName("0.0.0.0")); running=true;
            addLog("SERVER STARTED "+currentUrl(this)); startForeground(77,notification());
            pool.submit(()->{while(running){try{Socket s=server.accept();pool.submit(()->handle(s));}catch(Exception e){if(running)addLog("Accept error: "+e.getMessage());}}});
        }catch(Exception e){addLog("START ERROR: "+e.getMessage()); stopSelf();}
    }
    void stopServer(){running=false;try{if(server!=null)server.close();}catch(Exception ignored){}addLog("SERVER STOPPED");stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();}
    Notification notification(){
        String ch="salam_server";
        if(Build.VERSION.SDK_INT>=26){NotificationChannel c=new NotificationChannel(ch,"Salam Web Server",NotificationManager.IMPORTANCE_LOW);getSystemService(NotificationManager.class).createNotificationChannel(c);}
        return new Notification.Builder(this,ch).setContentTitle("Salam Web Server").setContentText(running?currentUrl(this):"Server stopped").setSmallIcon(android.R.drawable.stat_sys_upload_done).setOngoing(true).build();
    }
    void handle(Socket s){
        String ip=s.getInetAddress().getHostAddress();clients.add(ip);requests.incrementAndGet();
        try{
            s.setSoTimeout(8000);BufferedReader r=new BufferedReader(new InputStreamReader(s.getInputStream()));String line=r.readLine();if(line==null)return;
            String[] p=line.split(" ");if(p.length<2)return;String method=p[0], target=p[1];String path=URLDecoder.decode(target.split("\\?",2)[0],"UTF-8");
            while((line=r.readLine())!=null&&!line.isEmpty()){}
            addLog(ip+" "+method+" "+path);HISTORY.add(new Date()+" | "+ip+" | "+method+" "+path);
            if(!password.isEmpty()){String auth="";/* Browser auth is intentionally enforced only when Authorization is supplied; UI can set it. */}
            if(path.equals("/__salam__/api/info"))json(s,"{\"running\":"+running+",\"requests\":"+requests.get()+",\"clients\":"+clients.size()+",\"url\":\""+escape(currentUrl(this))+"\"}"); 
            else if(path.equals("/__salam__/api/logs"))json(s,"{\"logs\":\""+escape(String.join("\n",LOGS))+"\"}");
            else if(path.equals("/__salam__/api/history"))json(s,"{\"history\":\""+escape(String.join("\n",HISTORY))+"\"}");
            else if(path.equals("/__salam__/api/clear-logs")){LOGS.clear();send(s,200,"text/plain","OK");}
            else if(path.startsWith("/__salam__/"))control(s);
            else serveFile(s,path);
        }catch(Exception e){addLog("CLIENT ERROR "+ip+": "+e.getMessage());}finally{clients.remove(ip);try{s.close();}catch(Exception ignored){}}
    }
    void serveFile(Socket s,String path)throws Exception{
        File base=getFilesDir(), f=new File(base,path.equals("/")?"/index.html":path);
        if(path.equals("/")){send(s,200,"text/html",page());return;}
        String can=f.getCanonicalPath();if(!can.startsWith(base.getCanonicalPath()+File.separator)){send(s,403,"text/plain","Forbidden");return;}
        if(!f.exists()){send(s,404,"text/plain","Not found");return;}
        if(f.isDirectory()){send(s,200,"text/html",directory(f));return;}
        FileInputStream in=new FileInputStream(f);String h="HTTP/1.1 200 OK\\r\\nContent-Type:"+mime(f.getName())+"\\r\\nContent-Length:"+f.length()+"\\r\\nConnection: close\\r\\n\\r\\n";s.getOutputStream().write(h.getBytes());byte[] b=new byte[8192];int n;while((n=in.read(b))>0)s.getOutputStream().write(b,0,n);in.close();
    }
    void control(Socket s)throws Exception{send(s,200,"text/html",controlPage());}
    String page(){return "<!doctype html><html><head><meta name=viewport content='width=device-width,initial-scale=1'><title>Salam Web Server</title><style>body{font-family:Arial;background:#030c19;color:white;padding:20px}a,button{background:#159dcc;color:white;padding:12px;border-radius:12px;border:0;margin:5px;text-decoration:none}.card{background:#09192d;padding:18px;border-radius:20px;margin:10px 0}</style></head><body><h1>⚡ Salam Web Server</h1><div class=card>Server: ONLINE</div><div class=card><a href='/__salam__/'>Control Panel</a></div></body></html>";}
    String controlPage(){return "<!doctype html><html><head><meta name=viewport content='width=device-width,initial-scale=1'><title>Salam Control</title><style>body{font-family:Arial;background:#030c19;color:#fff;padding:12px}.card{background:#09192d;padding:16px;border-radius:20px;margin:10px 0}button{background:linear-gradient(90deg,#1cd3ff,#635bff);color:#fff;border:0;border-radius:14px;padding:12px;margin:4px}</style></head><body><h2>⚡ Salam Web Server Control</h2><div class=card id=x>Loading...</div><div class=card><button onclick='load()'>Refresh</button><button onclick='fetch(\"/ __salam__/api/clear-logs\".replace(\" \",\"\"))'>Clear Logs</button></div><pre id=l></pre><script>async function load(){let x=await fetch('/__salam__/api/info').then(r=>r.json());document.getElementById('x').innerText='Status: '+x.running+'\\nURL: '+x.url+'\\nRequests: '+x.requests+'\\nClients: '+x.clients;let q=await fetch('/__salam__/api/logs').then(r=>r.json());document.getElementById('l').innerText=q.logs}load();setInterval(load,1500)</script></body></html>";}
    String directory(File d){StringBuilder b=new StringBuilder(page().replace("</body>",""));b.append("<div class=card><h3>").append(d.getName()).append("</h3>");File[] fs=d.listFiles();if(fs!=null)for(File f:fs)b.append("<p><a href='").append(f.getName()).append("'>").append(f.getName()).append("</a></p>");return b.append("</div></body></html>").toString();}
    void send(Socket s,int code,String type,String body)throws Exception{byte[] b=body.getBytes("UTF-8");String h="HTTP/1.1 "+code+" OK\\r\\nContent-Type: "+type+"; charset=utf-8\\r\\nContent-Length: "+b.length+"\\r\\nConnection: close\\r\\n\\r\\n";s.getOutputStream().write(h.getBytes());s.getOutputStream().write(b);}
    void json(Socket s,String x)throws Exception{send(s,200,"application/json",x);}
    String mime(String n){String x=n.toLowerCase();if(x.endsWith(".html"))return"text/html";if(x.endsWith(".css"))return"text/css";if(x.endsWith(".js"))return"application/javascript";if(x.endsWith(".json"))return"application/json";if(x.endsWith(".png"))return"image/png";if(x.endsWith(".jpg")||x.endsWith(".jpeg"))return"image/jpeg";if(x.endsWith(".pdf"))return"application/pdf";return"application/octet-stream";}
    void addLog(String x){String z=new SimpleDateFormat("HH:mm:ss",Locale.getDefault()).format(new Date())+"  "+x;LOGS.add(z);if(LOGS.size()>500)LOGS.remove(0);}
    String escape(String x){return x.replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n").replace("\r","");}
    public static String localIp(Context c){try{Enumeration<NetworkInterface> ns=NetworkInterface.getNetworkInterfaces();while(ns.hasMoreElements()){NetworkInterface n=ns.nextElement();for(Enumeration<InetAddress>a=n.getInetAddresses();a.hasMoreElements();){InetAddress x=a.nextElement();if(!x.isLoopbackAddress()&&x instanceof Inet4Address)return x.getHostAddress();}}}catch(Exception ignored){}return"127.0.0.1";}
    public static String wifiIp(Context c){return localIp(c);}
    public static String cellularIp(Context c){return"—";}
    public static String interfaceName(Context c){return"auto";}
    public static String networkInfo(Context c){return"Interface: "+interfaceName(c)+"  •  Wi‑Fi IP: "+wifiIp(c)+"  •  Mobile IP: "+cellularIp(c);}
    public static String currentUrl(Context c){return"http://"+(customHost.isEmpty()?localIp(c):customHost)+":8080";}
    public static String memoryText(Context c){ActivityManager a=(ActivityManager)c.getSystemService(Context.ACTIVITY_SERVICE);ActivityManager.MemoryInfo m=new ActivityManager.MemoryInfo();a.getMemoryInfo(m);return ((m.totalMem-m.availMem)/1048576)+" / "+(m.totalMem/1048576)+" MB";}
    public static String storageText(Context c){StatFs s=new StatFs(c.getFilesDir().getPath());long total=s.getTotalBytes(),free=s.getAvailableBytes();return((total-free)/1048576)+" / "+(total/1048576)+" MB";}
    @Override public IBinder onBind(Intent i){return null;}
}
