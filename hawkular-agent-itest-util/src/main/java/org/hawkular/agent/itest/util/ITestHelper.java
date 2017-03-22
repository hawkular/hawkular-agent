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

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;

import org.hawkular.agent.monitor.storage.ExtendedInventoryStructure;
import org.hawkular.inventory.api.model.Blueprint;
import org.hawkular.inventory.api.model.Entity;
import org.hawkular.inventory.api.model.InventoryStructure;
import org.hawkular.inventory.json.InventoryJacksonConfig;
import org.hawkular.inventory.paths.CanonicalPath;
import org.hawkular.inventory.paths.RelativePath;
import org.hawkular.inventory.paths.SegmentType;
import org.testng.AssertJUnit;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Throwables;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class ITestHelper {

    private static final int ATTEMPT_COUNT = 500;
    private static final long ATTEMPT_DELAY = 5000;

    private final String tenantId;
    private final String hawkularAuthHeader;
    private final String baseInvUri;
    private final OkHttpClient client;
    private final ObjectMapper mapper;

    public ITestHelper(String tenantId, String hawkularAuthHeader, String baseInvUri) {
        this.tenantId = tenantId;
        this.hawkularAuthHeader = hawkularAuthHeader;
        this.baseInvUri = baseInvUri;
        this.mapper = new ObjectMapper(new JsonFactory());
        InventoryJacksonConfig.configure(mapper);
        this.client = new OkHttpClient();
    }

    public Request.Builder newAuthRequest() {
        return new Request.Builder()
                .addHeader("Authorization", hawkularAuthHeader)
                .addHeader("Accept", "application/json")
                .addHeader("Hawkular-Tenant", tenantId);
    }

    private Optional<InventoryStructure> extractStructureFromResponse(String responseBody) {
        List<ExtendedInventoryStructure> l = extractStructuresFromResponse(responseBody);
        if (l.size() != 1) {
            return Optional.empty();
        }
        return Optional.of(l.get(0).getStructure());
    }

    private List<ExtendedInventoryStructure> extractStructuresFromResponse(String responseBody) {
        try {
            // Dechunk data: "masters" are those with tag "chunk" == 0
            Map<String, JsonNode> mastersById = new HashMap<>();
            Map<String, JsonNode> slavesById = new HashMap<>();
            for (JsonNode child : mapper.readTree(responseBody)) {
                JsonNode dataNode = child.get("data").get(0);
                if (dataNode.has("tags") && dataNode.get("tags").has("chunk")) {
                    String chunkId = dataNode.get("tags").get("chunk").asText();
                    if ("0".equals(chunkId)) {
                        mastersById.put(child.get("id").asText(), dataNode);
                    } else {
                        slavesById.put(child.get("id").asText(), dataNode);
                    }
                }
            }
            // Extract each master with its slave(s)
            return mastersById.entrySet().stream()
                    .map(e -> rebuildFromChunks(e.getKey(), e.getValue(), slavesById))
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .collect(Collectors.toList());
        } catch (IOException e) {
            throw Throwables.propagate(e);
        }
    }

    private Optional<ExtendedInventoryStructure> rebuildFromChunks(String masterId, JsonNode masterData, Map<String, JsonNode> slavesById) {
        try {
            int nbChunks = masterData.get("tags").get("chunks").asInt();
            int totalSize = masterData.get("tags").get("size").asInt();
            byte[] master = masterData.get("value").binaryValue();
            if (master.length == 0) {
                return Optional.empty();
            }
            byte[] all = new byte[totalSize];
            int pos = 0;
            System.arraycopy(master, 0, all, pos, master.length);
            pos += master.length;
            for (int i = 1; i < nbChunks; i++) {
                String id = masterId + "." + i;
                JsonNode slaveData = slavesById.get(id);
                byte[] slave = slaveData.get("value").binaryValue();
                System.arraycopy(slave, 0, all, pos, slave.length);
                pos += slave.length;
            }
            String decompressed = decompress(all);
            ExtendedInventoryStructure structure = mapper.readValue(decompressed, ExtendedInventoryStructure.class);
            return Optional.of(structure);
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }

    private static String decompress(byte[] gzipped) throws IOException {
        if ((gzipped == null) || (gzipped.length == 0)) {
            return "";
        }
        StringBuilder outStr = new StringBuilder();
        GZIPInputStream gis = new GZIPInputStream(new ByteArrayInputStream(gzipped));
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(gis, "UTF-8"));
        String line;
        while ((line = bufferedReader.readLine()) != null) {
            outStr.append(line);
        }
        return outStr.toString();
    }

    public Optional<InventoryStructure> getInventoryStructure(String feedId, String type, String id) throws Throwable {
        // Fetch metrics by tag
        String url = baseInvUri + "/raw/query";
        String tags = "module:inventory,type:" + type + ",feed:" + feedId + ",id:" + id;
        String params = "{\"tags\":\"" + tags + "\"," +
                "\"limit\":1," +
                "\"order\":\"DESC\"}";
        String response = getWithRetries(newAuthRequest()
                .url(url)
                .post(RequestBody.create(MediaType.parse("application/json"), params))
                .build());
        return Optional.of(response)
                .filter(r -> !r.isEmpty())
                .flatMap(this::extractStructureFromResponse);
    }

    public Optional<Blueprint> getBlueprintFromCP(CanonicalPath path) throws Throwable {
        Iterator<CanonicalPath> upDown = path.descendingIterator();
        if (!upDown.hasNext()) {
            return Optional.empty();
        }
        // Ignore tenant
        upDown.next();
        if (!upDown.hasNext()) {
            return Optional.empty();
        }
        String feed = upDown.next().getSegment().getElementId();
        if (!upDown.hasNext()) {
            return Optional.empty();
        }
        CanonicalPath itemPath = upDown.next();
        Optional<InventoryStructure> inventoryStructure = getInventoryStructure(
                feed,
                itemPath.getSegment().getElementType().getSerialized(),
                itemPath.getSegment().getElementId());
        return inventoryStructure.map(struct -> (Blueprint) struct.get(path.relativeTo(itemPath)));
    }

    public Map<CanonicalPath, Blueprint> getBlueprintsByType(String feedId, String type) throws Throwable {
        // Fetch metrics by tag
        String url = baseInvUri + "/raw/query";
        String tags = "module:inventory,type:r,feed:" + feedId + ",restypes:.*|" + type + "|.*";
        String params = "{\"tags\":\"" + tags + "\"," +
                "\"limit\":1," +
                "\"order\":\"DESC\"}";
        String response = getWithRetries(newAuthRequest()
                .url(url)
                .post(RequestBody.create(MediaType.parse("application/json"), params))
                .build());
        if (response.isEmpty()) {
            return new HashMap<>();
        }

        // Now find each collected resource path in their belonging InventoryStructure
        List<ExtendedInventoryStructure> structures = extractStructuresFromResponse(response);
        Map<CanonicalPath, Blueprint> matchingResources = new HashMap<>();
        CanonicalPath feedPath = feedPath(feedId).get();
        structures.forEach(structure -> {
            Collection<String> childResources = structure.getTypesIndex().get(type);
            if (childResources != null) {
                CanonicalPath rootPath = feedPath.modified().extend(SegmentType.r, structure.getStructure().getRoot().getId()).get();
                for (String resourcePath : childResources) {
                    RelativePath relativePath = RelativePath.fromString(resourcePath);
                    Blueprint bp = structure.getStructure().get(relativePath);
                    if (bp != null) {
                        CanonicalPath absolutePath = relativePath.applyTo(rootPath);
                        matchingResources.put(absolutePath, bp);
                    }
                }
            }
        });
        return matchingResources;
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

    public Map.Entry<CanonicalPath, Blueprint> waitForResourceContaining(String feed, String rType, String containing, long sleep, int attempts)
            throws Throwable {
        for (int i = 0; i < attempts; i++) {
            Optional<Map.Entry<CanonicalPath, Blueprint>> resource = getBlueprintsByType(feed, rType)
                    .entrySet().stream()
                    .filter(e -> containing == null || ((Entity.Blueprint) (e.getValue())).getId().contains(containing))
                    .findFirst();
            if (resource.isPresent()) {
                return resource.get();
            }
            Thread.sleep(sleep);
        }
        throw new AssertionError("Resource [type=" + rType + ", containing=" + containing + "] not found after " + attempts + " attempts.");
    }

    public void waitForNoResourceContaining(String feed, String rType, String containing, long sleep, int attempts)
            throws Throwable {
        for (int i = 0; i < attempts; i++) {
            Optional<Map.Entry<CanonicalPath, Blueprint>> resource = getBlueprintsByType(feed, rType)
                    .entrySet().stream()
                    .filter(e -> containing == null || ((Entity.Blueprint) (e.getValue())).getId().contains(containing))
                    .findFirst();
            if (!resource.isPresent()) {
                return;
            }
            Thread.sleep(sleep);
        }
        throw new AssertionError("Resource [type=" + rType + ", containing=" + containing + "] still found after " + attempts + " attempts.");
    }

    public CanonicalPath.FeedBuilder feedPath(String feedId) {
        return CanonicalPath.of().tenant(tenantId).feed(feedId);
    }

    public OkHttpClient client() {
        return client;
    }

    public ObjectMapper mapper() {
        return mapper;
    }

    public String getTenantId() {
        return tenantId;
    }
}
