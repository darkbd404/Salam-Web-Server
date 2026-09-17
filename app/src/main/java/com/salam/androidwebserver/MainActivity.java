package com.salam.androidwebserver;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.journeyapps.barcodescanner.BarcodeEncoder;
import java.io.*;
import java.util.*;
import java.util.zip.*;

public class MainActivity extends AppCompatActivity {
    int bg=Color.rgb(10,13,18), card=Color.rgb(21,27,34), text=Color.rgb(244,247,250), muted=Color.rgb(160,170,180), green=Color.rgb(98,230,165), red=Color.rgb(255,92,92), blue=Color.rgb(92,170,255);
    LinearLayout root, body, filesBox, logsBox; TextView status,url,stats,uptime; EditText port, password, customPath, allowIps, blockIps; CheckBox allowOnly, autoStart;
    Handler h=new Handler(Looper.getMainLooper()); int pickMode=0;
    @Override public void onCreate(Bundle b){super.onCreate(b); getWindow().setStatusBarColor(bg); getWindow().setNavigationBarColor(bg); build(); if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED) requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},99); h.post(tick);}
    TextView tv(String s,float sp){TextView v=new TextView(this);v.setText(s);v.setTextColor(text);v.setTextSize(sp);v.setPadding(0,0,0,0);return v;}
    GradientDrawable gd(int c,float r){GradientDrawable g=new GradientDrawable();g.setColor(c);g.setCornerRadius(r);return g;}
    Button btn(String s){Button b=new Button(this);b.setText(s);b.setTextColor(text);b.setAllCaps(false);b.setTextSize(14);b.setBackground(gd(card,18));b.setPadding(18,8,18,8);return b;}
    EditText ed(String hint){EditText e=new EditText(this);e.setHint(hint);e.setHintTextColor(muted);e.setTextColor(text);e.setSingleLine(true);e.setTextSize(15);e.setPadding(14,8,14,8);e.setBackground(gd(Color.rgb(16,21,27),14));return e;}
    LinearLayout row(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.HORIZONTAL);l.setGravity(Gravity.CENTER_VERTICAL);l.setPadding(0,5,0,5);return l;}
    void add(View v,int w,int h){body.addView(v,new LinearLayout.LayoutParams(w,h));}
    void build(){
        ScrollView sv=new ScrollView(this); root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(18,18,18,28);root.setBackgroundColor(bg);sv.addView(root);setContentView(sv);
        TextView title=tv("SALAM  /  WEB SERVER PRO",25); title.setTypeface(null,1); root.addView(title,new LinearLayout.LayoutParams(-1,55));
        TextView sub=tv("LOCAL HTTP CONTROL PANEL",11);sub.setTextColor(green);root.addView(sub,new LinearLayout.LayoutParams(-1,28));
        LinearLayout dash=new LinearLayout(this);dash.setOrientation(LinearLayout.VERTICAL);dash.setPadding(18,16,18,16);dash.setBackground(gd(card,22));root.addView(dash,new LinearLayout.LayoutParams(-1,-2));
        status=tv("●  SERVER STOPPED",18);status.setTypeface(null,1);dash.addView(status); url=tv("http://0.0.0.0:8080",14);url.setTextColor(muted);dash.addView(url); stats=tv("Requests 0   •   Clients 0",14);stats.setTextColor(muted);dash.addView(stats);uptime=tv("Uptime 00:00:00",13);uptime.setTextColor(muted);dash.addView(uptime);
        LinearLayout r=row(); port=ed("Port");port.setText("8080");r.addView(port,new LinearLayout.LayoutParams(0,58,1)); Button start=btn("START");Button stop=btn("STOP");r.addView(start,new LinearLayout.LayoutParams(105,58));r.addView(stop,new LinearLayout.LayoutParams(105,58));dash.addView(r);
        LinearLayout actions=new LinearLayout(this);actions.setOrientation(LinearLayout.VERTICAL);root.addView(actions,new LinearLayout.LayoutParams(-1,-2));
        Button open=btn("OPEN WEBSITE   ›"), admin=btn("OPEN ADMIN DASHBOARD   ›"), qr=btn("SHOW QR CODE"), importB=btn("IMPORT FILES"), zip=btn("UPLOAD ZIP & EXTRACT"), refresh=btn("REFRESH FILES"); for(Button x:new Button[]{open,admin,qr,importB,zip,refresh}){actions.addView(x,new LinearLayout.LayoutParams(-1,54));}
        open.setOnClickListener(v->openUrl(false));admin.setOnClickListener(v->openUrl(true));qr.setOnClickListener(v->showQr());start.setOnClickListener(v->startServer());stop.setOnClickListener(v->stopServer());refresh.setOnClickListener(v->refreshFiles());importB.setOnClickListener(v->{pickMode=1;Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("*/*");i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);i.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,10);});zip.setOnClickListener(v->{pickMode=2;Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("application/zip");i.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,10);});
        section("ACCESS CONTROL"); allowOnly=new CheckBox(this);allowOnly.setText("Allow-list only (deny every IP not listed)");allowOnly.setTextColor(text);root.addView(allowOnly);allowIps=ed("Allowed IPs — comma separated");blockIps=ed("Blocked IPs — comma separated");root.addView(allowIps,new LinearLayout.LayoutParams(-1,55));root.addView(blockIps,new LinearLayout.LayoutParams(-1,55));
        section("WEBSITE SECURITY"); password=ed("Admin / website password (optional)");password.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);root.addView(password,new LinearLayout.LayoutParams(-1,55)); autoStart=new CheckBox(this);autoStart.setText("Start server automatically when app opens");autoStart.setTextColor(text);root.addView(autoStart);Button save=btn("SAVE ALL SERVER SETTINGS");root.addView(save,new LinearLayout.LayoutParams(-1,54));save.setOnClickListener(v->saveSettings());
        section("CUSTOM WEB PATH");customPath=ed("Custom URL prefix, e.g. /site");root.addView(customPath,new LinearLayout.LayoutParams(-1,55));
        section("WEBSITE FILES");filesBox=new LinearLayout(this);filesBox.setOrientation(LinearLayout.VERTICAL);root.addView(filesBox,new LinearLayout.LayoutParams(-1,-2));
        section("SERVER LOGS");logsBox=new LinearLayout(this);logsBox.setOrientation(LinearLayout.VERTICAL);root.addView(logsBox,new LinearLayout.LayoutParams(-1,-2));
        TextView foot=tv("LAN SERVER • No cloud relay • Your phone controls the server",11);foot.setTextColor(muted);foot.setPadding(0,25,0,0);root.addView(foot);
        loadSettings();refreshFiles();
    }
    void section(String s){TextView x=tv(s,13);x.setTextColor(green);x.setTypeface(null,1);x.setPadding(0,22,0,8);root.addView(x);}
    void startServer(){int p=8080;try{p=Integer.parseInt(port.getText().toString().trim());}catch(Exception e){} if(p<1024||p>65535){toast("Port must be 1024–65535");return;} getSharedPreferences("server",MODE_PRIVATE).edit().putInt("port",p).apply();Intent i=new Intent(this,WebServerService.class);i.setAction("START");i.putExtra("port",p);if(Build.VERSION.SDK_INT>=26)startForegroundService(i);else startService(i);}
    void stopServer(){Intent i=new Intent(this,WebServerService.class);i.setAction("STOP");startService(i);}
    void saveSettings(){getSharedPreferences("server",MODE_PRIVATE).edit().putString("password",password.getText().toString()).putBoolean("allowOnly",allowOnly.isChecked()).putString("allowIps",allowIps.getText().toString()).putString("blockIps",blockIps.getText().toString()).putBoolean("autoStart",autoStart.isChecked()).putString("customPath",normalizePath(customPath.getText().toString())).apply();WebServerService.applySettings(this);toast("Settings saved");if(autoStart.isChecked())startServer();}
    void loadSettings(){android.content.SharedPreferences p=getSharedPreferences("server",MODE_PRIVATE);port.setText(String.valueOf(p.getInt("port",8080)));password.setText(p.getString("password",""));allowOnly.setChecked(p.getBoolean("allowOnly",false));allowIps.setText(p.getString("allowIps",""));blockIps.setText(p.getString("blockIps",""));autoStart.setChecked(p.getBoolean("autoStart",false));customPath.setText(p.getString("customPath","/"));}
    String normalizePath(String x){if(x==null||x.trim().isEmpty())return "/";x=x.trim();if(!x.startsWith("/"))x="/"+x;if(x.length()>1&&x.endsWith("/"))x=x.substring(0,x.length()-1);return x;}
    void openUrl(boolean admin){String u=WebServerService.currentUrl(this)+(admin?"/__admin":"");startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(u)));}
    void showQr(){try{String u=WebServerService.currentUrl(this);android.graphics.Bitmap bm=new BarcodeEncoder().encodeBitmap(u,BarcodeFormat.QR_CODE,700,700);ImageView im=new ImageView(this);im.setImageBitmap(bm);im.setPadding(25,25,25,25);new AlertDialog.Builder(this).setTitle("Server QR").setView(im).setMessage(u).setPositiveButton("Close",null).show();}catch(WriterException e){toast("QR generation failed");}}
    @Override protected void onActivityResult(int r,int c,Intent d){super.onActivityResult(r,c,d);if(r!=10||c!=RESULT_OK||d==null)return;try{if(d.getClipData()!=null){for(int i=0;i<d.getClipData().getItemCount();i++)importUri(d.getClipData().getItemAt(i).getUri());}else importUri(d.getData());}catch(Exception e){toast(e.getMessage());}refreshFiles();}
    void importUri(Uri u)throws Exception{if(pickMode==2){extractZip(u);return;}String name=WebServerService.displayName(this,u);File out=new File(WebServerService.webRoot(this),name);try(InputStream in=getContentResolver().openInputStream(u);OutputStream o=new FileOutputStream(out)){byte[] b=new byte[8192];int n;while((n=in.read(b))>0)o.write(b,0,n);}}
    void extractZip(Uri u)throws Exception{File root=WebServerService.webRoot(this);try(InputStream in=getContentResolver().openInputStream(u);ZipInputStream z=new ZipInputStream(in)){ZipEntry e;while((e=z.getNextEntry())!=null){File out=new File(root,e.getName());String cp=root.getCanonicalPath(),op=out.getCanonicalPath();if(!op.equals(cp)&&!op.startsWith(cp+File.separator))continue;if(e.isDirectory())out.mkdirs();else{File par=out.getParentFile();if(par!=null)par.mkdirs();try(OutputStream o=new FileOutputStream(out)){byte[] b=new byte[8192];int n;while((n=z.read(b))>0)o.write(b,0,n);}}}}}
    void refreshFiles(){if(filesBox==null)return;filesBox.removeAllViews();File r=WebServerService.webRoot(this);File[] fs=r.listFiles();if(fs==null)return;Arrays.sort(fs,Comparator.comparing(File::getName,String.CASE_INSENSITIVE_ORDER));for(File f:fs){LinearLayout x=row();TextView n=tv((f.isDirectory()?"📁  ":"📄  ")+f.getName(),14);x.addView(n,new LinearLayout.LayoutParams(0,52,1));Button e=btn(f.isDirectory()?"OPEN":"EDIT");x.addView(e,new LinearLayout.LayoutParams(90,52));Button more=btn("⋮");x.addView(more,new LinearLayout.LayoutParams(60,52));if(f.isDirectory())e.setOnClickListener(v->showDir(f));else e.setOnClickListener(v->editFile(f));more.setOnClickListener(v->fileMenu(f));filesBox.addView(x);}}
    void showDir(File dir){toast(dir.getAbsolutePath());}
    void editFile(File f){try{String s=read(f);EditText e=new EditText(this);e.setText(s);e.setTextColor(text);e.setBackgroundColor(Color.rgb(12,16,21));e.setGravity(Gravity.TOP|Gravity.START);e.setMinLines(12);e.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE|InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);new AlertDialog.Builder(this).setTitle("Edit • "+f.getName()).setView(e).setNegativeButton("Cancel",null).setPositiveButton("Save",(d,w)->{try{write(f,e.getText().toString());refreshFiles();}catch(Exception ex){toast(ex.getMessage());}}).show();}catch(Exception e){toast(e.getMessage());}}
    String read(File f)throws Exception{if(f.length()>2_000_000)throw new Exception("File too large for editor");byte[] b=java.nio.file.Files.readAllBytes(f.toPath());return new String(b,java.nio.charset.StandardCharsets.UTF_8);}
    void write(File f,String s)throws Exception{try(FileOutputStream o=new FileOutputStream(f)){o.write(s.getBytes(java.nio.charset.StandardCharsets.UTF_8));}}
    void fileMenu(File f){String[] a={"Rename","Delete","Create folder here","Create file here"};new AlertDialog.Builder(this).setTitle(f.getName()).setItems(a,(d,w)->{if(w==0)rename(f);else if(w==1){if(f.delete())refreshFiles();else toast("Delete failed");}else if(w==2)newName(f.getParentFile(),true);else newName(f.getParentFile(),false);}).show();}
    void rename(File f){EditText e=ed("New name");e.setText(f.getName());new AlertDialog.Builder(this).setTitle("Rename").setView(e).setPositiveButton("Save",(d,w)->{File n=new File(f.getParentFile(),e.getText().toString().trim());if(!safeChild(f.getParentFile(),n)||!f.renameTo(n))toast("Rename failed");refreshFiles();}).setNegativeButton("Cancel",null).show();}
    void newName(File dir,boolean folder){EditText e=ed(folder?"Folder name":"File name");new AlertDialog.Builder(this).setTitle(folder?"New folder":"New file").setView(e).setPositiveButton("Create",(d,w)->{String s=e.getText().toString().trim();File n=new File(dir,s);if(!safeChild(dir,n)){toast("Invalid name");return;}try{if(folder)n.mkdirs();else n.createNewFile();refreshFiles();}catch(Exception ex){toast(ex.getMessage());}}).setNegativeButton("Cancel",null).show();}
    boolean safeChild(File dir,File x){try{return x.getCanonicalPath().startsWith(dir.getCanonicalPath()+File.separator);}catch(Exception e){return false;}}
    void refreshLogs(){if(logsBox==null)return;logsBox.removeAllViews();TextView l=tv(WebServerService.logsText(),12);l.setTextColor(muted);l.setTextIsSelectable(true);l.setTypeface(android.graphics.Typeface.MONOSPACE);logsBox.addView(l);}
    void tick(){if(isFinishing())return;status.setText(WebServerService.running?"●  SERVER RUNNING":"●  SERVER STOPPED");status.setTextColor(WebServerService.running?green:red);url.setText(WebServerService.currentUrl(this));stats.setText("Requests "+WebServerService.requests+"   •   Clients "+WebServerService.clients.size());uptime.setText("Uptime "+WebServerService.uptime());refreshLogs();h.postDelayed(this::tick,1000);}
    void toast(String s){Toast.makeText(this,s==null?"Error":s,Toast.LENGTH_SHORT).show();}
}
