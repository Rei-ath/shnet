# shnet

shnet is a tiny protocol/runtime layer for **device‑owned nodes**. It handles:
- starting/stopping a foreground node
- binding a local HTTP server (IPv4 + IPv6)
- routing requests to your handler
- generating LAN links + QR codes

It **does not** contain shop logic or payments. Those live on top of shnet.

## Quick start (run the demo)

```bash
./gradlew :demo:installDebug
adb shell am start -n com.shivy.shnet.demo/.MainActivity
```

Open the link shown in the demo on another device and you should see:

```
shnet demo online
```

## Minimal usage (drop into any app)

**Router**
```java
public class MyRouter implements ShnetRouter {
    @Override
    public ShnetResponse handle(ShnetRequest request) {
        if ("/".equals(request.path)) {
            return ShnetResponse.text(200, "text/plain; charset=utf-8", "hello from shnet");
        }
        return ShnetResponse.text(404, "text/plain; charset=utf-8", "Not Found");
    }
}
```

**Service**
```java
public class MyNodeService extends ShnetService {
    @Override
    protected ShnetRouter createRouter() {
        return new MyRouter();
    }

    @Override
    protected ShnetConfig createConfig() {
        return ShnetConfig.basic(8723, R.drawable.ic_node, "node running", "Tap to open");
    }
}
```

**Start / Stop**
```java
Intent start = new Intent(context, MyNodeService.class);
start.setAction(ShnetService.ACTION_START);
context.startForegroundService(start);

Intent stop = new Intent(context, MyNodeService.class);
stop.setAction(ShnetService.ACTION_STOP);
context.startService(stop);
```

**Links + QR**
```java
List<ShnetLinks.ShnetLink> links = ShnetLinks.getActiveLinks(context, 8723);
Bitmap qr = ShnetQr.createQrBitmap(links.get(0).url, 420);
```

## Build

```bash
./gradlew :shnet:assembleDebug
```
