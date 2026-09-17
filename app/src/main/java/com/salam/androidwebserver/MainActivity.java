package com.salam.androidwebserver;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import java.io.*;
import java.util.*;
import java.util.zip.*;

public class MainActivity extends AppCompatActivity {
    static final String PREF="server_prefs";
    LinearLayout root, content;
    TextView status, url, stats, logView, ipListView;
    EditText port, password, ipEdit;
    CheckBox protect;
    Switch accessOnly;
    SharedPreferences sp;
    File www;
    ActivityResultLauncher<String[]> filePicker;

    int bg=Color.rgb(10,13,18), card=Color.rgb(20,25,32), text=Color.WHITE, muted=Color.rgb(155,165,176), accent=Color.rgb(70,210,120);

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        sp=getSharedPreferences(PREF,MODE_PRIVATE);
        www=new File(getFilesDir(),"www"); if(!www.exists()) www.mkdirs();
        if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},55);
        filePicker=registerForActivityResult(new ActivityResultContracts.OpenMultipleDocuments(), uris->{
            if(uris==null)return;
            for(Uri u:uris) importUri(u);
            refreshFiles();
        });
        buildUI();
        refresh();
    }

    TextView tv(String s,float size){ TextView t=new TextView(this); t.setText(s); t.setTextColor(text); t.setTextSize(size); t.setPadding(0,4,0,4); return t; }
    TextView small(String s){ TextView t=tv(s,13); t.setTextColor(muted); return t; }
    LinearLayout card(String title){
        LinearLayout c=new LinearLayout(this); c.setOrientation(LinearLayout.VERTICAL); c.setPadding(18,16,18,16);
        GradientDrawable gd=new GradientDrawable(); gd.setColor(card); gd.setCornerRadius(22); c.setBackground(gd);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2); lp.setMargins(12,8,12,8); c.setLayoutParams(lp);
        TextView h=tv(title,18); h.setTypeface(null,1); h.setPadding(0,0,0,12); c.addView(h);
        return c;
    }
    Button btn(String s){
        Button b=new Button(this); b.setText(s); b.setTextSize(13); b.setAllCaps(false); b.setTextColor(Color.WHITE);
        b.setBackgroundColor(Color.rgb(36,45,55)); b.setPadding(12,2,12,2);
        b.setLayoutParams(new LinearLayout.LayoutParams(-1,52)); return b;
    }
    void gap(LinearLayout l){ Space s=new Space(this); l.addView(s,new LinearLayout.LayoutParams(1,8)); }

    void buildUI(){
        ScrollView sv=new ScrollView(this); sv.setFillViewport(true); root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(12,14,12,28); root.setBackgroundColor(bg); sv.addView(root);
        TextView head=tv("SALAM WEB SERVER PRO",25); head.setTypeface(null,1); head.setPadding(8,8,8,2); root.addView(head);
        TextView sub=small("LOCAL HTTP SERVER  •  FILES  •  ACCESS CONTROL  •  LIVE LOGS"); sub.setPadding(8,0,8,14); root.addView(sub);
        content=new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL); root.addView(content);
        setContentView(sv);
    }

    void refresh(){
        content.removeAllViews();
        LinearLayout dash=card("SERVER CONTROL");
        status=tv("●  SERVER STOPPED",17); status.setTextColor(Color.rgb(255,90,90)); dash.addView(status);
        url=tv("URL: —",16); url.setPadding(0,8,0,2); dash.addView(url);
        stats=small("Requests: 0   •   Clients: 0   •   Uptime: —"); dash.addView(stats);
        LinearLayout row=new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL);
        port=new EditText(this); port.setText(String.valueOf(sp.getInt("port",8080))); port.setTextColor(Color.WHITE); port.setHint("Port"); port.setSingleLine(); port.setInputType(InputType.TYPE_CLASS_NUMBER);
        row.addView(port,new LinearLayout.LayoutParams(0,56,1));
        Button start=btn("START"); start.setOnClickListener(v->startServer());
        Button stop=btn("STOP"); stop.setOnClickListener(v->stopServer());
        row.addView(start,new LinearLayout.LayoutParams(0,56,1)); row.addView(stop,new LinearLayout.LayoutParams(0,56,1)); dash.addView(row);
        Button open=btn("OPEN WEBSITE"); open.setOnClickListener(v->openSite()); dash.addView(open); gap(dash);
        Button admin=btn("OPEN SERVER ADMIN"); admin.setOnClickListener(v->openAdmin()); dash.addView(admin);
        Button qr=btn("SHOW QR CODE"); qr.setOnClickListener(v->showQr()); dash.addView(qr);
        content.addView(dash);

        LinearLayout sec=card("SECURITY & ACCESS CONTROL");
        protect=new CheckBox(this); protect.setText("Website password protection"); protect.setTextColor(text); protect.setChecked(sp.getBoolean("protect",false)); sec.addView(protect);
        password=new EditText(this); password.setHint("Admin / website password"); password.setTextColor(text); password.setHintTextColor(muted); password.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD); password.setText(sp.getString("password","")); sec.addView(password);
        accessOnly=new Switch(this); accessOnly.setText("ALLOW-LIST MODE (only allowed IPs can connect)"); accessOnly.setTextColor(text); accessOnly.setChecked(sp.getBoolean("allowOnly",false)); sec.addView(accessOnly);
        ipEdit=new EditText(this); ipEdit.setHint("IP address, e.g. 192.168.0.20"); ipEdit.setTextColor(text); ipEdit.setHintTextColor(muted); sec.addView(ipEdit);
        LinearLayout ar=new LinearLayout(this);
        Button add=btn("ADD ALLOWED IP"); add.setOnClickListener(v->addIp()); Button rem=btn("REMOVE IP"); rem.setOnClickListener(v->removeIp());
        ar.addView(add,new LinearLayout.LayoutParams(0,54,1)); ar.addView(rem,new LinearLayout.LayoutParams(0,54,1)); sec.addView(ar);
        ipListView=small(""); sec.addView(ipListView);
        Button save=btn("SAVE SECURITY SETTINGS"); save.setOnClickListener(v->saveSecurity()); sec.addView(save);
        content.addView(sec);

        LinearLayout files=card("FILE MANAGER");
        Button imp=btn("IMPORT FILES"); imp.setOnClickListener(v->filePicker.launch(new String[]{"text/*","image/*","application/pdf","application/zip","*/*"})); files.addView(imp);
        Button nf=btn("NEW FOLDER"); nf.setOnClickListener(v->newName(false)); files.addView(nf);
        Button nfile=btn("NEW FILE"); nfile.setOnClickListener(v->newName(true)); files.addView(nfile);
        Button refresh=btn("REFRESH FILES"); refresh.setOnClickListener(v->refreshFiles()); files.addView(refresh);
        LinearLayout list=new LinearLayout(this); list.setOrientation(LinearLayout.VERTICAL); files.addView(list); content.addView(files);
        refreshFilesInto(list);

        LinearLayout logs=card("LIVE SERVER LOGS");
        logView=small("No requests yet."); logView.setTypeface(android.graphics.Typeface.MONOSPACE); logs.addView(logView);
        Button clear=btn("CLEAR LOGS"); clear.setOnClickListener(v->{WebServerService.clearLogs(); refresh();}); logs.addView(clear);
        content.addView(logs);
        updateIpList();
    }

    void startServer(){
        int p=8080; try{p=Integer.parseInt(port.getText().toString());}catch(Exception ignored){}
        if(p<1024||p>65535){toast("Port must be 1024-65535");return;}
        sp.edit().putInt("port",p).apply();
        Intent i=new Intent(this,WebServerService.class); i.setAction("START"); startForegroundService(i); refresh(); toast("Server starting");
    }
    void stopServer(){ Intent i=new Intent(this,WebServerService.class); i.setAction("STOP"); startService(i); refresh(); toast("Server stopped"); }
    void openSite(){String u=WebServerService.currentUrl(this); if(u==null){toast("Start server first");return;} startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(u)));}
    void openAdmin(){String u=WebServerService.currentUrl(this); if(u==null){toast("Start server first");return;} startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(u+"/__admin")));}
    void showQr(){ toast("QR feature can be kept from the existing ZXing dependency."); }
    void saveSecurity(){sp.edit().putBoolean("protect",protect.isChecked()).putString("password",password.getText().toString()).putBoolean("allowOnly",accessOnly.isChecked()).apply(); WebServerService.reloadConfig(); toast("Security settings saved");}
    void addIp(){String ip=ipEdit.getText().toString().trim(); if(ip.isEmpty())return; Set<String> s=new HashSet<>(sp.getStringSet("allowedIps",new HashSet<>())); s.add(ip); sp.edit().putStringSet("allowedIps",s).apply(); ipEdit.setText(""); updateIpList();}
    void removeIp(){String ip=ipEdit.getText().toString().trim(); Set<String> s=new HashSet<>(sp.getStringSet("allowedIps",new HashSet<>())); s.remove(ip); sp.edit().putStringSet("allowedIps",s).apply(); updateIpList();}
    void updateIpList(){if(ipListView==null)return; Set<String>s=sp.getStringSet("allowedIps",new HashSet<>()); ipListView.setText(s.isEmpty()?"Allowed IPs: none": "Allowed IPs:\n• "+String.join("\n• ",s));}
    void refreshFiles(){buildUI(); refresh();}
    void refreshFilesInto(LinearLayout list){
        File[] fs=www.listFiles(); if(fs==null||fs.length==0){list.addView(small("No website files."));return;}
        Arrays.sort(fs,Comparator.comparing(File::getName,String.CASE_INSENSITIVE_ORDER));
        for(File f:fs){
            LinearLayout r=new LinearLayout(this); r.setGravity(Gravity.CENTER_VERTICAL);
            TextView n=tv((f.isDirectory()?"📁 ":"📄 ")+f.getName(),15); r.addView(n,new LinearLayout.LayoutParams(0,54,1));
            Button e=btn(f.isDirectory()?"OPEN":"EDIT"); e.setOnClickListener(v->{if(f.isDirectory()) openFolder(f); else editFile(f);}); r.addView(e,new LinearLayout.LayoutParams(0,50,1));
            Button d=btn("DELETE"); d.setOnClickListener(v->{deleteRecursive(f); refresh();}); r.addView(d,new LinearLayout.LayoutParams(0,50,1)); list.addView(r);
        }
    }
    void openFolder(File f){new AlertDialog.Builder(this).setTitle(f.getName()).setMessage("Folder path:\n"+f.getAbsolutePath()).setPositiveButton("OK",null).show();}
    void editFile(File f){
        EditText e=new EditText(this); e.setTextColor(Color.WHITE); e.setTextSize(13); e.setGravity(Gravity.TOP); e.setSingleLine(false);
        try{e.setText(read(f));}catch(Exception ex){toast(ex.toString());return;}
        ScrollView s=new ScrollView(this); s.addView(e);
        new AlertDialog.Builder(this).setTitle("Edit • "+f.getName()).setView(s).setNegativeButton("CANCEL",null).setPositiveButton("SAVE",(d,w)->{try{write(f,e.getText().toString());toast("Saved");}catch(Exception ex){toast(ex.toString());}}).show();
    }
    void newName(boolean file){
        EditText e=new EditText(this); e.setHint(file?"index.html":"folder-name"); e.setTextColor(Color.WHITE);
        new AlertDialog.Builder(this).setTitle(file?"New File":"New Folder").setView(e).setNegativeButton("CANCEL",null).setPositiveButton("CREATE",(d,w)->{
            String n=e.getText().toString().trim(); if(n.isEmpty())return; File f=new File(www,n);
            try{if(file){f.createNewFile();}else f.mkdirs();refresh();}catch(Exception ex){toast(ex.toString());}
        }).show();
    }
    void importUri(Uri u){String name=System.currentTimeMillis()+"_"+new File(u.getPath()==null?"file":u.getPath()).getName(); if(name.length()>80)name=name.substring(name.length()-80); File out=new File(www,name);
        try(InputStream in=getContentResolver().openInputStream(u);OutputStream o=new FileOutputStream(out)){byte[]b=new byte[8192];int n;while((n=in.read(b))>0)o.write(b,0,n); }catch(Exception e){toast(e.toString());}
    }
    static String read(File f)throws Exception{BufferedReader r=new BufferedReader(new InputStreamReader(new FileInputStream(f),"UTF-8"));StringBuilder s=new StringBuilder();String x;while((x=r.readLine())!=null)s.append(x).append('\n');r.close();return s.toString();}
    static void write(File f,String s)throws Exception{try(Writer w=new OutputStreamWriter(new FileOutputStream(f),"UTF-8")){w.write(s);}}
    static void deleteRecursive(File f){if(f.isDirectory()){File[]x=f.listFiles();if(x!=null)for(File c:x)deleteRecursive(c);}f.delete();}
    void toast(String s){Toast.makeText(this,s,Toast.LENGTH_SHORT).show();}
    @Override protected void onResume(){super.onResume(); if(content!=null) refresh();}
}
