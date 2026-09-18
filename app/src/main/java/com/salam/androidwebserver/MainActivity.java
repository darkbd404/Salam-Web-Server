package com.salam.androidwebserver;

import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.net.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class MainActivity extends Activity {
    static final int BG=Color.rgb(3,12,25), CARD=Color.rgb(9,25,45), CYAN=Color.rgb(28,211,255);
    static final int GREEN=Color.rgb(42,230,130), RED=Color.rgb(255,70,90), TEXT=Color.WHITE, MUTED=Color.rgb(150,174,198);
    LinearLayout root, body; TextView title, status, url, ram, storage, clients, requests, network;
    Handler h=new Handler(Looper.getMainLooper()); boolean live=true;
    int theme=0;

    @Override public void onCreate(Bundle b){super.onCreate(b); getWindow().setStatusBarColor(BG); getWindow().setNavigationBarColor(BG); build("Home"); startLive();}
    GradientDrawable gd(int c,float r){GradientDrawable g=new GradientDrawable();g.setColor(c);g.setCornerRadius(r);return g;}
    GradientDrawable grad(int a,int b,float r){GradientDrawable g=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{a,b});g.setCornerRadius(r);return g;}
    TextView tv(String s,float z,int c){TextView v=new TextView(this);v.setText(s);v.setTextSize(z);v.setTextColor(c);v.setGravity(Gravity.CENTER_VERTICAL);v.setPadding(16,10,16,10);return v;}
    TextView btn(String s){TextView v=tv(s,14,Color.WHITE);v.setGravity(Gravity.CENTER);v.setTypeface(null,1);v.setBackground(grad(CYAN,Color.rgb(80,70,255),22));v.setPadding(12,4,12,4);return v;}
    LinearLayout card(){LinearLayout x=new LinearLayout(this);x.setOrientation(LinearLayout.VERTICAL);x.setPadding(14,14,14,14);x.setBackground(gd(CARD,24));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,7,0,7);x.setLayoutParams(p);return x;}
    void build(String page){
        root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(BG);
        LinearLayout head=new LinearLayout(this);head.setGravity(Gravity.CENTER_VERTICAL);head.setPadding(8,10,8,6);
        title=tv("⚡  Salam Web Server",20,TEXT); title.setTypeface(null,1); head.addView(title,new LinearLayout.LayoutParams(0,60,1));
        TextView menu=tv("⋮",30,CYAN);head.addView(menu,new LinearLayout.LayoutParams(55,60));menu.setOnClickListener(v->popup(menu));
        root.addView(head);
        ScrollView sc=new ScrollView(this);body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(12,4,12,90);sc.addView(body);root.addView(sc,new LinearLayout.LayoutParams(-1,0,1));
        nav(); setContentView(root);
        if(page.equals("Home"))home(); else if(page.equals("Files"))files(); else if(page.equals("Logs"))logs(); else settings();
    }
    void nav(){
        LinearLayout n=new LinearLayout(this);n.setPadding(7,7,7,7);n.setBackground(gd(Color.rgb(5,18,34),24));
        String[] a={"⌂\nHome","▣\nFiles","≡\nLogs","⚙\nSettings"};
        for(String s:a){TextView v=tv(s,12,s.contains(titleText())?CYAN:MUTED);v.setGravity(Gravity.CENTER);n.addView(v,new LinearLayout.LayoutParams(0,62,1));
            final String p=s.contains("Home")?"Home":s.contains("Files")?"Files":s.contains("Logs")?"Logs":"Settings";v.setOnClickListener(x->build(p));}
        root.addView(n);
    }
    String titleText(){return "Home";}
    void home(){
        LinearLayout c=card(); TextView led=tv("●  ●  ●  ●  ●  ●  ●",25,RED);led.setGravity(Gravity.CENTER);c.addView(led);
        status=tv("SERVER OFFLINE",18,RED);status.setGravity(Gravity.CENTER);c.addView(status);
        url=tv("http://"+WebServerService.localIp(this)+":8080",14,CYAN);url.setGravity(Gravity.CENTER);c.addView(url);
        TextView toggle=btn("▶  START SERVER");c.addView(toggle,new LinearLayout.LayoutParams(-1,54));
        toggle.setOnClickListener(v->{if(WebServerService.running){stop();}else start();});
        body.addView(c);
        LinearLayout grid=new LinearLayout(this);grid.setOrientation(LinearLayout.HORIZONTAL);
        String[] x={"📁\nWeb Files","🌐\nNetwork","📜\nServer Logs","🔐\nSecurity"};
        for(int i=0;i<4;i++){TextView q=tv(x[i],14,TEXT);q.setGravity(Gravity.CENTER);q.setBackground(gd(CARD,20));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,92,1);p.setMargins(5,5,5,5);grid.addView(q,p);
            int k=i;q.setOnClickListener(v->{if(k==0)build("Files");else if(k==1)networkPage();else if(k==2)build("Logs");else security();});}
        body.addView(grid);
        LinearLayout stats=card();stats.addView(tv("📊  LIVE MONITORING",15,CYAN));ram=tv("",14,TEXT);storage=tv("",14,TEXT);clients=tv("",14,TEXT);requests=tv("",14,TEXT);
        stats.addView(ram);stats.addView(storage);stats.addView(clients);stats.addView(requests);body.addView(stats);
        network=tv("",13,MUTED);body.addView(network);
    }
    void start(){startService(new Intent(this,WebServerService.class).setAction("START")); toast("Server started");}
    void stop(){startService(new Intent(this,WebServerService.class).setAction("STOP")); toast("Server stopped");}
    void startLive(){h.postDelayed(new Runnable(){public void run(){refresh();h.postDelayed(this,1000);}},500);}
    void refresh(){
        if(status==null)return;
        boolean on=WebServerService.running; status.setText(on?"SERVER ONLINE":"SERVER OFFLINE");status.setTextColor(on?GREEN:RED);
        url.setText(WebServerService.currentUrl(this));ram.setText("🧠 RAM: "+WebServerService.memoryText(this));
        storage.setText("💾 Storage: "+WebServerService.storageText(this));clients.setText("👥 Clients: "+WebServerService.clients.size());
        requests.setText("📈 Requests: "+WebServerService.requests.get());network.setText("🌐 "+WebServerService.networkInfo(this));
    }
    void files(){
        TextView p=tv("📁  Web Files",20,TEXT);p.setTypeface(null,1);body.addView(p);
        TextView add=btn("＋ NEW FOLDER");body.addView(add,new LinearLayout.LayoutParams(-1,52));add.setOnClickListener(v->mkdir());
        File dir=getFilesDir(); listDir(dir);
    }
    void listDir(File dir){
        for(File f:Objects.requireNonNull(dir.listFiles())){
            LinearLayout c=card(); TextView n=tv((f.isDirectory()?"📂 ":"📄 ")+f.getName(),15,TEXT);c.addView(n);
            LinearLayout r=new LinearLayout(this);TextView edit=btn("OPEN");TextView del=btn("DELETE");r.addView(edit,new LinearLayout.LayoutParams(0,48,1));r.addView(del,new LinearLayout.LayoutParams(0,48,1));c.addView(r);
            edit.setOnClickListener(v->{if(f.isDirectory()){body.removeAllViews();listDir(f);}else editor(f);});del.setOnClickListener(v->{f.delete();build("Files");});body.addView(c);
        }
    }
    void mkdir(){final EditText e=new EditText(this);e.setHint("Folder name");new AlertDialog.Builder(this).setTitle("New folder").setView(e).setPositiveButton("Create",(d,w)->{new File(getFilesDir(),e.getText().toString()).mkdirs();build("Files");}).setNegativeButton("Cancel",null).show();}
    void editor(File f){final EditText e=new EditText(this);e.setText(read(f));e.setGravity(Gravity.TOP);e.setMinLines(12);new AlertDialog.Builder(this).setTitle("Edit: "+f.getName()).setView(e).setPositiveButton("Save",(d,w)->{write(f,e.getText().toString());}).setNegativeButton("Close",null).show();}
    String read(File f){try{return new String(java.nio.file.Files.readAllBytes(f.toPath()));}catch(Exception e){return "";}}
    void write(File f,String s){try{FileOutputStream o=new FileOutputStream(f);o.write(s.getBytes());o.close();}catch(Exception e){toast(e.getMessage());}}
    void logs(){body.addView(tv("📜  Live Server Logs",20,TEXT));TextView clear=btn("CLEAR LOGS");body.addView(clear,new LinearLayout.LayoutParams(-1,50));clear.setOnClickListener(v->WebServerService.LOGS.clear());TextView l=tv("",12,MUTED);body.addView(l);h.postDelayed(new Runnable(){public void run(){if(l!=null){StringBuilder s=new StringBuilder();for(String x:WebServerService.LOGS)s.append(x).append("\n");l.setText(s.toString());}h.postDelayed(this,1000);}},1000);}
    void settings(){body.addView(tv("⚙  Advanced Settings",20,TEXT)); row("🔐 Web Login & Password",v->security());row("🛡️ Allow / Block IP",v->security());row("⚡ Request Rate Limit",v->security());row("🎨 Themes",v->themes());row("🔗 Custom Server URL",v->customUrl());row("📱 QR Server Sharing",v->qr());row("ℹ️ Developer",v->about());}
    void row(String s,View.OnClickListener l){TextView v=tv(s,15,TEXT);v.setBackground(gd(CARD,18));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,58);p.setMargins(0,5,0,5);body.addView(v,p);v.setOnClickListener(l);}
    void security(){final EditText e=new EditText(this);e.setHint("Password (blank = off)");new AlertDialog.Builder(this).setTitle("Web Security").setMessage("Basic authentication is shared with the server.").setView(e).setPositiveButton("Save",(d,w)->{getPreferences(0).edit().putString("password",e.getText().toString()).apply();WebServerService.password=e.getText().toString();toast("Security saved");}).setNegativeButton("Cancel",null).show();}
    void themes(){String[] t={"Ocean Neon","Purple Night","Emerald","Sunset","Midnight","Arctic"};new AlertDialog.Builder(this).setTitle("Themes").setItems(t,(d,w)->{theme=w;toast(t[w]+" theme applied");});}
    void customUrl(){final EditText e=new EditText(this);e.setHint("example: salam.local");new AlertDialog.Builder(this).setTitle("Custom server URL / hostname").setView(e).setPositiveButton("Save",(d,w)->{WebServerService.customHost=e.getText().toString().trim();toast("Saved");}).setNegativeButton("Cancel",null).show();}
    void qr(){String u=WebServerService.currentUrl(this);new AlertDialog.Builder(this).setTitle("QR Server Sharing").setMessage(u+"\n\nUse a QR scanner to open this address.").setPositiveButton("Copy URL",(d,w)->{((android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("URL",u));toast("Copied");}).show();}
    void about(){new AlertDialog.Builder(this).setTitle("Salam Web Server v10.0").setMessage("Developer: Abdus Salam\nPhone: 09696590864\nEmail: salam230864@gmail.com").setPositiveButton("Messenger",(d,w)->{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("https://m.me/Salam.864")));}).setNegativeButton("Close",null).show();}
    void networkPage(){new AlertDialog.Builder(this).setTitle("Network Information").setMessage(WebServerService.networkInfo(this)+"\nWi‑Fi: "+WebServerService.wifiIp(this)+"\nMobile: "+WebServerService.cellularIp(this)).setPositiveButton("OK",null).show();}
    void popup(View a){new AlertDialog.Builder(this).setItems(new String[]{"Restart server","Open Android app settings","About"},(d,w)->{if(w==0){stop();h.postDelayed(()->start(),500);}else if(w==1)startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName())));else about();}).show();}
    void toast(String s){Toast.makeText(this,s,Toast.LENGTH_SHORT).show();}
}
