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

public class WebServerService extends Service {
    public static volatile boolean running=false;
    public static volatile long requests=0;
    public static final Set<String> clients=ConcurrentHashMap.newKeySet();
    static final List<String> LOGS=Collections.synchronizedList(new ArrayList<String>());
    static ServerSocket socket; static long startedAt;

    SharedPreferences pref(){return getSharedPreferences("server",MODE_PRIVATE);}
    public static File webRoot(Context c){File f=new File(c.getFilesDir(),"www");if(!f.exists())f.mkdirs();File i=new File(f,"index.html");if(!i.exists())try(FileWriter w=new FileWriter(i)){w.write("<!doctype html><html><head><meta name='viewport' content='width=device-width'><title>Salam Web Server</title></head><body style='font-family:sans-serif;background:#06101F;color:white;text-align:center;padding:50px'><h1>Salam Web Server</h1><p>Your local server is online.</p></body></html>");}catch(Exception ignored){}return f;}

    @Override public void onCreate(){super.onCreate();if(Build.VERSION.SDK_INT>=26){NotificationChannel c=new NotificationChannel("salam_server","Salam Web Server",NotificationManager.IMPORTANCE_LOW);((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(c);}}
    @Override public int onStartCommand(Intent i,int flags,int id){if(i!=null&&"STOP".equals(i.getAction())){stopServer();stopSelf();return START_NOT_STICKY;}startServer();return START_STICKY;}

    void startServer(){synchronized(WebServerService.class){if(running)return;int port=pref().getInt("port",8080);if(port<1024||port>65535)port=8080;try{socket=new ServerSocket();socket.setReuseAddress(true);socket.bind(new InetSocketAddress(port));running=true;requests=0;clients.clear();startedAt=System.currentTimeMillis();log("Server started successfully on port "+port);startForeground(77,notification("Running on port "+port));new Thread(this::acceptLoop,"Salam-Accept").start();}catch(Exception e){running=false;log("START ERROR: "+e.getMessage());}}}
    void acceptLoop(){while(running)try{Socket s=socket.accept();int max=Math.max(1,pref().getInt("maxClients",32));if(clients.size()>=max){s.close();log("Client limit reached: "+s.getInetAddress());continue;}new Thread(()->handle(s),"Salam-Client").start();}catch(Exception e){if(running)log("Accept error: "+e.getMessage());}}
    void handle(Socket s){String ip=s.getInetAddress().getHostAddress();clients.add(ip);try{s.setSoTimeout(15000);BufferedReader r=new BufferedReader(new InputStreamReader(s.getInputStream(),StandardCharsets.ISO_8859_1));String first=r.readLine();if(first==null)return;String[] p=first.split(" ",3);if(p.length<2)return;String method=p[0];String path=URLDecoder.decode(p[1].split("\\?",2)[0],"UTF-8");Map<String,String> h=new HashMap<>();String line;while((line=r.readLine())!=null&&!line.isEmpty()){int x=line.indexOf(':');if(x>0)h.put(line.substring(0,x).trim().toLowerCase(Locale.US),line.substring(x+1).trim());}requests++;if(!allowed(ip)){respond(s,403,"text/plain","IP blocked");return;}if(!authorized(h)){auth(s);return;}File f=safe(path);if(f==null){respond(s,403,"text/plain","Forbidden");return;}if(!f.exists()){respond(s,404,"text/plain","Not Found");return;}if(f.isDirectory()){File idx=new File(f,"index.html");if(idx.exists())f=idx;else{respond(s,200,"text/html",directory(f,path));return;}}sendFile(s,f,"HEAD".equals(method));log(ip+" "+method+" "+path+" 200");}catch(Exception e){log("Client error "+ip+": "+e.getMessage());}finally{clients.remove(ip);try{s.close();}catch(Exception ignored){}}}
    boolean allowed(String ip){if("127.0.0.1".equals(ip)||"::1".equals(ip))return true;for(String x:pref().getString("blockIps","").split(","))if(ip.equals(x.trim()))return false;if(!pref().getBoolean("allowOnly",false))return true;for(String x:pref().getString("allowIps","").split(","))if(ip.equals(x.trim()))return true;return false;}
    boolean authorized(Map<String,String> h){String pass=pref().getString("password","");if(pass.isEmpty())return true;String a=h.get("authorization");if(a==null||!a.startsWith("Basic "))return false;try{String z=new String(android.util.Base64.decode(a.substring(6),android.util.Base64.DEFAULT),StandardCharsets.UTF_8);int x=z.indexOf(':');return x>0&&"admin".equals(z.substring(0,x))&&pass.equals(z.substring(x+1));}catch(Exception e){return false;}}
    void auth(Socket s)throws IOException{String h="HTTP/1.1 401 Unauthorized\\r\\nWWW-Authenticate: Basic realm=\"Salam Web Server\"\\r\\nContent-Length: 0\\r\\nConnection: close\\r\\n\\r\\n";s.getOutputStream().write(h.getBytes(StandardCharsets.ISO_8859_1));}
    File safe(String path){try{File base=webRoot(this);File f=new File(base,path);String b=base.getCanonicalPath(),x=f.getCanonicalPath();return x.equals(b)||x.startsWith(b+File.separator)?f:null;}catch(Exception e){return null;}}
    void respond(Socket s,int code,String type,String body)throws IOException{byte[] b=body.getBytes(StandardCharsets.UTF_8);String h="HTTP/1.1 "+code+" "+reason(code)+"\\r\\nContent-Type: "+type+"; charset=utf-8\\r\\nContent-Length: "+b.length+"\\r\\nConnection: close\\r\\n\\r\\n";OutputStream o=s.getOutputStream();o.write(h.getBytes(StandardCharsets.ISO_8859_1));o.write(b);o.flush();}
    void sendFile(Socket s,File f,boolean head)throws IOException{String h="HTTP/1.1 200 OK\\r\\nContent-Type: "+mime(f.getName())+"\\r\\nContent-Length: "+f.length()+"\\r\\nConnection: close\\r\\n\\r\\n";OutputStream o=s.getOutputStream();o.write(h.getBytes(StandardCharsets.ISO_8859_1));if(!head)try(InputStream in=new FileInputStream(f)){byte[] b=new byte[16384];int n;while((n=in.read(b))>0)o.write(b,0,n);}o.flush();}
    String directory(File d,String path){StringBuilder h=new StringBuilder("<html><meta name=viewport content=width=device-width><body style='font-family:sans-serif;background:#06101F;color:white;padding:20px'><h2>Salam Web Server</h2>");File[] fs=d.listFiles();if(fs!=null)for(File f:fs){String p=(path.endsWith("/")?path:path+"/")+f.getName();h.append("<p><a style='color:#20D5FF' href='").append(p).append("'>").append(f.isDirectory()?"📁 ":"📄 ").append(f.getName()).append("</a></p>");}return h.append("</body></html>").toString();}
    static String currentUrl(Context c){return "http://"+localIp(c)+":"+c.getSharedPreferences("server",MODE_PRIVATE).getInt("port",8080);}
    static String localIp(Context c){try{ConnectivityManager m=(ConnectivityManager)c.getSystemService(CONNECTIVITY_SERVICE);for(Network n:m.getAllNetworks()){LinkProperties p=m.getLinkProperties(n);if(p!=null)for(LinkAddress a:p.getLinkAddresses()){String x=a.getAddress().getHostAddress();if(x!=null&&!x.contains(":")&&!x.startsWith("127."))return x;}}}catch(Exception ignored){}return "0.0.0.0";}
    static String networkInfo(Context c){StringBuilder s=new StringBuilder();try{ConnectivityManager m=(ConnectivityManager)c.getSystemService(CONNECTIVITY_SERVICE);for(Network n:m.getAllNetworks()){LinkProperties p=m.getLinkProperties(n);if(p!=null)s.append("Interface: ").append(p.getInterfaceName()).append("\\nAddresses: ").append(p.getLinkAddresses()).append("\\n\\n");}}catch(Exception e){s.append(e.getMessage());}return s.length()==0?"No network information":s.toString();}
    static String memoryText(Context c){ActivityManager a=(ActivityManager)c.getSystemService(ACTIVITY_SERVICE);ActivityManager.MemoryInfo m=new ActivityManager.MemoryInfo();a.getMemoryInfo(m);return fmt(m.totalMem-m.availMem)+" / "+fmt(m.totalMem);}
    static String uptime(){if(!running)return"00:00:00";long s=(System.currentTimeMillis()-startedAt)/1000;return String.format(Locale.US,"%02d:%02d:%02d",s/3600,(s%3600)/60,s%60);}
    static String logsText(){StringBuilder s=new StringBuilder();synchronized(LOGS){for(String x:LOGS)s.append(x).append('\n');}return s.toString();}
    static void clearLogs(){LOGS.clear();}
    void log(String x){synchronized(LOGS){LOGS.add(new SimpleDateFormat("HH:mm:ss",Locale.US).format(new Date())+"  "+x);while(LOGS.size()>500)LOGS.remove(0);}}
    String mime(String n){String x=n.toLowerCase(Locale.US);if(x.endsWith(".html")||x.endsWith(".htm"))return"text/html";if(x.endsWith(".css"))return"text/css";if(x.endsWith(".js"))return"application/javascript";if(x.endsWith(".json"))return"application/json";if(x.endsWith(".png"))return"image/png";if(x.endsWith(".jpg")||x.endsWith(".jpeg"))return"image/jpeg";if(x.endsWith(".svg"))return"image/svg+xml";return"application/octet-stream";}
    String reason(int c){return c==200?"OK":c==401?"Unauthorized":c==403?"Forbidden":c==404?"Not Found":"Error";}
    static String fmt(long b){if(b<1024)return b+" B";if(b<1048576)return(b/1024)+" KB";if(b<1073741824L)return(b/1048576)+" MB";return String.format(Locale.US,"%.1f GB",b/1073741824.0);}
    Notification notification(String s){Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(this,"salam_server"):new Notification.Builder(this);return b.setContentTitle("Salam Web Server").setContentText(s).setSmallIcon(android.R.drawable.ic_menu_view).setOngoing(true).build();}
    void stopServer(){synchronized(WebServerService.class){running=false;try{if(socket!=null)socket.close();}catch(Exception ignored){}socket=null;clients.clear();log("Server stopped");try{stopForeground(true);}catch(Exception ignored){}}}
    @Override public void onDestroy(){stopServer();super.onDestroy();}
    @Override public IBinder onBind(Intent i){return null;}
}
