# shnet

shnet is a tiny, self-contained node runtime for device-owned apps. It provides:
- a foreground node service (ephemeral or persistent)
- a dual-stack HTTP listener (IPv6 preferred by default)
- a minimal request/response handler
- LAN link discovery + QR generation

It does **not** include business logic (shops, payments, inventory). Those sit on top.

## Minimal usage

**Handler**
```java
public final class DemoHandler implements Shnet.Handler {
    @Override
    public Shnet.Response handle(Shnet.Request request) {
        if ("/".equals(request.path)) {
            return Shnet.Response.text(200, "text/plain; charset=utf-8", "hello from shnet");
        }
        return Shnet.Response.text(404, "text/plain; charset=utf-8", "Not Found");
    }
}
```

**Start (ephemeral, no auto-restart)**
```java
Shnet.Config config = Shnet.Config.builder(
        8723,
        R.drawable.ic_node,
        "node running",
        "Tap to open"
).build();

Shnet.start(context, config, new DemoHandler());
```

**Start (persistent, auto-restart)**
```java
public final class DemoFactory implements Shnet.HandlerFactory {
    @Override
    public Shnet.Handler create(Context context) {
        return new DemoHandler();
    }
}

Shnet.startPersistent(context, config, DemoFactory.class);
```
Note: the factory class must be public with a zero-arg constructor.

**Stop**
```java
Shnet.stop(context);
```

**Links + QR**
```java
List<Shnet.Link> links = Shnet.links(context, 8723, Shnet.LinkPreference.IPV6_FIRST);
Bitmap qr = Shnet.qr(links.get(0).url, 420);
```

## Build

```bash
./gradlew :shnet:assembleDebug
```
