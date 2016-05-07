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
package org.hawkular.agent.monitor.dynamicprotocol.prometheus;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;

import javax.net.ssl.SSLContext;

import org.hawkular.agent.monitor.extension.MonitorServiceConfiguration.DynamicEndpointConfiguration;
import org.hawkular.agent.monitor.util.BaseHttpClientGenerator;
import org.hawkular.agent.monitor.util.BaseHttpClientGenerator.Configuration;
import org.hawkular.agent.prometheus.PrometheusScraper;

import com.squareup.okhttp.Call;
import com.squareup.okhttp.MediaType;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;
import com.squareup.okhttp.ResponseBody;

public class HawkularPrometheusScraper extends PrometheusScraper {
    // if a username matches this, it means we are to use bearer token auth, not basic auth
    private static final String BEARER_TOKEN_USER_ID = "_bearer";

    private final DynamicEndpointConfiguration endpointConfig;
    private final SSLContext sslContext;

    public HawkularPrometheusScraper(DynamicEndpointConfiguration endpointConfig, SSLContext sslContext)
            throws MalformedURLException {
        super(endpointConfig.getConnectionData().getUri().toURL());
        this.endpointConfig = endpointConfig;
        this.sslContext = sslContext;
    }

    @Override
    protected OpenConnectionDetails openConnection(URL endpointUrl) throws IOException {
        Configuration.Builder bldr = new Configuration.Builder()
                .useSsl(endpointUrl.getProtocol().equals("https"))
                .sslContext(sslContext)
                .username(endpointConfig.getConnectionData().getUsername())
                .password(endpointConfig.getConnectionData().getPassword());

        BaseHttpClientGenerator httpClientGen = new BaseHttpClientGenerator(bldr.build());
        OkHttpClient httpClient = httpClientGen.getHttpClient();
        Request request = buildGetRequest(endpointUrl, httpClientGen);
        Call call = httpClient.newCall(request);
        Response response = call.execute();
        ResponseBody responseBody = response.body();
        InputStream inputStream = responseBody.byteStream();
        MediaType contentType = responseBody.contentType();

        return new OpenConnectionDetails(inputStream, (contentType != null) ? contentType.toString() : null);
    }

    private Request buildGetRequest(URL endpointUrl, BaseHttpClientGenerator httpClientGen) {

        Request.Builder requestBuilder = new Request.Builder()
                .get()
                .url(endpointUrl)
                .addHeader("Accept", getBinaryFormatContentType());

        if (httpClientGen.getConfiguration().getUsername() != null) {
            if (httpClientGen.getConfiguration().getUsername().equals(BEARER_TOKEN_USER_ID)) {
                requestBuilder.addHeader("Authorization", "Basic " + httpClientGen.buildBase64Credentials());
            } else {
                requestBuilder.addHeader("Authorization", "Bearer " + httpClientGen.getConfiguration().getPassword());
            }
        }

        return requestBuilder.get().build();
    }

}
