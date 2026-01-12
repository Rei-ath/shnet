package com.shivy.shnet;

import java.util.Map;

public class ShnetRequest {
    public final String method;
    public final String path;
    public final String query;
    public final Map<String, String> headers;

    public ShnetRequest(String method, String path, String query, Map<String, String> headers) {
        this.method = method;
        this.path = path;
        this.query = query;
        this.headers = headers;
    }
}
