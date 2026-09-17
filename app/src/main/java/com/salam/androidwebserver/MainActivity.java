package com.salam.androidwebserver;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.journeyapps.barcodescanner.BarcodeEncoder;

import android.graphics.Bitmap;
import android.widget.ImageView;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.NetworkInterface;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class MainActivity extends AppCompatActivity {

    private static final int BG = Color.rgb(10, 12, 18);
    private static final int CARD = Color.rgb(22, 26, 34);
    private static final int CARD2 = Color.rgb(28, 33, 43);
    private static final int TEXT = Color.WHITE;
    private static final int MUTED = Color.rgb(170, 178, 190);
    private static final int GREEN = Color.rgb(50, 210, 110);
    private static final int RED = Color.rgb(240, 75, 85);
    private static final int BLUE = Color.rgb(70, 145, 255);

    private LinearLayout root;
    private LinearLayout content;

    private EditText portInput;
    private EditText passwordInput;
    private CheckBox passwordCheck;

    private TextView statusText;
    private TextView urlText;
    private TextView requestText;
    private TextView clientText;

    private File webRoot;

    private final ArrayList<String> logs = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        webRoot = new File(getFilesDir(), "www");

        if (!webRoot.exists()) {
            webRoot.mkdirs();
        }

        createDefaultWebsite();
        buildUI();
        refreshUI();

        addLog("Application started");
    }

    // =========================================================
    // MAIN UI
    // =========================================================

    private void buildUI() {

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(18), dp(16), dp(30));

        scroll.addView(content);

        root.addView(scroll,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                ));

        setContentView(root);

        addHeader();
        addServerCard();
        addServerControls();
        addSecurityCard();
        addNetworkCard();
        addFileManager();
        addLogCard();
        addDeveloperCard();
    }

    private void addHeader() {

        TextView title = text(
                "SALAM WEB SERVER PRO",
                25,
                TEXT,
                Typeface.BOLD
        );

        title.setGravity(Gravity.CENTER);
        content.addView(title, lp(-1, -2, 0, 0, 0, 8));

        TextView subtitle = text(
                "Android Local HTTP Server",
                13,
                MUTED,
                Typeface.NORMAL
        );

        subtitle.setGravity(Gravity.CENTER);
        content.addView(subtitle, lp(-1, -2, 0, 0, 0, 18));
    }

    private void addServerCard() {

        LinearLayout card = card();

        statusText = text("● SERVER STOPPED", 18, RED, Typeface.BOLD);
        statusText.setGravity(Gravity.CENTER);

        urlText = text("http://0.0.0.0:8080", 16, TEXT, Typeface.BOLD);
        urlText.setGravity(Gravity.CENTER);

        requestText = text("Requests: 0", 14, MUTED, Typeface.NORMAL);
        requestText.setGravity(Gravity.CENTER);

        clientText = text("Clients: 0", 14, MUTED, Typeface.NORMAL);
        clientText.setGravity(Gravity.CENTER);

        card.addView(statusText, lp(-1, -2, 0, 0, 0, 8));
        card.addView(urlText, lp(-1, -2, 0, 0, 0, 8));

        LinearLayout stats = new LinearLayout(this);
        stats.setGravity(Gravity.CENTER);

        stats.addView(requestText, lp(0, -2, 1, 0, 0, 0));
        stats.addView(clientText, lp(0, -2, 1, 0, 0, 0));

        card.addView(stats);

        content.addView(card, lp(-1, -2, 0, 0, 0, 12));
    }

    private void addServerControls() {

        LinearLayout card = card();

        portInput = edit("8080");
        portInput.setInputType(InputType.TYPE_CLASS_NUMBER);

        card.addView(label("SERVER PORT"));
        card.addView(portInput, lp(-1, -2, 0, 4, 0, 10));

        LinearLayout row = new LinearLayout(this);

        Button start = button("START SERVER", GREEN);
        Button stop = button("STOP SERVER", RED);

        start.setOnClickListener(v -> startServer());
        stop.setOnClickListener(v -> stopServer());

        row.addView(start, lp(0, -2, 1, 0, 6, 0));
        row.addView(stop, lp(0, -2, 1, 6, 0, 0));

        card.addView(row);

        Button open = button("OPEN WEBSITE", BLUE);
        open.setOnClickListener(v -> openWebsite());

        card.addView(open, lp(-1, -2, 0, 0, 0, 8));

        Button copy = button("COPY SERVER URL", Color.rgb(90, 110, 130));
        copy.setOnClickListener(v -> copyUrl());

        card.addView(copy);

        Button qr = button("SHOW QR CODE", Color.rgb(120, 90, 210));
        qr.setOnClickListener(v -> showQR());

        card.addView(qr, lp(-1, -2, 0, 0, 0, 8));

        content.addView(card, lp(-1, -2, 0, 0, 0, 12));
    }

    // =========================================================
    // SECURITY
    // =========================================================

    private void addSecurityCard() {

        LinearLayout card = card();

        card.addView(sectionTitle("WEB SECURITY"));

        passwordCheck = new CheckBox(this);
        passwordCheck.setText("Require Website Password");
        passwordCheck.setTextColor(TEXT);
        passwordCheck.setTextSize(15);

        card.addView(passwordCheck);

        passwordInput = edit("Password");
        passwordInput.setInputType(
                InputType.TYPE_CLASS_TEXT |
                        InputType.TYPE_TEXT_VARIATION_PASSWORD
        );

        card.addView(passwordInput, lp(-1, -2, 0, 0, 0, 8));

        Button save = button("SAVE SECURITY SETTINGS", BLUE);

        save.setOnClickListener(v -> {
            getSharedPreferences("security", MODE_PRIVATE)
                    .edit()
                    .putBoolean("enabled", passwordCheck.isChecked())
                    .putString("password", passwordInput.getText().toString())
                    .apply();

            addLog("Security settings changed");
            toast("Security settings saved");
        });

        card.addView(save);

        content.addView(card, lp(-1, -2, 0, 0, 0, 12));
    }

    // =========================================================
    // NETWORK
    // =========================================================

    private void addNetworkCard() {

        LinearLayout card = card();

        card.addView(sectionTitle("NETWORK ACCESS CONTROL"));

        Button devices = button("VIEW CONNECTED CLIENTS", BLUE);

        devices.setOnClickListener(v -> showConnectedInfo());

        card.addView(devices, lp(-1, -2, 0, 0, 0, 8));

        Button ipInfo = button("SHOW PHONE IP ADDRESSES", Color.rgb(80, 130, 180));

        ipInfo.setOnClickListener(v -> showIPInfo());

        card.addView(ipInfo);

        Button network = button("NETWORK / INTERNET SETTINGS",
                Color.rgb(100, 100, 120));

        network.setOnClickListener(v -> {
            try {
                startActivity(
                        new Intent(Settings.ACTION_WIRELESS_SETTINGS)
                );
            } catch (Exception e) {
                toast("Settings unavailable");
            }
        });

        card.addView(network, lp(-1, -2, 0, 0, 0, 8));

        TextView note = text(
                "Server is intended for trusted local networks. " +
                        "Do not expose an unsecured server directly to the public Internet.",
                12,
                MUTED,
                Typeface.NORMAL
        );

        note.setPadding(0, dp(8), 0, 0);

        card.addView(note);

        content.addView(card, lp(-1, -2, 0, 0, 0, 12));
    }

    // =========================================================
    // FILE MANAGER
    // =========================================================

    private void addFileManager() {

        LinearLayout card = card();

        card.addView(sectionTitle("WEBSITE FILE MANAGER"));

        Button importFiles = button("IMPORT FILES", BLUE);

        importFiles.setOnClickListener(v -> chooseFiles());

        card.addView(importFiles, lp(-1, -2, 0, 0, 0, 8));

        Button zip = button("UPLOAD ZIP & EXTRACT",
                Color.rgb(150, 100, 220));

        zip.setOnClickListener(v -> chooseZip());

        card.addView(zip, lp(-1, -2, 0, 0, 0, 8));

        Button folder = button("NEW FOLDER", Color.rgb(70, 150, 120));

        folder.setOnClickListener(v -> newFolderDialog());

        card.addView(folder, lp(-1, -2, 0, 0, 0, 8));

        Button file = button("NEW FILE", Color.rgb(70, 130, 180));

        file.setOnClickListener(v -> newFileDialog());

        card.addView(file, lp(-1, -2, 0, 0, 0, 8));

        Button refresh = button("REFRESH FILES",
                Color.rgb(100, 105, 120));

        refresh.setOnClickListener(v -> refreshFiles());

        card.addView(refresh, lp(-1, -2, 0, 0, 0, 10));

        LinearLayout fileList = new LinearLayout(this);
        fileList.setOrientation(LinearLayout.VERTICAL);

        card.addView(fileList);

        refreshFileList(fileList);

        content.addView(card, lp(-1, -2, 0, 0, 0, 12));
    }

    private void refreshFileList(LinearLayout list) {

        list.removeAllViews();

        File[] files = webRoot.listFiles();

        if (files == null || files.length == 0) {

            TextView empty = text(
                    "No website files",
                    14,
                    MUTED,
                    Typeface.NORMAL
            );

            list.addView(empty);
            return;
        }

        for (File f : files) {

            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);

            TextView name = text(
                    (f.isDirectory() ? "📁 " : "📄 ") + f.getName(),
                    14,
                    TEXT,
                    Typeface.BOLD
            );

            Button edit = button("EDIT", BLUE);

            edit.setOnClickListener(v -> editFile(f));

            Button more = button("⋮", Color.rgb(80, 85, 95));

            more.setOnClickListener(v -> fileMenu(f));

            row.addView(name, lp(0, -2, 1, 0, 5, 0));
            row.addView(edit, lp(-2, -2, 0, 3, 3, 0));
            row.addView(more, lp(-2, -2, 0, 3, 0, 0));

            list.addView(row,
                    lp(-1, -2, 0, 0, 0, 8));
        }
    }

    // =========================================================
    // LOGS
    // =========================================================

    private void addLogCard() {

        LinearLayout card = card();

        card.addView(sectionTitle("SERVER LOGS"));

        TextView logView = text(
                "No logs yet",
                12,
                MUTED,
                Typeface.NORMAL
        );

        logView.setTag("LOG_VIEW");

        card.addView(logView, lp(-1, -2, 0, 0, 0, 8));

        Button clear = button("CLEAR LOGS",
                Color.rgb(120, 75, 80));

        clear.setOnClickListener(v -> {
            logs.clear();
            updateLogView();
        });

        card.addView(clear);

        content.addView(card, lp(-1, -2, 0, 0, 0, 12));
    }

    // =========================================================
    // DEVELOPER
    // =========================================================

    private void addDeveloperCard() {

        LinearLayout card = card();

        card.addView(sectionTitle("ADVANCED / DEVELOPER"));

        TextView info = text(
                "Server root:\n" +
                        webRoot.getAbsolutePath() +
                        "\n\n" +
                        "Package:\n" +
                        getPackageName() +
                        "\n\n" +
                        "Android:\n" +
                        android.os.Build.VERSION.RELEASE +
                        "\nAPI: " +
                        android.os.Build.VERSION.SDK_INT,
                12,
                MUTED,
                Typeface.NORMAL
        );

        card.addView(info, lp(-1, -2, 0, 0, 0, 10));

        Button appInfo = button("APP INFORMATION",
                Color.rgb(75, 100, 140));

        appInfo.setOnClickListener(v ->
                showAppInfo()
        );

        card.addView(appInfo);

        content.addView(card);
    }

    // =========================================================
    // SERVER
    // =========================================================

    private void startServer() {

        String portString = portInput.getText().toString().trim();

        int port;

        try {
            port = Integer.parseInt(portString);

            if (port < 1024 || port > 65535) {
                toast("Port must be between 1024 and 65535");
                return;
            }

        } catch (Exception e) {
            toast("Invalid port");
            return;
        }

        Intent intent = new Intent(
                this,
                WebServerService.class
        );

        intent.setAction("START_SERVER");
        intent.putExtra("PORT", port);

        try {
            if (android.os.Build.VERSION.SDK_INT >= 26) {
                startForegroundService(intent);
            } else {
                startService(intent);
            }

            addLog("Server start requested on port " + port);

            toast("Server starting...");

            refreshUI();

        } catch (Exception e) {

            addLog("Server start error: " + e.getMessage());

            toast("Server error: " + e.getMessage());
        }
    }

    private void stopServer() {

        Intent intent = new Intent(
                this,
                WebServerService.class
        );

        intent.setAction("STOP_SERVER");

        try {
            startService(intent);
        } catch (Exception ignored) {
        }

        stopService(
                new Intent(
                        this,
                        WebServerService.class
                )
        );

        addLog("Server stopped");

        refreshUI();

        toast("Server stopped");
    }

    private void refreshUI() {

        boolean running = isServiceRunning();

        if (statusText != null) {

            if (running) {
                statusText.setText("● SERVER RUNNING");
                statusText.setTextColor(GREEN);
            } else {
                statusText.setText("● SERVER STOPPED");
                statusText.setTextColor(RED);
            }
        }

        if (urlText != null) {

            String port = "8080";

            if (portInput != null &&
                    !portInput.getText().toString().trim().isEmpty()) {

                port = portInput.getText().toString().trim();
            }

            urlText.setText(
                    "http://" + getLocalIp() + ":" + port
            );
        }

        if (requestText != null) {
            requestText.setText(
                    "Requests: " +
                            WebServerService.getRequestCount()
            );
        }

        if (clientText != null) {
            clientText.setText(
                    "Clients: " +
                            WebServerService.getClientCount()
            );
        }

        updateLogView();
    }

    private boolean isServiceRunning() {

        return WebServerService.isRunning();
    }

    // =========================================================
    // FILE IMPORT
    // =========================================================

    private void chooseFiles() {

        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);

        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);

        intent.addCategory(Intent.CATEGORY_OPENABLE);

        try {
            startActivityForResult(intent, 100);
        } catch (Exception e) {
            toast("File picker unavailable");
        }
    }

    private void chooseZip() {

        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);

        intent.setType("application/zip");
        intent.addCategory(Intent.CATEGORY_OPENABLE);

        try {
            startActivityForResult(intent, 101);
        } catch (Exception e) {
            toast("ZIP picker unavailable");
        }
    }

    @Override
    protected void onActivityResult(
            int requestCode,
            int resultCode,
            Intent data
    ) {

        super.onActivityResult(
                requestCode,
                resultCode,
                data
        );

        if (resultCode != RESULT_OK ||
                data == null) {
            return;
        }

        if (requestCode == 100) {

            if (data.getClipData() != null) {

                int count =
                        data.getClipData().getItemCount();

                for (int i = 0; i < count; i++) {

                    Uri uri =
                            data.getClipData()
                                    .getItemAt(i)
                                    .getUri();

                    copyUri(uri);
                }

            } else if (data.getData() != null) {

                copyUri(data.getData());
            }

            addLog("Files imported");

            refreshUI();

            recreate();

        } else if (requestCode == 101) {

            if (data.getData() != null) {

                extractZip(data.getData());

                addLog("ZIP extracted");

                recreate();
            }
        }
    }

    private void copyUri(Uri uri) {

        String name =
                getFileName(uri);

        if (name == null ||
                name.trim().isEmpty()) {

            name = "uploaded_file";
        }

        File destination =
                new File(webRoot, safeName(name));

        try {

            FileInputStream in =
                    (FileInputStream) null;

            android.os.ParcelFileDescriptor pfd =
                    getContentResolver()
                            .openFileDescriptor(uri, "r");

            if (pfd == null) {
                return;
            }

            java.io.FileInputStream input =
                    new java.io.FileInputStream(
                            pfd.getFileDescriptor()
                    );

            FileOutputStream output =
                    new FileOutputStream(destination);

            byte[] buffer =
                    new byte[8192];

            int length;

            while ((length =
                    input.read(buffer)) > 0) {

                output.write(
                        buffer,
                        0,
                        length
                );
            }

            output.flush();
            output.close();
            input.close();
            pfd.close();

        } catch (Exception e) {

            toast(
                    "Import failed: " +
                            e.getMessage()
            );
        }
    }

    private String getFileName(Uri uri) {

        String result = null;

        try {

            android.database.Cursor cursor =
                    getContentResolver()
                            .query(
                                    uri,
                                    null,
                                    null,
                                    null,
                                    null
                            );

            if (cursor != null) {

                int index =
                        cursor.getColumnIndex(
                                "_display_name"
                        );

                if (index >= 0 &&
                        cursor.moveToFirst()) {

                    result =
                            cursor.getString(index);
                }

                cursor.close();
            }

        } catch (Exception ignored) {
        }

        return result;
    }

    // =========================================================
    // ZIP
    // =========================================================

    private void extractZip(Uri uri) {

        try {

            android.os.ParcelFileDescriptor pfd =
                    getContentResolver()
                            .openFileDescriptor(uri, "r");

            if (pfd == null) {
                return;
            }

            FileInputStream input =
                    new FileInputStream(
                            pfd.getFileDescriptor()
                    );

            ZipInputStream zip =
                    new ZipInputStream(input);

            ZipEntry entry;

            byte[] buffer =
                    new byte[8192];

            while ((entry =
                    zip.getNextEntry()) != null) {

                String entryName =
                        entry.getName();

                if (entryName.contains("..")) {
                    zip.closeEntry();
                    continue;
                }

                File output =
                        new File(
                                webRoot,
                                entryName
                        );

                String rootPath =
                        webRoot
                                .getCanonicalPath();

                String outputPath =
                        output
                                .getCanonicalPath();

                if (!outputPath.startsWith(
                        rootPath +
                                File.separator)) {

                    zip.closeEntry();
                    continue;
                }

                if (entry.isDirectory()) {

                    output.mkdirs();

                } else {

                    File parent =
                            output.getParentFile();

                    if (parent != null) {
                        parent.mkdirs();
                    }

                    FileOutputStream out =
                            new FileOutputStream(
                                    output
                            );

                    int length;

                    while ((length =
                            zip.read(buffer)) > 0) {

                        out.write(
                                buffer,
                                0,
                                length
                        );
                    }

                    out.close();
                }

                zip.closeEntry();
            }

            zip.close();
            input.close();
            pfd.close();

            toast("ZIP extracted successfully");

        } catch (Exception e) {

            toast(
                    "ZIP extraction failed: " +
                            e.getMessage()
            );
        }
    }

    // =========================================================
    // NEW FOLDER
    // =========================================================

    private void newFolderDialog() {

        final EditText input =
                edit("Folder name");

        AlertDialog dialog =
                new AlertDialog.Builder(this)
                        .setTitle("New Folder")
                        .setView(input)
                        .setNegativeButton(
                                "CANCEL",
                                null
                        )
                        .setPositiveButton(
                                "CREATE",
                                null
                        )
                        .create();

        dialog.setOnShowListener(d ->
                dialog.getButton(
                        AlertDialog.BUTTON_POSITIVE
                ).setOnClickListener(v -> {

                    String name =
                            input.getText()
                                    .toString()
                                    .trim();

                    if (validName(name)) {

                        File folder =
                                new File(
                                        webRoot,
                                        name
                                );

                        if (folder.mkdirs()) {

                            toast("Folder created");

                            addLog(
                                    "Folder created: " +
                                            name
                            );

                            dialog.dismiss();

                            recreate();

                        } else {

                            toast(
                                    "Could not create folder"
                            );
                        }
                    }
                })
        );

        dialog.show();
    }

    // =========================================================
    // NEW FILE
    // =========================================================

    private void newFileDialog() {

        final EditText input =
                edit("example.html");

        AlertDialog dialog =
                new AlertDialog.Builder(this)
                        .setTitle("New File")
                        .setView(input)
                        .setNegativeButton(
                                "CANCEL",
                                null
                        )
                        .setPositiveButton(
                                "CREATE",
                                null
                        )
                        .create();

        dialog.setOnShowListener(d ->
                dialog.getButton(
                        AlertDialog.BUTTON_POSITIVE
                ).setOnClickListener(v -> {

                    String name =
                            input.getText()
                                    .toString()
                                    .trim();

                    if (!validName(name)) {
                        toast("Invalid file name");
                        return;
                    }

                    File file =
                            new File(
                                    webRoot,
                                    name
                            );

                    try {

                        if (file.createNewFile()) {

                            addLog(
                                    "File created: " +
                                            name
                            );

                            toast("File created");

                            dialog.dismiss();

                            recreate();

                        } else {

                            toast(
                                    "File already exists"
                            );
                        }

                    } catch (Exception e) {

                        toast(
                                "Create failed"
                        );
                    }
                })
        );

        dialog.show();
    }

    // =========================================================
    // FILE MENU
    // =========================================================

    private void fileMenu(File file) {

        String[] options = {
                "Rename",
                "Delete",
                "Open"
        };

        new AlertDialog.Builder(this)
                .setTitle(file.getName())
                .setItems(
                        options,
                        (dialog, which) -> {

                            if (which == 0) {
                                renameFile(file);
                            } else if (which == 1) {
                                deleteFile(file);
                            } else {
                                editFile(file);
                            }
                        }
                )
                .show();
    }

    private void renameFile(File file) {

        EditText input =
                edit(file.getName());

        new AlertDialog.Builder(this)
                .setTitle("Rename")
                .setView(input)
                .setNegativeButton(
                        "CANCEL",
                        null
                )
                .setPositiveButton(
                        "SAVE",
                        (d, w) -> {

                            String name =
                                    input.getText()
                                            .toString()
                                            .trim();

                            if (!validName(name)) {
                                toast("Invalid name");
                                return;
                            }

                            File target =
                                    new File(
                                            file.getParentFile(),
                                            name
                                    );

                            if (file.renameTo(target)) {

                                addLog(
                                        "Renamed: " +
                                                file.getName() +
                                                " → " +
                                                name
                                );

                                recreate();

                            } else {

                                toast(
                                        "Rename failed"
                                );
                            }
                        }
                )
                .show();
    }

    private void deleteFile(File file) {

        new AlertDialog.Builder(this)
                .setTitle("Delete")
                .setMessage(
                        "Delete " +
                                file.getName() +
                                "?"
                )
                .setNegativeButton(
                        "CANCEL",
                        null
                )
                .setPositiveButton(
                        "DELETE",
                        (d, w) -> {

                            if (deleteRecursive(file)) {

                                addLog(
                                        "Deleted: " +
                                                file.getName()
                                );

                                recreate();

                            } else {

                                toast(
                                        "Delete failed"
                                );
                            }
                        }
                )
                .show();
    }

    private boolean deleteRecursive(File file) {

        if (file.isDirectory()) {

            File[] children =
                    file.listFiles();

            if (children != null) {

                for (File child :
                        children) {

                    deleteRecursive(child);
                }
            }
        }

        return file.delete();
    }

    // =========================================================
    // EDITOR
    // =========================================================

    private void editFile(File file) {

        if (file.isDirectory()) {

            toast("Folder cannot be edited");

            return;
        }

        String contentText =
                readFile(file);

        EditText editor =
                new EditText(this);

        editor.setText(contentText);
        editor.setTextColor(TEXT);
        editor.setTextSize(13);
        editor.setGravity(
                Gravity.TOP |
                        Gravity.START
        );

        editor.setSingleLine(false);
        editor.setInputType(
                InputType.TYPE_CLASS_TEXT |
                        InputType.TYPE_TEXT_FLAG_MULTI_LINE
        );

        editor.setBackgroundColor(
                Color.rgb(8, 10, 15)
        );

        int padding = dp(12);

        editor.setPadding(
                padding,
                padding,
                padding,
                padding
        );

        new AlertDialog.Builder(this)
                .setTitle(
                        "EDIT: " +
                                file.getName()
                )
                .setView(editor)
                .setNegativeButton(
                        "CANCEL",
                        null
                )
                .setPositiveButton(
                        "SAVE",
                        (d, w) -> {

                            writeFile(
                                    file,
                                    editor.getText()
                                            .toString()
                            );

                            addLog(
                                    "File saved: " +
                                            file.getName()
                            );

                            toast("Saved");
                        }
                )
                .show();
    }

    private String readFile(File file) {

        try {

            FileInputStream input =
                    new FileInputStream(file);

            byte[] data =
                    new byte[(int) file.length()];

            int read =
                    input.read(data);

            input.close();

            if (read <= 0) {
                return "";
            }

            return new String(
                    data,
                    0,
                    read,
                    java.nio.charset.StandardCharsets.UTF_8
            );

        } catch (Exception e) {

            return "";
        }
    }

    private void writeFile(
            File file,
            String data
    ) {

        try {

            FileOutputStream output =
                    new FileOutputStream(file);

            output.write(
                    data.getBytes(
                            java.nio.charset.StandardCharsets.UTF_8
                    )
            );

            output.flush();
            output.close();

        } catch (Exception e) {

            toast("Save failed");
        }
    }

    // =========================================================
    // QR
    // =========================================================

    private void showQR() {

        String port = "8080";

        if (portInput != null &&
                !portInput.getText()
                        .toString()
                        .trim()
                        .isEmpty()) {

            port =
                    portInput.getText()
                            .toString()
                            .trim();
        }

        String url =
                "http://" +
                        getLocalIp() +
                        ":" +
                        port;

        try {

            BarcodeEncoder encoder =
                    new BarcodeEncoder();

            Bitmap bitmap =
                    encoder.encodeBitmap(
                            url,
                            com.google.zxing.BarcodeFormat.QR_CODE,
                            800,
                            800
                    );

            ImageView image =
                    new ImageView(this);

            image.setImageBitmap(bitmap);

            image.setPadding(
                    dp(20),
                    dp(20),
                    dp(20),
                    dp(20)
            );

            new AlertDialog.Builder(this)
                    .setTitle("SERVER QR CODE")
                    .setMessage(url)
                    .setView(image)
                    .setPositiveButton(
                            "CLOSE",
                            null
                    )
                    .show();

        } catch (Exception e) {

            toast(
                    "QR error: " +
                            e.getMessage()
            );
        }
    }

    // =========================================================
    // URL
    // =========================================================

    private String getServerUrl() {

        String port = "8080";

        if (portInput != null &&
                !portInput.getText()
                        .toString()
                        .trim()
                        .isEmpty()) {

            port =
                    portInput.getText()
                            .toString()
                            .trim();
        }

        return "http://" +
                getLocalIp() +
                ":" +
                port;
    }

    private void copyUrl() {

        String url =
                getServerUrl();

        android.content.ClipboardManager clipboard =
                (android.content.ClipboardManager)
                        getSystemService(
                                CLIPBOARD_SERVICE
                        );

        if (clipboard != null) {

            clipboard.setPrimaryClip(
                    android.content.ClipData.newPlainText(
                            "Server URL",
                            url
                    )
            );

            toast("URL copied");
            addLog("URL copied: " + url);
        }
    }

    private void openWebsite() {

        try {

            Intent intent =
                    new Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse(
                                    getServerUrl()
                            )
                    );

            startActivity(intent);

        } catch (Exception e) {

            toast(
                    "No browser available"
            );
        }
    }

    // =========================================================
    // NETWORK INFO
    // =========================================================

    private String getLocalIp() {

        try {

            List<NetworkInterface> interfaces =
                    Collections.list(
                            NetworkInterface
                                    .getNetworkInterfaces()
                    );

            for (NetworkInterface network :
                    interfaces) {

                List<InetAddress> addresses =
                        Collections.list(
                                network
                                        .getInetAddresses()
                        );

                for (InetAddress address :
                        addresses) {

                    if (!address.isLoopbackAddress()
                            && address
                            .getHostAddress()
                            .indexOf(':') < 0) {

                        return address
                                .getHostAddress();
                    }
                }
            }

        } catch (Exception ignored) {
        }

        return "127.0.0.1";
    }

    private void showIPInfo() {

        StringBuilder result =
                new StringBuilder();

        try {

            Enumeration<NetworkInterface> en =
                    NetworkInterface
                            .getNetworkInterfaces();

            while (en.hasMoreElements()) {

                NetworkInterface network =
                        en.nextElement();

                result.append(
                        "\nInterface: "
                ).append(
                        network.getName()
                ).append("\n");

                Enumeration<InetAddress> addresses =
                        network.getInetAddresses();

                while (addresses.hasMoreElements()) {

                    InetAddress address =
                            addresses.nextElement();

                    result.append(
                            "IP: "
                    ).append(
                            address
                                    .getHostAddress()
                    ).append("\n");
                }
            }

        } catch (Exception e) {

            result.append(
                    "Unable to read network info"
            );
        }

        new AlertDialog.Builder(this)
                .setTitle("PHONE NETWORK IPs")
                .setMessage(result.toString())
                .setPositiveButton(
                        "CLOSE",
                        null
                )
                .show();
    }

    private void showConnectedInfo() {

        String message =
                "Server requests: " +
                        WebServerService
                                .getRequestCount() +
                        "\n\nActive clients: " +
                        WebServerService
                                .getClientCount() +
                        "\n\nServer URL:\n" +
                        getServerUrl();

        new AlertDialog.Builder(this)
                .setTitle("CONNECTED CLIENTS")
                .setMessage(message)
                .setPositiveButton(
                        "CLOSE",
                        null
                )
                .show();
    }

    // =========================================================
    // LOG SYSTEM
    // =========================================================

    private void addLog(String message) {

        String time =
                new java.text.SimpleDateFormat(
                        "HH:mm:ss",
                        java.util.Locale.getDefault()
                ).format(
                        new java.util.Date()
                );

        logs.add(
                "[" +
                        time +
                        "] " +
                        message
        );

        if (logs.size() > 200) {
            logs.remove(0);
        }

        updateLogView();
    }

    private void updateLogView() {

        if (content == null) {
            return;
        }

        TextView logView =
                content.findViewWithTag(
                        "LOG_VIEW"
                );

        if (logView == null) {
            return;
        }

        StringBuilder output =
                new StringBuilder();

        for (String log :
                logs) {

            output.append(log)
                    .append("\n");
        }

        if (output.length() == 0) {
            output.append("No logs yet");
        }

        logView.setText(
                output.toString()
        );
    }

    // =========================================================
    // FILE REFRESH
    // =========================================================

    private void refreshFiles() {

        toast("Files refreshed");

        recreate();
    }

    // =========================================================
    // APP INFO
    // =========================================================

    private void showAppInfo() {

        String message =
                "Salam Web Server Pro\n\n" +
                        "Package: " +
                        getPackageName() +
                        "\n\n" +
                        "Web Root:\n" +
                        webRoot.getAbsolutePath() +
                        "\n\n" +
                        "Phone IP:\n" +
                        getLocalIp() +
                        "\n\n" +
                        "Server URL:\n" +
                        getServerUrl();

        new AlertDialog.Builder(this)
                .setTitle("APP INFORMATION")
                .setMessage(message)
                .setPositiveButton(
                        "CLOSE",
                        null
                )
                .show();
    }

    // =========================================================
    // VALIDATION
    // =========================================================

    private boolean validName(String name) {

        if (name == null ||
                name.trim().isEmpty()) {

            return false;
        }

        if (name.contains("/") ||
                name.contains("\\") ||
                name.contains("..")) {

            return false;
        }

        return true;
    }

    private String safeName(String name) {

        return name
                .replace("/", "_")
                .replace("\\", "_")
                .replace("..", "_");
    }

    // =========================================================
    // DEFAULT WEBSITE
    // =========================================================

    private void createDefaultWebsite() {

        File index =
                new File(
                        webRoot,
                        "index.html"
                );

        if (index.exists()) {
            return;
        }

        String html =
                "<!doctype html>" +
                        "<html>" +
                        "<head>" +
                        "<meta name='viewport' " +
                        "content='width=device-width,initial-scale=1'>" +
                        "<title>Salam Web Server</title>" +
                        "<style>" +
                        "body{" +
                        "margin:0;" +
                        "background:#0b0f16;" +
                        "color:white;" +
                        "font-family:system-ui;" +
                        "text-align:center;" +
                        "padding:40px 20px" +
                        "}" +
                        ".card{" +
                        "background:#151b26;" +
                        "padding:25px;" +
                        "border-radius:20px;" +
                        "max-width:500px;" +
                        "margin:auto;" +
                        "}" +
                        "h1{color:#43e878}" +
                        "</style>" +
                        "</head>" +
                        "<body>" +
                        "<div class='card'>" +
                        "<h1>Salam Web Server</h1>" +
                        "<p>Server is running successfully.</p>" +
                        "<p>Website files are served from Android.</p>" +
                        "</div>" +
                        "</body>" +
                        "</html>";

        writeFile(index, html);
    }

    // =========================================================
    // UI HELPERS
    // =========================================================

    private LinearLayout card() {

        LinearLayout layout =
                new LinearLayout(this);

        layout.setOrientation(
                LinearLayout.VERTICAL
        );

        layout.setPadding(
                dp(14),
                dp(14),
                dp(14),
                dp(14)
        );

        GradientDrawable background =
                new GradientDrawable();

        background.setColor(CARD);
        background.setCornerRadius(
                dp(18)
        );

        layout.setBackground(
                background
        );

        return layout;
    }

    private TextView sectionTitle(
            String title
    ) {

        TextView view =
                text(
                        title,
                        17,
                        TEXT,
                        Typeface.BOLD
                );

        view.setPadding(
                0,
                0,
                0,
                dp(12)
        );

        return view;
    }

    private TextView label(
            String title
    ) {

        return text(
                title,
                12,
                MUTED,
                Typeface.BOLD
        );
    }

    private TextView text(
            String value,
            float size,
            int color,
            int style
    ) {

        TextView view =
                new TextView(this);

        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setTypeface(
                Typeface.DEFAULT,
                style
        );

        return view;
    }

    private EditText edit(
            String hint
    ) {

        EditText input =
                new EditText(this);

        input.setHint(hint);
        input.setHintTextColor(
                Color.rgb(120, 128, 140)
        );

        input.setTextColor(TEXT);
        input.setTextSize(15);

        input.setSingleLine(true);

        GradientDrawable background =
                new GradientDrawable();

        background.setColor(CARD2);
        background.setCornerRadius(
                dp(12)
        );

        input.setBackground(
                background
        );

        input.setPadding(
                dp(12),
                dp(10),
                dp(12),
                dp(10)
        );

        return input;
    }

    private Button button(
            String title,
            int color
    ) {

        Button button =
                new Button(this);

        button.setText(title);
        button.setTextColor(Color.WHITE);
        button.setTextSize(13);
        button.setTypeface(
                Typeface.DEFAULT,
                Typeface.BOLD
        );

        button.setAllCaps(false);

        GradientDrawable background =
                new GradientDrawable();

        background.setColor(
                Color.rgb(45, 51, 62)
        );

        background.setCornerRadius(
                dp(12)
        );

        button.setBackground(
                background
        );

        button.setPadding(
                dp(8),
                dp(8),
                dp(8),
                dp(8)
        );

        return button;
    }

    private LinearLayout.LayoutParams lp(
            int width,
            int height,
            float weight,
            int left,
            int right,
            int bottom
    ) {

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        width == -1
                                ? ViewGroup.LayoutParams.MATCH_PARENT
                                : width == -2
                                ? ViewGroup.LayoutParams.WRAP_CONTENT
                                : dp(width),

                        height == -1
                                ? ViewGroup.LayoutParams.MATCH_PARENT
                                : height == -2
                                ? ViewGroup.LayoutParams.WRAP_CONTENT
                                : dp(height),

                        weight
                );

        params.setMargins(
                dp(left),
                0,
                dp(right),
                dp(bottom)
        );

        return params;
    }

    private int dp(int value) {

        return (int) (
                value *
                        getResources()
                                .getDisplayMetrics()
                                .density
        );
    }

    private void toast(String message) {

        Toast.makeText(
                this,
                message,
                Toast.LENGTH_SHORT
        ).show();
    }

    @Override
    protected void onResume() {

        super.onResume();

        if (statusText != null) {
            refreshUI();
        }
    }
}
