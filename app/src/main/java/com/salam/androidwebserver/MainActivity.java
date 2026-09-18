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
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.*;
import java.io.*;
import java.util.*;

public class MainActivity extends Activity {
    // MASTER visual system taken from the supplied Salam Web Server reference:
    // dark navy background, electric blue/cyan gradients, rounded cards, wave footer,
    // compact header, 2x2 quick-action grid and four-item bottom navigation.
    static final int BG=Color.rgb(3,13,27), SURFACE=Color.rgb(7,25,48), SURFACE2=Color.rgb(9,31,57);
    static final int BLUE=Color.rgb(16,139,255), CYAN=Color.rgb(27,207,255), GREEN=Color.rgb(21,229,163);
    static final int WHITE=Color.rgb(246,249,255), MUTED=Color.rgb(154,181,213), RED=Color.rgb(255,86,100);
    static final int SCREEN_SPLASH=0, SCREEN_HOME=1, SCREEN_FILES=2, SCREEN_SETTINGS=3;
    static final int SCREEN_NETWORK=4, SCREEN_LOGS=5, SCREEN_ABOUT=6, SCREEN_BROWSER=7;

    SharedPreferences pref;
    FrameLayout root;
    LinearLayout body;
    TextView title;
    Handler handler=new Handler(Looper.getMainLooper());
    int screen=SCREEN_HOME;
    String folder="/";
    File pending;

