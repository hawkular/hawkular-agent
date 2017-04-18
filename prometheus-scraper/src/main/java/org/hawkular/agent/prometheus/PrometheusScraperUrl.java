/*
 * Copyright 2015-2016 Red Hat, Inc. and/or its affiliates
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
package org.hawkular.agent.prometheus;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.List;

import org.jboss.logging.Logger;

import io.prometheus.client.Metrics.MetricFamily;

/**
 * Given a URL, this will scrape the Prometheus metric data it finds there.
 */
public class PrometheusScraperUrl extends PrometheusScraper {
    private static final Logger log = Logger.getLogger(PrometheusScraperUrl.class);

    private final URL url;

    public PrometheusScraperUrl(String host, int port, String context) throws MalformedURLException {
        if (host == null) {
            host = "127.0.0.1";
        }
        if (port == 0) {
            port = 9090;
        }
        if (context == null || context.isEmpty()) {
            context = "/metrics";
        }
        this.url = new URL("http", host, port, context);
        log.debugf("Will scrape Permetheus data from URL [%s]", this.url);
    }

    public PrometheusScraperUrl(URL url) {
        if (url == null) {
            throw new IllegalArgumentException("URL must not be null");
        }
        this.url = url;
        log.debugf("Will scrape Permetheus data from URL [%s]", this.url);
    }

    public List<MetricFamily> scrape() throws IOException {
        URLConnection conn = url.openConnection();
        conn.setRequestProperty("Accept",
                "application/vnd.google.protobuf; proto=io.prometheus.client.MetricFamily; encoding=delimited");
        try (InputStream inputStream = conn.getInputStream()) {
            try {
                return super.scrape(inputStream);
            } catch (IOException e) {
                // if we failed, throw a special exception if it looks to be due to a bad content type
                String contentType = conn.getContentType();
                if (!contentType.contains("application/vnd.google.protobuf")) {
                    throw new IOException("Cannot scrape Permetheus data: bad content type: " + contentType, e);
                }
                if (!conn.getContentType().contains("proto=io.prometheus.client.MetricFamily")) {
                    throw new IOException("Cannot scrape Permetheus data: bad protocol: " + contentType, e);
                }
                if (!conn.getContentType().contains("encoding=delimited")) {
                    throw new IOException("Cannot scrape Permetheus data: bad encoding: " + contentType, e);
                }
                throw e;
            }
        }
    }
}
