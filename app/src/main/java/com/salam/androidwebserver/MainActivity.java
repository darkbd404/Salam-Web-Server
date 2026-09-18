package com.salam.androidwebserver;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.*;

public class MainActivity extends Activity {
    static final int BG=Color.rgb(3,12,25), CARD=Color.rgb(7,24,46), CARD2=Color.rgb(10,31,56);
    static final int BLUE=Color.rgb(20,139,255), CYAN=Color.rgb(28,211,255), GREEN=Color.rgb(20,230,163);
    static final int WHITE=Color.rgb(245,249,255), MUTED=Color.rgb(154,183,214);
    SharedPreferences pref; LinearLayout content; TextView title,status,url,stats; Handler handler=new Handler(Looper.getMainLooper());
    int page=0; String folder="/"; File pending;

    int dp(float x){return (int)(x*getResources().getDisplayMetrics().density+.5f);}
    TextView t(String s,float z,int c){TextView v=new TextView(this);v.setText(s);v.setTextSize(z);v.setTextColor(c);return v;}
    GradientDrawable gd(int a,int b,float r){GradientDrawable g=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{a,b});g.setCornerRadius(dp(r));return g;}
    LinearLayout card(){LinearLayout x=new LinearLayout(this);x.setOrientation(LinearLayout.VERTICAL);x.setPadding(dp(15),dp(12),dp(15),dp(12));x.setBackground(gd(CARD,CARD2,20));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,dp(5),0,dp(5));x.setLayoutParams(p);return x;}
    Button btn(String s){Button b=new Button(this);b.setText(s);b.setTextColor(WHITE);b.setTextSize(13);b.setAllCaps(false);b.setBackground(gd(BLUE,CYAN,22));return b;}
    void add(ViewGroup p,View v,int w,int h){p.addView(v,new LinearLayout.LayoutParams(w,dp(h)));}

    @Override public void onCreate(Bundle b){
        super.onCreate(b); getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);
        pref=getSharedPreferences("server",MODE_PRIVATE);
        if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},11);
        buildApp();
    }

    void buildApp(){
        FrameLayout root=new FrameLayout(this);root.addView(new Wave(this));
        LinearLayout shell=new LinearLayout(this);shell.setOrientation(LinearLayout.VERTICAL);shell.setPadding(dp(12),0,dp(12),0);

        LinearLayout head=new LinearLayout(this);head.setGravity(Gravity.CENTER_VERTICAL);
        TextView mark=t("▬",27,CYAN);mark.setGravity(Gravity.CENTER);add(head,mark,42,56);
        title=t("Salam Web Server",18,WHITE);title.setTypeface(null,1);title.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(title,new LinearLayout.LayoutParams(0,56,1));
        TextView menu=t("☰",28,WHITE);menu.setGravity(Gravity.CENTER);menu.setOnClickListener(v->about());add(head,menu,48,56);
        shell.addView(head,new LinearLayout.LayoutParams(-1,60));

        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);
        content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);content.setPadding(0,0,0,dp(8));
        scroll.addView(content);shell.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));

        LinearLayout nav=new LinearLayout(this);nav.setGravity(Gravity.CENTER);nav.setBackground(gd(Color.rgb(4,15,29),Color.rgb(7,23,42),18));
        String[] labels={"⌂\nHome","▱\nFiles","≡\nLogs","⚙\nSettings"};
        for(int i=0;i<4;i++){final int p=i;TextView n=t(labels[i],11,MUTED);n.setGravity(Gravity.CENTER);n.setOnClickListener(v->show(p));nav.addView(n,new LinearLayout.LayoutParams(0,dp(64),1));}
        shell.addView(nav,new LinearLayout.LayoutParams(-1,dp(68)));
        root.addView(shell);setContentView(root);show(0);refresh();
    }

    void show(int p){
        page=p;content.removeAllViews();
        if(p==0)home();else if(p==1)files();else if(p==2)logs();else settings();
        if(title!=null)title.setText(p==0?"Salam Web Server":p==1?"Web Files":p==2?"Server Logs":"Server Settings");
    }

    void home(){
        LinearLayout c=card();
        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);
        status=t(WebServerService.running?"Server Running":"Server Stopped",17,WebServerService.running?GREEN:Color.RED);status.setTypeface(null,1);top.addView(status,new LinearLayout.LayoutParams(0,40,1));
        TextView dot=t("●",31,WebServerService.running?GREEN:Color.RED);dot.setGravity(Gravity.CENTER);add(top,dot,42,40);c.addView(top);
        url=t(WebServerService.currentUrl(this),13,WHITE);url.setGravity(Gravity.CENTER_VERTICAL);url.setPadding(dp(14),0,dp(10),0);url.setBackground(gd(Color.rgb(3,16,31),Color.rgb(7,26,47),15));add(c,url,-1,54);
        LinearLayout a=new LinearLayout(this);Button open=btn("🌐  Open Browser"),copy=btn("▣  Copy URL");open.setOnClickListener(v->openBrowser());copy.setOnClickListener(v->copyUrl());
        a.addView(open,new LinearLayout.LayoutParams(0,dp(48),1));a.addView(copy,new LinearLayout.LayoutParams(0,dp(48),1));c.addView(a);content.addView(c);

        stats=t("",12,WHITE);stats.setPadding(dp(15),dp(8),dp(8),0);stats.setBackground(gd(CARD,CARD2,18));add(content,stats,-1,68);
        section("QUICK ACTIONS");
        tile("📁","Web Files","",()->show(1));tile("⚙","Settings","",()->show(3));tile("⌁","Network Info","",()->network());tile("▤","Server Logs","",()->show(2));
        section("SERVER");
        tile("▣","QR Server Sharing","",()->shareText(WebServerService.currentUrl(this)));
        tile("🔐","Web Login","",()->security());
        tile("🟢","IP Allowlist / Blocklist","",()->security());
        tile("🚦","Request Rate Limit","",()->security());
        section("DEVELOPER");
        tile("👨‍💻","About Salam Web Server","Version 10.0 • Abdus Salam",()->about());
        tile("💬","Messenger","09696590864",()->messenger());
    }

    void section(String s){TextView v=t(s,11,Color.rgb(125,174,224));v.setTypeface(null,1);v.setPadding(dp(10),dp(7),0,0);add(content,v,-1,34);}
    void tile(String icon,String name,String desc,Runnable run){
        LinearLayout c=card();c.setOrientation(LinearLayout.HORIZONTAL);
        TextView i=t(icon,25,WHITE);i.setGravity(Gravity.CENTER);add(c,i,48,50);
        LinearLayout m=new LinearLayout(this);m.setOrientation(LinearLayout.VERTICAL);
        TextView n=t(name,15,WHITE);n.setTypeface(null,1);add(m,n,-1,27);
        if(!desc.isEmpty())add(m,t(desc,10,MUTED),-1,20);
        c.addView(m,new LinearLayout.LayoutParams(0,50,1));
        TextView ar=t("›",30,CYAN);ar.setGravity(Gravity.CENTER);add(c,ar,26,50);c.setOnClickListener(v->run.run());content.addView(c);
    }

    void refresh(){
        handler.postDelayed(new Runnable(){public void run(){
            if(isFinishing())return;
            if(page==0&&stats!=null){
                boolean r=WebServerService.running;status.setText(r?"Server Running":"Server Stopped");status.setTextColor(r?GREEN:Color.RED);
                url.setText(WebServerService.currentUrl(MainActivity.this));
                stats.setText((r?"● Online":"● Offline")+"     Requests: "+WebServerService.requests+"     Clients: "+WebServerService.clients.size()+"\nUptime: "+WebServerService.uptime()+"     RAM: "+WebServerService.memoryText(MainActivity.this));
            }
            handler.postDelayed(this,1000);
        }},1000);
    }

    void files(){
        LinearLayout p=card();TextView path=t("📁  "+folder,12,WHITE);path.setGravity(Gravity.CENTER_VERTICAL);path.setPadding(dp(13),0,0,0);path.setBackground(gd(Color.rgb(5,16,31),CARD2,13));add(p,path,-1,45);content.addView(p);
        LinearLayout a=new LinearLayout(this);Button up=btn("＋ Upload"),fo=btn("＋ Folder"),fi=btn("＋ File");
        up.setOnClickListener(v->pick());fo.setOnClickListener(v->newFolder());fi.setOnClickListener(v->newFile());
        a.addView(up,new LinearLayout.LayoutParams(0,dp(44),1));a.addView(fo,new LinearLayout.LayoutParams(0,dp(44),1));a.addView(fi,new LinearLayout.LayoutParams(0,dp(44),1));content.addView(a);
        File d=new File(WebServerService.webRoot(this),folder);File[] fs=d.listFiles();if(fs==null)return;
        Arrays.sort(fs,(x,y)->{if(x.isDirectory()!=y.isDirectory())return x.isDirectory()?-1:1;return x.getName().compareToIgnoreCase(y.getName());});
        if(!"/".equals(folder))tile("‹","..","Parent folder",()->{int q=folder.lastIndexOf('/');folder=q<=0?"/":folder.substring(0,q);show(1);});
        for(File f:fs){final File z=f;tile(z.isDirectory()?"📂":icon(z.getName()),z.getName(),z.isDirectory()?"Folder • "+count(z)+" items":size(z.length()),()->fileMenu(z));}
    }
    int count(File f){File[] x=f.listFiles();return x==null?0:x.length;}
    String icon(String n){String x=n.toLowerCase(Locale.US);if(x.endsWith(".html")||x.endsWith(".htm"))return"🌐";if(x.endsWith(".css"))return"🎨";if(x.endsWith(".js"))return"🟨";if(x.endsWith(".json"))return"🧩";if(x.endsWith(".zip"))return"📦";if(x.matches(".*\\.(png|jpg|jpeg|webp)$"))return"🖼";return"📄";}
    String size(long b){if(b<1024)return b+" B";if(b<1048576)return(b/1024)+" KB";return(b/1048576)+" MB";}

    void fileMenu(File f){
        ArrayList<String>a=new ArrayList<>();if(f.isDirectory())a.add("📂 Open");else a.add("✏️ Edit");
        a.add("✏️ Rename");a.add("📋 Copy");a.add("📤 Move");a.add("⬇️ Save As");a.add("🗑 Delete");
        new AlertDialog.Builder(this).setTitle(f.getName()).setItems(a.toArray(new String[0]),(d,w)->{
            String s=a.get(w);if(s.startsWith("📂")){folder=(folder.endsWith("/")?folder:folder+"/")+f.getName();show(1);}
            else if(s.startsWith("✏️ Edit"))edit(f);else if(s.startsWith("✏️ Rename"))rename(f);else if(s.startsWith("📋"))copyMove(f,false);else if(s.startsWith("📤"))copyMove(f,true);else if(s.startsWith("⬇"))saveAs(f);else deleteConfirm(f);
        }).show();
    }
    void edit(File f){EditText e=input("");e.setGravity(Gravity.TOP);e.setMinLines(16);try{e.setText(read(f));}catch(Exception ignored){}new AlertDialog.Builder(this).setTitle("📝 "+f.getName()).setView(e).setNegativeButton("Cancel",null).setPositiveButton("Save",(d,w)->{try{write(f,e.getText().toString());toast("File saved");}catch(Exception x){toast(x.getMessage());}}).show();}
    String read(File f)throws Exception{BufferedReader r=new BufferedReader(new FileReader(f));StringBuilder s=new StringBuilder(),x;String q;while((q=r.readLine())!=null)s.append(q).append('\n');r.close();return s.toString();}
    void write(File f,String s)throws Exception{FileWriter w=new FileWriter(f);w.write(s);w.close();}
    void rename(File f){EditText e=input(f.getName());new AlertDialog.Builder(this).setTitle("✏️ Rename").setView(e).setNegativeButton("Cancel",null).setPositiveButton("Rename",(d,w)->{String n=e.getText().toString().trim().replace("/","_");if(!n.isEmpty())f.renameTo(new File(f.getParent(),n));show(1);}).show();}
    void copyMove(File f,boolean move){EditText e=input(folder);new AlertDialog.Builder(this).setTitle(move?"📤 Move":"📋 Copy").setView(e).setNegativeButton("Cancel",null).setPositiveButton(move?"Move":"Copy",(d,w)->{try{File dir=new File(WebServerService.webRoot(this),e.getText().toString());dir.mkdirs();copyRecursive(f,new File(dir,f.getName()));if(move)delete(f);show(1);}catch(Exception x){toast(x.getMessage());}}).show();}
    void copyRecursive(File a,File b)throws IOException{if(a.isDirectory()){b.mkdirs();File[]z=a.listFiles();if(z!=null)for(File q:z)copyRecursive(q,new File(b,q.getName()));}else{InputStream i=new FileInputStream(a);OutputStream o=new FileOutputStream(b);byte[]z=new byte[16384];int n;while((n=i.read(z))>0)o.write(z,0,n);i.close();o.close();}}
    void deleteConfirm(File f){new AlertDialog.Builder(this).setTitle("🗑 Delete").setMessage("Delete "+f.getName()+"?").setNegativeButton("Cancel",null).setPositiveButton("Delete",(d,w)->{delete(f);show(1);}).show();}
    void delete(File f){if(f.isDirectory()){File[]z=f.listFiles();if(z!=null)for(File q:z)delete(q);}f.delete();}
    void saveAs(File f){Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.setType("*/*");i.putExtra(Intent.EXTRA_TITLE,f.getName());pending=f;startActivityForResult(i,102);}
    void pick(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("*/*");i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);i.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,101);}
    @Override protected void onActivityResult(int r,int c,Intent d){super.onActivityResult(r,c,d);try{if(r==101&&c==RESULT_OK&&d!=null){if(d.getClipData()!=null)for(int k=0;k<d.getClipData().getItemCount();k++)copyUri(d.getClipData().getItemAt(k).getUri());else if(d.getData()!=null)copyUri(d.getData());show(1);}else if(r==102&&c==RESULT_OK&&d!=null&&d.getData()!=null&&pending!=null){InputStream i=new FileInputStream(pending);OutputStream o=getContentResolver().openOutputStream(d.getData());byte[]z=new byte[16384];int n;while((n=i.read(z))>0)o.write(z,0,n);i.close();o.close();pending=null;toast("Saved");}}catch(Exception e){toast(e.getMessage());}}
    void copyUri(Uri u)throws Exception{String n="upload_"+System.currentTimeMillis();String q=u.getPath();if(q!=null&&q.contains(":"))n=q.substring(q.lastIndexOf(':')+1);if(n.contains("/"))n=n.substring(n.lastIndexOf('/')+1);File o=new File(WebServerService.webRoot(this),folder+"/"+n);InputStream i=getContentResolver().openInputStream(u);OutputStream x=new FileOutputStream(o);byte[]z=new byte[8192];int k;while((k=i.read(z))>0)x.write(z,0,k);i.close();x.close();}
    void newFolder(){EditText e=input("");new AlertDialog.Builder(this).setTitle("📂 Create Folder").setView(e).setNegativeButton("Cancel",null).setPositiveButton("Create",(d,w)->{String n=e.getText().toString().trim();if(!n.isEmpty())new File(WebServerService.webRoot(this),folder+"/"+n).mkdirs();show(1);}).show();}
    void newFile(){EditText e=input("index.html");new AlertDialog.Builder(this).setTitle("📄 Create File").setView(e).setNegativeButton("Cancel",null).setPositiveButton("Create",(d,w)->{try{File f=new File(WebServerService.webRoot(this),folder+"/"+e.getText().toString().trim());f.getParentFile().mkdirs();f.createNewFile();show(1);}catch(Exception x){toast(x.getMessage());}}).show();}
    EditText input(String s){EditText e=new EditText(this);e.setText(s);e.setTextColor(WHITE);e.setHintTextColor(Color.GRAY);e.setSingleLine(true);return e;}

    void logs(){LinearLayout c=card();LinearLayout r=new LinearLayout(this);Button a=btn("🗑 Clear Log"),b=btn("↻ Refresh");a.setOnClickListener(v->{WebServerService.clearLogs();logs();});b.setOnClickListener(v->logs());r.addView(a,new LinearLayout.LayoutParams(0,dp(44),1));r.addView(b,new LinearLayout.LayoutParams(0,dp(44),1));c.addView(r);TextView l=t(WebServerService.logsText(),11,WHITE);l.setGravity(Gravity.TOP);l.setPadding(dp(5),dp(10),dp(5),0);c.addView(l,new LinearLayout.LayoutParams(-1,dp(560)));content.addView(c);}
    void settings(){section("SERVER CONFIGURATION");setting("▣","Port",""+pref.getInt("port",8080),()->port());setting("📁","Document Root","App-local /files/www",()->toast(WebServerService.webRoot(this)));setting("🚀","Auto Start",pref.getBoolean("autostart",false)?"ON":"OFF",()->{boolean x=!pref.getBoolean("autostart",false);pref.edit().putBoolean("autostart",x).apply();settings();});section("SECURITY");setting("🔐","Password Protection",pref.getString("password","").isEmpty()?"OFF":"ON",()->security());setting("🟢","IP Allowlist",pref.getString("allowIps","").isEmpty()?"Empty":"Configured",()->security());setting("🔴","IP Blocklist",pref.getString("blockIps","").isEmpty()?"Empty":"Configured",()->security());setting("🚦","Request Rate Limit",pref.getInt("rate",120)+" requests/min/IP",()->security());section("NETWORK & APPEARANCE");setting("🌐","Network Info","LAN address & interfaces",()->network());setting("▣","QR Server Sharing","Share server URL",()->shareText(WebServerService.currentUrl(this)));setting("🎨","Theme","Midnight • Ocean • Violet • Emerald • Sunset",()->theme());section("ABOUT");setting("👨‍💻","Developer","Abdus Salam • 09696590864",()->about());setting("💬","Messenger","m.me/Salam.864",()->messenger());}
    void setting(String i,String n,String d,Runnable r){tile(i,n,d,r);}
    void port(){EditText e=input(""+pref.getInt("port",8080));new AlertDialog.Builder(this).setTitle("Port").setView(e).setNegativeButton("Cancel",null).setPositiveButton("Save",(d,w)->{try{int p=Integer.parseInt(e.getText().toString());if(p>=1024&&p<=65535)pref.edit().putInt("port",p).apply();}catch(Exception ignored){}settings();}).show();}
    void security(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);l.setPadding(dp(8),0,dp(8),0);EditText pass=input(pref.getString("password",""));pass.setHint("Web password");EditText allow=input(pref.getString("allowIps",""));allow.setHint("Allow IPs, comma separated");EditText block=input(pref.getString("blockIps",""));block.setHint("Block IPs, comma separated");EditText rate=input(""+pref.getInt("rate",120));rate.setHint("Requests/min/IP");l.addView(pass);l.addView(allow);l.addView(block);l.addView(rate);new AlertDialog.Builder(this).setTitle("🛡 Security").setView(l).setNegativeButton("Cancel",null).setPositiveButton("Save",(d,w)->pref.edit().putString("password",pass.getText().toString()).putString("allowIps",allow.getText().toString()).putString("blockIps",block.getText().toString()).putInt("rate",parse(rate,120)).apply()).show();}
    int parse(EditText e,int d){try{return Integer.parseInt(e.getText().toString());}catch(Exception x){return d;}}
    void theme(){String[] n={"🌑 Midnight","🌊 Ocean","💜 Violet","💚 Emerald","🌅 Sunset","⚡ Neon"};new AlertDialog.Builder(this).setTitle("🎨 Theme").setSingleChoiceItems(n,pref.getInt("theme",0),(d,w)->{pref.edit().putInt("theme",w).apply();d.dismiss();buildApp();}).show();}
    void network(){new AlertDialog.Builder(this).setTitle("🌐 Network Info").setMessage("Local Server URL\n"+WebServerService.currentUrl(this)+"\n\n"+WebServerService.networkInfo(this)).setPositiveButton("OK",null).show();}
    void copyUrl(){((android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("Server URL",WebServerService.currentUrl(this)));toast("URL copied");}
    void openBrowser(){startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(WebServerService.currentUrl(this))));}
    void shareText(String s){Intent i=new Intent(Intent.ACTION_SEND);i.setType("text/plain");i.putExtra(Intent.EXTRA_TEXT,s);startActivity(Intent.createChooser(i,"Share Server URL"));}
    void messenger(){try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("https://m.me/Salam.864")));}catch(Exception ignored){}}
    void about(){new AlertDialog.Builder(this).setTitle("Salam Web Server").setMessage("Version 10.0\n\nDeveloped by Abdus Salam\n09696590864\nsalam230864@gmail.com\n\nFast • Secure • Local\nNative Android control panel").setPositiveButton("OK",null).show();}
    void toast(Object x){Toast.makeText(this,String.valueOf(x),Toast.LENGTH_LONG).show();}

    static class Wave extends View{
        Paint p=new Paint(1);Path path=new Path();Wave(Context c){super(c);}
        protected void onDraw(Canvas c){int w=getWidth(),h=getHeight();int theme=getContext().getSharedPreferences("server",Context.MODE_PRIVATE).getInt("theme",0);int a=Color.rgb(3,12,25),b=Color.rgb(7,27,51),line=Color.rgb(10,112,240);if(theme==1){a=Color.rgb(2,18,32);b=Color.rgb(4,55,78);line=CYAN;}else if(theme==2){a=Color.rgb(12,5,28);b=Color.rgb(45,12,78);line=Color.rgb(190,80,255);}else if(theme==3){a=Color.rgb(3,22,19);b=Color.rgb(5,60,48);line=GREEN;}else if(theme==4){a=Color.rgb(28,10,5);b=Color.rgb(70,25,10);line=Color.rgb(255,135,35);}else if(theme==5){a=Color.rgb(3,7,24);b=Color.rgb(15,35,80);line=Color.rgb(60,120,255);}p.setShader(new LinearGradient(0,0,w,h,a,b,Shader.TileMode.CLAMP));c.drawRect(0,0,w,h,p);p.setShader(null);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2);p.setColor(line);float d=getResources().getDisplayMetrics().density;for(int j=0;j<3;j++){path.reset();for(int x=0;x<=w;x+=10){float y=h*.72f+j*d*18+(float)Math.sin(x*.013+j)*d*19;if(x==0)path.moveTo(x,y);else path.lineTo(x,y);}c.drawPath(path,p);}p.setStyle(Paint.Style.FILL);}
    }
}