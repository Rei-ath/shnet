package com.shivy.shnet;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class ShnetResponse {
    public final int statusCode;
    public final String statusMessage;
    public final String contentType;
    public final byte[] body;
    public final File file;
    public final String downloadName;
    public final Map<String, String> headers;

    private ShnetResponse(int statusCode,
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

    public static ShnetResponse text(int statusCode, String contentType, String text) {
        String safeType = contentType == null ? "text/plain; charset=utf-8" : contentType;
        byte[] payload = text == null ? new byte[0] : text.getBytes(StandardCharsets.UTF_8);
        return new ShnetResponse(statusCode, statusMessageFor(statusCode), safeType, payload, null, null, null);
    }

    public static ShnetResponse bytes(int statusCode, String contentType, byte[] body) {
        String safeType = contentType == null ? "application/octet-stream" : contentType;
        return new ShnetResponse(statusCode, statusMessageFor(statusCode), safeType, body, null, null, null);
    }

    public static ShnetResponse file(File file, String contentType, String downloadName) {
        return new ShnetResponse(200, "OK", contentType, null, file, downloadName, null);
    }

    public ShnetResponse withHeader(String key, String value) {
        Map<String, String> merged = new HashMap<>(headers);
        merged.put(key, value);
        return new ShnetResponse(statusCode, statusMessage, contentType, body, file, downloadName, merged);
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
            case 500:
                return "Internal Server Error";
            default:
                return "";
        }
    }
}
