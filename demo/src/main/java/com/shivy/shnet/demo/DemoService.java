package com.shivy.shnet.demo;

import com.shivy.shnet.ShnetConfig;
import com.shivy.shnet.ShnetRouter;
import com.shivy.shnet.ShnetService;

public class DemoService extends ShnetService {
    public static final int PORT = 8723;

    @Override
    protected ShnetRouter createRouter() {
        return new DemoRouter();
    }

    @Override
    protected ShnetConfig createConfig() {
        return ShnetConfig.basic(
                PORT,
                R.drawable.ic_shnet,
                "shnet demo running",
                "Tap to open"
        );
    }
}
