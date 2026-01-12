package com.shivy.shnet;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

import java.io.IOException;

public abstract class ShnetService extends Service {
    public static final String ACTION_START = "com.shivy.shnet.action.START";
    public static final String ACTION_STOP = "com.shivy.shnet.action.STOP";

    private ShnetServer server;
    private ShnetConfig config;
    private boolean running;

    protected abstract ShnetRouter createRouter();

    protected abstract ShnetConfig createConfig();

    @Override
    public void onCreate() {
        super.onCreate();
        ShnetRouter router = createRouter();
        config = createConfig();
        server = new ShnetServer(router, config.port);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : null;
        if (ACTION_STOP.equals(action)) {
            stopNode();
            return START_NOT_STICKY;
        }

        if (ACTION_START.equals(action)) {
            startNode();
            return START_STICKY;
        }

        if (intent == null && ShnetRuntime.isRunning(this, getClass())) {
            startNode();
            return START_STICKY;
        }

        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        stopNode();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    protected Intent createOpenIntent() {
        Intent launch = getPackageManager().getLaunchIntentForPackage(getPackageName());
        if (launch != null) {
            launch.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        }
        return launch;
    }

    private void startNode() {
        if (running) {
            return;
        }
        ShnetRuntime.setLastError(this, getClass(), "");
        ShnetRuntime.setLastStartAttempt(this, getClass(), System.currentTimeMillis());
        try {
            Notification notification = buildNotification();
            startForeground(config.port, notification);
            server.start();
            running = true;
            ShnetRuntime.setRunning(this, getClass(), true);
        } catch (IOException e) {
            running = false;
            ShnetRuntime.setRunning(this, getClass(), false);
            ShnetRuntime.setLastError(this, getClass(), safeError(e));
            stopForeground(true);
            stopSelf();
        } catch (Exception e) {
            running = false;
            ShnetRuntime.setRunning(this, getClass(), false);
            ShnetRuntime.setLastError(this, getClass(), safeError(e));
            stopForeground(true);
            stopSelf();
        }
    }

    private void stopNode() {
        if (running) {
            server.stop();
        }
        running = false;
        ShnetRuntime.setRunning(this, getClass(), false);
        ShnetRuntime.setLastError(this, getClass(), "");
        stopForeground(true);
        stopSelf();
    }

    private Notification buildNotification() {
        createNotificationChannel();
        Intent openIntent = createOpenIntent();
        PendingIntent openPending = null;
        if (openIntent != null) {
            openPending = PendingIntent.getActivity(
                    this,
                    0,
                    openIntent,
                    pendingIntentFlags()
            );
        }

        Intent stopIntent = new Intent(this, getClass());
        stopIntent.setAction(ACTION_STOP);
        PendingIntent stopPending = PendingIntent.getService(
                this,
                1,
                stopIntent,
                pendingIntentFlags()
        );

        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, config.channelId)
                : new Notification.Builder(this);

        builder.setContentTitle(config.notificationTitle)
                .setContentText(config.notificationText)
                .setSmallIcon(config.notificationIconRes)
                .setOngoing(true);
        if (openPending != null) {
            builder.setContentIntent(openPending);
        }

        String stopLabel = config.stopActionLabel == null ? "Stop" : config.stopActionLabel;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            builder.addAction(new Notification.Action.Builder(0, stopLabel, stopPending).build());
        } else {
            builder.addAction(0, stopLabel, stopPending);
        }

        return builder.build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager == null) {
            return;
        }
        NotificationChannel channel = new NotificationChannel(
                config.channelId,
                config.channelName,
                NotificationManager.IMPORTANCE_LOW
        );
        channel.setDescription(config.channelDescription);
        manager.createNotificationChannel(channel);
    }

    private int pendingIntentFlags() {
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        return flags;
    }

    private String safeError(Exception e) {
        String message = e.getMessage();
        if (message == null || message.trim().isEmpty()) {
            return e.getClass().getSimpleName();
        }
        if (message.length() > 120) {
            return message.substring(0, 120);
        }
        return message;
    }
}
