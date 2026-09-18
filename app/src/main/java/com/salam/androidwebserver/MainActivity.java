package com.salam.androidwebserver;

import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.net.*;
import android.net.Uri;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.*;
import java.util.zip.*;

public class MainActivity extends Activity {
    static final int BG=Color.rgb(2,10,21), CARD=Color.rgb(7,25,45), CARD2=Color.rgb(9,33,57);
    static final int WHITE=Color.WHITE, MUTED=Color.rgb(145,173,202), GREEN=Color.rgb(45,238,135);
    static final int RED=Color.rgb(255,62,86), CYAN=Color.rgb(25,213,255), BLUE=Color.rgb(76,83,255);
    static final int PURPLE=Color.rgb(190,78,255), YELLOW=Color.rgb(255,205,48), ORANGE=Color.rgb(255,137,35);
    static final int[] LEDS={GREEN,CYAN,PURPLE,YELLOW,WHITE,ORANGE,RED};

    Handler h=new Handler(Looper.getMainLooper()); LinearLayout body,bottom; TextView pageTitle;
    int page=0, theme=0; String cwd="/"; Runnable refreshTask;

    int accent(){return new int[]{CYAN,PURPLE,GREEN,ORANGE}[theme];}
    int accent2(){return new int[]{BLUE,Color.rgb(93,64,255),Color.rgb(0,177,146),RED}[theme];}
    int dp(float x){return (int)(x*getResources().getDisplayMetrics().density+.5f);}
    GradientDrawable solid(int c,float r){GradientDrawable g=new GradientDrawable();g.setColor(c);g.setCornerRadius(dp(r));return g;}
    GradientDrawable gradient(float r){GradientDrawable g=new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,new int[]{accent(),accent2()});g.setCornerRadius(dp(r));return g;}
    TextView t(String s,float z,int c){TextView v=new TextView(this);v.setText(s);v.setTextSize(z);v.setTextColor(c);return v;}
    TextView button(String s){TextView v=t(s,13,WHITE);v.setGravity(Gravity.CENTER);v.setTypeface(null,1);v.setBackground(gradient(18));v.setPadding(dp(8),0,dp(8),0);return v;}
    LinearLayout card(){LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setPadding(dp(14),dp(12),dp(14),dp(12));c.setBackground(gradientCard());LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(dp(3),dp(5),dp(3),dp(5));c.setLayoutParams(p);return c;}
    GradientDrawable gradientCard(){GradientDrawable g=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{CARD,CARD2});g.setCornerRadius(dp(22));g.setStroke(dp(1),Color.rgb(13,53,82));return g;}

    @Override public void onCreate(Bundle b){
        super.onCreate(b); getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);
        theme=getPreferences(0).getInt("theme",0); shell(); startRefresh();
    }
    void shell(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(BG);
        LinearLayout head=new LinearLayout(this);head.setGravity(Gravity.CENTER_VERTICAL);head.setPadding(dp(12),dp(5),dp(8),0);
        TextView logo=t("⚡",29,accent());logo.setGravity(Gravity.CENTER);head.addView(logo,new LinearLayout.LayoutParams(dp(42),dp(55)));
        pageTitle=t("Salam Web Server",20,WHITE);pageTitle.setTypeface(null,1);head.addView(pageTitle,new LinearLayout.LayoutParams(0,dp(55),1));
        TextView menu=t("☰",27,accent());menu.setGravity(Gravity.CENTER);menu.setOnClickListener(v->menu());head.addView(menu,new LinearLayout.LayoutParams(dp(48),dp(55)));root.addView(head);
        ScrollView sc=new ScrollView(this);sc.setFillViewport(true);body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(7),0,dp(7),dp(15));sc.addView(body);root.addView(sc,new LinearLayout.LayoutParams(-1,0,1));
        bottom=new LinearLayout(this);bottom.setPadding(dp(4),dp(5),dp(4),dp(5));bottom.setBackground(solid(Color.rgb(4,20,37),24));
        nav("⌂","Home",0);nav("▣","Files",1);nav("≡","Logs",2);nav("⚙","Settings",3);root.addView(bottom,new LinearLayout.LayoutParams(-1,dp(72)));
        setContentView(root); show(0);
    }
    void nav(String ic,String name,int p){LinearLayout x=new LinearLayout(this);x.setOrientation(LinearLayout.VERTICAL);x.setGravity(Gravity.CENTER);TextView a=t(ic,23,p==page?accent():MUTED);a.setGravity(Gravity.CENTER);TextView b=t(name,10,p==page?accent():MUTED);b.setGravity(Gravity.CENTER);x.addView(a,new LinearLayout.LayoutParams(-1,dp(33)));x.addView(b,new LinearLayout.LayoutParams(-1,dp(23)));x.setOnClickListener(v->show(p));bottom.addView(x,new LinearLayout.LayoutParams(0,dp(62),1));}
    void show(int p){page=p;body.removeAllViews();if(p==0)home();else if(p==1)files();else if(p==2)logs();else settings();pageTitle.setText(p==0?"Salam Web Server":p==1?"Web Files":p==2?"Server Logs":"Server Settings");refreshNav();}
    void refreshNav(){if(bottom==null)return;for(int i=0;i<bottom.getChildCount();i++){View v=bottom.getChildAt(i);if(v instanceof LinearLayout){TextView a=(TextView)((LinearLayout)v).getChildAt(0),b=(TextView)((LinearLayout)v).getChildAt(1);a.setTextColor(i==page?accent():MUTED);b.setTextColor(i==page?accent():MUTED);}}}

    void home(){
        ServerCard sc=new ServerCard(this);body.addView(sc,new LinearLayout.LayoutParams(-1,dp(250)));
        LinearLayout q=new LinearLayout(this);q.addView(tile("▣","Web Files",()->show(1)),new LinearLayout.LayoutParams(0,dp(98),1));q.addView(tile("◎","Network",()->network()),new LinearLayout.LayoutParams(0,dp(98),1));body.addView(q);
        LinearLayout q2=new LinearLayout(this);q2.addView(tile("≡","Server Logs",()->show(2)),new LinearLayout.LayoutParams(0,dp(98),1));q2.addView(tile("♙","Security",()->security()),new LinearLayout.LayoutParams(0,dp(98),1));body.addView(q2);
        LinearLayout m=card();TextView hd=t("▥   LIVE MONITORING",17,accent());hd.setTypeface(null,1);m.addView(hd);TextView metrics=t("",13,WHITE);metrics.setPadding(0,dp(5),0,0);m.addView(metrics);body.addView(m);
        TextView net=t("",12,MUTED);net.setPadding(dp(10),dp(3),dp(10),dp(12));body.addView(net);
        Runnable r=new Runnable(){public void run(){if(metrics.getParent()==null)return;metrics.setText("CPU        "+WebServerService.cpuText()+"\nRAM        "+WebServerService.memoryText(MainActivity.this)+"\nStorage    "+WebServerService.storageText(MainActivity.this)+"\nClients    "+WebServerService.clients.size()+"\nRequests   "+WebServerService.requests.get()+"\nTraffic    "+WebServerService.trafficText()+"\nUptime     "+WebServerService.uptime());net.setText("◎  "+WebServerService.networkInfo(MainActivity.this)+"\n   URL: "+WebServerService.currentUrl(MainActivity.this));h.postDelayed(this,900);}};r.run();
    }
    LinearLayout tile(String ic,String name,Runnable run){LinearLayout x=new LinearLayout(this);x.setOrientation(LinearLayout.VERTICAL);x.setGravity(Gravity.CENTER);x.setBackground(gradientCard());TextView i=t(ic,31,iconColor(name));i.setGravity(Gravity.CENTER);TextView n=t(name,12,WHITE);n.setGravity(Gravity.CENTER);n.setTypeface(null,1);x.addView(i,new LinearLayout.LayoutParams(-1,dp(55)));x.addView(n,new LinearLayout.LayoutParams(-1,dp(30)));x.setOnClickListener(v->run.run());LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(98),1);p.setMargins(dp(4),dp(4),dp(4),dp(4));x.setLayoutParams(p);return x;}
    int iconColor(String n){if(n.contains("Files"))return YELLOW;if(n.contains("Network"))return CYAN;if(n.contains("Logs"))return ORANGE;if(n.contains("Security"))return RED;return accent();}

    class ServerCard extends ViewGroup {
        Paint p=new Paint(3); long sequenceStart=0; boolean last=false; TextView state,link,toggle;
        ServerCard(Context c){super(c);setWillNotDraw(false);setBackground(gradientCard());state=t("SERVER OFFLINE",18,RED);state.setGravity(Gravity.CENTER);state.setTypeface(null,1);addView(state);
            link=t(WebServerService.currentUrl(MainActivity.this),13,accent());link.setGravity(Gravity.CENTER);link.setBackground(solid(Color.rgb(3,19,37),16));addView(link);
            toggle=button("▶  START SERVER");addView(toggle);toggle.setOnClickListener(v->{if(WebServerService.running)stop();else start();});
            postDelayed(this::tick,100);
        }
        void tick(){boolean on=WebServerService.running;if(on&&!last)sequenceStart=System.currentTimeMillis();if(!on&&!last){}last=on;state.setText(on?"SERVER ONLINE":"SERVER OFFLINE");state.setTextColor(on?GREEN:RED);link.setText(WebServerService.currentUrl(MainActivity.this));toggle.setText(on?"■  STOP SERVER":"▶  START SERVER");toggle.setBackground(on?gradient(18):solid(Color.rgb(62,17,29),18));invalidate();postDelayed(this::tick,180);}
        @Override protected void onDraw(Canvas c){super.onDraw(c);float w=getWidth(),y=dp(38),gap=w/8f;long age=System.currentTimeMillis()-sequenceStart;int active=last?Math.min(7,(int)(age/170)+1):0;for(int i=0;i<7;i++){float x=gap*(i+1);int col=(!last?(i==6?RED:Color.rgb(38,49,62)):(i<active?LEDS[i]:Color.rgb(38,58,72)));p.setColor(col);p.setShadowLayer(last&&i<active?dp(11):(!last&&i==6?dp(5):0),0,0,col);c.drawCircle(x,y,dp(9),p);p.clearShadowLayer();if(last&&i<active){p.setColor(Color.argb(75,LEDS[i]));p.setStyle(Paint.Style.FILL);c.drawCircle(x,y,dp(16),p);}}if(last)postInvalidateDelayed(70);}
        @Override protected void onLayout(boolean c,int l,int top,int r,int b){int w=r-l;state.layout(0,dp(72),w,dp(115));link.layout(dp(12),dp(122),w-dp(12),dp(171));toggle.layout(dp(12),dp(181),w-dp(12),dp(235));}
        @Override protected void onMeasure(int ws,int hs){int w=MeasureSpec.getSize(ws);state.measure(MeasureSpec.makeMeasureSpec(w,1073741824),MeasureSpec.makeMeasureSpec(dp(43),1073741824));link.measure(MeasureSpec.makeMeasureSpec(w-dp(24),1073741824),MeasureSpec.makeMeasureSpec(dp(49),1073741824));toggle.measure(MeasureSpec.makeMeasureSpec(w-dp(24),1073741824),MeasureSpec.makeMeasureSpec(dp(54),1073741824));setMeasuredDimension(w,dp(250));}
    }

    void start(){toast("Starting Server");server("START");}
    void stop(){toast("Stopping Server");server("STOP");}
    void server(String a){Intent i=new Intent(this,WebServerService.class).setAction(a);try{if(Build.VERSION.SDK_INT>=26&&a.equals("START"))startForegroundService(i);else startService(i);}catch(Exception e){toast(e.getMessage());}}
    void files(){body.addView(t("▣  WEB FILE MANAGER",20,WHITE));LinearLayout a=new LinearLayout(this);String[] b={"＋ FILE","＋ FOLDER","UPLOAD","ZIP"};for(String s:b){TextView x=button(s);a.addView(x,new LinearLayout.LayoutParams(0,dp(46),1));if(s.equals("＋ FILE"))x.setOnClickListener(v->newFile());if(s.equals("＋ FOLDER"))x.setOnClickListener(v->newFolder());if(s.equals("UPLOAD"))x.setOnClickListener(v->pick());if(s.equals("ZIP"))x.setOnClickListener(v->zipMenu());}body.addView(a);body.addView(t("Path: "+cwd,11,MUTED));File d=new File(WebServerService.webRoot(this),cwd);File[] fs=d.listFiles();if(fs==null){body.addView(t("Folder unavailable",13,RED));return;}Arrays.sort(fs,(x,y)->x.isDirectory()!=y.isDirectory()?(x.isDirectory()?-1:1):x.getName().compareToIgnoreCase(y.getName()));if(!cwd.equals("/"))row("‹","..","Parent",()->{int k=cwd.lastIndexOf('/');cwd=k<=0?"/":cwd.substring(0,k);show(1);});for(File f:fs)row(f.isDirectory()?"▰":"▤",f.getName(),f.isDirectory()?"Folder":"File • "+size(f.length()),()->fileMenu(f));}
    void row(String ic,String name,String sub,Runnable run){LinearLayout c=card();c.setOrientation(LinearLayout.HORIZONTAL);TextView i=t(ic,25,iconColor(name));i.setGravity(Gravity.CENTER);c.addView(i,new LinearLayout.LayoutParams(dp(45),dp(55)));LinearLayout m=new LinearLayout(this);m.setOrientation(LinearLayout.VERTICAL);TextView n=t(name,14,WHITE);n.setTypeface(null,1);m.addView(n,new LinearLayout.LayoutParams(-1,dp(30)));m.addView(t(sub,10,MUTED),new LinearLayout.LayoutParams(-1,dp(22)));c.addView(m,new LinearLayout.LayoutParams(0,dp(55),1));TextView ar=t("›",30,accent());ar.setGravity(Gravity.CENTER);c.addView(ar,new LinearLayout.LayoutParams(dp(30),dp(55)));c.setOnClickListener(v->run.run());body.addView(c);}
    String size(long n){if(n<1024)return n+" B";if(n<1048576)return n/1024+" KB";if(n<1073741824L)return n/1048576+" MB";return String.format(Locale.US,"%.1f GB",n/1073741824d);}
    void fileMenu(File f){String[] a=f.isDirectory()?new String[]{"Open","Rename","Copy","Move","ZIP","Delete"}:new String[]{"Edit","Rename","Copy","Move","ZIP","Download","Delete"};new AlertDialog.Builder(this).setTitle(f.getName()).setItems(a,(d,w)->{String s=a[w];if(s.equals("Open")){cwd=(cwd.equals("/")?"/":cwd+"/")+f.getName();show(1);}else if(s.equals("Edit"))edit(f);else if(s.equals("Rename"))rename(f);else if(s.equals("Copy"))copyMove(f,false);else if(s.equals("Move"))copyMove(f,true);else if(s.equals("ZIP"))zipOne(f);else if(s.equals("Download"))toast("Open the file URL from the browser control panel");else deleteConfirm(f);}).show();}
    void edit(File f){EditText e=new EditText(this);e.setGravity(Gravity.TOP);e.setMinLines(16);try{e.setText(WebServerService.readText(f));}catch(Exception x){toast(x.getMessage());}new AlertDialog.Builder(this).setTitle("Editor").setView(e).setNegativeButton("CANCEL",null).setPositiveButton("SAVE",(d,w)->{try{WebServerService.writeText(f,e.getText().toString());toast("Saved");}catch(Exception x){toast(x.getMessage());}}).show();}
    void rename(File f){EditText e=new EditText(this);e.setText(f.getName());new AlertDialog.Builder(this).setTitle("Rename").setView(e).setNegativeButton("CANCEL",null).setPositiveButton("SAVE",(d,w)->{File n=new File(f.getParent(),e.getText().toString().replace("/","_"));if(!f.renameTo(n))toast("Rename failed");show(1);}).show();}
    void copyMove(File f,boolean move){EditText e=new EditText(this);e.setText(cwd);new AlertDialog.Builder(this).setTitle(move?"Move to folder":"Copy to folder").setView(e).setNegativeButton("CANCEL",null).setPositiveButton("OK",(d,w)->{try{File dst=new File(WebServerService.webRoot(this),e.getText().toString());dst.mkdirs();WebServerService.copyRecursive(f,new File(dst,f.getName()));if(move)WebServerService.deleteRecursive(f);toast(move?"Moved":"Copied");show(1);}catch(Exception x){toast(x.getMessage());}}).show();}
    void deleteConfirm(File f){new AlertDialog.Builder(this).setTitle("Delete "+f.getName()+"?").setMessage("This cannot be undone.").setNegativeButton("CANCEL",null).setPositiveButton("DELETE",(d,w)->{WebServerService.deleteRecursive(f);show(1);}).show();}
    void newFolder(){nameDialog("New Folder",true);}void newFile(){nameDialog("New File",false);}
    void nameDialog(String title,boolean dir){EditText e=new EditText(this);e.setHint("name");new AlertDialog.Builder(this).setTitle(title).setView(e).setNegativeButton("CANCEL",null).setPositiveButton("CREATE",(d,w)->{File f=new File(WebServerService.webRoot(this),cwd+"/"+e.getText().toString());try{if(dir)f.mkdirs();else f.createNewFile();show(1);}catch(Exception x){toast(x.getMessage());}}).show();}
    void pick(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("*/*");i.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,22);}
    @Override protected void onActivityResult(int r,int c,Intent d){super.onActivityResult(r,c,d);if(c!=RESULT_OK||d==null)return;
        try{
            if(r==22){
                InputStream in=getContentResolver().openInputStream(d.getData());
                String name=String.valueOf(d.getData().getLastPathSegment()).replaceAll("[^a-zA-Z0-9._-]","_");
                File f=new File(WebServerService.webRoot(this),cwd+"/"+name);
                FileOutputStream o=new FileOutputStream(f);byte[] b=new byte[16384];int n;while((n=in.read(b))>0)o.write(b,0,n);in.close();o.close();toast("Uploaded");show(1);
            }else if(r==23){
                InputStream in=getContentResolver().openInputStream(d.getData());
                String name=String.valueOf(d.getData().getLastPathSegment()).replaceAll("[^a-zA-Z0-9._-]","_");
                File z=new File(WebServerService.webRoot(this),cwd+"/"+name);FileOutputStream o=new FileOutputStream(z);byte[] b=new byte[16384];int n;while((n=in.read(b))>0)o.write(b,0,n);in.close();o.close();
                WebServerService.unzip(z,new File(WebServerService.webRoot(this),cwd));toast("ZIP extracted");show(1);
            }
        }catch(Exception e){toast(e.getMessage());}}
    void zipMenu(){new AlertDialog.Builder(this).setItems(new String[]{"Create ZIP from current folder","Extract a ZIP here"},(d,w)->{if(w==0)zipOne(new File(WebServerService.webRoot(this),cwd));else chooseZip();}).show();}
    void zipOne(File f){try{File z=new File(f.getParent(),f.getName()+".zip");WebServerService.zip(f,z);toast("ZIP created");}catch(Exception e){toast(e.getMessage());}}
    void chooseZip(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("application/zip");i.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,23);}
    void network(){new AlertDialog.Builder(this).setTitle("NETWORK").setMessage(WebServerService.networkInfo(this)+"\n\nWi-Fi IP: "+WebServerService.wifiIp(this)+"\nMobile IP: "+WebServerService.cellularIp(this)+"\nGateway: "+WebServerService.gateway(this)+"\nDNS: "+WebServerService.dns(this)).setPositiveButton("OK",null).show();}
    void security(){
    LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);
    EditText pass=new EditText(this);pass.setHint("Web password");EditText allow=new EditText(this);allow.setHint("Allow IP");EditText block=new EditText(this);block.setHint("Block IP");EditText rate=new EditText(this);rate.setHint("Requests/minute");
    box.addView(pass);box.addView(allow);box.addView(block);box.addView(rate);
    new AlertDialog.Builder(this).setTitle("Security & Access Control").setView(box)
      .setPositiveButton("SAVE",(d,w)->{WebServerService.saveSecurity(this,pass.getText().toString(),allow.getText().toString(),block.getText().toString(),rate.getText().toString());toast("Security state saved");})
      .setNeutralButton("UNBLOCK IP",(d,w)->unblockDialog()).setNegativeButton("CANCEL",null).show();
}
void unblockDialog(){
    EditText e=new EditText(this);e.setHint("192.168.0.10");
    new AlertDialog.Builder(this).setTitle("Unblock IP").setView(e).setNegativeButton("CANCEL",null)
      .setPositiveButton("UNBLOCK",(d,w)->{WebServerService.unblockIp(e.getText().toString());toast("IP unblocked");}).show();
}
    void logs(){body.addView(t("≡  LIVE SERVER LOGS",20,WHITE));TextView hist=t("",11,MUTED);body.addView(hist);Runnable r=new Runnable(){public void run(){if(hist.getParent()==null)return;StringBuilder b=new StringBuilder();for(String s:WebServerService.LOGS)b.append(s).append("\n");b.append("\n--- ACCESS HISTORY ---\n");for(String s:WebServerService.HISTORY)b.append(s).append("\n");hist.setText(b.toString());h.postDelayed(this,700);}};r.run();TextView cl=button("CLEAR LOGS & HISTORY");body.addView(cl,new LinearLayout.LayoutParams(-1,dp(48)));cl.setOnClickListener(v->{WebServerService.LOGS.clear();WebServerService.HISTORY.clear();});}
    void settings(){body.addView(t("V10 CONTROL CENTER",12,accent()));row("🎨","Themes","Cards, LEDs, buttons, waves and navigation",()->themeDialog());row("🔐","Security","Login, password, allowlist, blocklist, unblock",()->security());row("⚡","Rate / Client Limit","Request and connection limits",()->limits());row("🔗","Custom URL / Hostname","Server URL display and browser hostname",()->host());row("◎","Network","Wi-Fi, mobile, gateway, DNS",()->network());row("▣","Browser Control","Open the full browser-side panel",()->browser());row("ⓘ","Developer","Abdus Salam • v10.0",()->about());}
    void themeDialog(){String[] a={"Ocean Neon","Purple Night","Emerald","Sunset"};new AlertDialog.Builder(this).setTitle("THEMES").setItems(a,(d,w)->{theme=w;getPreferences(0).edit().putInt("theme",w).apply();shell();toast(a[w]+" theme applied");}).show();}
    void limits(){EditText e=new EditText(this);e.setHint("Max clients");new AlertDialog.Builder(this).setTitle("Client limit").setView(e).setPositiveButton("SAVE",(d,w)->{try{WebServerService.maxClients=Integer.parseInt(e.getText().toString());}catch(Exception ignored){}}).setNegativeButton("CANCEL",null).show();}
    void host(){EditText e=new EditText(this);e.setHint("salam.local");new AlertDialog.Builder(this).setTitle("Custom hostname").setMessage("A custom hostname requires DNS/mDNS for other devices; the app stores and uses it as the advertised URL.").setView(e).setPositiveButton("SAVE",(d,w)->WebServerService.customHost=e.getText().toString().trim()).setNegativeButton("CANCEL",null).show();}
    void browser(){startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(WebServerService.currentUrl(this)+"/__salam__/")));} 
    void qr(){copy(WebServerService.currentUrl(this));}
    void about(){new AlertDialog.Builder(this).setTitle("Salam Web Server v10.0").setMessage("Developer: Abdus Salam\nPhone: 09696590864\nEmail: salam230864@gmail.com").setPositiveButton("Messenger",(d,w)->{try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("https://m.me/Salam.864")));}catch(Exception ignored){}}).setNegativeButton("CLOSE",null).show();}
    void messenger(){try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("https://m.me/Salam.864")));}catch(Exception ignored){}}
    void menu(){new AlertDialog.Builder(this).setItems(new String[]{"Restart Server","Open Browser Control","Developer"},(d,w)->{if(w==0){stop();h.postDelayed(this::start,700);}else if(w==1)browser();else about();}).show();}
    void copy(String s){android.content.ClipboardManager c=(android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);c.setPrimaryClip(ClipData.newPlainText("Server URL",s));toast("URL copied");}
    String cpu(){return WebServerService.cpuText();}
    String storage(){return WebServerService.storageText(this);}
    void startRefresh(){refreshTask=new Runnable(){public void run(){if(page==0&&WebServerService.running){}h.postDelayed(this,1000);}};h.post(refreshTask);}
    void toast(String s){Toast.makeText(this,s,Toast.LENGTH_SHORT).show();}
    @Override protected void onDestroy(){if(refreshTask!=null)h.removeCallbacks(refreshTask);super.onDestroy();}
}