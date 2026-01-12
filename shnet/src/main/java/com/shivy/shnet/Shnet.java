package com.shivy.shnet;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Build;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public final class Shnet {
    public static final String ACTION_START = "com.shivy.shnet.action.START";
    public static final String ACTION_STOP = "com.shivy.shnet.action.STOP";

    private Shnet() {
    }

    public enum BindMode {
        IPV6_PREFERRED,
        DUAL,
        IPV6_ONLY,
        IPV4_ONLY
    }

    public enum LinkPreference {
        IPV6_FIRST,
        IPV4_FIRST,
        ALL
    }

    public interface Handler {
        Response handle(Request request);
    }

    public interface HandlerFactory {
        Handler create(Context context);
    }

    public static final class Config {
        public final int port;
        public final int notificationIconRes;
        public final String notificationTitle;
        public final String notificationText;
        public final String channelId;
        public final String channelName;
        public final String channelDescription;
        public final String stopActionLabel;
        public final BindMode bindMode;
        public final int readTimeoutMs;
        public final int maxBodyBytes;
        public final int workerThreads;

        private Config(Builder builder) {
            this.port = builder.port;
            this.notificationIconRes = builder.notificationIconRes;
            this.notificationTitle = builder.notificationTitle;
            this.notificationText = builder.notificationText;
            this.channelId = builder.channelId;
            this.channelName = builder.channelName;
            this.channelDescription = builder.channelDescription;
            this.stopActionLabel = builder.stopActionLabel;
            this.bindMode = builder.bindMode;
            this.readTimeoutMs = builder.readTimeoutMs;
            this.maxBodyBytes = builder.maxBodyBytes;
            this.workerThreads = builder.workerThreads;
        }

        public static Builder builder(int port, int notificationIconRes, String title, String text) {
            return new Builder(port, notificationIconRes, title, text);
        }

        public static Config basic(int port, int notificationIconRes, String title, String text) {
            return builder(port, notificationIconRes, title, text).build();
        }

        public static final class Builder {
            private static final int DEFAULT_READ_TIMEOUT_MS = 4000;
            private static final int DEFAULT_MAX_BODY_BYTES = 256 * 1024;
            private static final int DEFAULT_WORKER_THREADS = 4;

            private final int port;
            private final int notificationIconRes;
            private final String notificationTitle;
            private final String notificationText;
            private String channelId = "shnet_node";
            private String channelName = "shnet node";
            private String channelDescription = "Keeps the shnet node running";
            private String stopActionLabel = "Stop";
            private BindMode bindMode = BindMode.IPV6_PREFERRED;
            private int readTimeoutMs = DEFAULT_READ_TIMEOUT_MS;
            private int maxBodyBytes = DEFAULT_MAX_BODY_BYTES;
            private int workerThreads = DEFAULT_WORKER_THREADS;

            private Builder(int port, int notificationIconRes, String title, String text) {
                this.port = port;
                this.notificationIconRes = notificationIconRes;
                this.notificationTitle = title;
                this.notificationText = text;
            }

            public Builder setChannel(String channelId, String channelName, String channelDescription) {
                if (channelId != null) {
                    this.channelId = channelId;
                }
                if (channelName != null) {
                    this.channelName = channelName;
                }
                if (channelDescription != null) {
                    this.channelDescription = channelDescription;
                }
                return this;
            }

            public Builder setStopActionLabel(String label) {
                if (label != null) {
                    this.stopActionLabel = label;
                }
                return this;
            }

            public Builder setBindMode(BindMode mode) {
                if (mode != null) {
                    this.bindMode = mode;
                }
                return this;
            }

            public Builder setReadTimeoutMs(int readTimeoutMs) {
                if (readTimeoutMs > 0) {
                    this.readTimeoutMs = readTimeoutMs;
                }
                return this;
            }

            public Builder setMaxBodyBytes(int maxBodyBytes) {
                if (maxBodyBytes > 0) {
                    this.maxBodyBytes = maxBodyBytes;
                }
                return this;
            }

            public Builder setWorkerThreads(int workerThreads) {
                if (workerThreads > 0) {
                    this.workerThreads = workerThreads;
                }
                return this;
            }

            public Config build() {
                return new Config(this);
            }
        }
    }

    public static final class Request {
        public final String method;
        public final String path;
        public final String query;
        public final Map<String, String> headers;
        public final byte[] body;

        Request(String method, String path, String query, Map<String, String> headers, byte[] body) {
            this.method = method;
            this.path = path;
            this.query = query;
            this.headers = headers;
            this.body = body;
        }
    }

    public static final class Response {
        public final int statusCode;
        public final String statusMessage;
        public final String contentType;
        public final byte[] body;
        public final File file;
        public final String downloadName;
        public final Map<String, String> headers;

        private Response(int statusCode,
                         String statusMessage,
                         String contentType,
                         byte[] body,
                         File file,
                         String downloadName,
                         Map<String, String> headers) {
            this.statusCode = statusCode;
            this.statusMessage = statusMessage;
            this.contentType = contentType;
            this.body = body;
            this.file = file;
            this.downloadName = downloadName;
            this.headers = headers == null ? Collections.emptyMap() : headers;
        }

        public static Response text(int statusCode, String contentType, String text) {
            String safeType = contentType == null ? "text/plain; charset=utf-8" : contentType;
            byte[] payload = text == null ? new byte[0] : text.getBytes(StandardCharsets.UTF_8);
            return new Response(statusCode, statusMessageFor(statusCode), safeType, payload, null, null, null);
        }

        public static Response bytes(int statusCode, String contentType, byte[] body) {
            String safeType = contentType == null ? "application/octet-stream" : contentType;
            return new Response(statusCode, statusMessageFor(statusCode), safeType, body, null, null, null);
        }

        public static Response file(File file, String contentType, String downloadName) {
            return new Response(200, "OK", contentType, null, file, downloadName, null);
        }

        public Response withHeader(String key, String value) {
            Map<String, String> merged = new HashMap<>(headers);
            merged.put(key, value);
            return new Response(statusCode, statusMessage, contentType, body, file, downloadName, merged);
        }

        static String statusMessageFor(int code) {
            switch (code) {
                case 200:
                    return "OK";
                case 400:
                    return "Bad Request";
                case 401:
                    return "Unauthorized";
                case 403:
                    return "Forbidden";
                case 404:
                    return "Not Found";
                case 405:
                    return "Method Not Allowed";
                case 413:
                    return "Payload Too Large";
                case 500:
                    return "Internal Server Error";
                default:
                    return "";
            }
        }
    }

    public static final class Link {
        public final String label;
        public final String url;
        public final boolean isIpv6;

        public Link(String label, String url, boolean isIpv6) {
            this.label = label;
            this.url = url;
            this.isIpv6 = isIpv6;
        }
    }

    public static void start(Context context, Config config, Handler handler) {
        if (context == null || config == null || handler == null) {
            throw new IllegalArgumentException("Missing context/config/handler");
        }
        Context appContext = context.getApplicationContext();
        ShnetRuntime.setEphemeral(appContext, config, handler);
        Intent intent = new Intent(appContext, ShnetNodeService.class);
        intent.setAction(ACTION_START);
        startServiceCompat(appContext, intent);
    }

    public static void startPersistent(Context context, Config config, Class<? extends HandlerFactory> factoryClass) {
        if (context == null || config == null || factoryClass == null) {
            throw new IllegalArgumentException("Missing context/config/factory");
        }
        Context appContext = context.getApplicationContext();
        ShnetRuntime.setPersistent(appContext, config, factoryClass);
        Intent intent = new Intent(appContext, ShnetNodeService.class);
        intent.setAction(ACTION_START);
        startServiceCompat(appContext, intent);
    }

    public static void stop(Context context) {
        if (context == null) {
            return;
        }
        Context appContext = context.getApplicationContext();
        Intent intent = new Intent(appContext, ShnetNodeService.class);
        intent.setAction(ACTION_STOP);
        appContext.startService(intent);
    }

    public static boolean isRunning(Context context) {
        return ShnetRuntime.isRunning(context);
    }

    public static String lastError(Context context) {
        return ShnetRuntime.getLastError(context);
    }

    public static long lastStartAttempt(Context context) {
        return ShnetRuntime.getLastStartAttempt(context);
    }

    public static List<Link> links(Context context, int port, LinkPreference preference) {
        List<Link> results = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        if (context != null) {
            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm != null) {
                Network[] networks = cm.getAllNetworks();
                if (networks != null) {
                    for (Network network : networks) {
                        NetworkCapabilities caps = cm.getNetworkCapabilities(network);
                        if (caps != null && (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                                || caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))) {
                            collectActiveLinks(cm, network, port, results, seen);
                        }
                    }
                }
                if (results.isEmpty()) {
                    Network active = cm.getActiveNetwork();
                    if (active != null) {
                        collectActiveLinks(cm, active, port, results, seen);
                    }
                }
            }
        }

        if (results.isEmpty()) {
            collectLocalLinks(port, results, seen);
        }

        if (results.isEmpty()) {
            results.add(new Link("localhost IPv4", "http://127.0.0.1:" + port + "/", false));
            results.add(new Link("localhost IPv6", "http://[::1]:" + port + "/", true));
        }

        return orderLinks(results, preference);
    }

    public static Bitmap qr(String text, int size) throws WriterException {
        QRCodeWriter writer = new QRCodeWriter();
        Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
        hints.put(EncodeHintType.MARGIN, 1);
        BitMatrix matrix = writer.encode(text, BarcodeFormat.QR_CODE, size, size, hints);
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565);
        for (int x = 0; x < size; x++) {
            for (int y = 0; y < size; y++) {
                bitmap.setPixel(x, y, matrix.get(x, y) ? Color.BLACK : Color.WHITE);
            }
        }
        return bitmap;
    }

    private static void startServiceCompat(Context context, Intent intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }

    private static void collectActiveLinks(ConnectivityManager cm, Network network, int port,
                                           List<Link> results, Set<String> seen) {
        LinkProperties props = cm.getLinkProperties(network);
        if (props == null) {
            return;
        }
        String ifaceName = props.getInterfaceName();
        for (LinkAddress link : props.getLinkAddresses()) {
            InetAddress address = link.getAddress();
            if (address == null || address.isLoopbackAddress() || address.isAnyLocalAddress()
                    || address.isMulticastAddress() || address.isLinkLocalAddress()) {
                continue;
            }
            addLink(address, ifaceName, port, results, seen);
        }
    }

    private static void collectLocalLinks(int port, List<Link> results, Set<String> seen) {
        try {
            for (NetworkInterface iface : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!iface.isUp() || iface.isLoopback()) {
                    continue;
                }
                for (InetAddress address : Collections.list(iface.getInetAddresses())) {
                    if (address.isLoopbackAddress() || address.isLinkLocalAddress()) {
                        continue;
                    }
                    addLink(address, iface.getName(), port, results, seen);
                }
            }
        } catch (SocketException ignored) {
            // Ignore network enumeration failures.
        }
    }

    private static void addLink(InetAddress address, String ifaceName, int port,
                                List<Link> results, Set<String> seen) {
        if (address instanceof Inet4Address) {
            String host = address.getHostAddress();
            String url = "http://" + host + ":" + port + "/";
            String key = "v4-" + host;
            if (seen.add(key)) {
                results.add(new Link(labelForInterface(ifaceName, "IPv4"), url, false));
            }
        } else if (address instanceof Inet6Address) {
            String host = address.getHostAddress();
            int zoneIndex = host.indexOf('%');
            if (zoneIndex >= 0) {
                host = host.substring(0, zoneIndex) + "%25" + host.substring(zoneIndex + 1);
            }
            String url = "http://[" + host + "]:" + port + "/";
            String key = "v6-" + host;
            if (seen.add(key)) {
                results.add(new Link(labelForInterface(ifaceName, "IPv6"), url, true));
            }
        }
    }

    private static String labelForInterface(String ifaceName, String suffix) {
        if (ifaceName == null || ifaceName.trim().isEmpty()) {
            return "Active " + suffix;
        }
        return ifaceName + " " + suffix;
    }

    private static List<Link> orderLinks(List<Link> links, LinkPreference preference) {
        if (preference == null || preference == LinkPreference.ALL) {
            return links;
        }
        List<Link> v6 = new ArrayList<>();
        List<Link> v4 = new ArrayList<>();
        for (Link link : links) {
            if (link.isIpv6) {
                v6.add(link);
            } else {
                v4.add(link);
            }
        }
        List<Link> ordered = new ArrayList<>(links.size());
        if (preference == LinkPreference.IPV6_FIRST) {
            ordered.addAll(v6);
            ordered.addAll(v4);
        } else {
            ordered.addAll(v4);
            ordered.addAll(v6);
        }
        return ordered;
    }
}

