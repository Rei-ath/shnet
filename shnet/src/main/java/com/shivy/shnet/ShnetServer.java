package com.shivy.shnet;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class ShnetServer {
    private final ShnetRouter router;
    private final int port;
    private ServerSocket ipv6Socket;
    private ServerSocket ipv4Socket;
    private Thread ipv6Thread;
    private Thread ipv4Thread;
    private volatile boolean running;
    private String bindHost = "";

    public ShnetServer(ShnetRouter router, int port) {
        this.router = router;
        this.port = port;
    }

    public synchronized void start() throws IOException {
        if (running) {
            return;
        }
        IOException lastException = null;
        ipv6Socket = null;
        ipv4Socket = null;

        try {
            ipv6Socket = new ServerSocket();
            ipv6Socket.setReuseAddress(true);
            ipv6Socket.bind(new InetSocketAddress(InetAddress.getByName("::"), port));
        } catch (IOException ex) {
            lastException = ex;
            closeQuietly(ipv6Socket);
            ipv6Socket = null;
        }

        try {
            ipv4Socket = new ServerSocket();
            ipv4Socket.setReuseAddress(true);
            ipv4Socket.bind(new InetSocketAddress(InetAddress.getByName("0.0.0.0"), port));
        } catch (IOException ex) {
            if (lastException == null) {
                lastException = ex;
            }
            closeQuietly(ipv4Socket);
            ipv4Socket = null;
        }

        if (ipv6Socket == null && ipv4Socket == null) {
            if (lastException != null) {
                throw lastException;
            }
            throw new IOException("Unable to bind shnet server");
        }

        running = true;
        if (ipv6Socket != null) {
            ipv6Thread = new Thread(() -> runLoop(ipv6Socket), "ShnetServer-v6");
            ipv6Thread.start();
        }
        if (ipv4Socket != null) {
            ipv4Thread = new Thread(() -> runLoop(ipv4Socket), "ShnetServer-v4");
            ipv4Thread.start();
        }

        if (ipv6Socket != null && ipv4Socket != null) {
            bindHost = "dual";
        } else if (ipv6Socket != null) {
            bindHost = "::";
        } else {
            bindHost = "0.0.0.0";
        }
    }

    public synchronized void stop() {
        running = false;
        closeQuietly(ipv6Socket);
        closeQuietly(ipv4Socket);
        ipv6Socket = null;
        ipv4Socket = null;
        if (ipv6Thread != null) {
            ipv6Thread.interrupt();
        }
        if (ipv4Thread != null) {
            ipv4Thread.interrupt();
        }
    }

    public boolean isRunning() {
        return running;
    }

    public int getPort() {
        return port;
    }

    public String getBindHost() {
        return bindHost;
    }

    private void runLoop(ServerSocket socket) {
        while (running && socket != null) {
            try {
                Socket clientSocket = socket.accept();
                Thread worker = new Thread(() -> handleClient(clientSocket), "ShnetWorker");
                worker.start();
            } catch (IOException ignored) {
                if (!running) {
                    return;
                }
            }
        }
    }

    private void handleClient(Socket socket) {
        try (Socket client = socket) {
            client.setSoTimeout(4000);
            BufferedReader reader = new BufferedReader(new InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8));
            OutputStream output = client.getOutputStream();

            String requestLine = reader.readLine();
            if (requestLine == null || requestLine.isEmpty()) {
                return;
            }
            String[] parts = requestLine.split(" ");
            String method = parts.length > 0 ? parts[0] : "";
            String path = parts.length > 1 ? parts[1] : "/";
            String query = "";
            int queryIndex = path.indexOf('?');
            if (queryIndex >= 0) {
                query = path.substring(queryIndex + 1);
                path = path.substring(0, queryIndex);
            }

            Map<String, String> headers = new HashMap<>();
            String headerLine;
            while ((headerLine = reader.readLine()) != null && !headerLine.isEmpty()) {
                int idx = headerLine.indexOf(':');
                if (idx > 0) {
                    String key = headerLine.substring(0, idx).trim();
                    String value = headerLine.substring(idx + 1).trim();
                    headers.put(key, value);
                }
            }

            ShnetRequest request = new ShnetRequest(method, path, query, headers);
            ShnetResponse response = router != null ? router.handle(request) : null;
            if (response == null) {
                response = ShnetResponse.text(404, "text/plain; charset=utf-8", "Not Found");
            }

            if (response.file != null) {
                sendFileResponse(output, response);
            } else {
                sendResponse(output, response);
            }
        } catch (IOException ignored) {
            // Ignore socket errors.
        }
    }

    private void sendResponse(OutputStream output, ShnetResponse response) throws IOException {
        byte[] body = response.body == null ? new byte[0] : response.body;
        String contentType = response.contentType == null ? "text/plain; charset=utf-8" : response.contentType;
        StringBuilder header = new StringBuilder();
        header.append("HTTP/1.1 ").append(response.statusCode).append(" ").append(response.statusMessage).append("\r\n");
        header.append("Content-Type: ").append(contentType).append("\r\n");
        header.append("Content-Length: ").append(body.length).append("\r\n");
        header.append("Cache-Control: no-store\r\n");
        for (Map.Entry<String, String> entry : response.headers.entrySet()) {
            header.append(entry.getKey()).append(": ").append(entry.getValue()).append("\r\n");
        }
        header.append("Connection: close\r\n\r\n");
        output.write(header.toString().getBytes(StandardCharsets.UTF_8));
        output.write(body);
    }

    private void sendFileResponse(OutputStream output, ShnetResponse response) throws IOException {
        File file = response.file;
        if (file == null || !file.exists()) {
            sendResponse(output, ShnetResponse.text(404, "text/plain; charset=utf-8", "Not Found"));
            return;
        }
        String contentType = response.contentType == null ? "application/octet-stream" : response.contentType;
        long length = file.length();
        StringBuilder header = new StringBuilder();
        header.append("HTTP/1.1 ").append(response.statusCode).append(" ").append(response.statusMessage).append("\r\n");
        header.append("Content-Type: ").append(contentType).append("\r\n");
        header.append("Content-Length: ").append(length).append("\r\n");
        if (response.downloadName != null && !response.downloadName.isEmpty()) {
            header.append("Content-Disposition: attachment; filename=\"")
                    .append(response.downloadName)
                    .append("\"\r\n");
        }
        header.append("Cache-Control: no-store\r\n");
        for (Map.Entry<String, String> entry : response.headers.entrySet()) {
            header.append(entry.getKey()).append(": ").append(entry.getValue()).append("\r\n");
        }
        header.append("Connection: close\r\n\r\n");
        output.write(header.toString().getBytes(StandardCharsets.UTF_8));
        try (FileInputStream input = new FileInputStream(file);
             ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
            byte[] chunk = new byte[8192];
            int read;
            while ((read = input.read(chunk)) != -1) {
                buffer.write(chunk, 0, read);
            }
            output.write(buffer.toByteArray());
        }
    }

    private void closeQuietly(ServerSocket socket) {
        if (socket == null) {
            return;
        }
        try {
            socket.close();
        } catch (IOException ignored) {
            // Ignore close errors.
        }
    }
}
