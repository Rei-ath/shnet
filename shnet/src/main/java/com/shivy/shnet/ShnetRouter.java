package com.shivy.shnet;

public interface ShnetRouter {
    ShnetResponse handle(ShnetRequest request);
}
