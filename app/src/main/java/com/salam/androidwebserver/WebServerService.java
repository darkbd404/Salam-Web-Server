package com.salam.androidwebserver;

import android.app.*;
import android.content.*;
import android.os.*;
import java.io.*;
import java.net.*;
import java.text.*;
import java.util.*;
import java.util.concurrent.*;
import android.content.SharedPreferences;

public class WebServerService extends Service {
    static volatile WebServerService instance;
    volatile boolean running=false;
    ServerSocket server;
    Thread acceptThread;
    File www;
    SharedPreferences sp;
    final List<String> logs=Collections.synchronizedList(new ArrayList<>());
    final Set<String> clients=ConcurrentHashMap.newKeySet();
    volatile long requests=0, startedAt=0;

    @Override public void onCreate(){super.onCreate();instance=this;sp=getSharedPreferences(MainActivity.PREF,MODE_PRIVATE);www=new File(getFilesDir(),"www");www.mkdirs();createChannel();}
    @Override public int onStartCommand(Intent i,int flags,int id){if(i!=null&&"STOP".equals(i.getAction()))stopServer();else if(i==null||"START".equals(i.getAction()))startServer();return START_STICKY;}
    void startServer(){if(running)return; int port=sp.getInt("port",8080); try{server=new ServerSocket(port);running=true;startedAt=System.currentTimeMillis();log("SERVER STARTED on "+currentUrl(this));startForeground(7,notification()); acceptThread=new Thread(()->acceptLoop(),"SalamWebServer");acceptThread.start();}catch(Exception e){log("START ERROR: "+e);}}
    void acceptLoop(){while(running){try{Socket s=server.accept();new Thread(()->handle(s)).start();}catch(Exception e){if(running)log("ACCEPT ERROR: "+e);}}}
    void handle(Socket s){String ip=s.getInetAddress().getHostAddress();clients.add(ip);requests++;String method="?",path="/",ua="-";int status=500;try{s.setSoTimeout(8000);BufferedReader r=new BufferedReader(new InputStreamReader(s.getInputStream(),"ISO-8859-1"));String first=r.readLine();if(first==null){s.close();return;}String[] p=first.split(" ");if(p.length>=2){method=p[0];path=p[1];}
        Map<String,String> h=new HashMap<>();String line;while((line=r.readLine())!=null&&!line.isEmpty()){int k=line.indexOf(':');if(k>0)h.put(line.substring(0,k).trim().toLowerCase(),line.substring(k+1).trim());}ua=h.getOrDefault("user-agent","-");
        if(!allowed(ip)){send(s,403,"text/plain","403 Forbidden - IP not allowed");status=403;return;}
        if(path.startsWith("/__admin")){if(!auth(h.get("authorization"))){send401(s);status=401;return;}send(s,200,"text/html",adminHtml());status=200;return;}
        if(sp.getBoolean("protect",false)&&!auth(h.get("authorization"))){send401(s);status=401;return;}
        if(!method.equals("GET")&&!method.equals("HEAD")){send(s,405,"text/plain","Method Not Allowed");status=405;return;}
        String clean=URLDecoder.decode(path.split("\\?",2)[0],"UTF-8");if(clean.equals("/"))clean="/index.html";
        File f=new File(www,clean.substring(1));String root=www.getCanonicalPath(), target=f.getCanonicalPath();if(!target.startsWith(root+File.separator)&&!target.equals(root)){send(s,403,"text/plain","Forbidden");status=403;return;}
        if(f.isDirectory()){File idx=new File(f,"index.html");if(idx.exists())f=idx;else{send(s,200,"text/html",directory(f));status=200;return;}}
        if(!f.exists()||!f.isFile()){send(s,404,"text/plain","404 Not Found");status=404;return;}
        byte[]data=readBytes(f);sendBytes(s,200,mime(f.getName()),data,!method.equals("HEAD"));status=200;
    }catch(Exception e){log("REQUEST ERROR "+ip+" "+e);try{send(s,500,"text/plain","500 Internal Server Error");}catch(Exception ignored){}}
    finally{log(ip+"  "+method+"  "+path+"  "+status+"  UA="+ua);try{s.close();}catch(Exception ignored){} clients.remove(ip);}}
    boolean allowed(String ip){Set<String> allow=sp.getStringSet("allowedIps",new HashSet<>());Set<String> block=sp.getStringSet("blockedIps",new HashSet<>());if(block.contains(ip))return false;return !sp.getBoolean("allowOnly",false)||allow.contains(ip);}
    boolean auth(String a){if(a==null||!a.startsWith("Basic "))return false;try{String x=new String(Base64.getDecoder().decode(a.substring(6)),"UTF-8");int k=x.indexOf(':');return k>=0&&x.substring(0,k).equals("admin")&&x.substring(k+1).equals(sp.getString("password",""));}catch(Exception e){return false;}}
    void send401(Socket s)throws Exception{OutputStream o=s.getOutputStream();String h="HTTP/1.1 401 Unauthorized\\r\\nWWW-Authenticate: Basic realm=\"Salam Web Server\"\\r\\nContent-Length: 0\\r\\nConnection: close\\r\\n\\r\\n";o.write(h.getBytes("ISO-8859-1"));o.flush();}
    void send(Socket s,int code,String type,String body)throws Exception{sendBytes(s,code,type,body.getBytes("UTF-8"),true);}
    void sendBytes(Socket s,int code,String type,byte[]data,boolean body)throws Exception{String msg=code==200?"OK":code==401?"Unauthorized":code==403?"Forbidden":code==404?"Not Found":code==405?"Method Not Allowed":"Internal Server Error";OutputStream o=s.getOutputStream();String h="HTTP/1.1 "+code+" "+msg+"\\r\\nContent-Type: "+type+"\\r\\nContent-Length: "+data.length+"\\r\\nConnection: close\\r\\n\\r\\n";o.write(h.getBytes("ISO-8859-1"));if(body)o.write(data);o.flush();}
    byte[] readBytes(File f)throws Exception{ByteArrayOutputStream b=new ByteArrayOutputStream();try(InputStream in=new FileInputStream(f)){byte[]x=new byte[16384];int n;while((n=in.read(x))>0)b.write(x,0,n);}return b.toByteArray();}
    String mime(String n){n=n.toLowerCase();if(n.endsWith(".html")||n.endsWith(".htm"))return"text/html";if(n.endsWith(".css"))return"text/css";if(n.endsWith(".js"))return"application/javascript";if(n.endsWith(".json"))return"application/json";if(n.endsWith(".png"))return"image/png";if(n.endsWith(".jpg")||n.endsWith(".jpeg"))return"image/jpeg";if(n.endsWith(".gif"))return"image/gif";if(n.endsWith(".svg"))return"image/svg+xml";if(n.endsWith(".pdf"))return"application/pdf";if(n.endsWith(".txt"))return"text/plain";return"application/octet-stream";}
    String directory(File d){StringBuilder x=new StringBuilder("<!doctype html><meta name='viewport' content='width=device-width'><style>body{font-family:system-ui;background:#0b0f14;color:#fff;padding:20px}a{display:block;padding:12px;color:#7df0a8}</style><h2>Salam Web Server</h2>");File[]fs=d.listFiles();if(fs!=null)for(File f:fs)x.append("<a href='").append(URLEncoder.encode(f.getName(),"UTF-8")).append("'>").append(esc(f.getName())).append(f.isDirectory()?"/":"").append("</a>");return x+"</html>";}
    String adminHtml(){StringBuilder x=new StringBuilder("<!doctype html><meta name='viewport' content='width=device-width'><style>body{font-family:system-ui;background:#0b0f14;color:#fff;padding:16px}section{background:#151b22;border-radius:16px;padding:16px;margin:10px 0}pre{white-space:pre-wrap;word-break:break-word;font:12px monospace}</style><h1>Salam Web Server</h1><section><b>Status:</b> RUNNING<br><b>URL:</b> "+esc(currentUrl(this))+"<br><b>Requests:</b> "+requests+"<br><b>Active clients:</b> "+clients.size()+"</section><section><h2>Access</h2><p>Allow-list: "+sp.getBoolean("allowOnly",false)+"</p><p>Allowed IPs: "+esc(sp.getStringSet("allowedIps",new HashSet<>()).toString())+"</p><p>Blocked IPs: "+esc(sp.getStringSet("blockedIps",new HashSet<>()).toString())+"</p></section><section><h2>Live request logs</h2><pre>");synchronized(logs){for(String l:logs)x.append(esc(l)).append("\\n");}return x+"</pre></section>");}
    static String esc(String s){return s==null?"":s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;");}
    void log(String s){String t=new SimpleDateFormat("yyyy-MM-dd HH:mm:ss",Locale.US).format(new Date())+"  "+s;logs.add(t);while(logs.size()>500)logs.remove(0);}
    Notification notification(){Intent i=new Intent(this,MainActivity.class);PendingIntent p=PendingIntent.getActivity(this,0,i,PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);return new Notification.Builder(this,"server").setContentTitle("Salam Web Server Pro").setContentText("Server running • "+currentUrl(this)).setSmallIcon(com.salam.androidwebserver.R.drawable.ic_server).setContentIntent(p).setOngoing(true).build();}
    void createChannel(){if(Build.VERSION.SDK_INT>=26)getSystemService(NotificationManager.class).createNotificationChannel(new NotificationChannel("server","Web Server",NotificationManager.IMPORTANCE_LOW));}
    void stopServer(){running=false;try{if(server!=null)server.close();}catch(Exception ignored){}log("SERVER STOPPED");stopForeground(true);stopSelf();}
    static String currentUrl(Context c){try{int p=c.getSharedPreferences(MainActivity.PREF,0).getInt("port",8080);Enumeration<NetworkInterface> es=NetworkInterface.getNetworkInterfaces();while(es.hasMoreElements()){NetworkInterface n=es.nextElement();Enumeration<InetAddress>a=n.getInetAddresses();while(a.hasMoreElements()){InetAddress x=a.nextElement();if(!x.isLoopbackAddress()&&x instanceof Inet4Address)return "http://"+x.getHostAddress()+":"+p;}}}catch(Exception ignored){}return null;}
    static void reloadConfig(){}
    static void clearLogs(){if(instance!=null)instance.logs.clear();}
    @Override public IBinder onBind(Intent i){return null;}
    @Override public void onDestroy(){stopServer();instance=null;super.onDestroy();}
}
