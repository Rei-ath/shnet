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

public class ShnetNodeService extends Service {
    private ShnetServer server;
    private Shnet.Config config;
    private boolean running;

    @Override
    public void onCreate() {
        super.onCreate();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : null;
        if (Shnet.ACTION_STOP.equals(action)) {
            stopNode(true);
            return START_NOT_STICKY;
        }

        if (Shnet.ACTION_START.equals(action) || intent == null) {
            return startNode();
        }

        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        stopNode(false);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private int startNode() {
        if (running) {
            return startMode();
        }
        ShnetRuntime.setLastError(this, "");
        ShnetRuntime.setLastStartAttempt(this, System.currentTimeMillis());
        config = ShnetRuntime.loadConfig(this);
        Shnet.Handler handler = ShnetRuntime.loadHandler(this);
        if (config == null || handler == null) {
            ShnetRuntime.setLastError(this, "Missing handler/config");
            stopSelf();
            return START_NOT_STICKY;
        }
        server = new ShnetServer(handler, config);
        try {
            Notification notification = buildNotification();
            startForeground(notificationId(), notification);
            server.start();
            running = true;
            ShnetRuntime.setRunning(this, true);
            return startMode();
        } catch (IOException e) {
            running = false;
            ShnetRuntime.setRunning(this, false);
            ShnetRuntime.setLastError(this, safeError(e));
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        } catch (Exception e) {
            running = false;
            ShnetRuntime.setRunning(this, false);
            ShnetRuntime.setLastError(this, safeError(e));
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }
    }

    private int startMode() {
        return ShnetRuntime.isPersistent(this) ? START_STICKY : START_NOT_STICKY;
    }

    private void stopNode(boolean clearRuntime) {
        if (running && server != null) {
            server.stop();
        }
        running = false;
        ShnetRuntime.setRunning(this, false);
        if (clearRuntime) {
            ShnetRuntime.clear(this);
        }
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
        stopIntent.setAction(Shnet.ACTION_STOP);
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

    private Intent createOpenIntent() {
        Intent launch = getPackageManager().getLaunchIntentForPackage(getPackageName());
        if (launch != null) {
            launch.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        }
        return launch;
    }

    private int pendingIntentFlags() {
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        return flags;
    }

    private int notificationId() {
        if (config == null) {
            return 1;
        }
        return config.port > 0 ? config.port : 1;
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
