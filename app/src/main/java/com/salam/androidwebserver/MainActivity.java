package com.salam.androidwebserver;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.content.Intent;
import android.net.Uri;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Enumeration;
import java.util.Locale;

/**
 * Salam Android Web Server
 *
 * Simple HTTP server for hosting files from Android.
 */
public class MainActivity extends Activity {

    private EditText portInput;
    private TextView statusText;
    private TextView ipText;
    private Button startButton;
    private Button stopButton;
    private Button openButton;

    private ServerSocket serverSocket;
    private Thread serverThread;

    private volatile boolean serverRunning = false;

    private int serverPort = 8080;

    private File wwwDirectory;

    private final Handler mainHandler =
            new Handler(Looper.getMainLooper());


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        createWebDirectory();
        createInterface();
    }


    /**
     * Create website directory.
     */
    private void createWebDirectory() {

        wwwDirectory = new File(getFilesDir(), "www");

        if (!wwwDirectory.exists()) {
            wwwDirectory.mkdirs();
        }

        File indexFile = new File(wwwDirectory, "index.html");

        if (!indexFile.exists()) {

            String html =
                    "<!DOCTYPE html>" +
                    "<html>" +
                    "<head>" +
                    "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">" +
                    "<title>Salam Web Server</title>" +
                    "<style>" +
                    "body{" +
                    "font-family:Arial,sans-serif;" +
                    "background:#111;" +
                    "color:white;" +
                    "text-align:center;" +
                    "padding:40px 20px;" +
                    "}" +
                    ".box{" +
                    "max-width:500px;" +
                    "margin:auto;" +
                    "padding:30px;" +
                    "border-radius:20px;" +
                    "background:#222;" +
                    "}" +
                    "h1{margin-bottom:10px;}" +
                    "</style>" +
                    "</head>" +
                    "<body>" +
                    "<div class=\"box\">" +
                    "<h1>Salam Web Server</h1>" +
                    "<p>Server is working successfully.</p>" +
                    "<p>Welcome to your Android Web Server.</p>" +
                    "</div>" +
                    "</body>" +
                    "</html>";

            try {

                FileOutputStream fos =
                        new FileOutputStream(indexFile);

                fos.write(html.getBytes("UTF-8"));
                fos.close();

            } catch (Exception ignored) {
            }
        }
    }


    /**
     * Create Android UI programmatically.
     * This means the MainActivity does not depend on
     * specific IDs inside activity_main.xml.
     */
    private void createInterface() {

        LinearLayout root =
                new LinearLayout(this);

        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(30, 40, 30, 30);
        root.setBackgroundColor(Color.rgb(15, 15, 15));


        TextView title =
                new TextView(this);

        title.setText("Salam Android Web Server");
        title.setTextColor(Color.WHITE);
        title.setTextSize(24);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 10, 0, 30);

        root.addView(
                title,
                new LinearLayout.LayoutParams(
                        -1,
                        -2
                )
        );


        TextView info =
                new TextView(this);

        info.setText(
                "Host websites directly from your Android phone"
        );

        info.setTextColor(Color.LTGRAY);
        info.setTextSize(15);
        info.setGravity(Gravity.CENTER);
        info.setPadding(0, 0, 0, 30);

        root.addView(
                info,
                new LinearLayout.LayoutParams(
                        -1,
                        -2
                )
        );


        TextView portLabel =
                new TextView(this);

        portLabel.setText("Server Port");
        portLabel.setTextColor(Color.WHITE);
        portLabel.setTextSize(16);

        root.addView(
                portLabel,
                new LinearLayout.LayoutParams(
                        -1,
                        -2
                )
        );


        portInput =
                new EditText(this);

        portInput.setText("8080");
        portInput.setTextColor(Color.WHITE);
        portInput.setTextSize(17);
        portInput.setSingleLine(true);
        portInput.setInputType(2);

        root.addView(
                portInput,
                new LinearLayout.LayoutParams(
                        -1,
                        -2
                )
        );


        statusText =
                new TextView(this);

        statusText.setText("● Server Stopped");
        statusText.setTextColor(Color.RED);
        statusText.setTextSize(18);
        statusText.setGravity(Gravity.CENTER);
        statusText.setPadding(0, 35, 0, 15);

        root.addView(
                statusText,
                new LinearLayout.LayoutParams(
                        -1,
                        -2
                )
        );


        ipText =
                new TextView(this);

        ipText.setText("IP: Not running");
        ipText.setTextColor(Color.LTGRAY);
        ipText.setTextSize(15);
        ipText.setGravity(Gravity.CENTER);
        ipText.setPadding(0, 5, 0, 25);

        root.addView(
                ipText,
                new LinearLayout.LayoutParams(
                        -1,
                        -2
                )
        );


        startButton =
                new Button(this);

        startButton.setText("START SERVER");

        root.addView(
                startButton,
                new LinearLayout.LayoutParams(
                        -1,
                        -2
                )
        );


        stopButton =
                new Button(this);

        stopButton.setText("STOP SERVER");
        stopButton.setEnabled(false);

        root.addView(
                stopButton,
                new LinearLayout.LayoutParams(
                        -1,
                        -2
                )
        );


        openButton =
                new Button(this);

        openButton.setText("OPEN WEBSITE");
        openButton.setEnabled(false);

        root.addView(
                openButton,
                new LinearLayout.LayoutParams(
                        -1,
                        -2
                )
        );


        TextView folderText =
                new TextView(this);

        folderText.setText(
                "\nWebsite folder:\n" +
                "Internal Storage / files / www\n\n" +
                "Place your HTML, CSS, JS, JSON, images and PDF files there."
        );

        folderText.setTextColor(Color.GRAY);
        folderText.setTextSize(13);
        folderText.setGravity(Gravity.CENTER);

        root.addView(
                folderText,
                new LinearLayout.LayoutParams(
                        -1,
                        -2
                )
        );


        startButton.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startServer();
                    }
                }
        );


        stopButton.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        stopServer();
                    }
                }
        );


        openButton.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        openWebsite();
                    }
                }
        );


        setContentView(root);
    }


    /**
     * Start HTTP server.
     */
    private void startServer() {

        if (serverRunning) {
            return;
        }


        String portText =
                portInput.getText().toString().trim();


        try {

            serverPort =
                    Integer.parseInt(portText);

        } catch (Exception e) {

            Toast.makeText(
                    this,
                    "Invalid port number",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }


        if (serverPort < 1024 ||
                serverPort > 65535) {

            Toast.makeText(
                    this,
                    "Use port between 1024 and 65535",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }


        serverThread =
                new Thread(
                        new Runnable() {

                            @Override
                            public void run() {

                                try {

                                    serverSocket =
                                            new ServerSocket(serverPort);

                                    serverRunning = true;

                                    updateServerUI(true);

                                    while (serverRunning) {

                                        Socket client =
                                                serverSocket.accept();

                                        new Thread(
                                                new ClientHandler(client)
                                        ).start();
                                    }

                                } catch (Exception e) {

                                    serverRunning = false;

                                    updateServerUI(false);
                                }
                            }
                        }
                );


        serverThread.start();
    }


    /**
     * Stop HTTP server.
     */
    private void stopServer() {

        serverRunning = false;

        try {

            if (serverSocket != null) {
                serverSocket.close();
            }

        } catch (Exception ignored) {
        }


        updateServerUI(false);
    }


    /**
     * Update UI from server thread.
     */
    private void updateServerUI(
            final boolean running) {

        mainHandler.post(
                new Runnable() {

                    @Override
                    public void run() {

                        if (running) {

                            String ip =
                                    getLocalIpAddress();

                            statusText.setText(
                                    "● Server Running"
                            );

                            statusText.setTextColor(
                                    Color.GREEN
                            );

                            ipText.setText(
                                    "http://" +
                                    ip +
                                    ":" +
                                    serverPort
                            );

                            startButton.setEnabled(false);
                            stopButton.setEnabled(true);
                            openButton.setEnabled(true);

                        } else {

                            statusText.setText(
                                    "● Server Stopped"
                            );

                            statusText.setTextColor(
                                    Color.RED
                            );

                            ipText.setText(
                                    "IP: Not running"
                            );

                            startButton.setEnabled(true);
                            stopButton.setEnabled(false);
                            openButton.setEnabled(false);
                        }
                    }
                }
        );
    }


    /**
     * Get Android phone's local IPv4 address.
     */
    private String getLocalIpAddress() {

        try {

            Enumeration<NetworkInterface> interfaces =
                    NetworkInterface.getNetworkInterfaces();

            while (interfaces.hasMoreElements()) {

                NetworkInterface networkInterface =
                        interfaces.nextElement();

                Enumeration<InetAddress> addresses =
                        networkInterface.getInetAddresses();

                while (addresses.hasMoreElements()) {

                    InetAddress address =
                            addresses.nextElement();

                    if (!address.isLoopbackAddress()
                            && address instanceof Inet4Address) {

                        return address.getHostAddress();
                    }
                }
            }

        } catch (Exception ignored) {
        }


        return "127.0.0.1";
    }


    /**
     * Open website in Android browser.
     */
    private void openWebsite() {

        String ip =
                getLocalIpAddress();

        String url =
                "http://" +
                ip +
                ":" +
                serverPort;


        try {

            Intent intent =
                    new Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse(url)
                    );

            startActivity(intent);

        } catch (Exception e) {

            Toast.makeText(
                    this,
                    url,
                    Toast.LENGTH_LONG
            ).show();
        }
    }


    /**
     * Handle HTTP clients.
     */
    private class ClientHandler
            implements Runnable {

        private final Socket socket;


        ClientHandler(Socket socket) {
            this.socket = socket;
        }


        @Override
        public void run() {

            try {

                InputStream input =
                        socket.getInputStream();

                OutputStream output =
                        socket.getOutputStream();


                BufferedReader reader =
                        new BufferedReader(
                                new java.io.InputStreamReader(input)
                        );


                String requestLine =
                        reader.readLine();


                if (requestLine == null) {

                    socket.close();
                    return;
                }


                String[] requestParts =
                        requestLine.split(" ");


                if (requestParts.length < 2) {

                    socket.close();
                    return;
                }


                String path =
                        requestParts[1];


                int questionMark =
                        path.indexOf("?");


                if (questionMark >= 0) {

                    path =
                            path.substring(
                                    0,
                                    questionMark
                            );
                }


                path =
                        Uri.decode(path);


                if (path.equals("/")) {

                    path = "/index.html";
                }


                /*
                 * Security:
                 * Prevent ../ path traversal.
                 */
                if (path.contains("..")) {

                    sendError(
                            output,
                            403,
                            "Forbidden"
                    );

                    socket.close();
                    return;
                }


                File requestedFile =
                        new File(
                                wwwDirectory,
                                path.substring(1)
                        );


                if (!requestedFile.exists()
                        || !requestedFile.isFile()) {

                    sendError(
                            output,
                            404,
                            "File Not Found"
                    );

                    socket.close();
                    return;
                }


                String mimeType =
                        getMimeType(
                                requestedFile.getName()
                        );


                long fileLength =
                        requestedFile.length();


                String headers =
                        "HTTP/1.1 200 OK\r\n" +
                        "Content-Type: " +
                        mimeType +
                        "\r\n" +
                        "Content-Length: " +
                        fileLength +
                        "\r\n" +
                        "Connection: close\r\n" +
                        "\r\n";


                BufferedWriter writer =
                        new BufferedWriter(
                                new OutputStreamWriter(
                                        output,
                                        "UTF-8"
                                )
                        );


                writer.write(headers);
                writer.flush();


                FileInputStream fileInput =
                        new FileInputStream(
                                requestedFile
                        );


                byte[] buffer =
                        new byte[8192];


                int count;


                while ((count =
                        fileInput.read(buffer)) != -1) {

                    output.write(
                            buffer,
                            0,
                            count
                    );
                }


                output.flush();

                fileInput.close();
                socket.close();


            } catch (Exception ignored) {

                try {
                    socket.close();
                } catch (Exception ignored2) {
                }
            }
        }
    }


    /**
     * Send HTTP error response.
     */
    private void sendError(
            OutputStream output,
            int code,
            String message) {

        try {

            String body =
                    "<html>" +
                    "<head>" +
                    "<meta charset=\"UTF-8\">" +
                    "<title>" +
                    code +
                    "</title>" +
                    "</head>" +
                    "<body>" +
                    "<h1>" +
                    code +
                    " " +
                    message +
                    "</h1>" +
                    "</body>" +
                    "</html>";


            byte[] bodyBytes =
                    body.getBytes("UTF-8");


            String headers =
                    "HTTP/1.1 " +
                    code +
                    " " +
                    message +
                    "\r\n" +
                    "Content-Type: text/html; charset=UTF-8\r\n" +
                    "Content-Length: " +
                    bodyBytes.length +
                    "\r\n" +
                    "Connection: close\r\n" +
                    "\r\n";


            output.write(
                    headers.getBytes("UTF-8")
            );

            output.write(bodyBytes);

            output.flush();

        } catch (Exception ignored) {
        }
    }


    /**
     * MIME type detection.
     */
    private String getMimeType(
            String fileName) {

        String name =
                fileName.toLowerCase(
                        Locale.US
                );


        if (name.endsWith(".html")
                || name.endsWith(".htm")) {

            return "text/html; charset=UTF-8";
        }


        if (name.endsWith(".css")) {

            return "text/css; charset=UTF-8";
        }


        if (name.endsWith(".js")) {

            return "application/javascript; charset=UTF-8";
        }


        if (name.endsWith(".json")) {

            return "application/json; charset=UTF-8";
        }


        if (name.endsWith(".txt")) {

            return "text/plain; charset=UTF-8";
        }


        if (name.endsWith(".xml")) {

            return "application/xml; charset=UTF-8";
        }


        if (name.endsWith(".pdf")) {

            return "application/pdf";
        }


        if (name.endsWith(".png")) {

            return "image/png";
        }


        if (name.endsWith(".jpg")
                || name.endsWith(".jpeg")) {

            return "image/jpeg";
        }


        if (name.endsWith(".gif")) {

            return "image/gif";
        }


        if (name.endsWith(".webp")) {

            return "image/webp";
        }


        if (name.endsWith(".svg")) {

            return "image/svg+xml";
        }


        if (name.endsWith(".ico")) {

            return "image/x-icon";
        }


        if (name.endsWith(".mp3")) {

            return "audio/mpeg";
        }


        if (name.endsWith(".mp4")) {

            return "video/mp4";
        }


        if (name.endsWith(".zip")) {

            return "application/zip";
        }


        return "application/octet-stream";
    }


    @Override
    protected void onDestroy() {

        stopServer();

        super.onDestroy();
    }
}
