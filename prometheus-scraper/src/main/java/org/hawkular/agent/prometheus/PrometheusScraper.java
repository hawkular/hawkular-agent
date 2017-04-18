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
import java.util.ArrayList;
import java.util.List;

import io.prometheus.client.Metrics;
import io.prometheus.client.Metrics.MetricFamily;

/**
 * Provides a method that can scrape Permetheus metric data from input streams.
 */
public class PrometheusScraper {

    public PrometheusScraper() {
    }

    /**
     * Reads the Permetheus binary metric data from the given stream. The stream will not be closed
     * upon return - the caller must close the stream.
     *
     * @param inputStream the input stream that must contain the delimited binary protocol buffer stream.
     * @return the metric data found in the stream
     * @throws IOException if failed to read the data from the stream
     */
    public List<MetricFamily> scrape(InputStream inputStream) throws IOException {
        ArrayList<MetricFamily> list = new ArrayList<>();

        while (true) {
            MetricFamily metricFamily = Metrics.MetricFamily.parseDelimitedFrom(inputStream);
            if (metricFamily == null) {
                break; // we reached the end, nothing more to process
            }
            list.add(metricFamily);
        }

        return list;
    }
}
