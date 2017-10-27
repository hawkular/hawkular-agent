/*
 * Copyright 2015-2017 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.hawkular.agent.monitor.prometheus;

import java.io.File;
import java.net.InetSocketAddress;

import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.exporter.HTTPServer;
import io.prometheus.client.hotspot.DefaultExports;
import io.prometheus.jmx.JmxCollector;

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