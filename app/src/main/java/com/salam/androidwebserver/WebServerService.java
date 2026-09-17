package com.salam.androidwebserver;

import android.app.*;
import android.content.*;
import android.os.IBinder;
import androidx.annotation.Nullable;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

public class WebServerService extends Service {
    public static final String ACTION_STATUS="com.salam.androidwebserver.STATUS";
    public static final String EXTRA_RUNNING="running", EXTRA_URL="url", EXTRA_REQUESTS="requests", EXTRA_CLIENTS="clients", EXTRA_LOG="log";
    private static final String CHANNEL="web_server"; private static final int NOTIFY_ID=4040;
    private ServerSocket serverSocket; private ExecutorService pool; private volatile boolean running=false;
    private volatile long requests=0; private final Set<String> clients=ConcurrentHashMap.newKeySet();
    private File www; private int port=8080; private String password="";

    @Override public void onCreate(){ super.onCreate(); www=new File(getFilesDir(),"www"); if(!www.exists())www.mkdirs(); createStarterSite(); createChannel(); }
    @Override public int onStartCommand(Intent intent,int flags,int startId){ if(intent!=null&&"STOP".equals(intent.getAction())){stopServer();stopSelf();return START_NOT_STICKY;} if(!running)startServer(); return START_STICKY; }
    private void createChannel(){ NotificationManager nm=getSystemService(NotificationManager.class); if(nm!=null)nm.createNotificationChannel(new NotificationChannel(CHANNEL,"Web Server",NotificationManager.IMPORTANCE_LOW)); }
    private Notification notification(String text){ Intent i=new Intent(this,MainActivity.class); PendingIntent pi=PendingIntent.getActivity(this,0,i,PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT); return new Notification.Builder(this,CHANNEL).setContentTitle("Salam Web Server").setContentText(text).setSmallIcon(android.R.drawable.stat_sys_upload).setContentIntent(pi).setOngoing(true).build(); }
    private void startServer(){
        SharedPreferences p=getSharedPreferences("server",MODE_PRIVATE); port=p.getInt("port",8080); password=p.getBoolean("auth",false)?p.getString("password",""):"";
        try{ serverSocket=new ServerSocket(port); pool=Executors.newCachedThreadPool(); running=true; startForeground(NOTIFY_ID,notification("Running on port "+port)); broadcast("Server started",true);
            pool.execute(()->{while(running){try{Socket s=serverSocket.accept();pool.execute(()->handle(s));}catch(IOException e){if(running)broadcast("Accept error: "+e.getMessage(),true);}}});
        }catch(Exception e){running=false;broadcast("Server failed: "+e.getMessage(),false);stopSelf();}
    }
    private void stopServer(){running=false;try{if(serverSocket!=null)serverSocket.close();}catch(Exception ignored){} if(pool!=null)pool.shutdownNow();clients.clear();broadcast("Server stopped",false);stopForeground(STOP_FOREGROUND_REMOVE);}
    private void handle(Socket socket){ String client=socket.getInetAddress().getHostAddress(); clients.add(client); requests++; try{
        socket.setSoTimeout(8000); BufferedReader r=new BufferedReader(new InputStreamReader(socket.getInputStream(),StandardCharsets.ISO_8859_1)); OutputStream out=socket.getOutputStream();
        String first=r.readLine(); if(first==null)return; String[] parts=first.split(" "); if(parts.length<2)return; String method=parts[0]; String target=URLDecoder.decode(parts[1],StandardCharsets.UTF_8.name()); String line,auth="";
        while((line=r.readLine())!=null&&!line.isEmpty())if(line.toLowerCase(Locale.US).startsWith("authorization:"))auth=line.substring(14).trim();
        if(!"GET".equalsIgnoreCase(method)&&!"HEAD".equalsIgnoreCase(method)){send(out,405,"Method Not Allowed","text/plain","Only GET and HEAD are supported.");return;}
        if(!password.isEmpty()&&!authorized(auth)){out.write(("HTTP/1.1 401 Unauthorized\r\nWWW-Authenticate: Basic realm=\"Salam Web Server\"\r\nContent-Length: 0\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.ISO_8859_1));return;}
        int q=target.indexOf('?'); if(q>=0)target=target.substring(0,q); if(target.isEmpty()||"/".equals(target))target="/index.html";
        if(target.contains("..")||target.contains("\\")||target.startsWith("//")){send(out,403,"Forbidden","text/plain","Forbidden");return;}
        File base=www.getCanonicalFile(); File f=new File(base,target.substring(1)).getCanonicalFile(); if(!f.toPath().startsWith(base.toPath())||!f.isFile()){send(out,404,"Not Found","text/plain","404 - File Not Found");return;}
        String mime=mime(f.getName()); long len=f.length(); out.write(("HTTP/1.1 200 OK\r\nContent-Type: "+mime+"\r\nContent-Length: "+len+"\r\nCache-Control: no-cache\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.ISO_8859_1));
        if(!"HEAD".equalsIgnoreCase(method)){try(InputStream in=new FileInputStream(f)){byte[] buf=new byte[16384];int n;while((n=in.read(buf))!=-1)out.write(buf,0,n);}} out.flush(); broadcast("GET "+target+" ["+client+"]",true);
    }catch(Exception e){broadcast("Client error: "+e.getMessage(),true);}finally{clients.remove(client);try{socket.close();}catch(Exception ignored){}updateNotification();}}
    private boolean authorized(String header){try{if(!header.regionMatches(true,0,"Basic ",0,6))return false;String d=new String(Base64.getDecoder().decode(header.substring(6).trim()),StandardCharsets.UTF_8);return d.equals("admin:"+password);}catch(Exception e){return false;}}
    private void send(OutputStream out,int code,String msg,String mime,String body)throws IOException{byte[] b=body.getBytes(StandardCharsets.UTF_8);out.write(("HTTP/1.1 "+code+" "+msg+"\r\nContent-Type: "+mime+"; charset=UTF-8\r\nContent-Length: "+b.length+"\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.UTF_8));out.write(b);}
    private String mime(String n){String s=n.toLowerCase(Locale.US);if(s.endsWith(".html")||s.endsWith(".htm"))return"text/html";if(s.endsWith(".css"))return"text/css";if(s.endsWith(".js"))return"application/javascript";if(s.endsWith(".json"))return"application/json";if(s.endsWith(".xml"))return"application/xml";if(s.endsWith(".txt"))return"text/plain";if(s.endsWith(".pdf"))return"application/pdf";if(s.endsWith(".png"))return"image/png";if(s.endsWith(".jpg")||s.endsWith(".jpeg"))return"image/jpeg";if(s.endsWith(".gif"))return"image/gif";if(s.endsWith(".webp"))return"image/webp";if(s.endsWith(".svg"))return"image/svg+xml";if(s.endsWith(".mp3"))return"audio/mpeg";if(s.endsWith(".mp4"))return"video/mp4";if(s.endsWith(".zip"))return"application/zip";return"application/octet-stream";}
    private void broadcast(String log,boolean isRunning){Intent i=new Intent(ACTION_STATUS);i.setPackage(getPackageName());i.putExtra(EXTRA_RUNNING,isRunning);i.putExtra(EXTRA_URL,"http://"+localIp()+":"+port);i.putExtra(EXTRA_REQUESTS,requests);i.putExtra(EXTRA_CLIENTS,clients.size());i.putExtra(EXTRA_LOG,log);sendBroadcast(i);}
    private void updateNotification(){if(running){NotificationManager nm=getSystemService(NotificationManager.class);if(nm!=null)nm.notify(NOTIFY_ID,notification("Running • "+requests+" requests • "+clients.size()+" clients"));}}
    private String localIp(){try{Enumeration<NetworkInterface> en=NetworkInterface.getNetworkInterfaces();while(en.hasMoreElements()){NetworkInterface ni=en.nextElement();Enumeration<InetAddress>a=ni.getInetAddresses();while(a.hasMoreElements()){InetAddress x=a.nextElement();if(!x.isLoopbackAddress()&&x instanceof Inet4Address)return x.getHostAddress();}}}catch(Exception ignored){}return"127.0.0.1";}
    private void createStarterSite(){File f=new File(www,"index.html");if(f.exists())return;String h="<!doctype html><html><head><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"><title>Salam Web Server</title><style>body{font-family:Arial;background:#101114;color:#fff;text-align:center;padding:40px}.box{max-width:600px;margin:auto;padding:30px;border-radius:24px;background:#1a1c21}</style></head><body><div class=\"box\"><h1>Salam Web Server</h1><p>Your Android web server is working.</p><p>Import your website files into the app.</p></div></body></html>";try(FileOutputStream o=new FileOutputStream(f)){o.write(h.getBytes(StandardCharsets.UTF_8));}catch(Exception ignored){}}
    @Override public void onDestroy(){stopServer();super.onDestroy();}
    @Nullable @Override public IBinder onBind(Intent intent){return null;}
}
