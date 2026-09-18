package com.salam.androidwebserver;

import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.net.Uri;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import java.io.*;
import java.util.*;

public class MainActivity extends Activity {

    private static final int BG=Color.rgb(2,10,21);
    private static final int CARD=Color.rgb(6,23,42);
    private static final int CARD2=Color.rgb(9,35,61);
    private static final int WHITE=Color.WHITE;
    private static final int MUTED=Color.rgb(139,168,196);
    private static final int GREEN=Color.rgb(39,244,151);
    private static final int RED=Color.rgb(255,61,88);
    private static final int CYAN=Color.rgb(24,218,255);
    private static final int BLUE=Color.rgb(76,83,255);
    private static final int PURPLE=Color.rgb(188,78,255);
    private static final int YELLOW=Color.rgb(255,207,46);
    private static final int ORANGE=Color.rgb(255,139,38);

    private final int[] LEDS={GREEN,CYAN,PURPLE,YELLOW,WHITE,ORANGE,RED};
    private final String[] THEME_NAMES={"Ocean Neon","Purple Night","Emerald Matrix","Sunset Core","Ice Blue","Cyber Gold"};

    private Handler handler=new Handler(Looper.getMainLooper());
    private LinearLayout content,bottom;
    private TextView title,onlineText,urlText;
    private ServerMachine machine;
    private int page=0,theme=0;
    private String cwd="/";
    private EditText fileSearch;
    private Runnable liveLoop;

