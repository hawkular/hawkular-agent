package io.prometheus.jmx;

import java.io.File;
import java.net.InetSocketAddress;

import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.exporter.HTTPServer;
import io.prometheus.client.hotspot.DefaultExports;

public class WebServer {

   public static void main(String[] args) throws Exception {
     if (args.length < 2) {
       throw new Exception("Usage: WebServer <[hostname:]port> <yaml configuration file>");
     }

     String[] hostnamePort = args[0].split(":");
     int port;
     InetSocketAddress socket;
     
     if (hostnamePort.length == 2) {
       port = Integer.parseInt(hostnamePort[1]);
       socket = new InetSocketAddress(hostnamePort[0], port);
     } else {
       port = Integer.parseInt(hostnamePort[0]);
       socket = new InetSocketAddress(port);
     }

     new JmxCollector(new File(args[1])).register();
     DefaultExports.initialize();
     new HTTPServer(socket, CollectorRegistry.defaultRegistry, true); // true == daemon
   }
}