package com.shivy.shnet;

public class ShnetConfig {
    public final int port;
    public final int notificationIconRes;
    public final String notificationTitle;
    public final String notificationText;
    public final String channelId;
    public final String channelName;
    public final String channelDescription;
    public final String stopActionLabel;

    public ShnetConfig(int port,
                       int notificationIconRes,
                       String notificationTitle,
                       String notificationText,
                       String channelId,
                       String channelName,
                       String channelDescription,
                       String stopActionLabel) {
        this.port = port;
        this.notificationIconRes = notificationIconRes;
        this.notificationTitle = notificationTitle;
        this.notificationText = notificationText;
        this.channelId = channelId;
        this.channelName = channelName;
        this.channelDescription = channelDescription;
        this.stopActionLabel = stopActionLabel;
    }

    public static ShnetConfig basic(int port, int notificationIconRes, String title, String text) {
        return new ShnetConfig(
                port,
                notificationIconRes,
                title,
                text,
                "shnet_node",
                "shnet node",
                "Keeps the shnet node running",
                "Stop"
        );
    }
}