final class ShnetRuntime {
    private static final String PREFS_NAME = "shnet_runtime";
    private static final String KEY_PERSISTENT = "persistent";
    private static final String KEY_RUNNING = "running";
    private static final String KEY_ERROR = "error";
    private static final String KEY_LAST_START = "last_start";
    private static final String KEY_CONFIG_SET = "config_set";
    private static final String KEY_PORT = "port";
    private static final String KEY_ICON = "icon";
    private static final String KEY_TITLE = "title";
    private static final String KEY_TEXT = "text";
    private static final String KEY_CHANNEL_ID = "channel_id";
    private static final String KEY_CHANNEL_NAME = "channel_name";
    private static final String KEY_CHANNEL_DESC = "channel_desc";
    private static final String KEY_STOP_LABEL = "stop_label";
    private static final String KEY_BIND_MODE = "bind_mode";
    private static final String KEY_READ_TIMEOUT = "read_timeout";
    private static final String KEY_MAX_BODY = "max_body";
    private static final String KEY_WORKERS = "workers";
    private static final String KEY_FACTORY = "factory";

    private static volatile Shnet.Config config;
    private static volatile Shnet.Handler handler;
    private static volatile Class<? extends Shnet.HandlerFactory> factoryClass;

    static void setEphemeral(Context context, Shnet.Config config, Shnet.Handler handler) {
        ShnetRuntime.config = config;
        ShnetRuntime.handler = handler;
        ShnetRuntime.factoryClass = null;
        clearPersistent(context);
    }