    int dp(float x){return (int)(x*getResources().getDisplayMetrics().density+.5f);}
    TextView tv(String s,float size,int color){TextView v=new TextView(this);v.setText(s);v.setTextSize(size);v.setTextColor(color);return v;}
TextView t(String s,float size,int color){return tv(s,size,color);}
    GradientDrawable gradient(int a,int b,float radius){GradientDrawable g=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{a,b});g.setCornerRadius(dp(radius));return g;}
    LinearLayout card(){LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setPadding(dp(14),dp(12),dp(14),dp(12));c.setBackground(gradient(SURFACE,SURFACE2,20));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,dp(5),0,dp(5));c.setLayoutParams(p);return c;}
    Button blueButton(String s){Button b=new Button(this);b.setText(s);b.setTextSize(13);b.setTextColor(WHITE);b.setAllCaps(false);b.setGravity(Gravity.CENTER);b.setBackground(gradient(BLUE,CYAN,18));return b;}
    void add(ViewGroup p,View v,int w,int h){p.addView(v,new LinearLayout.LayoutParams(w,dp(h)));}

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);
        pref=getSharedPreferences("server",MODE_PRIVATE);
        if(pref.getBoolean("autostart",false)){
            handler.postDelayed(()->server(true),950);
        }
        if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},20);
        splash();
    }

    void splash(){
        root=new FrameLayout(this);root.setBackgroundColor(BG);root.addView(new Wave(this));
        LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setGravity(Gravity.CENTER);c.setPadding(dp(30),0,dp(30),0);
        Space top=new Space(this);c.addView(top,new LinearLayout.LayoutParams(1,0,1));
        TextView logo=tv("▰\n▰\n▰",25,CYAN);logo.setGravity(Gravity.CENTER);logo.setTypeface(null,1);add(c,logo,-1,105);
        TextView a=tv("Salam",40,BLUE);a.setTypeface(null,1);a.setGravity(Gravity.CENTER);add(c,a,-1,52);
        TextView b=tv("Web Server",29,WHITE);b.setTypeface(null,1);b.setGravity(Gravity.CENTER);add(c,b,-1,45);
        TextView sub=tv("Fast  •  Secure  •  Local",14,MUTED);sub.setGravity(Gravity.CENTER);add(c,sub,-1,42);
        TextView made=tv("Made with ♥ by Salam",12,MUTED);made.setGravity(Gravity.CENTER);add(c,made,-1,45);
        TextView badge=tv("Local Web Server for Android",10,MUTED);badge.setGravity(Gravity.CENTER);badge.setBackground(gradient(Color.TRANSPARENT,Color.TRANSPARENT,20));add(c,badge,-1,38);
        Space bottom=new Space(this);c.addView(bottom,new LinearLayout.LayoutParams(1,0,1));
        root.addView(c,new FrameLayout.LayoutParams(-1,-1));setContentView(root);
        handler.postDelayed(()->{if(!isFinishing())appShell();},850);
    }

    void appShell(){
        root=new FrameLayout(this);root.addView(new Wave(this));
        LinearLayout shell=new LinearLayout(this);shell.setOrientation(LinearLayout.VERTICAL);shell.setPadding(dp(12),0,dp(12),0);

        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);
        TextView mark=tv("▬",25,CYAN);mark.setGravity(Gravity.CENTER);add(header,mark,40,54);
        title=tv("Salam Web Server",18,WHITE);title.setTypeface(null,1);title.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(title,new LinearLayout.LayoutParams(0,54,1));
        TextView menu=tv("☰",28,WHITE);menu.setGravity(Gravity.CENTER);menu.setOnClickListener(v->menuDialog());add(header,menu,46,54);
        shell.addView(header,new LinearLayout.LayoutParams(-1,58));

        body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.addView(body);
        shell.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));

        LinearLayout nav=new LinearLayout(this);nav.setGravity(Gravity.CENTER);nav.setPadding(0,dp(2),0,dp(2));nav.setBackground(gradient(Color.rgb(3,15,29),Color.rgb(7,24,44),18));
        addNav(nav,"⌂","Home",SCREEN_HOME);addNav(nav,"▱","Files",SCREEN_FILES);addNav(nav,"≡","Logs",SCREEN_LOGS);addNav(nav,"⚙","Settings",SCREEN_SETTINGS);
        shell.addView(nav,new LinearLayout.LayoutParams(-1,dp(68)));
        root.addView(shell);setContentView(root);show(SCREEN_HOME);refreshLoop();
    }

    void addNav(LinearLayout nav,String icon,String label,int target){
        TextView n=tv(icon+"\n"+label,11,target==screen?CYAN:MUTED);n.setGravity(Gravity.CENTER);n.setOnClickListener(v->show(target));nav.addView(n,new LinearLayout.LayoutParams(0,dp(64),1));
    }

    void show(int s){
        screen=s;body.removeAllViews();
        if(s==SCREEN_HOME)home(); else if(s==SCREEN_FILES)files(); else if(s==SCREEN_SETTINGS)settings();
        else if(s==SCREEN_NETWORK)network(); else if(s==SCREEN_LOGS)logs(); else if(s==SCREEN_ABOUT)aboutPage();
        else if(s==SCREEN_BROWSER)browserPage();
        title.setText(s==SCREEN_HOME?"Salam Web Server":s==SCREEN_FILES?"Web Files":s==SCREEN_SETTINGS?"Server Settings":s==SCREEN_NETWORK?"Network Info":s==SCREEN_LOGS?"Server Logs":s==SCREEN_ABOUT?"About":"Web Preview");
    }

    void home(){
        LinearLayout statusCard=card();
        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);
        TextView st=tv(WebServerService.running?"Server Running":"Server Stopped",18,WebServerService.running?GREEN:RED);st.setTypeface(null,1);
        top.addView(st,new LinearLayout.LayoutParams(0,42,1));
        TextView dot=tv("●",31,WebServerService.running?GREEN:RED);dot.setGravity(Gravity.CENTER);add(top,dot,40,42);
        statusCard.addView(top);
        TextView u=tv(WebServerService.currentUrl(this),14,WHITE);u.setGravity(Gravity.CENTER_VERTICAL);u.setPadding(dp(14),0,dp(10),0);u.setBackground(gradient(Color.rgb(3,16,32),Color.rgb(6,26,48),15));add(statusCard,u,-1,54);
        LinearLayout actions=new LinearLayout(this);Button open=blueButton("🌐  Open Browser"),copy=blueButton("▣  Copy URL");
        open.setOnClickListener(v->{openBrowser();show(SCREEN_BROWSER);});copy.setOnClickListener(v->copyUrl());
        actions.addView(open,new LinearLayout.LayoutParams(0,dp(48),1));actions.addView(copy,new LinearLayout.LayoutParams(0,dp(48),1));statusCard.addView(actions);body.addView(statusCard);

        TextView metrics=tv("",12,WHITE);metrics.setGravity(Gravity.CENTER_VERTICAL);metrics.setPadding(dp(14),0,dp(8),0);metrics.setBackground(gradient(SURFACE,SURFACE2,18));add(body,metrics,-1,70);
        metrics.setText((WebServerService.running?"● Online":"● Offline")+"     Requests: "+WebServerService.requests+"     Clients: "+WebServerService.clients.size()+"\nUptime: "+WebServerService.uptime()+"     RAM: "+WebServerService.memoryText(this));
