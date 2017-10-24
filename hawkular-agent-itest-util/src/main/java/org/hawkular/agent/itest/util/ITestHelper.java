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
package org.hawkular.agent.itest.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Optional;

import org.hawkular.inventory.api.model.Resource;
import org.hawkular.inventory.api.model.ResultSet;
import org.testng.AssertJUnit;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.databind.ObjectMapper;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class ITestHelper {

    private static final int ATTEMPT_COUNT = 500;
    private static final long ATTEMPT_DELAY = 5000;

    private final String hawkularAuthHeader;
    private final String baseInvUri;
    private final OkHttpClient client;
    private final ObjectMapper mapper;

    public ITestHelper(String hawkularAuthHeader, String baseInvUri) {
        this.hawkularAuthHeader = hawkularAuthHeader;
        this.baseInvUri = baseInvUri;
        this.mapper = new ObjectMapper(new JsonFactory());
        this.client = new OkHttpClient();
    }

    public Request.Builder newAuthRequest() {
        return new Request.Builder()
                .addHeader("Authorization", hawkularAuthHeader)
                .addHeader("Accept", "application/json");
    }

    public Collection<Resource> getResourceByType(String feedId, String type, int expectedCount)
            throws Throwable {
        for (int attempt = 0; attempt < ATTEMPT_COUNT; attempt++) {
            // TODO [lponce] this call is not paginating, perhaps enough for itest but it should be adapted in the future
            String url = baseInvUri + "/resources?feedId=" + feedId + "&typeId=" + type;
            String response = getWithRetries(newAuthRequest()
                    .url(url)
                    .get()
                    .build());
            if (response.isEmpty()) {
                return new ArrayList<>();
            }
            ResultSet<Resource> rs = mapper.readValue(response, ResultSet.class);
            if (rs.getResults().size() >= expectedCount) {
                return rs.getResults();
            }
        }
        throw new IllegalStateException("Cannot get expected number of resources. Retries have been exceeded.");
    }

    public String getWithRetries(String url) throws Throwable {
        return getWithRetries(newAuthRequest().url(url).build());
    }

    private String getWithRetries(Request request) throws Throwable {
        Throwable e = null;
        for (int i = 0; i < ATTEMPT_COUNT; i++) {
            try {
                try (Response response = client.newCall(request).execute()) {
                    System.out.println(
                            "Got code " + response.code() + " and message [" + response.message() + "] retries: " +
                                    request.url());
                    AssertJUnit.assertTrue(response.code() == 200 || response.code() == 204);
                    return response.body().string();
                }
            } catch (Throwable t) {
                // some initial attempts may fail so we continue
                e = t;
            }
            System.out.println("URL [" + request.url() + "] not ready yet on " + (i + 1) + " of " + ATTEMPT_COUNT
                    + " attempts, about to retry after " + ATTEMPT_DELAY + " ms");
            Thread.sleep(ATTEMPT_DELAY);
        }
        throw e;
    }

    public Resource waitForResourceContaining(String feed, String rType, String containing,
            long sleep, int attempts)
            throws Throwable {
        for (int i = 0; i < attempts; i++) {
            Optional<Resource> resource = getResourceByType(feed, rType, 0)
                    .stream()
                    .filter(e -> containing == null
                            || e.getName().contains(containing))
                    .findFirst();
            if (resource.isPresent()) {
                return resource.get();
            }
            Thread.sleep(sleep);
        }
        throw new AssertionError("Resource [type=" + rType + ", containing=" + containing + "] not found after "
                + attempts + " attempts.");
    }

    public void waitForNoResourceContaining(String feed, String rType, String containing, long sleep, int attempts)
            throws Throwable {
        for (int i = 0; i < attempts; i++) {
            Optional<Resource> resource = getResourceByType(feed, rType, 0)
                    .stream()
                    .filter(e -> containing == null
                            || e.getName().contains(containing))
                    .findFirst();
            if (!resource.isPresent()) {
                return;
            }
            Thread.sleep(sleep);
        }
        throw new AssertionError("Resource [type=" + rType + ", containing=" + containing + "] still found after "
                + attempts + " attempts.");
    }

    public OkHttpClient client() {
        return client;
    }

    public ObjectMapper mapper() {
        return mapper;
    }
}
