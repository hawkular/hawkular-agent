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

import java.net.URL;
import java.util.List;

import io.prometheus.client.Metrics.MetricFamily;

/**
 * This is a command line utility that can scrape a Prometheus protocol endpoint and outputs the metric data it finds.
 * You provide a single required argument on the command line - the URL of the Prometheus protocol endpoint, which is
 * typically something like "http://localhost:9090/metrics".
 */
public class PrometheusScraperCli {

    enum PrometheusMetricsWalkerType {
        SIMPLE, XML, JSON
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            throw new Exception("Specify the URL of the Prometheus protocol endpoint.");
        }

        PrometheusMetricsWalkerType walkerType = PrometheusMetricsWalkerType.SIMPLE;
        URL url = null;

        for (String arg : args) {
            if (arg.startsWith("--")) {
                if (arg.equalsIgnoreCase("--xml")) {
                    walkerType = PrometheusMetricsWalkerType.XML;
                } else if (arg.equalsIgnoreCase("--json")) {
                    walkerType = PrometheusMetricsWalkerType.JSON;
                } else if (arg.equalsIgnoreCase("--simple")) {
                    walkerType = PrometheusMetricsWalkerType.SIMPLE;
                } else {
                    throw new Exception("Invalid argument: " + arg);
                }
            } else {
                url = new URL(arg);
                break;
            }
        }

        if (url == null) {
            throw new Exception("Specify the URL of the Prometheus protocol endpoint.");
        }

        PrometheusScraperUrl scraper = new PrometheusScraperUrl(url);
        List<MetricFamily> metrics = scraper.scrape();

        AbstractPrometheusMetricsWalker walker;
        switch (walkerType) {
            case SIMPLE:
                walker = new SimplePrometheusMetricsWalker(metrics, url);
                break;
            case XML:
                walker = new XMLPrometheusMetricsWalker(metrics, url);
                break;
            case JSON:
                walker = new JSONPrometheusMetricsWalker(metrics);
                break;
            default:
                throw new Exception("Invalid walker type: " + walkerType);
        }

        walker.walk();
    }

}