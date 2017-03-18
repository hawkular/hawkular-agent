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

import org.hawkular.agent.javaagent.config.StringExpression.StringValue;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonAutoDetect( //
        fieldVisibility = Visibility.NONE, //
        getterVisibility = Visibility.NONE, //
        setterVisibility = Visibility.NONE, //
        isGetterVisibility = Visibility.NONE)
public class SecurityRealm implements Validatable {

    @JsonProperty(required = true)
    private String name;

    @JsonProperty(value = "keystore-path", required = true)
    private StringExpression keystorePath;

    @JsonProperty(value = "keystore-password", required = true)
    private StringExpression keystorePassword;

    @JsonProperty(value = "key-password")
    private StringExpression keyPassword;

    @JsonProperty(value = "keystore-type")
    private String keystoreType = KeyStore.getDefaultType();

    @JsonProperty(value = "key-manager-algorithm")
    private String keyManagerAlgorithm = KeyManagerFactory.getDefaultAlgorithm();

    @JsonProperty(value = "trust-manager-algorithm")
    private String trustManagerAlgorithm = TrustManagerFactory.getDefaultAlgorithm();

    @JsonProperty(value = "ssl-protocol")
    private String sslProtocol = "TLSv1";

    public SecurityRealm() {
    }

    public SecurityRealm(SecurityRealm original) {
        this.name = original.name;
        this.keystorePath = original.keystorePath == null ? null : new StringExpression(original.keystorePath);
        this.keystorePassword = original.keystorePassword == null ? null
                : new StringExpression(original.keystorePassword);
        this.keyPassword = original.keyPassword == null ? null : new StringExpression(original.keyPassword);
        this.keystoreType = original.keystoreType;
        this.keyManagerAlgorithm = original.keyManagerAlgorithm;
        this.trustManagerAlgorithm = original.trustManagerAlgorithm;
        this.sslProtocol = original.sslProtocol;
    }

    @Override
    public void validate() throws Exception {
        if (name == null || name.trim().isEmpty()) {
            throw new Exception("security-realm name must be specified");
        }

        if (keystorePath == null || keystorePath.get().toString().trim().length() == 0) {
            throw new Exception("security-realm: [" + name + "] keystore-path must be specified");
        }

        if (keystorePassword == null || keystorePassword.get().toString().trim().length() == 0) {
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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getKeystorePath() {
        return keystorePath == null ? null : keystorePath.get().toString();
    }

    public void setKeystorePath(String keystorePath) {
        if (this.keystorePath != null) {
            this.keystorePath.set(new StringValue(keystorePath));
        } else {
            this.keystorePath = new StringExpression(new StringValue(keystorePath));
        }
    }

    public String getKeystorePassword() {
        return keystorePassword == null ? null : keystorePassword.get().toString();
    }

    public void setKeystorePassword(String keystorePassword) {
        if (this.keystorePassword != null) {
            this.keystorePassword.set(new StringValue(keystorePassword));
        } else {
            this.keystorePassword = new StringExpression(new StringValue(keystorePassword));
        }
    }

    public String getKeyPassword() {
        return keyPassword == null ? null : keyPassword.get().toString();
    }

    public void setKeyPassword(String keyPassword) {
        if (this.keyPassword != null) {
            this.keyPassword.set(new StringValue(keyPassword));
        } else {
            this.keyPassword = new StringExpression(new StringValue(keyPassword));
        }
    }

    public String getKeystoreType() {
        return keystoreType;
    }

    public void setKeystoreType(String keystoreType) {
        this.keystoreType = keystoreType;
    }

    public String getKeyManagerAlgorithm() {
        return keyManagerAlgorithm;
    }

    public void setKeyManagerAlgorithm(String keyManagerAlgorithm) {
        this.keyManagerAlgorithm = keyManagerAlgorithm;
    }

    public String getTrustManagerAlgorithm() {
        return trustManagerAlgorithm;
    }

    public void setTrustManagerAlgorithm(String trustManagerAlgorithm) {
        this.trustManagerAlgorithm = trustManagerAlgorithm;
    }

    public String getSslProtocol() {
        return sslProtocol;
    }

    public void setSslProtocol(String sslProtocol) {
        this.sslProtocol = sslProtocol;
    }
}
