package com.shivy.shnet.demo;

import com.shivy.shnet.ShnetRequest;
import com.shivy.shnet.ShnetResponse;
import com.shivy.shnet.ShnetRouter;

public class DemoRouter implements ShnetRouter {
    @Override
    public ShnetResponse handle(ShnetRequest request) {
        if (request == null) {
            return ShnetResponse.text(400, "text/plain; charset=utf-8", "Bad Request");
        }
        if (!"GET".equalsIgnoreCase(request.method)) {
            return ShnetResponse.text(405, "text/plain; charset=utf-8", "Method Not Allowed");
        }
        if ("/".equals(request.path)) {
            return ShnetResponse.text(200, "text/plain; charset=utf-8", "shnet demo online");
        }
        if ("/ping".equals(request.path)) {
            return ShnetResponse.text(200, "text/plain; charset=utf-8", "ok");
        }
        return ShnetResponse.text(404, "text/plain; charset=utf-8", "Not Found");
    }
}
