<!doctype html>
<html lang="en">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>PHP Info - Salam Cyber Web Server</title>
    <style>
        body { margin: 0; padding: 20px; background: #06101e; color: #fff; font-family: system-ui, sans-serif; }
        .container { max-width: 800px; margin: 0 auto; }
        a { color: #00e5ff; text-decoration: none; font-weight: bold; }
    </style>
</head>
<body>
<div class="container">
    <p><a href="/">← Back to Server Home</a></p>
    <?php
        echo "<h2>⚡ Salam Server PHP Test Script</h2>";
        $currentTime = date("Y-m-d H:i:s");
        echo "<p style='color:#00ffcc;'>Current Server Time: " . $currentTime . "</p>";
        phpinfo();
    ?>
</div>
</body>
</html>