    private int accent(){
        switch(theme){
            case 1:return PURPLE;
            case 2:return GREEN;
            case 3:return ORANGE;
            case 4:return Color.rgb(100,220,255);
            case 5:return YELLOW;
            default:return CYAN;
        }
    }
    private int accent2(){
        switch(theme){
            case 1:return BLUE;
            case 2:return Color.rgb(0,180,145);
            case 3:return RED;
            case 4:return BLUE;
            case 5:return ORANGE;
            default:return BLUE;
        }
    }
    private int dp(float n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
    private GradientDrawable bg(int color,float radius){GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(dp(radius));return g;}
    private GradientDrawable gradient(float radius){
        GradientDrawable g=new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,new int[]{accent(),accent2()});
        g.setCornerRadius(dp(radius)); return g;
    }
    private GradientDrawable cardBg(){
        GradientDrawable g=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{CARD,CARD2});
        g.setCornerRadius(dp(22)); g.setStroke(dp(1),Color.rgb(15,65,95)); return g;
    }
    private TextView text(String s,float size,int color){
        TextView v=new TextView(this); v.setText(s); v.setTextSize(size); v.setTextColor(color);
        v.setFontFeatureSettings("kern"); return v;
    }
    private TextView action(String label){
        TextView v=text(label,12,WHITE); v.setGravity(Gravity.CENTER); v.setTypeface(null,1);
        v.setPadding(dp(10),0,dp(10),0); v.setBackground(gradient(16));
        v.setMinHeight(dp(46)); return v;
    }
    private LinearLayout card(){
        LinearLayout c=new LinearLayout(this); c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(14),dp(13),dp(14),dp(13)); c.setBackground(cardBg());
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);
        p.setMargins(dp(4),dp(5),dp(4),dp(5)); c.setLayoutParams(p); return c;
    }

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        getWindow().setStatusBarColor(BG); getWindow().setNavigationBarColor(BG);
        theme=getPreferences(0).getInt("theme",0);
        buildShell();
        startLiveLoop();
    }

    private void buildShell(){
        LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(BG);

        LinearLayout header=new LinearLayout(this); header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(10),dp(4),dp(8),0);
        ImageView logo=new ImageView(this); logo.setImageResource(R.drawable.ic_server); logo.setPadding(dp(4),dp(4),dp(4),dp(4));
        header.addView(logo,new LinearLayout.LayoutParams(dp(50),dp(58)));
        title=text("Salam Web Server",20,WHITE); title.setTypeface(null,1);
        header.addView(title,new LinearLayout.LayoutParams(0,dp(58),1));
        TextView menu=text("☰",27,accent()); menu.setGravity(Gravity.CENTER);
        menu.setOnClickListener(v->moreMenu()); header.addView(menu,new LinearLayout.LayoutParams(dp(50),dp(58)));
        root.addView(header);

        ScrollView scroll=new ScrollView(this); scroll.setFillViewport(true);
        content=new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(6),0,dp(6),dp(14)); scroll.addView(content);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));

        bottom=new LinearLayout(this); bottom.setGravity(Gravity.CENTER); bottom.setPadding(dp(4),dp(5),dp(4),dp(5));
        bottom.setBackground(bg(Color.rgb(4,19,35),24));
        addNav("⌂","Home",0); addNav("▣","Files",1); addNav("≡","Logs",2); addNav("⚙","Settings",3);
        root.addView(bottom,new LinearLayout.LayoutParams(-1,dp(74)));

        setContentView(root); showPage(0);
    }

    private void addNav(String icon,String name,int p){
        LinearLayout item=new LinearLayout(this); item.setOrientation(LinearLayout.VERTICAL); item.setGravity(Gravity.CENTER);
        TextView i=text(icon,25,p==page?accent():MUTED); i.setGravity(Gravity.CENTER);
        TextView n=text(name,10,p==page?accent():MUTED); n.setGravity(Gravity.CENTER); n.setTypeface(null,1);
        item.addView(i,new LinearLayout.LayoutParams(-1,dp(35)));
        item.addView(n,new LinearLayout.LayoutParams(-1,dp(24)));
        item.setOnClickListener(v->showPage(p)); bottom.addView(item,new LinearLayout.LayoutParams(0,dp(64),1));
    }

    private void refreshNav(){
        if(bottom==null)return;
        for(int i=0;i<bottom.getChildCount();i++){
            LinearLayout x=(LinearLayout)bottom.getChildAt(i);
            TextView a=(TextView)x.getChildAt(0),b=(TextView)x.getChildAt(1);
            a.setTextColor(i==page?accent():MUTED); b.setTextColor(i==page?accent():MUTED);
        }
    }

    private void showPage(int p){
        page=p; content.removeAllViews();
        if(p==0)home(); else if(p==1)files(); else if(p==2)logs(); else settings();
        title.setText(p==0?"Salam Web Server":p==1?"📁 Web Files":p==2?"📝 Server Logs":"⚙️ Control Center");
        refreshNav();
    }

    private void home(){
        machine=new ServerMachine(this);
        content.addView(machine,new LinearLayout.LayoutParams(-1,dp(292)));

        LinearLayout row1=new LinearLayout(this);
        row1.addView(tile("📁","Web Files","Upload • Edit • ZIP",v->showPage(1)),new LinearLayout.LayoutParams(0,dp(108),1));
        row1.addView(tile("🌐","Network","Wi-Fi • Mobile • DNS",v->networkDialog()),new LinearLayout.LayoutParams(0,dp(108),1));
        content.addView(row1);
        LinearLayout row2=new LinearLayout(this);
        row2.addView(tile("📝","Live Logs","Requests • History",v->showPage(2)),new LinearLayout.LayoutParams(0,dp(108),1));
        row2.addView(tile("🔐","Security","Login • IP Control",v->securityDialog()),new LinearLayout.LayoutParams(0,dp(108),1));
        content.addView(row2);

        LinearLayout monitor=card();
        TextView h=text("📊  LIVE MONITORING",17,accent()); h.setTypeface(null,1); monitor.addView(h);
        TextView sub=text("Auto refresh • real device values",10,MUTED); monitor.addView(sub);
        LinearLayout grid=new LinearLayout(this); grid.setOrientation(LinearLayout.VERTICAL);
        LinearLayout a=new LinearLayout(this),b=new LinearLayout(this);
        TextView cpu=metric("🧠","CPU"); TextView ram=metric("💾","RAM");
        TextView storage=metric("💽","Storage"); TextView clients=metric("👥","Clients");
        a.addView(cpu,new LinearLayout.LayoutParams(0,dp(75),1)); a.addView(ram,new LinearLayout.LayoutParams(0,dp(75),1));
        b.addView(storage,new LinearLayout.LayoutParams(0,dp(75),1)); b.addView(clients,new LinearLayout.LayoutParams(0,dp(75),1));
        grid.addView(a); grid.addView(b); monitor.addView(grid);
        TextView more=text("📈 Requests  —     📡 Traffic  —     ⏱️ Uptime  —",11,MUTED); more.setPadding(0,dp(7),0,0); monitor.addView(more);
        content.addView(monitor);

        TextView network=text("🌐  Network: "+WebServerService.networkInfo(this)+"\n🔗  URL: "+WebServerService.currentUrl(this),11,MUTED);
        network.setPadding(dp(10),dp(5),dp(10),dp(8)); content.addView(network);

        liveLoop=null; liveLoop=new Runnable(){
            public void run(){
                if(cpu.getParent()==null)return;
                cpu.setText("🧠 CPU\n"+WebServerService.cpuText());
                ram.setText("💾 RAM\n"+WebServerService.memoryText(MainActivity.this));
                storage.setText("💽 Storage\n"+WebServerService.storageText(MainActivity.this));
                clients.setText("👥 Clients\n"+WebServerService.clients.size());
                more.setText("📈 Requests  "+WebServerService.requests.get()+"     📡 "+WebServerService.trafficText()+"     ⏱️ "+WebServerService.uptime());
                network.setText("🌐  Network: "+WebServerService.networkInfo(MainActivity.this)+"\n🔗  URL: "+WebServerService.currentUrl(MainActivity.this));
                handler.postDelayed(this,1000);
            }
        };
        handler.post(liveLoop);
    }

    private TextView metric(String emoji,String label){
        TextView v=text(emoji+" "+label+"\n—",12,WHITE); v.setGravity(Gravity.CENTER_VERTICAL);
        v.setPadding(dp(10),0,dp(8),0); v.setBackground(bg(Color.rgb(3,17,31),16)); return v;
    }

    private LinearLayout tile(String emoji,String name,String desc,View.OnClickListener click){
        LinearLayout x=new LinearLayout(this); x.setOrientation(LinearLayout.VERTICAL); x.setGravity(Gravity.CENTER);
        x.setPadding(dp(5),dp(7),dp(5),dp(5)); x.setBackground(cardBg()); x.setOnClickListener(click);
        TextView i=text(emoji,30,WHITE); i.setGravity(Gravity.CENTER);
        TextView n=text(name,13,WHITE); n.setGravity(Gravity.CENTER); n.setTypeface(null,1);
        TextView d=text(desc,9,MUTED); d.setGravity(Gravity.CENTER);
        x.addView(i,new LinearLayout.LayoutParams(-1,dp(44))); x.addView(n,new LinearLayout.LayoutParams(-1,dp(28))); x.addView(d,new LinearLayout.LayoutParams(-1,dp(23)));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(108),1);p.setMargins(dp(4),dp(4),dp(4),dp(4));x.setLayoutParams(p);return x;
    }

    private class ServerMachine extends ViewGroup{
        Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        TextView state,url,startStop,copy,stage;
        long sequenceAt=0; boolean last=false;
        ServerMachine(Context c){
            super(c); setWillNotDraw(false); setBackground(cardBg());
            state=text("🔴 SERVER OFFLINE",18,RED);state.setGravity(Gravity.CENTER);state.setTypeface(null,1);addView(state);
            stage=text("Ready • press START to initialize",10,MUTED);stage.setGravity(Gravity.CENTER);addView(stage);
            url=text(WebServerService.currentUrl(MainActivity.this),12,accent());url.setGravity(Gravity.CENTER);url.setPadding(dp(10),0,dp(10),0);url.setBackground(bg(Color.rgb(2,14,27),16));addView(url);
            LinearLayout actions=new LinearLayout(c); actions.setGravity(Gravity.CENTER);
            startStop=action("▶  START SERVER"); copy=action("📋 COPY URL");
            actions.addView(startStop,new LinearLayout.LayoutParams(0,dp(54),2));actions.addView(copy,new LinearLayout.LayoutParams(0,dp(54),1));addView(actions);
            startStop.setOnClickListener(v->{if(WebServerService.running)stopServer();else startServer();});
            copy.setOnClickListener(v->copyUrl());
            postDelayed(this::tick,80);
        }
        private void tick(){
            boolean on=WebServerService.running;
            if(on&&!last)sequenceAt=System.currentTimeMillis();
            last=on;
            state.setText(on?"🟢 SERVER ONLINE":"🔴 SERVER OFFLINE");
            state.setTextColor(on?GREEN:RED);
            url.setText(WebServerService.currentUrl(MainActivity.this));
            startStop.setText(on?"⏹  STOP SERVER":"▶  START SERVER");
            startStop.setBackground(on?bg(Color.rgb(75,17,31),18):gradient(18));
            long age=System.currentTimeMillis()-sequenceAt;
            int active=on?Math.min(7,Math.max(1,(int)(age/260)+1)):0;
            if(!on)stage.setText("Ready • Server engine stopped");
            else if(active<2)stage.setText("Starting Server • Initializing Network");
            else if(active<4)stage.setText("Binding Port • Starting HTTP Engine");
            else if(active<7)stage.setText("HTTP Engine • Loading control modules");
            else stage.setText("Server Online • Live monitoring active");
            invalidate();postInvalidateDelayed(on?70:250);postDelayed(this::tick,180);
        }
        protected void onDraw(Canvas c){
            super.onDraw(c);
            float w=getWidth(),y=dp(47),gap=w/8f;
            long age=System.currentTimeMillis()-sequenceAt;
            int active=last?Math.min(7,Math.max(1,(int)(age/260)+1)):0;
            for(int i=0;i<7;i++){
                float x=gap*(i+1);int color;
                if(!last)color=(i==6?RED:Color.rgb(38,53,68));
                else color=i<active?LEDS[i]:Color.rgb(34,52,67);
                paint.setColor(color);
                float glow=(!last&&i==6)?dp(5):(last&&i<active?dp(10):0);
                paint.setShadowLayer(glow,0,0,color);c.drawCircle(x,y,dp(9),paint);paint.clearShadowLayer();
                if(last&&i<active){
                    float pulse=(float)(0.55+0.45*Math.sin(System.currentTimeMillis()/120.0+i));
                    paint.setColor(Color.argb((int)(35+45*pulse),Color.red(color),Color.green(color),Color.blue(color)));
                    c.drawCircle(x,y,dp(16+4*pulse),paint);
                }
            }
        }
        protected void onMeasure(int ws,int hs){
            int w=MeasureSpec.getSize(ws);
            state.measure(MeasureSpec.makeMeasureSpec(w,1073741824),MeasureSpec.makeMeasureSpec(dp(38),1073741824));
            stage.measure(MeasureSpec.makeMeasureSpec(w,1073741824),MeasureSpec.makeMeasureSpec(dp(28),1073741824));
            url.measure(MeasureSpec.makeMeasureSpec(w-dp(24),1073741824),MeasureSpec.makeMeasureSpec(dp(44),1073741824));
            startStop.measure(MeasureSpec.makeMeasureSpec(w-dp(100),1073741824),MeasureSpec.makeMeasureSpec(dp(54),1073741824));
            copy.measure(MeasureSpec.makeMeasureSpec(w-dp(100),1073741824),MeasureSpec.makeMeasureSpec(dp(54),1073741824));
            setMeasuredDimension(w,dp(292));
        }
        protected void onLayout(boolean ch,int l,int t,int r,int b){
            int w=r-l;state.layout(0,dp(70),w,dp(108));stage.layout(0,dp(108),w,dp(136));
            url.layout(dp(12),dp(143),w-dp(12),dp(187));ViewGroup actions=(ViewGroup)getChildAt(3);
            actions.layout(dp(12),dp(202),w-dp(12),dp(256));
        }
    }

    private void startServer(){
        showStartDialog();
        Intent i=new Intent(this,WebServerService.class).setAction("START");
        try{
            if(Build.VERSION.SDK_INT>=26)startForegroundService(i);else startService(i);
        }catch(Exception e){toast("Server start failed: "+e.getMessage());}
    }
    private void stopServer(){
        Intent i=new Intent(this,WebServerService.class).setAction("STOP");
        try{startService(i);toast("🔴 Server stopping…");}catch(Exception e){toast(e.getMessage());}
    }
    private void showStartDialog(){
        final String[] steps={"🚀 Starting Server","🌐 Initializing Network","🔌 Binding Port 8080","⚡ Starting HTTP Engine","🖥️ Loading Control Panel","🟢 Server Online"};
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(10),dp(4),dp(10),dp(4));
        TextView status=text(steps[0],15,WHITE);status.setGravity(Gravity.CENTER);box.addView(status,new LinearLayout.LayoutParams(-1,dp(48)));
        ProgressBar bar=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);box.addView(bar,new LinearLayout.LayoutParams(-1,dp(8)));
        AlertDialog d=new AlertDialog.Builder(this).setTitle("⚡ SERVER BOOT").setView(box).create();d.show();
        final int[] n={0};
        Runnable r=new Runnable(){public void run(){if(!d.isShowing())return;status.setText(steps[Math.min(n[0],steps.length-1)]);bar.setProgress((n[0]*100)/(steps.length-1));n[0]++;if(n[0]<steps.length)handler.postDelayed(this,330);else handler.postDelayed(d::dismiss,500);}};
        handler.post(r);
    }

    private void copyUrl(){
        android.content.ClipboardManager cm=(android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("Salam Web Server URL",WebServerService.currentUrl(this)));
        toast("📋 Server URL copied");
    }

    private void files(){
        TextView head=text("📁  ADVANCED FILE MANAGER",20,WHITE);head.setTypeface(null,1);content.addView(head);
        TextView info=text("Upload • Download • Edit • Rename • Copy • Move • Delete • ZIP • Search",10,MUTED);content.addView(info);
        LinearLayout tools=new LinearLayout(this);
        String[] labels={"➕ FILE","📂 FOLDER","⬆️ UPLOAD","📦 ZIP"};
        for(String s:labels){
            TextView v=action(s);tools.addView(v,new LinearLayout.LayoutParams(0,dp(48),1));
            if(s.contains("FILE"))v.setOnClickListener(x->newName(false));
            else if(s.contains("FOLDER"))v.setOnClickListener(x->newName(true));
            else if(s.contains("UPLOAD"))v.setOnClickListener(x->pickFile());
            else v.setOnClickListener(x->zipMenu());
        }
        content.addView(tools);
        fileSearch=new EditText(this);fileSearch.setSingleLine(true);fileSearch.setHint("🔎 Search files in current folder…");fileSearch.setTextColor(WHITE);fileSearch.setHintTextColor(MUTED);fileSearch.setBackground(bg(Color.rgb(4,18,31),16));fileSearch.setPadding(dp(14),0,dp(14),0);
        content.addView(fileSearch,new LinearLayout.LayoutParams(-1,dp(50)));
        fileSearch.setOnEditorActionListener((v,id,event)->{showPage(1);return true;});
        renderFiles();
    }

    private void renderFiles(){
        if(content==null)return;
        File root=WebServerService.webRoot(this),dir=new File(root,cwd);
        TextView path=text("📍 "+cwd,11,accent());content.addView(path);
        if(!cwd.equals("/")){TextView up=action("⬆️  PARENT FOLDER");up.setOnClickListener(v->{int k=cwd.lastIndexOf('/');cwd=k<=0?"/":cwd.substring(0,k);showPage(1);});content.addView(up);}
        File[] fs=dir.listFiles();if(fs==null){content.addView(text("Folder unavailable",13,RED));return;}
        String q=fileSearch==null?"":fileSearch.getText().toString().trim().toLowerCase(Locale.US);
        Arrays.sort(fs,(a,b)->a.isDirectory()!=b.isDirectory()?(a.isDirectory()?-1:1):a.getName().compareToIgnoreCase(b.getName()));
        int shown=0;
        for(File f:fs){
            if(!q.isEmpty()&&!f.getName().toLowerCase(Locale.US).contains(q))continue;
            shown++;
            fileRow(f);
        }
        if(shown==0)content.addView(text("🔎 No matching files",13,MUTED));
    }

    private void fileRow(File f){
        LinearLayout row=card();row.setOrientation(LinearLayout.HORIZONTAL);
        TextView icon=text(f.isDirectory()?"📁":"📄",30,f.isDirectory()?YELLOW:CYAN);icon.setGravity(Gravity.CENTER);
        row.addView(icon,new LinearLayout.LayoutParams(dp(48),dp(62)));
        LinearLayout mid=new LinearLayout(this);mid.setOrientation(LinearLayout.VERTICAL);
        TextView name=text(f.getName(),14,WHITE);name.setTypeface(null,1);mid.addView(name,new LinearLayout.LayoutParams(-1,dp(32)));
        mid.addView(text(f.isDirectory()?"Folder":"File • "+size(f.length()),10,MUTED),new LinearLayout.LayoutParams(-1,dp(24)));
        row.addView(mid,new LinearLayout.LayoutParams(0,dp(62),1));
        TextView more=text("⋮",28,accent());more.setGravity(Gravity.CENTER);row.addView(more,new LinearLayout.LayoutParams(dp(38),dp(62)));
        View.OnClickListener open=v->{if(f.isDirectory()){cwd=(cwd.equals("/")?"/":cwd+"/")+f.getName();showPage(1);}else fileMenu(f);};
        row.setOnClickListener(open);more.setOnClickListener(v->fileMenu(f));content.addView(row);
    }

    private void fileMenu(File f){
        String[] opts=f.isDirectory()?new String[]{"📂 Open","✏️ Rename","📋 Copy","🚚 Move","📦 Create ZIP","🗑️ Delete"}:
                new String[]{"✏️ Edit","⬇️ Download URL","✏️ Rename","📋 Copy","🚚 Move","📦 Create ZIP","📂 Extract ZIP","🗑️ Delete"};
        new AlertDialog.Builder(this).setTitle("🛠️ "+f.getName()).setItems(opts,(d,w)->{
            String s=opts[w];
            if(s.contains("Open")){cwd=(cwd.equals("/")?"/":cwd+"/")+f.getName();showPage(1);}
            else if(s.contains("Edit"))editFile(f);
            else if(s.contains("Rename"))renameFile(f);
            else if(s.contains("Copy"))copyMove(f,false);
            else if(s.contains("Move"))copyMove(f,true);
            else if(s.contains("Create ZIP"))zipOne(f);
            else if(s.contains("Extract ZIP"))extractZip(f);
            else if(s.contains("Download"))copyUrlForFile(f);
            else deleteFile(f);
        }).show();
    }

    private void editFile(File f){
        EditText e=new EditText(this);e.setGravity(Gravity.TOP|Gravity.START);e.setTextColor(WHITE);e.setMinLines(18);
        try{e.setText(WebServerService.readText(f));}catch(Exception x){toast("Cannot read file");return;}
        new AlertDialog.Builder(this).setTitle("✏️ Editor • "+f.getName()).setView(e)
                .setNegativeButton("CANCEL",null).setPositiveButton("💾 SAVE",(d,w)->{try{WebServerService.writeText(f,e.getText().toString());toast("💾 Saved");}catch(Exception x){toast(x.getMessage());}}).show();
    }
    private void renameFile(File f){
        EditText e=new EditText(this);e.setText(f.getName());
        new AlertDialog.Builder(this).setTitle("✏️ Rename").setView(e).setNegativeButton("CANCEL",null).setPositiveButton("SAVE",(d,w)->{
            String n=e.getText().toString().trim().replace("/","_").replace("\\","_");if(n.isEmpty())return;
            if(!f.renameTo(new File(f.getParentFile(),n)))toast("Rename failed");else toast("✅ Renamed");showPage(1);
        }).show();
    }
    private void copyMove(File f,boolean move){
        EditText e=new EditText(this);e.setText(cwd);e.setHint("/folder");
        new AlertDialog.Builder(this).setTitle(move?"🚚 Move to folder":"📋 Copy to folder").setView(e).setNegativeButton("CANCEL",null).setPositiveButton("APPLY",(d,w)->{
            try{File dst=new File(WebServerService.webRoot(this),e.getText().toString());if(!dst.exists())dst.mkdirs();WebServerService.copyRecursive(f,new File(dst,f.getName()));if(move)WebServerService.deleteRecursive(f);toast(move?"🚚 Moved":"📋 Copied");showPage(1);}catch(Exception x){toast(x.getMessage());}
        }).show();
    }
    private void deleteFile(File f){new AlertDialog.Builder(this).setTitle("🗑️ Delete?").setMessage(f.getName()+" will be permanently removed.").setNegativeButton("CANCEL",null).setPositiveButton("DELETE",(d,w)->{WebServerService.deleteRecursive(f);toast("🗑️ Deleted");showPage(1);}).show();}
    private void newName(boolean folder){EditText e=new EditText(this);e.setHint(folder?"folder-name":"index.html");
        new AlertDialog.Builder(this).setTitle(folder?"📂 New Folder":"📄 New File").setView(e).setNegativeButton("CANCEL",null).setPositiveButton("CREATE",(d,w)->{String n=e.getText().toString().trim().replace("/","_");if(n.isEmpty())return;File f=new File(WebServerService.webRoot(this),cwd+"/"+n);try{if(folder)f.mkdirs();else f.createNewFile();showPage(1);}catch(Exception x){toast(x.getMessage());}}).show();
    }
    private void pickFile(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("*/*");i.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,22);}
    private void pickZip(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("application/zip");i.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,23);}
    private void zipMenu(){new AlertDialog.Builder(this).setTitle("📦 ZIP TOOLS").setItems(new String[]{"📦 Create ZIP from current folder","📦 Extract ZIP here"},(d,w)->{if(w==0)zipOne(new File(WebServerService.webRoot(this),cwd));else pickZip();}).show();}
    private void zipOne(File f){try{WebServerService.zip(f,new File(f.getParentFile(),f.getName()+".zip"));toast("📦 ZIP created");showPage(1);}catch(Exception e){toast("ZIP failed: "+e.getMessage());}}
    private void extractZip(File f){try{WebServerService.unzip(f,new File(WebServerService.webRoot(this),cwd));toast("📦 ZIP extracted");showPage(1);}catch(Exception e){toast("Extract failed: "+e.getMessage());}}
    private void copyUrlForFile(File f){String rel=f.getAbsolutePath().substring(WebServerService.webRoot(this).getAbsolutePath().length()).replace(File.separatorChar,'/');copyText("File URL","http://"+WebServerService.currentUrl(this).replace("http://","").replace(":8080","")+":8080"+rel);}
    private void copyText(String title,String value){((android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText(title,value));toast("📋 Copied");}
    private String size(long n){if(n<1024)return n+" B";if(n<1048576)return n/1024+" KB";if(n<1073741824L)return n/1048576+" MB";return String.format(Locale.US,"%.1f GB",n/1073741824d);}

    private void logs(){
        TextView h=text("📝  LIVE REQUEST LOGS",20,WHITE);h.setTypeface(null,1);content.addView(h);
        TextView info=text("Auto refresh • up to 1000 log entries + access history",10,MUTED);content.addView(info);
        TextView box=text("",10,Color.rgb(175,207,228));box.setGravity(Gravity.TOP|Gravity.START);box.setPadding(dp(12),dp(12),dp(12),dp(12));box.setBackground(bg(Color.rgb(2,13,24),18));content.addView(box,new LinearLayout.LayoutParams(-1,dp(360)));
        TextView clear=action("🧹 CLEAR LOGS + HISTORY");content.addView(clear);clear.setOnClickListener(v->{WebServerService.LOGS.clear();WebServerService.HISTORY.clear();toast("🧹 Cleared");});
        Runnable r=new Runnable(){public void run(){if(box.getParent()==null)return;StringBuilder s=new StringBuilder("⚡ LIVE LOGS\n\n");for(String x:WebServerService.LOGS)s.append(x).append('\n');s.append("\n──────── ACCESS HISTORY ────────\n");for(String x:WebServerService.HISTORY)s.append(x).append('\n');box.setText(s.toString());handler.postDelayed(this,700);}};handler.post(r);
    }

    private void settings(){
        TextView h=text("⚙️  V10 ADVANCED CONTROL CENTER",20,WHITE);h.setTypeface(null,1);content.addView(h);
        setting("🖥️","Server Engine","Start • Stop • Restart • Port 8080 • Background",v->engineDialog());
        setting("💡","Server Status Engine","7-color LEDs • sequence • pulse • shutdown state",v->statusDialog());
        setting("📊","Live Monitoring","CPU • RAM • Storage • Traffic • Clients • Uptime",v->monitorDialog());
        setting("🌐","Network & URL","Wi-Fi • Mobile • Interface • Gateway • DNS • Hostname",v->networkDialog());
        setting("📁","File Manager Tools","Upload • Search • Editor • ZIP • Copy • Move",v->showPage(1));
        setting("🔐","Web Login & Password","Browser authentication and password protection",v->securityDialog());
        setting("🟢","IP Allowlist","Only approved client IPs can connect",v->allowDialog());
        setting("🚫","IP Blocklist","Block selected client IP addresses",v->blockDialog());
        setting("🔓","Unblock IP","Remove a client from the blocklist",v->unblockDialog());
        setting("⚡","Rate Limit","Requests per minute and maximum clients",v->limitDialog());
        setting("🖥️","Browser Control Panel","Open the actual web-side server dashboard",v->browserControl());
        setting("🔄","Auto Refresh","Dashboard 1s • Logs 0.7s • server status live",v->toast("🔄 Live refresh is active"));
        setting("🎨","Themes","Cards • buttons • LEDs • waves • navigation • animations",v->themeDialog());
        setting("🔔","Persistent Notification","Background server status and server URL",v->notificationInfo());
        setting("📱","QR / Share Server","Copy server URL or open browser control",v->shareDialog());
        setting("🛡️","Path Protection","Canonical path checks and ZIP-slip protection",v->toast("🛡️ Path protection is active"));
        setting("🧾","Access History","Request history and client activity",v->showPage(2));
        setting("👨‍💻","Developer","About • Messenger • Email • Version",v->about());
    }

    private void setting(String emoji,String name,String desc,View.OnClickListener click){
        LinearLayout r=card();r.setOrientation(LinearLayout.HORIZONTAL);
        TextView e=text(emoji,27,WHITE);e.setGravity(Gravity.CENTER);r.addView(e,new LinearLayout.LayoutParams(dp(50),dp(66)));
        LinearLayout m=new LinearLayout(this);m.setOrientation(LinearLayout.VERTICAL);
        TextView n=text(name,14,WHITE);n.setTypeface(null,1);m.addView(n,new LinearLayout.LayoutParams(-1,dp(32)));
        m.addView(text(desc,10,MUTED),new LinearLayout.LayoutParams(-1,dp(27)));r.addView(m,new LinearLayout.LayoutParams(0,dp(66),1));
        TextView ar=text("›",30,accent());ar.setGravity(Gravity.CENTER);r.addView(ar,new LinearLayout.LayoutParams(dp(34),dp(66)));
        r.setOnClickListener(click);content.addView(r);
    }

    private void engineDialog(){new AlertDialog.Builder(this).setTitle("🖥️ SERVER ENGINE").setMessage("Port: 8080\nHTTP engine: local LAN server\nBackground service: "+(WebServerService.running?"RUNNING":"STOPPED")+"\nPersistent service mode: enabled").setPositiveButton(WebServerService.running?"STOP":"START",(d,w)->{if(WebServerService.running)stopServer();else startServer();}).setNeutralButton("RESTART",(d,w)->{Intent i=new Intent(this,WebServerService.class).setAction("RESTART");try{if(Build.VERSION.SDK_INT>=26)startForegroundService(i);else startService(i);}catch(Exception e){toast(e.getMessage());}}).setNegativeButton("CLOSE",null).show();}
    private void statusDialog(){new AlertDialog.Builder(this).setTitle("💡 STATUS ENGINE").setMessage("ON: 7 LEDs initialize one-by-one, then pulse independently.\nOFF: only the red LED remains with low-intensity breathing glow.\nBoot stages: Starting → Network → Port → HTTP Engine → Online.").setPositiveButton("OK",null).show();}
    private void monitorDialog(){new AlertDialog.Builder(this).setTitle("📊 LIVE MONITOR").setMessage("CPU: "+WebServerService.cpuText()+"\nRAM: "+WebServerService.memoryText(this)+"\nStorage: "+WebServerService.storageText(this)+"\nClients: "+WebServerService.clients.size()+"\nRequests: "+WebServerService.requests.get()+"\nTraffic: "+WebServerService.trafficText()+"\nUptime: "+WebServerService.uptime()).setPositiveButton("OK",null).show();}
    private void networkDialog(){new AlertDialog.Builder(this).setTitle("🌐 NETWORK INFORMATION").setMessage("Active interface: "+WebServerService.interfaceName(this)+"\n\nWi-Fi IP: "+WebServerService.wifiIp(this)+"\nMobile Data IP: "+WebServerService.cellularIp(this)+"\nGateway: "+WebServerService.gateway(this)+"\nDNS: "+WebServerService.dns(this)+"\n\nServer URL:\n"+WebServerService.currentUrl(this)).setPositiveButton("COPY URL",(d,w)->copyUrl()).setNegativeButton("CLOSE",null).show();}
    private void securityDialog(){
        LinearLayout box=form();
        EditText pass=input("🔐 Web password");EditText allow=input("🟢 Allow IP(s), comma separated");EditText block=input("🚫 Block IP(s), comma separated");EditText rate=input("⚡ Requests / minute");
        box.addView(pass);box.addView(allow);box.addView(block);box.addView(rate);
        new AlertDialog.Builder(this).setTitle("🔐 SECURITY & ACCESS CONTROL").setView(box).setPositiveButton("💾 SAVE",(d,w)->{WebServerService.saveSecurity(this,pass.getText().toString(),allow.getText().toString(),block.getText().toString(),rate.getText().toString());toast("✅ Security state synchronized");}).setNeutralButton("🔓 UNBLOCK", (d,w)->unblockDialog()).setNegativeButton("CLOSE",null).show();
    }
    private LinearLayout form(){LinearLayout b=new LinearLayout(this);b.setOrientation(LinearLayout.VERTICAL);b.setPadding(dp(6),0,dp(6),0);return b;}
    private EditText input(String hint){EditText e=new EditText(this);e.setHint(hint);e.setTextColor(WHITE);e.setHintTextColor(MUTED);e.setSingleLine(true);return e;}
    private void allowDialog(){EditText e=input("192.168.1.10, 192.168.1.11");new AlertDialog.Builder(this).setTitle("🟢 ALLOWLIST").setMessage("Enter approved IPv4 addresses separated by commas. Saving replaces the current allowlist.").setView(e).setPositiveButton("SAVE",(d,w)->WebServerService.saveSecurity(this,"",e.getText().toString(),"",String.valueOf(WebServerService.rateLimit))).setNegativeButton("CLOSE",null).show();}
    private void blockDialog(){EditText e=input("192.168.1.50");new AlertDialog.Builder(this).setTitle("🚫 BLOCKLIST").setView(e).setPositiveButton("BLOCK",(d,w)->WebServerService.saveSecurity(this,"","",e.getText().toString(),String.valueOf(WebServerService.rateLimit))).setNegativeButton("CLOSE",null).show();}
    private void unblockDialog(){EditText e=input("192.168.1.50");new AlertDialog.Builder(this).setTitle("🔓 UNBLOCK IP").setView(e).setPositiveButton("UNBLOCK",(d,w)->{WebServerService.unblockIp(MainActivity.this,e.getText().toString());toast("🔓 IP removed from blocklist");}).setNegativeButton("CLOSE",null).show();}
    private void limitDialog(){LinearLayout b=form();EditText r=input("Requests/minute");r.setText(String.valueOf(WebServerService.rateLimit));EditText m=input("Maximum clients");m.setText(String.valueOf(WebServerService.maxClients));b.addView(r);b.addView(m);new AlertDialog.Builder(this).setTitle("⚡ RATE + CLIENT LIMIT").setView(b).setPositiveButton("SAVE",(d,w)->{try{WebServerService.rateLimit=Math.max(0,Integer.parseInt(r.getText().toString()));}catch(Exception ignored){}try{WebServerService.maxClients=Math.max(1,Integer.parseInt(m.getText().toString()));}catch(Exception ignored){}toast("⚡ Limits updated");}).setNegativeButton("CLOSE",null).show();}
    private void hostDialog(){networkDialog();}
    private void browserControl(){try{startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(WebServerService.currentUrl(this)+"/__salam__/")));}catch(Exception e){toast("Browser unavailable");}}
    private void themeDialog(){new AlertDialog.Builder(this).setTitle("🎨 PREMIUM THEMES").setItems(THEME_NAMES,(d,w)->{theme=w;getPreferences(0).edit().putInt("theme",theme).apply();buildShell();toast("🎨 "+THEME_NAMES[w]+" applied");}).show();}
    private void notificationInfo(){new AlertDialog.Builder(this).setTitle("🔔 BACKGROUND SERVER").setMessage("When the server is running, WebServerService remains a foreground service and publishes a persistent server-status notification. Closing the app UI does not request the server to stop.").setPositiveButton("OK",null).show();}
    private void shareDialog(){new AlertDialog.Builder(this).setTitle("📱 SHARE SERVER").setItems(new String[]{"📋 Copy Server URL","🖥️ Open Browser Control"},(d,w)->{if(w==0)copyUrl();else browserControl();}).show();}
    private void about(){new AlertDialog.Builder(this).setTitle("👨‍💻 Salam Web Server V10.0").setMessage("Developer: Abdus Salam\n📞 09696590864\n✉️ salam230864@gmail.com\n\nPremium local web server control platform.").setPositiveButton("💬 Messenger",(d,w)->{try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("https://m.me/Salam.864")));}catch(Exception ignored){}}).setNeutralButton("✉️ Email",(d,w)->{try{startActivity(new Intent(Intent.ACTION_SENDTO,Uri.parse("mailto:salam230864@gmail.com")));}catch(Exception ignored){}}).setNegativeButton("CLOSE",null).show();}
    private void moreMenu(){new AlertDialog.Builder(this).setTitle("⚡ SERVER MENU").setItems(new String[]{"▶ Start Server","⏹ Stop Server","↻ Restart Server","🖥️ Browser Control","📱 Share URL","👨‍💻 Developer"},(d,w)->{if(w==0)startServer();else if(w==1)stopServer();else if(w==2){Intent i=new Intent(this,WebServerService.class).setAction("RESTART");if(Build.VERSION.SDK_INT>=26)startForegroundService(i);else startService(i);}else if(w==3)browserControl();else if(w==4)copyUrl();else about();}).show();}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_SHORT).show();}
    private void startLiveLoop(){handler.postDelayed(()->{if(machine!=null)machine.invalidate();handler.postDelayed(this::startLiveLoop,1000);},1000);}
    @Override protected void onDestroy(){if(liveLoop!=null)handler.removeCallbacks(liveLoop);super.onDestroy();}

    @Override protected void onActivityResult(int r,int c,Intent d){
        super.onActivityResult(r,c,d);if(c!=RESULT_OK||d==null||d.getData()==null)return;
        try{
            InputStream in=getContentResolver().openInputStream(d.getData());
            String name=String.valueOf(d.getData().getLastPathSegment()).replaceAll("[^a-zA-Z0-9._-]","_");
            if(r==23){
                File z=new File(WebServerService.webRoot(this),cwd+"/"+name);copyStream(in,z);WebServerService.unzip(z,new File(WebServerService.webRoot(this),cwd));toast("📦 ZIP extracted");showPage(1);
            }else{
                File f=new File(WebServerService.webRoot(this),cwd+"/"+name);copyStream(in,f);toast("⬆️ Upload complete");showPage(1);
            }
        }catch(Exception e){toast("Operation failed: "+e.getMessage());}
    }
    private void copyStream(InputStream in,File out)throws Exception{if(in==null)throw new IOException("Input unavailable");File p=out.getParentFile();if(p!=null)p.mkdirs();FileOutputStream o=new FileOutputStream(out);byte[] b=new byte[16384];int n;while((n=in.read(b))>0)o.write(b,0,n);in.close();o.close();}
}