    static void setPersistent(Context context, Shnet.Config config,
                              Class<? extends Shnet.HandlerFactory> factoryClass) {
        ShnetRuntime.config = config;
        ShnetRuntime.handler = null;
        ShnetRuntime.factoryClass = factoryClass;
        savePersistent(context, config, factoryClass);
    }

    static Shnet.Config loadConfig(Context context) {
        if (config != null) {
            return config;
        }
        return readConfig(context);
    }

    static Shnet.Handler loadHandler(Context context) {
        if (handler != null) {
            return handler;
        }
        Class<? extends Shnet.HandlerFactory> factory = factoryClass;
        if (factory == null) {
            factory = readFactoryClass(context);
        }
        if (factory == null) {
            return null;
        }
        try {
            Shnet.HandlerFactory instance = factory.getDeclaredConstructor().newInstance();
            return instance.create(context.getApplicationContext());
        } catch (Exception ignored) {
            return null;
        }
    }

    static boolean isPersistent(Context context) {
        if (context == null) {
            return false;
        }
        return context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_PERSISTENT, false);
    }

    static boolean isRunning(Context context) {
        if (context == null) {
            return false;
        }
        return context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_RUNNING, false);
    }

    static String getLastError(Context context) {
        if (context == null) {
            return "";
        }
        return context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_ERROR, "");
    }

    static long getLastStartAttempt(Context context) {
        if (context == null) {
            return 0L;
        }
        return context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getLong(KEY_LAST_START, 0L);
    }

    static void setRunning(Context context, boolean running) {
        if (context == null) {
            return;
        }
        context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_RUNNING, running)
                .apply();
    }

    static void setLastError(Context context, String message) {
        if (context == null) {
            return;
        }
        context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_ERROR, message == null ? "" : message)
                .apply();
    }

    static void setLastStartAttempt(Context context, long timestamp) {
        if (context == null) {
            return;
        }
        context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putLong(KEY_LAST_START, timestamp)
                .apply();
    }

    static void clear(Context context) {
        config = null;
        handler = null;
        factoryClass = null;
        if (context == null) {
            return;
        }
        context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .clear()
                .apply();
    }

    private static void clearPersistent(Context context) {
        if (context == null) {
            return;
        }
        context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .remove(KEY_PERSISTENT)
                .remove(KEY_CONFIG_SET)
                .remove(KEY_PORT)
                .remove(KEY_ICON)
                .remove(KEY_TITLE)
                .remove(KEY_TEXT)
                .remove(KEY_CHANNEL_ID)
                .remove(KEY_CHANNEL_NAME)
                .remove(KEY_CHANNEL_DESC)
                .remove(KEY_STOP_LABEL)
                .remove(KEY_BIND_MODE)
                .remove(KEY_READ_TIMEOUT)
                .remove(KEY_MAX_BODY)
                .remove(KEY_WORKERS)
                .remove(KEY_FACTORY)
                .apply();
    }

    private static void savePersistent(Context context, Shnet.Config config,
                                       Class<? extends Shnet.HandlerFactory> factoryClass) {
        if (context == null || config == null || factoryClass == null) {
            return;
        }
        context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_PERSISTENT, true)
                .putBoolean(KEY_CONFIG_SET, true)
                .putInt(KEY_PORT, config.port)
                .putInt(KEY_ICON, config.notificationIconRes)
                .putString(KEY_TITLE, config.notificationTitle)
                .putString(KEY_TEXT, config.notificationText)
                .putString(KEY_CHANNEL_ID, config.channelId)
                .putString(KEY_CHANNEL_NAME, config.channelName)
                .putString(KEY_CHANNEL_DESC, config.channelDescription)
                .putString(KEY_STOP_LABEL, config.stopActionLabel)
                .putString(KEY_BIND_MODE, config.bindMode.name())
                .putInt(KEY_READ_TIMEOUT, config.readTimeoutMs)
                .putInt(KEY_MAX_BODY, config.maxBodyBytes)
                .putInt(KEY_WORKERS, config.workerThreads)
                .putString(KEY_FACTORY, factoryClass.getName())
                .apply();
    }

    private static Shnet.Config readConfig(Context context) {
        if (context == null) {
            return null;
        }
        android.content.SharedPreferences prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        if (!prefs.getBoolean(KEY_CONFIG_SET, false)) {
            return null;
        }
        int port = prefs.getInt(KEY_PORT, 0);
        int icon = prefs.getInt(KEY_ICON, 0);
        String title = prefs.getString(KEY_TITLE, "");
        String text = prefs.getString(KEY_TEXT, "");
        String channelId = prefs.getString(KEY_CHANNEL_ID, "shnet_node");
        String channelName = prefs.getString(KEY_CHANNEL_NAME, "shnet node");
        String channelDesc = prefs.getString(KEY_CHANNEL_DESC, "Keeps the shnet node running");
        String stopLabel = prefs.getString(KEY_STOP_LABEL, "Stop");
        String bindName = prefs.getString(KEY_BIND_MODE, Shnet.BindMode.IPV6_PREFERRED.name());
        Shnet.BindMode bindMode;
        try {
            bindMode = Shnet.BindMode.valueOf(bindName);
        } catch (Exception ignored) {
            bindMode = Shnet.BindMode.IPV6_PREFERRED;
        }
        int readTimeout = prefs.getInt(KEY_READ_TIMEOUT, 4000);
        int maxBody = prefs.getInt(KEY_MAX_BODY, 256 * 1024);
        int workers = prefs.getInt(KEY_WORKERS, 4);
        return Shnet.Config.builder(port, icon, title, text)
                .setChannel(channelId, channelName, channelDesc)
                .setStopActionLabel(stopLabel)
                .setBindMode(bindMode)
                .setReadTimeoutMs(readTimeout)
                .setMaxBodyBytes(maxBody)
                .setWorkerThreads(workers)
                .build();
    }

    @SuppressWarnings("unchecked")
    private static Class<? extends Shnet.HandlerFactory> readFactoryClass(Context context) {
        if (context == null) {
            return null;
        }
        String className = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_FACTORY, null);
        if (className == null || className.trim().isEmpty()) {
            return null;
        }
        try {
            Class<?> raw = Class.forName(className);
            if (Shnet.HandlerFactory.class.isAssignableFrom(raw)) {
                return (Class<? extends Shnet.HandlerFactory>) raw;
            }
        } catch (Exception ignored) {
            return null;
        }
        return null;
    }
}

