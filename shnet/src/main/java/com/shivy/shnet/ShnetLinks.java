package com.shivy.shnet;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ShnetLinks {
    public static class ShnetLink {
        public final String label;
        public final String url;
        public final boolean isIpv6;

        public ShnetLink(String label, String url, boolean isIpv6) {
            this.label = label;
            this.url = url;
            this.isIpv6 = isIpv6;
        }
    }

    public static List<ShnetLink> getLocalLinks(int port) {
        List<ShnetLink> results = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();
                if (!iface.isUp() || iface.isLoopback()) {
                    continue;
                }
                Enumeration<InetAddress> addresses = iface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress address = addresses.nextElement();
                    if (address.isLoopbackAddress()) {
                        continue;
                    }
                    if (address instanceof Inet4Address) {
                        String host = address.getHostAddress();
                        String url = "http://" + host + ":" + port + "/";
                        String key = "v4-" + host;
                        if (seen.add(key)) {
                            results.add(new ShnetLink(iface.getName() + " IPv4", url, false));
                        }
                    } else if (address instanceof Inet6Address) {
                        if (address.isLinkLocalAddress()) {
                            continue;
                        }
                        String host = address.getHostAddress();
                        int zoneIndex = host.indexOf('%');
                        if (zoneIndex >= 0) {
                            host = host.substring(0, zoneIndex) + "%25" + host.substring(zoneIndex + 1);
                        }
                        String url = "http://[" + host + "]:" + port + "/";
                        String key = "v6-" + host;
                        if (seen.add(key)) {
                            results.add(new ShnetLink(iface.getName() + " IPv6", url, true));
                        }
                    }
                }
            }
        } catch (SocketException ignored) {
            // Fall through to defaults.
        }

        if (results.isEmpty()) {
            results.add(new ShnetLink("localhost IPv4", "http://127.0.0.1:" + port + "/", false));
            results.add(new ShnetLink("localhost IPv6", "http://[::1]:" + port + "/", true));
        }

        return results;
    }

    public static List<ShnetLink> getActiveLinks(Context context, int port) {
        if (context == null) {
            return getLocalLinks(port);
        }
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) {
            return getLocalLinks(port);
        }
        List<ShnetLink> results = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        Network[] networks = cm.getAllNetworks();
        if (networks != null) {
            for (Network network : networks) {
                NetworkCapabilities caps = cm.getNetworkCapabilities(network);
                if (caps != null && (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                        || caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))) {
                    collectAddresses(cm, network, port, results, seen);
                }
            }
        }

        if (!results.isEmpty()) {
            return results;
        }

        Network active = cm.getActiveNetwork();
        if (active != null) {
            collectAddresses(cm, active, port, results, seen);
        }

        if (results.isEmpty()) {
            return getLocalLinks(port);
        }
        return results;
    }

    private static void collectAddresses(ConnectivityManager cm, Network network, int port,
                                         List<ShnetLink> results, Set<String> seen) {
        LinkProperties props = cm.getLinkProperties(network);
        if (props == null) {
            return;
        }
        String ifaceName = props.getInterfaceName();
        for (LinkAddress link : props.getLinkAddresses()) {
            InetAddress address = link.getAddress();
            if (address == null || address.isLoopbackAddress() || address.isAnyLocalAddress()
                    || address.isMulticastAddress() || address.isLinkLocalAddress()) {
                continue;
            }
            if (address instanceof Inet4Address) {
                String host = address.getHostAddress();
                String url = "http://" + host + ":" + port + "/";
                String key = "v4-" + host;
                if (seen.add(key)) {
                    results.add(new ShnetLink(labelForInterface(ifaceName, "IPv4"), url, false));
                }
            } else if (address instanceof Inet6Address) {
                String host = address.getHostAddress();
                int zoneIndex = host.indexOf('%');
                if (zoneIndex >= 0) {
                    host = host.substring(0, zoneIndex) + "%25" + host.substring(zoneIndex + 1);
                }
                String url = "http://[" + host + "]:" + port + "/";
                String key = "v6-" + host;
                if (seen.add(key)) {
                    results.add(new ShnetLink(labelForInterface(ifaceName, "IPv6"), url, true));
                }
            }
        }
    }

    private static String labelForInterface(String ifaceName, String suffix) {
        if (ifaceName == null || ifaceName.trim().isEmpty()) {
            return "Active " + suffix;
        }
        return ifaceName + " " + suffix;
    }
}