Button serverToggle=blueButton(WebServerService.running?"■  Stop Server":"▶  Start Server");
        serverToggle.setOnClickListener(v->{server(!WebServerService.running);handler.postDelayed(()->show(SCREEN_HOME),350);});
        body.addView(serverToggle,new LinearLayout.LayoutParams(-1,dp(48)));

        section("QUICK ACTIONS");
        LinearLayout grid1=new LinearLayout(this);grid1.setOrientation(LinearLayout.HORIZONTAL);grid1.addView(actionTile("📁","Web Files",()->show(SCREEN_FILES)),new LinearLayout.LayoutParams(0,dp(96),1));grid1.addView(actionTile("⚙","Settings",()->show(SCREEN_SETTINGS)),new LinearLayout.LayoutParams(0,dp(96),1));body.addView(grid1);
        LinearLayout grid2=new LinearLayout(this);grid2.setOrientation(LinearLayout.HORIZONTAL);grid2.addView(actionTile("⌁","Network Info",()->show(SCREEN_NETWORK)),new LinearLayout.LayoutParams(0,dp(96),1));grid2.addView(actionTile("▤","Server Logs",()->show(SCREEN_LOGS)),new LinearLayout.LayoutParams(0,dp(96),1));body.addView(grid2);

        section("SERVER");
        compactTile("▣","QR Server Sharing","Share local URL",()->shareText(WebServerService.currentUrl(this)));
        compactTile("🌐","Web Preview","Open the browser-facing server page",()->show(SCREEN_BROWSER));
        compactTile("🔐","Security","Web password, IP rules and rate limit",()->security());
        section("DEVELOPER");
        compactTile("👨‍💻","About","Version 10.0 • Abdus Salam",()->show(SCREEN_ABOUT));
        compactTile("💬","Messenger","09696590864",()->messenger());
    }

    View actionTile(String icon,String name,Runnable r){
        LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setGravity(Gravity.CENTER);c.setPadding(dp(8),dp(7),dp(8),dp(7));c.setBackground(gradient(SURFACE,SURFACE2,18));TextView i=tv(icon,27,WHITE);i.setGravity(Gravity.CENTER);add(c,i,-1,43);TextView n=tv(name,12,WHITE);n.setGravity(Gravity.CENTER);add(c,n,-1,28);c.setOnClickListener(v->r.run());LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(96),1);p.setMargins(dp(3),dp(5),dp(3),dp(5));c.setLayoutParams(p);return c;
    }
    void section(String s){TextView v=tv(s,11,Color.rgb(126,174,224));v.setTypeface(null,1);v.setPadding(dp(10),dp(8),0,0);add(body,v,-1,34);}
    void compactTile(String icon,String name,String desc,Runnable r){LinearLayout c=card();c.setOrientation(LinearLayout.HORIZONTAL);TextView i=tv(icon,25,WHITE);i.setGravity(Gravity.CENTER);add(c,i,45,50);LinearLayout m=new LinearLayout(this);m.setOrientation(LinearLayout.VERTICAL);TextView n=tv(name,15,WHITE);n.setTypeface(null,1);add(m,n,-1,27);add(m,tv(desc,10,MUTED),-1,20);c.addView(m,new LinearLayout.LayoutParams(0,50,1));TextView a=tv("›",30,CYAN);a.setGravity(Gravity.CENTER);add(c,a,25,50);c.setOnClickListener(v->r.run());body.addView(c);}

    void files(){
        LinearLayout search=card();EditText q=input("");q.setHint("🔎  Search files...");q.setSingleLine(true);q.setTextColor(WHITE);q.setHintTextColor(MUTED);q.setBackground(gradient(Color.rgb(3,16,31),Color.rgb(7,27,49),15));add(search,q,-1,48);body.addView(search);
        LinearLayout actions=new LinearLayout(this);Button up=blueButton("＋ Upload"),folderBtn=blueButton("＋ Folder"),fileBtn=blueButton("＋ File");
        up.setOnClickListener(v->pick());folderBtn.setOnClickListener(v->newFolder());fileBtn.setOnClickListener(v->newFile());
        actions.addView(up,new LinearLayout.LayoutParams(0,dp(43),1));actions.addView(folderBtn,new LinearLayout.LayoutParams(0,dp(43),1));actions.addView(fileBtn,new LinearLayout.LayoutParams(0,dp(43),1));body.addView(actions);
        TextView path=tv("📁  "+folder,12,WHITE);path.setPadding(dp(10),dp(8),0,0);add(body,path,-1,34);
        File dir=new File(WebServerService.webRoot(this),folder);File[] list=dir.listFiles();if(list==null){body.addView(t("No files yet.",12,MUTED));return;}
        Arrays.sort(list,(a,b)->{if(a.isDirectory()!=b.isDirectory())return a.isDirectory()?-1:1;return a.getName().compareToIgnoreCase(b.getName());});
        if(!"/".equals(folder))compactTile("‹","..","Parent folder",()->{int x=folder.lastIndexOf('/');folder=x<=0?"/":folder.substring(0,x);show(SCREEN_FILES);});
        for(File f:list){final File z=f;String name=z.getName();if(q.getText().length()>0&&!name.toLowerCase(Locale.US).contains(q.getText().toString().toLowerCase(Locale.US)))continue;compactTile(z.isDirectory()?"📂":fileIcon(name),name,z.isDirectory()?"Folder • "+count(z)+" items":format(z.length()),()->fileMenu(z));}
        q.setOnEditorActionListener((v,a,e)->{show(SCREEN_FILES);return true;});
    }
    String fileIcon(String n){String x=n.toLowerCase(Locale.US);if(x.endsWith(".html")||x.endsWith(".htm"))return"🌐";if(x.endsWith(".css"))return"🎨";if(x.endsWith(".js"))return"🟨";if(x.endsWith(".json"))return"🧩";if(x.endsWith(".zip"))return"📦";if(x.matches(".*\\.(png|jpg|jpeg|webp)$"))return"🖼";return"📄";}
    int count(File f){File[] x=f.listFiles();return x==null?0:x.length;}
    String format(long b){if(b<1024)return b+" B";if(b<1048576)return(b/1024)+" KB";return(b/1048576)+" MB";}

    void fileMenu(File f){ArrayList<String>a=new ArrayList<>();if(f.isDirectory())a.add("📂 Open");else a.add("✏️ Edit");a.add("✏️ Rename");a.add("📋 Copy");a.add("📤 Move");a.add("⬇ Save As");a.add("🗑 Delete");new AlertDialog.Builder(this).setTitle(f.getName()).setItems(a.toArray(new String[0]),(d,w)->{String s=a.get(w);if(s.startsWith("📂")){folder=(folder.endsWith("/")?folder:folder+"/")+f.getName();show(SCREEN_FILES);}else if(s.startsWith("✏️ Edit"))edit(f);else if(s.startsWith("✏️ Rename"))rename(f);else if(s.startsWith("📋"))copyMove(f,false);else if(s.startsWith("📤"))copyMove(f,true);else if(s.startsWith("⬇"))saveAs(f);else deleteConfirm(f);}).show();}
    void edit(File f){EditText e=input("");e.setGravity(Gravity.TOP);e.setMinLines(16);try{e.setText(read(f));}catch(Exception ignored){}new AlertDialog.Builder(this).setTitle("📝 "+f.getName()).setView(e).setNegativeButton("Cancel",null).setPositiveButton("Save",(d,w)->{try{write(f,e.getText().toString());toast("File saved");}catch(Exception x){toast(x.getMessage());}}).show();}
    String read(File f)throws Exception{BufferedReader r=new BufferedReader(new FileReader(f));StringBuilder s=new StringBuilder();String x;while((x=r.readLine())!=null)s.append(x).append('\n');r.close();return s.toString();}
    void write(File f,String s)throws Exception{FileWriter w=new FileWriter(f);w.write(s);w.close();}
    void rename(File f){EditText e=input(f.getName());new AlertDialog.Builder(this).setTitle("✏️ Rename").setView(e).setNegativeButton("Cancel",null).setPositiveButton("Rename",(d,w)->{String n=e.getText().toString().trim().replace("/","_");if(!n.isEmpty())f.renameTo(new File(f.getParent(),n));show(SCREEN_FILES);}).show();}
    void copyMove(File f,boolean move){EditText e=input(folder);new AlertDialog.Builder(this).setTitle(move?"📤 Move":"📋 Copy").setView(e).setNegativeButton("Cancel",null).setPositiveButton(move?"Move":"Copy",(d,w)->{try{File dir=new File(WebServerService.webRoot(this),e.getText().toString());dir.mkdirs();copyRecursive(f,new File(dir,f.getName()));if(move)delete(f);show(SCREEN_FILES);}catch(Exception x){toast(x.getMessage());}}).show();}
    void copyRecursive(File a,File b)throws IOException{if(a.isDirectory()){b.mkdirs();File[]z=a.listFiles();if(z!=null)for(File q:z)copyRecursive(q,new File(b,q.getName()));}else{InputStream i=new FileInputStream(a);OutputStream o=new FileOutputStream(b);byte[]z=new byte[16384];int n;while((n=i.read(z))>0)o.write(z,0,n);i.close();o.close();}}
    void deleteConfirm(File f){new AlertDialog.Builder(this).setTitle("🗑 Delete").setMessage("Delete "+f.getName()+"?").setNegativeButton("Cancel",null).setPositiveButton("Delete",(d,w)->{delete(f);show(SCREEN_FILES);}).show();}
    void delete(File f){if(f.isDirectory()){File[]z=f.listFiles();if(z!=null)for(File q:z)delete(q);}f.delete();}
    void saveAs(File f){Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.setType("*/*");i.putExtra(Intent.EXTRA_TITLE,f.getName());pending=f;startActivityForResult(i,102);}
    void pick(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("*/*");i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);i.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,101);}
    @Override protected void onActivityResult(int r,int c,Intent d){super.onActivityResult(r,c,d);try{if(r==101&&c==RESULT_OK&&d!=null){if(d.getClipData()!=null)for(int k=0;k<d.getClipData().getItemCount();k++)copyUri(d.getClipData().getItemAt(k).getUri());else if(d.getData()!=null)copyUri(d.getData());show(SCREEN_FILES);}else if(r==102&&c==RESULT_OK&&d!=null&&d.getData()!=null&&pending!=null){InputStream i=new FileInputStream(pending);OutputStream o=getContentResolver().openOutputStream(d.getData());byte[]z=new byte[16384];int n;while((n=i.read(z))>0)o.write(z,0,n);i.close();o.close();pending=null;toast("Saved");}}catch(Exception e){toast(e.getMessage());}}
    void copyUri(Uri u)throws Exception{String n="upload_"+System.currentTimeMillis();String p=u.getPath();if(p!=null&&p.contains(":"))n=p.substring(p.lastIndexOf(':')+1);if(n.contains("/"))n=n.substring(n.lastIndexOf('/')+1);File o=new File(WebServerService.webRoot(this),folder+"/"+n);InputStream i=getContentResolver().openInputStream(u);OutputStream x=new FileOutputStream(o);byte[]z=new byte[8192];int k;while((k=i.read(z))>0)x.write(z,0,k);i.close();x.close();}

    void settings(){
        section("SERVER CONFIGURATION");
        settingRow("▣","Port",""+pref.getInt("port",8080),()->portDialog());
        settingRow("📁","Document Root",WebServerService.webRoot(this).getAbsolutePath(),()->toast(WebServerService.webRoot(this).getAbsolutePath()));
        settingSwitch("🚀","Auto Start","Start server on app launch","autostart");
        section("SECURITY");
        settingSwitch("🌐","Allow Local Network","Accessible from devices on your network","allowNetwork");
        settingSwitch("🔐","Password Protection","Protect website with Basic Auth","passwordOn");
        settingRow("🟢","IP Allowlist","Allow selected client IPs",()->security());
        settingRow("🔴","IP Blocklist","Block selected client IPs",()->security());
        settingRow("🚦","Request Rate Limit",pref.getInt("rate",120)+" requests/min/IP",()->security());
        settingRow("👥","Maximum Clients",""+pref.getInt("maxClients",32),()->security());
        section("ADVANCED");
        settingRow("🌐","Network Info","LAN address and interface details",()->show(SCREEN_NETWORK));
        settingRow("▣","QR Server Sharing","Share local URL",()->shareText(WebServerService.currentUrl(this)));
        settingRow("🎨","Theme","Midnight • Ocean • Violet • Emerald • Sunset",()->theme());
        section("ABOUT");
        settingRow("👨‍💻","Developer","Abdus Salam • 09696590864",()->show(SCREEN_ABOUT));
        settingRow("💬","Messenger","m.me/Salam.864",()->messenger());
    }
    void settingRow(String icon,String name,String value,Runnable r){LinearLayout c=card();c.setOrientation(LinearLayout.HORIZONTAL);TextView i=tv(icon,23,WHITE);i.setGravity(Gravity.CENTER);add(c,i,43,53);LinearLayout m=new LinearLayout(this);m.setOrientation(LinearLayout.VERTICAL);add(m,tv(name,14,WHITE),-1,27);TextView valueView=tv(value,10,MUTED);valueView.setEllipsize(android.text.TextUtils.TruncateAt.END);valueView.setSingleLine(true);add(m,valueView,-1,20);c.addView(m,new LinearLayout.LayoutParams(0,53,1));TextView a=tv("›",28,CYAN);a.setGravity(Gravity.CENTER);add(c,a,25,53);c.setOnClickListener(clickedView->r.run());body.addView(c);}
    void settingSwitch(String icon,String name,String desc,String key){LinearLayout c=card();c.setOrientation(LinearLayout.HORIZONTAL);TextView i=tv(icon,23,WHITE);i.setGravity(Gravity.CENTER);add(c,i,43,53);LinearLayout m=new LinearLayout(this);m.setOrientation(LinearLayout.VERTICAL);add(m,tv(name,14,WHITE),-1,27);add(m,tv(desc,10,MUTED),-1,20);c.addView(m,new LinearLayout.LayoutParams(0,53,1));Switch sw=new Switch(this);sw.setChecked(pref.getBoolean(key,key.equals("allowNetwork")));sw.setOnCheckedChangeListener((b,v)->pref.edit().putBoolean(key,v).apply());add(c,sw,52,53);body.addView(c);}

    void network(){
        LinearLayout c=card();add(c,tv("🌐  Local IP Address",11,MUTED),-1,25);TextView ip=tv(WebServerService.localIp(this),22,WHITE);ip.setTypeface(null,1);add(c,ip,-1,38);
        add(c,tv("Public IP Address",11,MUTED),-1,25);add(c,tv("Not detected (LAN server)",14,WHITE),-1,32);
        add(c,tv("Network Status",11,MUTED),-1,25);add(c,tv("● Connected",14,GREEN),-1,32);
        add(c,tv("Server URL",11,MUTED),-1,25);add(c,tv(WebServerService.currentUrl(this),14,CYAN),-1,35);
        add(c,tv("Interface details\n"+WebServerService.networkInfo(this),11,WHITE),-1,180);body.addView(c);
        compactTile("▣","Copy Server URL","Copy the LAN address",()->copyUrl());
        compactTile("⌁","Share Server URL","Send address to another device",()->shareText(WebServerService.currentUrl(this)));
    }

    void logs(){
        LinearLayout buttons=new LinearLayout(this);Button clear=blueButton("🗑 Clear Log"),refresh=blueButton("↻ Refresh");clear.setOnClickListener(v->{WebServerService.clearLogs();logs();});refresh.setOnClickListener(v->logs());buttons.addView(clear,new LinearLayout.LayoutParams(0,dp(44),1));buttons.addView(refresh,new LinearLayout.LayoutParams(0,dp(44),1));body.addView(buttons);
        LinearLayout c=card();TextView l=tv(WebServerService.logsText(),11,WHITE);l.setGravity(Gravity.TOP);l.setPadding(dp(4),dp(7),dp(4),dp(7));c.addView(l,new LinearLayout.LayoutParams(-1,dp(590)));body.addView(c);
    }

    void aboutPage(){
        LinearLayout c=card();c.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView logo=tv("▰\n▰\n▰",23,CYAN);logo.setGravity(Gravity.CENTER);add(c,logo,-1,90);
        TextView name=tv("Salam Web Server",20,WHITE);name.setTypeface(null,1);name.setGravity(Gravity.CENTER);add(c,name,-1,35);
        TextView ver=tv("Version 10.0",11,MUTED);ver.setGravity(Gravity.CENTER);add(c,ver,-1,28);
        add(c,tv("Developed by",11,MUTED),-1,25);TextView dev=tv("Abdus Salam",15,WHITE);dev.setTypeface(null,1);add(c,dev,-1,30);
        add(c,tv("09696590864\nsalam230864@gmail.com",12,MUTED),-1,55);
        add(c,tv("✓ Local Web Server for Android\n✓ Open Source & Free\n✓ Fast & Lightweight\n✓ Secure & Reliable",12,WHITE),-1,105);
        body.addView(c);compactTile("💬","Messenger","m.me/Salam.864",()->messenger());compactTile("✉","Email","salam230864@gmail.com",()->{Intent i=new Intent(Intent.ACTION_SENDTO,Uri.parse("mailto:salam230864@gmail.com"));startActivity(i);});
    }

    void browserPage(){
        LinearLayout c=card();TextView address=tv("◉  "+WebServerService.currentUrl(this),12,WHITE);address.setGravity(Gravity.CENTER_VERTICAL);address.setPadding(dp(12),0,dp(5),0);address.setBackground(gradient(Color.rgb(3,16,31),Color.rgb(8,29,51),14));add(c,address,-1,48);
        WebView web=new WebView(this);web.setBackgroundColor(BG);web.getSettings().setJavaScriptEnabled(true);web.getSettings().setDomStorageEnabled(true);web.setWebViewClient(new WebViewClient());web.loadUrl(WebServerService.currentUrl(this));c.addView(web,new LinearLayout.LayoutParams(-1,dp(560)));body.addView(c);
        TextView note=tv("Salam Web Server\nYour local server is working here.",13,WHITE);note.setGravity(Gravity.CENTER);body.addView(note,new LinearLayout.LayoutParams(-1,dp(70)));
    }

    void portDialog(){EditText e=input(""+pref.getInt("port",8080));new AlertDialog.Builder(this).setTitle("Port").setView(e).setNegativeButton("Cancel",null).setPositiveButton("Save",(d,w)->{try{int p=Integer.parseInt(e.getText().toString());if(p>=1024&&p<=65535)pref.edit().putInt("port",p).apply();}catch(Exception ignored){}show(SCREEN_SETTINGS);}).show();}
    void security(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);l.setPadding(dp(8),0,dp(8),0);EditText pass=input(pref.getString("password",""));pass.setHint("Web password");EditText allow=input(pref.getString("allowIps",""));allow.setHint("Allow IPs, comma separated");EditText block=input(pref.getString("blockIps",""));block.setHint("Block IPs, comma separated");EditText rate=input(""+pref.getInt("rate",120));rate.setHint("Requests/min/IP");EditText max=input(""+pref.getInt("maxClients",32));max.setHint("Maximum clients");l.addView(pass);l.addView(allow);l.addView(block);l.addView(rate);l.addView(max);new AlertDialog.Builder(this).setTitle("🛡 Security").setView(l).setNegativeButton("Cancel",null).setPositiveButton("Save",(d,w)->pref.edit().putString("password",pass.getText().toString()).putString("allowIps",allow.getText().toString()).putString("blockIps",block.getText().toString()).putInt("rate",parse(rate,120)).putInt("maxClients",parse(max,32)).apply()).show();}
    int parse(EditText e,int d){try{return Integer.parseInt(e.getText().toString());}catch(Exception x){return d;}}
    void theme(){String[] names={"🌑 Midnight","🌊 Ocean","💜 Violet","💚 Emerald","🌅 Sunset","⚡ Neon"};new AlertDialog.Builder(this).setTitle("🎨 Theme").setSingleChoiceItems(names,pref.getInt("theme",0),(d,w)->{pref.edit().putInt("theme",w).apply();d.dismiss();appShell();}).show();}
    EditText input(String s){EditText e=new EditText(this);e.setText(s);e.setTextColor(WHITE);e.setHintTextColor(MUTED);e.setSingleLine(true);return e;}
    void newFolder(){EditText e=input("");new AlertDialog.Builder(this).setTitle("📂 Create Folder").setView(e).setNegativeButton("Cancel",null).setPositiveButton("Create",(d,w)->{String n=e.getText().toString().trim();if(!n.isEmpty())new File(WebServerService.webRoot(this),folder+"/"+n).mkdirs();show(SCREEN_FILES);}).show();}
    void newFile(){EditText e=input("index.html");new AlertDialog.Builder(this).setTitle("📄 Create File").setView(e).setNegativeButton("Cancel",null).setPositiveButton("Create",(d,w)->{try{File f=new File(WebServerService.webRoot(this),folder+"/"+e.getText().toString().trim());f.getParentFile().mkdirs();f.createNewFile();show(SCREEN_FILES);}catch(Exception x){toast(x.getMessage());}}).show();}
    void server(boolean start){try{Intent i=new Intent(this,WebServerService.class);i.setAction(start?"START":"STOP");if(start&&Build.VERSION.SDK_INT>=26)startForegroundService(i);else startService(i);}catch(Exception e){toast(e.getMessage());}}
    void copyUrl(){((android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("Server URL",WebServerService.currentUrl(this)));toast("URL copied");}
    void openBrowser(){startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(WebServerService.currentUrl(this))));}
    void shareText(String s){Intent i=new Intent(Intent.ACTION_SEND);i.setType("text/plain");i.putExtra(Intent.EXTRA_TEXT,s);startActivity(Intent.createChooser(i,"Share Server URL"));}
    void messenger(){try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("https://m.me/Salam.864")));}catch(Exception ignored){}}
    void menuDialog(){new AlertDialog.Builder(this).setTitle("Salam Web Server").setItems(new String[]{"🏠 Home","📁 Web Files","🌐 Network Info","▤ Server Logs","⚙ Server Settings","ℹ About","🌐 Web Preview"},(d,w)->{int[] x={1,2,4,5,3,6,7};show(x[w]);}).show();}
    void toast(Object x){Toast.makeText(this,String.valueOf(x),Toast.LENGTH_LONG).show();}

    void refreshLoop(){handler.postDelayed(new Runnable(){public void run(){if(isFinishing())return;if(screen==SCREEN_HOME){/* metrics are rebuilt on navigation; server state remains live */}handler.postDelayed(this,1000);}},1000);}

    static class Wave extends View{
        Paint p=new Paint(1);Path path=new Path();Wave(Context c){super(c);}
        protected void onDraw(Canvas c){int w=getWidth(),h=getHeight();int theme=getContext().getSharedPreferences("server",Context.MODE_PRIVATE).getInt("theme",0);int a=BG,b=Color.rgb(7,28,53),line=Color.rgb(10,112,235);if(theme==1){a=Color.rgb(2,19,34);b=Color.rgb(4,57,82);line=CYAN;}else if(theme==2){a=Color.rgb(12,5,28);b=Color.rgb(47,12,82);line=Color.rgb(190,80,255);}else if(theme==3){a=Color.rgb(3,22,19);b=Color.rgb(5,60,48);line=GREEN;}else if(theme==4){a=Color.rgb(28,10,5);b=Color.rgb(70,25,10);line=Color.rgb(255,135,35);}else if(theme==5){a=Color.rgb(3,7,24);b=Color.rgb(15,35,80);line=Color.rgb(70,125,255);}p.setShader(new LinearGradient(0,0,w,h,a,b,Shader.TileMode.CLAMP));c.drawRect(0,0,w,h,p);p.setShader(null);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2);p.setColor(line);float d=getResources().getDisplayMetrics().density;for(int j=0;j<3;j++){path.reset();for(int x=0;x<=w;x+=10){float y=h*.70f+j*d*18+(float)Math.sin(x*.013+j)*d*19;if(x==0)path.moveTo(x,y);else path.lineTo(x,y);}c.drawPath(path,p);}p.setStyle(Paint.Style.FILL);}
    }
}