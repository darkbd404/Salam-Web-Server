package com.salam.androidwebserver;

import android.app.Activity;
import android.os.Bundle;
import android.os.StrictMode;
import android.content.Intent;
import android.net.Uri;
import android.widget.*;
import java.io.*;
import java.net.*;
import java.util.concurrent.*;

public class MainActivity extends Activity {
    EditText port; TextView status,url; HttpServer server;
    File www;
    @Override public void onCreate(Bundle b){
        super.onCreate(b); setContentView(R.layout.activity_main);
        port=findViewById(R.id.port); status=findViewById(R.id.status); url=findViewById(R.id.url);
        www=new File(getFilesDir(),"www"); if(!www.exists()) www.mkdirs();
        File index=new File(www,"index.html");
        if(!index.exists()) try { FileWriter w=new FileWriter(index); w.write("<!doctype html><html><head><meta name='viewport' content='width=device-width'><title>Salam Server</title></head><body><h1>Salam Web Server</h1><p>Your Android server is running.</p></body></html>"); w.close(); } catch(Exception ignored){}
        findViewById(R.id.start).setOnClickListener(v->startServer());
        findViewById(R.id.stop).setOnClickListener(v->stopServer());
        findViewById(R.id.open).setOnClickListener(v->{ if(server!=null){ Intent i=new Intent(Intent.ACTION_VIEW,Uri.parse("http://127.0.0.1:"+server.port)); startActivity(i);} });
    }
    void startServer(){
        stopServer();
        try { int p=Integer.parseInt(port.getText().toString().trim()); server=new HttpServer(p,www); server.start();
            status.setText("● Server running"); url.setText("URL: http://"+getIp()+":"+p);
        } catch(Exception e){ Toast.makeText(this,"Could not start: "+e.getMessage(),Toast.LENGTH_LONG).show(); }
    }
    void stopServer(){ if(server!=null){server.stop(); server=null; status.setText("● Server stopped"); url.setText("URL: -");} }
    String getIp(){ try { Enumeration<NetworkInterface> en=NetworkInterface.getNetworkInterfaces(); while(en.hasMoreElements()){ NetworkInterface ni=en.nextElement(); Enumeration<InetAddress> a=ni.getInetAddresses(); while(a.hasMoreElements()){ InetAddress x=a.nextElement(); if(!x.isLoopbackAddress() && x instanceof Inet4Address) return x.getHostAddress(); }} }catch(Exception ignored){} return "127.0.0.1"; }
    @Override protected void onDestroy(){ stopServer(); super.onDestroy(); }

    static class HttpServer {
        ServerSocket ss; volatile boolean run; int port; File root; ExecutorService pool=Executors.newCachedThreadPool();
        HttpServer(int p,File r){port=p;root=r;}
        void start() throws IOException { ss=new ServerSocket(port); run=true; pool.submit(()->{while(run) try{Socket s=ss.accept();pool.submit(()->handle(s));}catch(Exception ignored){}}); }
        void stop(){run=false;try{if(ss!=null)ss.close();}catch(Exception ignored){} pool.shutdownNow();}
        void handle(Socket s){
            try{
                s.setSoTimeout(5000); BufferedReader br=new BufferedReader(new InputStreamReader(s.getInputStream()));
                String line=br.readLine(); if(line==null){s.close();return;}
                String[] q=line.split(" "); String path=q.length>1?q[1]:"/";
                path=URLDecoder.decode(path,"UTF-8"); if(path.contains("..")){send(s,403,"Forbidden","text/plain");return;}
                File f=new File(root,path.equals("/")?"index.html":path.substring(1));
                if(f.isDirectory()) f=new File(f,"index.html");
                if(!f.exists()||!f.isFile()){send(s,404,"Not Found","text/plain");return;}
                byte[] data=read(f); String type=mime(f.getName());
                OutputStream out=s.getOutputStream(); String h="HTTP/1.1 200 OK\r\nContent-Type: "+type+"\r\nContent-Length: "+data.length+"\r\nConnection: close\r\n\r\n"; out.write(h.getBytes());out.write(data);out.flush();s.close();
            }catch(Exception ignored){try{s.close();}catch(Exception x){}}
        }
        byte[] read(File f)throws Exception{ByteArrayOutputStream b=new ByteArrayOutputStream();InputStream in=new FileInputStream(f);byte[] x=new byte[8192];int n;while((n=in.read(x))!=-1)b.write(x,0,n);in.close();return b.toByteArray();}
        void send(Socket s,int code,String text,String type)throws Exception{byte[] d=text.getBytes();String h="HTTP/1.1 "+code+" "+text+"\r\nContent-Type: "+type+"\r\nContent-Length: "+d.length+"\r\nConnection: close\r\n\r\n";s.getOutputStream().write(h.getBytes());s.getOutputStream().write(d);s.close();}
        String mime(String n){n=n.toLowerCase();if(n.endsWith(".html"))return"text/html; charset=utf-8";if(n.endsWith(".css"))return"text/css";if(n.endsWith(".js"))return"application/javascript";if(n.endsWith(".json"))return"application/json";if(n.endsWith(".png"))return"image/png";if(n.endsWith(".jpg")||n.endsWith(".jpeg"))return"image/jpeg";if(n.endsWith(".svg"))return"image/svg+xml";if(n.endsWith(".pdf"))return"application/pdf";return"application/octet-stream";}
    }
}
