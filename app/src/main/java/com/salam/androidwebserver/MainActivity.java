package com.salam.androidwebserver;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.net.*;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.*;

public class MainActivity extends Activity {
    static final int BG=Color.rgb(5,14,28), CARD=Color.rgb(9,24,44), CARD2=Color.rgb(13,34,60);
    static final int BLUE=Color.rgb(20,135,255), CYAN=Color.rgb(31,211,255), GREEN=Color.rgb(23,231,164);
    static final int WHITE=Color.rgb(247,250,255), MUTED=Color.rgb(166,190,218);
    SharedPreferences pref;
    LinearLayout content, bottom;
    TextView pageTitle, homeStatus, homeUrl, homeStats;
    Handler handler=new Handler(Looper.getMainLooper());
    int page=0;
    String folder="/";

    int dp(float n){return (int)(n*getResources().getDisplayMetrics().density+0.5f);}
    TextView text(String s,float size,int color){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);return t;}
    GradientDrawable grad(int a,int b,float radius){GradientDrawable g=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{a,b});g.setCornerRadius(dp(radius));return g;}
    LinearLayout card(){LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setPadding(dp(15),dp(13),dp(15),dp(13));c.setBackground(grad(CARD,CARD2,18));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,dp(5),0,dp(5));c.setLayoutParams(p);return c;}
    Button blueButton(String s){Button b=new Button(this);b.setText(s);b.setTextColor(WHITE);b.setTextSize(13);b.setAllCaps(false);b.setBackground(grad(BLUE,CYAN,15));return b;}
    void add(ViewGroup p,View v,int w,int h){p.addView(v,new LinearLayout.LayoutParams(w,dp(h)));}

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        getWindow().setStatusBarColor(BG); getWindow().setNavigationBarColor(BG);
        pref=getSharedPreferences("server",MODE_PRIVATE);
        if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},50);
        splash();
    }

    void splash(){
        final FrameLayout root=new FrameLayout(this);root.setBackgroundColor(BG);root.addView(new Wave(this));
        LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setGravity(Gravity.CENTER);c.setPadding(dp(30),0,dp(30),0);
        TextView logo=text("▰",62,CYAN);logo.setGravity(Gravity.CENTER);add(c,logo,-1,95);
        TextView s=text("Salam",40,BLUE);s.setTypeface(null,1);s.setGravity(Gravity.CENTER);add(c,s,-1,52);
        TextView w=text("Web Server",30,WHITE);w.setTypeface(null,1);w.setGravity(Gravity.CENTER);add(c,w,-1,45);
        TextView sub=text("Fast  •  Secure  •  Local",14,MUTED);sub.setGravity(Gravity.CENTER);add(c,sub,-1,40);
        TextView made=text("Made with ♥ by Salam",12,MUTED);made.setGravity(Gravity.CENTER);c.addView(made,new LinearLayout.LayoutParams(-1,0,1));
        root.addView(c,new FrameLayout.LayoutParams(-1,-1));setContentView(root);
        handler.postDelayed(()->{ app(); if(pref.getBoolean("autostart",false)) server(true); },600);
    }

    void app(){
        FrameLayout root=new FrameLayout(this);root.setBackgroundColor(BG);root.addView(new Wave(this));
        LinearLayout shell=new LinearLayout(this);shell.setOrientation(LinearLayout.VERTICAL);shell.setPadding(dp(10),dp(5),dp(10),0);
        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);
        TextView logo=text("▰",25,CYAN);logo.setGravity(Gravity.CENTER);add(top,logo,dp(43),56);
        LinearLayout titles=new LinearLayout(this);titles.setOrientation(LinearLayout.VERTICAL);
        pageTitle=text("Salam Web Server",18,WHITE);pageTitle.setTypeface(null,1);add(titles,pageTitle,-1,29);
        add(titles,text("Your Local Server. Your Control.",10,MUTED),-1,20);
        top.addView(titles,new LinearLayout.LayoutParams(0,55,1));
        TextView more=text("☰",23,WHITE);more.setGravity(Gravity.CENTER);more.setOnClickListener(v->about());add(top,more,42,55);
        shell.addView(top,new LinearLayout.LayoutParams(-1,60));

        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);
        content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);content.setPadding(dp(1),dp(2),dp(1),dp(8));
        scroll.addView(content);shell.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));

        bottom=new LinearLayout(this);bottom.setGravity(Gravity.CENTER);bottom.setPadding(0,dp(3),0,dp(3));bottom.setBackground(grad(Color.rgb(5,13,25),Color.rgb(9,24,42),16));
        String[] labels={"⌂\nHome","▱\nFiles","≡\nLogs","⚙\nSettings"};
        for(int i=0;i<4;i++){final int x=i;TextView n=text(labels[i],11,MUTED);n.setGravity(Gravity.CENTER);n.setOnClickListener(v->show(x));bottom.addView(n,new LinearLayout.LayoutParams(0,dp(58),1));}
        shell.addView(bottom,new LinearLayout.LayoutParams(-1,dp(65)));
        root.addView(shell,new FrameLayout.LayoutParams(-1,-1));setContentView(root);
        show(0);refreshLoop();
    }

    void show(int p){
        page=p;content.removeAllViews();
        if(p==0) home(); else if(p==1) files(); else if(p==2) logs(); else settings();
        if(pageTitle!=null) pageTitle.setText(p==0?"Salam Web Server":p==1?"Web Files":p==2?"Server Logs":"Server Settings");
    }

    void home(){
        LinearLayout c=card();LinearLayout head=new LinearLayout(this);head.setGravity(Gravity.CENTER_VERTICAL);
        TextView icon=text("▰",45,CYAN);icon.setGravity(Gravity.CENTER);add(head,icon,70,70);
        LinearLayout x=new LinearLayout(this);x.setOrientation(LinearLayout.VERTICAL);
        homeStatus=text("Server Stopped",16,Color.RED);homeStatus.setTypeface(null,1);add(x,homeStatus,-1,28);add(x,text("Your local server is ready.",11,MUTED),-1,23);
        head.addView(x,new LinearLayout.LayoutParams(0,62,1));
        Switch sw=new Switch(this);sw.setChecked(WebServerService.running);sw.setOnCheckedChangeListener((b,v)->server(v));add(head,sw,58,58);c.addView(head);
        homeUrl=text(WebServerService.currentUrl(this),13,WHITE);homeUrl.setGravity(Gravity.CENTER_VERTICAL);homeUrl.setPadding(dp(13),0,dp(13),0);homeUrl.setBackground(grad(Color.rgb(5,16,31),Color.rgb(10,28,50),13));add(c,homeUrl,-1,50);
        LinearLayout actions=new LinearLayout(this);
        Button open=blueButton("🌐  Open Browser");open.setOnClickListener(v->openBrowser());Button copy=blueButton("▣  Copy URL");copy.setOnClickListener(v->copyUrl());
        actions.addView(open,new LinearLayout.LayoutParams(0,dp(46),1));actions.addView(copy,new LinearLayout.LayoutParams(0,dp(46),1));c.addView(actions);
        content.addView(c);
        homeStats=text("●  Online      Requests: 0      Clients: 0\nUptime: 00:00:00      RAM: —",12,WHITE);homeStats.setPadding(dp(15),dp(8),dp(15),dp(8));homeStats.setBackground(grad(CARD,CARD2,17));add(content,homeStats,-1,65);
        section("QUICK ACTIONS");
        tile("📁","Web Files","Manage your website files",()->show(1));
        tile("⚙","Settings","Server configuration & security",()->show(3));
        tile("⌁","Network Info","LAN address, Wi-Fi and interfaces",()->network());
        tile("▤","Server Logs","Live request and access history",()->show(2));
        section("SERVER");
        tile("🔳","QR Server Sharing","Share your local server address",()->qr());
        tile("🔐","Web Login","Password protection for website access",()->security());
        tile("🟢","IP Allowlist / Blocklist","Control who can access the server",()->security());
        tile("🚦","Request Rate Limit","Limit requests per IP",()->security());
        tile("🎨","Themes","Midnight • Ocean • Violet • Emerald • Sunset",()->theme());
        section("DEVELOPER");
        tile("👨‍💻","About Salam Web Server","Version 10.0 • Abdus Salam",()->about());
        tile("💬","Messenger","Contact the developer",()->messenger());
    }

    void section(String s){TextView t=text("  "+s,11,Color.rgb(120,165,215));t.setTypeface(null,1);add(content,t,-1,35);}
    void tile(String ico,String name,String desc,final Runnable run){
        LinearLayout c=card();c.setOrientation(LinearLayout.HORIZONTAL);TextView i=text(ico,25,WHITE);i.setGravity(Gravity.CENTER);add(c,i,52,55);
        LinearLayout x=new LinearLayout(this);x.setOrientation(LinearLayout.VERTICAL);add(x,text(name,14,WHITE),-1,27);add(x,text(desc,10,MUTED),-1,23);
        c.addView(x,new LinearLayout.LayoutParams(0,55,1));TextView arrow=text("›",27,CYAN);arrow.setGravity(Gravity.CENTER);add(c,arrow,28,55);c.setOnClickListener(v->run.run());content.addView(c);
    }

    void server(boolean start){
        try{Intent i=new Intent(this,WebServerService.class);i.setAction(start?"START":"STOP");
            if(start&&Build.VERSION.SDK_INT>=26)startForegroundService(i);else startService(i);
        }catch(Exception e){Toast.makeText(this,e.getMessage(),Toast.LENGTH_LONG).show();}
        handler.postDelayed(()->show(0),300);
    }
    void refreshLoop(){
        handler.postDelayed(new Runnable(){public void run(){
            if(isFinishing())return;
            if(page==0 && homeStatus!=null){
                boolean r=WebServerService.running;
                homeStatus.setText(r?"Server Running":"Server Stopped");homeStatus.setTextColor(r?GREEN:Color.RED);
                homeUrl.setText(WebServerService.currentUrl(MainActivity.this));
                homeStats.setText((r?"●  Online":"●  Offline")+"      Requests: "+WebServerService.requests+"      Clients: "+WebServerService.clients.size()+
                        "\nUptime: "+WebServerService.uptime()+"      RAM: "+WebServerService.memoryText(MainActivity.this));
            }
            handler.postDelayed(this,1000);
        }},1000);
    }

    void files(){
        LinearLayout pbar=card();TextView path=text("📁  "+folder,12,WHITE);path.setGravity(Gravity.CENTER_VERTICAL);path.setPadding(dp(13),0,dp(13),0);path.setBackground(grad(Color.rgb(6,17,32),CARD2,12));add(pbar,path,-1,47);content.addView(pbar);
        LinearLayout act=new LinearLayout(this);Button u=blueButton("＋ Upload");u.setOnClickListener(v->pick());Button f=blueButton("＋ Folder");f.setOnClickListener(v->newFolder());Button nf=blueButton("＋ File");nf.setOnClickListener(v->newFile());
        act.addView(u,new LinearLayout.LayoutParams(0,dp(44),1));act.addView(f,new LinearLayout.LayoutParams(0,dp(44),1));act.addView(nf,new LinearLayout.LayoutParams(0,dp(44),1));content.addView(act);
        File d=new File(WebServerService.webRoot(this),folder);File[] list=d.listFiles();if(list==null)return;Arrays.sort(list,(a,b)->{if(a.isDirectory()!=b.isDirectory())return a.isDirectory()?-1:1;return a.getName().compareToIgnoreCase(b.getName());});
        if(!"/".equals(folder))tile("‹","..","Parent folder",()->{int q=folder.lastIndexOf('/');folder=q<=0?"/":folder.substring(0,q);show(1);});
        for(File q:list){final File file=q;tile(file.isDirectory()?"📂":fileIcon(file.getName()),file.getName(),file.isDirectory()?"Folder • "+count(file)+" items":format(file.length()),()->fileMenu(file));}
    }
    int count(File f){File[] x=f.listFiles();return x==null?0:x.length;}
    String fileIcon(String n){String x=n.toLowerCase(Locale.US);if(x.endsWith(".html")||x.endsWith(".htm"))return"🌐";if(x.endsWith(".css"))return"🎨";if(x.endsWith(".js"))return"🟨";if(x.endsWith(".json"))return"🧩";if(x.endsWith(".zip"))return"📦";if(x.endsWith(".png")||x.endsWith(".jpg")||x.endsWith(".jpeg"))return"🖼";return"📄";}
    String format(long b){if(b<1024)return b+" B";if(b<1048576)return(b/1024)+" KB";return(b/1048576)+" MB";}

    void fileMenu(File f){
        ArrayList<String> a=new ArrayList<>();if(f.isDirectory())a.add("📂 Open");else a.add("✏️ Edit");a.add("✏️ Rename");a.add("📋 Copy");a.add("📤 Move");a.add("⬇️ Save As");a.add("🗑 Delete");
        new AlertDialog.Builder(this).setTitle(f.getName()).setItems(a.toArray(new String[0]),(d,w)->{
            String s=a.get(w);if("📂 Open".equals(s)){folder=(folder.endsWith("/")?folder:folder+"/")+f.getName();show(1);}
            else if("✏️ Edit".equals(s))edit(f);else if("✏️ Rename".equals(s))rename(f);else if("📋 Copy".equals(s))copyMove(f,false);
            else if("📤 Move".equals(s))copyMove(f,true);else if("⬇️ Save As".equals(s))saveAs(f);else if("🗑 Delete".equals(s))deleteConfirm(f);
        }).show();
    }
    void edit(File f){EditText e=new EditText(this);e.setTextColor(WHITE);e.setTextSize(12);e.setGravity(Gravity.TOP);e.setMinLines(15);try{e.setText(read(f));}catch(Exception ignored){}new AlertDialog.Builder(this).setTitle("📝 "+f.getName()).setView(e).setNegativeButton("Cancel",null).setPositiveButton("Save",(d,w)->{try{write(f,e.getText().toString());Toast.makeText(this,"File saved",Toast.LENGTH_SHORT).show();}catch(Exception x){Toast.makeText(this,x.getMessage(),Toast.LENGTH_LONG).show();}}).show();}
    String read(File f)throws Exception{BufferedReader r=new BufferedReader(new FileReader(f));StringBuilder s=new StringBuilder();String x;while((x=r.readLine())!=null)s.append(x).append('\n');r.close();return s.toString();}
    void write(File f,String s)throws Exception{FileWriter w=new FileWriter(f);w.write(s);w.close();}
    void rename(File f){EditText e=input(f.getName());new AlertDialog.Builder(this).setTitle("✏️ Rename").setView(e).setNegativeButton("Cancel",null).setPositiveButton("Rename",(d,w)->{String n=e.getText().toString().trim().replace("/","_");if(!n.isEmpty()&&!new File(f.getParent(),n).exists()&&f.renameTo(new File(f.getParent(),n)))show(1);}).show();}
    void copyMove(File f,boolean move){EditText e=input(folder);new AlertDialog.Builder(this).setTitle(move?"📤 Move":"📋 Copy").setView(e).setNegativeButton("Cancel",null).setPositiveButton(move?"Move":"Copy",(d,w)->{try{File destDir=new File(WebServerService.webRoot(this),e.getText().toString());destDir.mkdirs();copyRecursive(f,new File(destDir,f.getName()));if(move)delete(f);show(1);}catch(Exception x){Toast.makeText(this,x.getMessage(),Toast.LENGTH_LONG).show();}}).show();}
    void copyRecursive(File a,File b)throws IOException{if(a.isDirectory()){b.mkdirs();File[] x=a.listFiles();if(x!=null)for(File q:x)copyRecursive(q,new File(b,q.getName()));}else{InputStream in=new FileInputStream(a);OutputStream out=new FileOutputStream(b);byte[] z=new byte[16384];int n;while((n=in.read(z))>0)out.write(z,0,n);in.close();out.close();}}
    void deleteConfirm(File f){new AlertDialog.Builder(this).setTitle("🗑 Delete").setMessage("Delete "+f.getName()+"?").setNegativeButton("Cancel",null).setPositiveButton("Delete",(d,w)->{delete(f);show(1);}).show();}
    void delete(File f){if(f.isDirectory()){File[] x=f.listFiles();if(x!=null)for(File q:x)delete(q);}f.delete();}
    void saveAs(File f){Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.setType("*/*");i.putExtra(Intent.EXTRA_TITLE,f.getName());pending=f;startActivityForResult(i,102);}
    File pending;
    void pick(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("*/*");i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);i.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,101);}
    @Override protected void onActivityResult(int r,int c,Intent d){super.onActivityResult(r,c,d);try{
        if(r==101&&c==RESULT_OK&&d!=null){if(d.getClipData()!=null)for(int k=0;k<d.getClipData().getItemCount();k++)copyUri(d.getClipData().getItemAt(k).getUri());else if(d.getData()!=null)copyUri(d.getData());show(1);}
        if(r==102&&c==RESULT_OK&&d!=null&&d.getData()!=null&&pending!=null){InputStream in=new FileInputStream(pending);OutputStream out=getContentResolver().openOutputStream(d.getData());byte[] z=new byte[16384];int n;while((n=in.read(z))>0)out.write(z,0,n);in.close();out.close();pending=null;Toast.makeText(this,"Saved",Toast.LENGTH_SHORT).show();}
    }catch(Exception e){Toast.makeText(this,e.getMessage(),Toast.LENGTH_LONG).show();}}
    void copyUri(Uri u)throws Exception{String n="upload_"+System.currentTimeMillis();String s=u.getPath();if(s!=null&&s.contains(":"))n=s.substring(s.lastIndexOf(':')+1);if(n.contains("/"))n=n.substring(n.lastIndexOf('/')+1);File out=new File(WebServerService.webRoot(this),folder+"/"+n);InputStream in=getContentResolver().openInputStream(u);OutputStream o=new FileOutputStream(out);byte[] z=new byte[8192];int q;while((q=in.read(z))>0)o.write(z,0,q);in.close();o.close();}
    void newFolder(){EditText e=input("");new AlertDialog.Builder(this).setTitle("📂 Create Folder").setView(e).setNegativeButton("Cancel",null).setPositiveButton("Create",(d,w)->{String n=e.getText().toString().trim();if(!n.isEmpty())new File(WebServerService.webRoot(this),folder+"/"+n).mkdirs();show(1);}).show();}
    void newFile(){EditText e=input("index.html");new AlertDialog.Builder(this).setTitle("📄 Create File").setView(e).setNegativeButton("Cancel",null).setPositiveButton("Create",(d,w)->{try{File f=new File(WebServerService.webRoot(this),folder+"/"+e.getText().toString().trim());f.getParentFile().mkdirs();f.createNewFile();show(1);}catch(Exception ignored){}}).show();}
    EditText input(String s){EditText e=new EditText(this);e.setText(s);e.setTextColor(WHITE);e.setHintTextColor(Color.GRAY);e.setSingleLine(true);return e;}

    void logs(){LinearLayout c=card();LinearLayout r=new LinearLayout(this);Button clear=blueButton("🗑 Clear Log");Button refresh=blueButton("↻ Refresh");clear.setOnClickListener(v->{WebServerService.clearLogs();logs();});refresh.setOnClickListener(v->logs());r.addView(clear,new LinearLayout.LayoutParams(0,dp(44),1));r.addView(refresh,new LinearLayout.LayoutParams(0,dp(44),1));c.addView(r);TextView l=text(WebServerService.logsText(),11,WHITE);l.setGravity(Gravity.TOP);l.setPadding(dp(4),dp(10),dp(4),dp(10));c.addView(l,new LinearLayout.LayoutParams(-1,dp(560)));content.addView(c);}
    void settings(){section("SERVER CONFIGURATION");setting("▣","Port","8080 • Change listening port",()->portDialog());setting("📁","Document Root","App-local web root • /files/www",()->Toast.makeText(this,"Web root: "+WebServerService.webRoot(this),Toast.LENGTH_LONG).show());setting("🚀","Auto Start",pref.getBoolean("autostart",false)?"Enabled":"Disabled",()->{boolean x=!pref.getBoolean("autostart",false);pref.edit().putBoolean("autostart",x).apply();settings();});section("SECURITY");setting("🔐","Password Protection",pref.getString("password","").isEmpty()?"Off":"On",()->security());setting("🟢","IP Allowlist",pref.getString("allowIps","").isEmpty()?"Empty":"Configured",()->security());setting("🔴","IP Blocklist",pref.getString("blockIps","").isEmpty()?"Empty":"Configured",()->security());setting("🚦","Request Rate Limit",""+pref.getInt("rate",120)+" requests/min/IP",()->security());section("NETWORK & APPEARANCE");setting("🌐","Network Info","LAN address & interfaces",()->network());setting("🔳","QR Server Sharing","Share server URL",()->qr());setting("🎨","Theme","Midnight • Ocean • Violet • Emerald • Sunset",()->theme());section("ABOUT");setting("👨‍💻","Developer","Abdus Salam • 09696590864",()->about());setting("💬","Messenger","m.me/Salam.864",()->messenger());}
    void setting(String i,String n,String d,Runnable r){tile(i,n,d,r);}
    void portDialog(){EditText e=input(""+pref.getInt("port",8080));new AlertDialog.Builder(this).setTitle("Port").setView(e).setNegativeButton("Cancel",null).setPositiveButton("Save",(d,w)->{try{int p=Integer.parseInt(e.getText().toString());if(p>=1024&&p<=65535)pref.edit().putInt("port",p).apply();}catch(Exception ignored){}settings();}).show();}
    void security(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);l.setPadding(dp(8),0,dp(8),0);EditText pass=input(pref.getString("password",""));pass.setHint("Web password");EditText allow=input(pref.getString("allowIps",""));allow.setHint("Allow IPs, comma separated");EditText block=input(pref.getString("blockIps",""));block.setHint("Block IPs, comma separated");EditText rate=input(""+pref.getInt("rate",120));rate.setHint("Requests/min/IP");EditText max=input(""+pref.getInt("maxClients",32));max.setHint("Maximum clients");l.addView(pass);l.addView(allow);l.addView(block);l.addView(rate);l.addView(max);new AlertDialog.Builder(this).setTitle("🛡 Security").setView(l).setNegativeButton("Cancel",null).setPositiveButton("Save",(d,w)->pref.edit().putString("password",pass.getText().toString()).putString("allowIps",allow.getText().toString()).putString("blockIps",block.getText().toString()).putInt("rate",parse(rate,120)).putInt("maxClients",parse(max,32)).apply()).show();}
    int parse(EditText e,int d){try{return Integer.parseInt(e.getText().toString());}catch(Exception x){return d;}}
    void theme(){
        String[] names={"🌑 Midnight","🌊 Ocean","💜 Violet","💚 Emerald","🌅 Sunset","⚡ Neon"};
        int current=pref.getInt("theme",0);
        new AlertDialog.Builder(this).setTitle("🎨 Theme").setSingleChoiceItems(names,current,(d,w)->{
            pref.edit().putInt("theme",w).apply(); d.dismiss(); app();
        }).show();
    }
    void network(){new AlertDialog.Builder(this).setTitle("🌐 Network Info").setMessage("Local Server URL\n"+WebServerService.currentUrl(this)+"\n\n"+WebServerService.networkInfo(this)).setPositiveButton("OK",null).show();}
    void qr(){new AlertDialog.Builder(this).setTitle("🔳 QR Server Sharing").setMessage("Server address:\n\n"+WebServerService.currentUrl(this)+"\n\nUse Share to send the URL to another device.").setNegativeButton("Close",null).setPositiveButton("Share",(d,w)->shareText(WebServerService.currentUrl(this))).show();}
    void shareText(String s){Intent i=new Intent(Intent.ACTION_SEND);i.setType("text/plain");i.putExtra(Intent.EXTRA_TEXT,s);startActivity(Intent.createChooser(i,"Share Server URL"));}
    void copyUrl(){((android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("Server URL",WebServerService.currentUrl(this)));Toast.makeText(this,"URL copied",Toast.LENGTH_SHORT).show();}
    void openBrowser(){startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(WebServerService.currentUrl(this))));}
    void messenger(){try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("https://m.me/Salam.864")));}catch(Exception ignored){}}
    void about(){new AlertDialog.Builder(this).setTitle("Salam Web Server").setMessage("Version 10.0\n\nDeveloped by Abdus Salam\n09696590864\nsalam230864@gmail.com\n\nFast • Secure • Local\nNative Android control panel").setPositiveButton("OK",null).show();}
    static class Wave extends View{
        Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);Path path=new Path();
        Wave(Context c){super(c);}
        protected void onDraw(Canvas c){
            int w=getWidth(),h=getHeight(); int theme= getContext().getSharedPreferences("server",Context.MODE_PRIVATE).getInt("theme",0);
            int a=Color.rgb(3,12,25), b=Color.rgb(7,27,51), line=Color.rgb(10,102,225);
            if(theme==1){a=Color.rgb(2,22,35);b=Color.rgb(4,55,78);line=Color.rgb(20,190,255);}
            else if(theme==2){a=Color.rgb(12,5,28);b=Color.rgb(45,12,78);line=Color.rgb(190,80,255);}
            else if(theme==3){a=Color.rgb(3,22,19);b=Color.rgb(5,60,48);line=Color.rgb(30,230,160);}
            else if(theme==4){a=Color.rgb(28,10,5);b=Color.rgb(70,25,10);line=Color.rgb(255,135,35);}
            else if(theme==5){a=Color.rgb(3,7,24);b=Color.rgb(15,35,80);line=Color.rgb(60,120,255);}
            LinearGradient g=new LinearGradient(0,0,w,h,a,b,Shader.TileMode.CLAMP);p.setShader(g);c.drawRect(0,0,w,h,p);p.setShader(null);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2);p.setColor(line);
            for(int j=0;j<3;j++){path.reset();for(int x=0;x<=w;x+=10){float den=getResources().getDisplayMetrics().density;float y=h*.70f+j*den*18f+(float)Math.sin(x*.013+j)*den*19f;if(x==0)path.moveTo(x,y);else path.lineTo(x,y);}c.drawPath(path,p);}p.setStyle(Paint.Style.FILL);
        }
    }
}