final class ShnetServer {
    private static final int MAX_HEADER_BYTES = 32 * 1024;
    private static final int MAX_LINE_BYTES = 8192;

    private final Shnet.Handler handler;
    private final Shnet.Config config;
    private ServerSocket ipv6Socket;
    private ServerSocket ipv4Socket;
    private Thread ipv6Thread;
    private Thread ipv4Thread;
    private ExecutorService workers;
    private volatile boolean running;
    private String bindHost = "";

    ShnetServer(Shnet.Handler handler, Shnet.Config config) {
        this.handler = handler;
        this.config = config;
    }

    synchronized void start() throws IOException {
        if (running) {
            return;
        }
        running = true;
        workers = Executors.newFixedThreadPool(Math.max(1, config.workerThreads),
                new ShnetThreadFactory());
        ipv6Socket = null;
        ipv4Socket = null;
        IOException lastException = null;

        if (config.bindMode == Shnet.BindMode.IPV6_ONLY || config.bindMode == Shnet.BindMode.DUAL
                || config.bindMode == Shnet.BindMode.IPV6_PREFERRED) {
            try {
                ipv6Socket = bindSocket("::", config.port);
            } catch (IOException ex) {
                lastException = ex;
                ipv6Socket = null;
            }
        }

        if (config.bindMode == Shnet.BindMode.IPV4_ONLY || config.bindMode == Shnet.BindMode.DUAL
                || (config.bindMode == Shnet.BindMode.IPV6_PREFERRED && ipv6Socket == null)) {
            try {
                ipv4Socket = bindSocket("0.0.0.0", config.port);
            } catch (IOException ex) {
                if (lastException == null) {
                    lastException = ex;
                }
                ipv4Socket = null;
            }
        }

        if (ipv6Socket == null && ipv4Socket == null) {
            running = false;
            shutdownWorkers();
            if (lastException != null) {
                throw lastException;
            }
            throw new IOException("Unable to bind shnet server");
        }

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

    synchronized void stop() {
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
        shutdownWorkers();
    }

    boolean isRunning() {
        return running;
    }

    int getPort() {
        return config.port;
    }

    String getBindHost() {
        return bindHost;
    }

    private ServerSocket bindSocket(String host, int port) throws IOException {
        ServerSocket socket = new ServerSocket();
        socket.setReuseAddress(true);
        socket.bind(new InetSocketAddress(InetAddress.getByName(host), port));
        return socket;
    }

    private void runLoop(ServerSocket socket) {
        while (running && socket != null) {
            try {
                Socket clientSocket = socket.accept();
                if (workers != null) {
                    workers.execute(() -> handleClient(clientSocket));
                } else {
                    handleClient(clientSocket);
                }
            } catch (IOException ignored) {
                if (!running) {
                    return;
                }
            }
        }
    }

    private void handleClient(Socket socket) {
        try (Socket client = socket) {
            client.setSoTimeout(config.readTimeoutMs);
            InputStream input = client.getInputStream();
            OutputStream output = client.getOutputStream();

            String requestLine = readLine(input, MAX_LINE_BYTES);
            if (requestLine == null || requestLine.isEmpty()) {
                return;
            }
            String[] parts = requestLine.split(" ");
            if (parts.length < 2) {
                sendResponse(output, Shnet.Response.text(400, "text/plain; charset=utf-8", "Bad Request"));
                return;
            }
            String method = parts[0].trim();
            String rawPath = parts[1].trim();
            String path = rawPath;
            String query = "";
            int queryIndex = rawPath.indexOf('?');
            if (queryIndex >= 0) {
                path = rawPath.substring(0, queryIndex);
                query = rawPath.substring(queryIndex + 1);
            }

            Map<String, String> headers = new HashMap<>();
            int headerBytes = 0;
            String headerLine;
            while ((headerLine = readLine(input, MAX_LINE_BYTES)) != null && !headerLine.isEmpty()) {
                headerBytes += headerLine.length();
                if (headerBytes > MAX_HEADER_BYTES) {
                    sendResponse(output, Shnet.Response.text(400, "text/plain; charset=utf-8", "Bad Request"));
                    return;
                }
                int idx = headerLine.indexOf(':');
                if (idx > 0) {
                    String key = headerLine.substring(0, idx).trim().toLowerCase(Locale.US);
                    String value = headerLine.substring(idx + 1).trim();
                    headers.put(key, value);
                }
            }

            int contentLength = 0;
            String lengthHeader = headers.get("content-length");
            if (lengthHeader != null) {
                try {
                    contentLength = Integer.parseInt(lengthHeader);
                } catch (NumberFormatException ignored) {
                    contentLength = 0;
                }
            }
            if (contentLength > config.maxBodyBytes) {
                sendResponse(output, Shnet.Response.text(413, "text/plain; charset=utf-8", "Payload Too Large"));
                return;
            }
            byte[] body = contentLength > 0 ? readBody(input, contentLength) : new byte[0];

            Shnet.Request request = new Shnet.Request(method, path, query, headers, body);
            Shnet.Response response;
            try {
                response = handler != null ? handler.handle(request) : null;
            } catch (Exception ex) {
                response = Shnet.Response.text(500, "text/plain; charset=utf-8", "Internal Server Error");
            }
            if (response == null) {
                response = Shnet.Response.text(404, "text/plain; charset=utf-8", "Not Found");
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

    private byte[] readBody(InputStream input, int length) throws IOException {
        byte[] data = new byte[length];
        int total = 0;
        while (total < length) {
            int read = input.read(data, total, length - total);
            if (read == -1) {
                break;
            }
            total += read;
        }
        if (total == length) {
            return data;
        }
        return Arrays.copyOf(data, total);
    }

    private String readLine(InputStream input, int limit) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int b;
        while ((b = input.read()) != -1) {
            if (b == '\n') {
                break;
            }
            if (b != '\r') {
                buffer.write(b);
                if (buffer.size() > limit) {
                    throw new IOException("Line too long");
                }
            }
        }
        if (b == -1 && buffer.size() == 0) {
            return null;
        }
        return buffer.toString(StandardCharsets.US_ASCII.name());
    }

    private void sendResponse(OutputStream output, Shnet.Response response) throws IOException {
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

    private void sendFileResponse(OutputStream output, Shnet.Response response) throws IOException {
        File file = response.file;
        if (file == null || !file.exists()) {
            sendResponse(output, Shnet.Response.text(404, "text/plain; charset=utf-8", "Not Found"));
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
        try (java.io.FileInputStream input = new java.io.FileInputStream(file)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
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

    private void shutdownWorkers() {
        if (workers != null) {
            workers.shutdownNow();
            workers = null;
        }
    }

    private static final class ShnetThreadFactory implements ThreadFactory {
        private final AtomicInteger counter = new AtomicInteger(1);

        @Override
        public Thread newThread(Runnable runnable) {
            return new Thread(runnable, "ShnetWorker-" + counter.getAndIncrement());
        }
    }
}
