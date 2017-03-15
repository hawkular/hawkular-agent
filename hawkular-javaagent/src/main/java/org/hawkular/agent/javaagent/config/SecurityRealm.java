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
package org.hawkular.agent.javaagent.config;

import java.security.KeyStore;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

import com.fasterxml.jackson.annotation.JsonProperty;

public class SecurityRealm implements Validatable {

    @JsonProperty(required = true)
    public String name;

    @JsonProperty(value = "keystore-path", required = true)
    public String keystorePath;

    @JsonProperty(value = "keystore-password", required = true)
    public String keystorePassword;

    @JsonProperty(value = "key-password")
    public String keyPassword;

    @JsonProperty(value = "keystore-type")
    public String keystoreType = KeyStore.getDefaultType();

    @JsonProperty(value = "key-manager-algorithm")
    public String keyManagerAlgorithm = KeyManagerFactory.getDefaultAlgorithm();

    @JsonProperty(value = "trust-manager-algorithm")
    public String trustManagerAlgorithm = TrustManagerFactory.getDefaultAlgorithm();

    @JsonProperty(value = "ssl-protocol")
    public String sslProtocol = "TLSv1";

    public SecurityRealm() {
    }

    public SecurityRealm(SecurityRealm original) {
        this.name = original.name;
        this.keystorePath = original.keystorePath;
        this.keystorePassword = original.keystorePassword;
        this.keyPassword = original.keyPassword;
        this.keystoreType = original.keystoreType;
        this.keyManagerAlgorithm = original.keyManagerAlgorithm;
        this.trustManagerAlgorithm = original.trustManagerAlgorithm;
        this.sslProtocol = original.sslProtocol;
    }

    @Override
    public void validate() throws Exception {
        if (name == null) {
            throw new Exception("security-realm name must be specified");
        }

        if (keystorePath == null || keystorePath.trim().length() == 0) {
            throw new Exception("security-realm: [" + name + "] keystore-path must be specified");
        }

        if (keystorePassword == null || keystorePassword.trim().length() == 0) {
            throw new Exception("security-realm: [" + name + "] keystore-password must be specified");
        }

        try {
            KeyStore.getInstance(keystoreType);
        } catch (Exception e) {
            throw new Exception("security-realm: [" + name + "] keystore-type [" + keystoreType
                    + "] is invalid. You may want to use the VM default of ["
                    + KeyStore.getDefaultType() + "]", e);
        }

        try {
            KeyManagerFactory.getInstance(keyManagerAlgorithm);
        } catch (Exception e) {
            throw new Exception("security-realm: [" + name + "] key-manager-algorithm [" + keyManagerAlgorithm
                    + "] is invalid. You may want to use the VM default of ["
                    + KeyManagerFactory.getDefaultAlgorithm() + "]", e);
        }

        try {
            TrustManagerFactory.getInstance(trustManagerAlgorithm);
        } catch (Exception e) {
            throw new Exception("security-realm: [" + name + "] trust-manager-algorithm [" + trustManagerAlgorithm
                    + "] is invalid. You may want to use the VM default of ["
                    + TrustManagerFactory.getDefaultAlgorithm() + "]", e);
        }

        try {
            SSLContext.getInstance(sslProtocol);
        } catch (Exception e) {
            throw new Exception("security-realm: [" + name + "] ssl-protocol [" + sslProtocol
                    + "] is invalid. You may want to use [TLSv1]", e);
        }
    }
}
