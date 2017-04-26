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
    private StringExpression keystoreType = new StringExpression(KeyStore.getDefaultType());

    @JsonProperty(value = "key-manager-algorithm")
    private StringExpression keyManagerAlgorithm = new StringExpression(KeyManagerFactory.getDefaultAlgorithm());

    @JsonProperty(value = "trust-manager-algorithm")
    private StringExpression trustManagerAlgorithm = new StringExpression(TrustManagerFactory.getDefaultAlgorithm());

    @JsonProperty(value = "ssl-protocol")
    private StringExpression sslProtocol = new StringExpression("TLSv1");

    public SecurityRealm() {
    }

    public SecurityRealm(SecurityRealm original) {
        this.name = original.name;
        this.keystorePath = original.keystorePath == null ? null : new StringExpression(original.keystorePath);
        this.keystorePassword = original.keystorePassword == null ? null
                : new StringExpression(original.keystorePassword);
        this.keyPassword = original.keyPassword == null ? null : new StringExpression(original.keyPassword);
        this.keystoreType = original.keystoreType == null ? null : new StringExpression(original.keystoreType);
        this.keyManagerAlgorithm = original.keyManagerAlgorithm == null ? null
                : new StringExpression(original.keyManagerAlgorithm);
        this.trustManagerAlgorithm = original.trustManagerAlgorithm == null ? null
                : new StringExpression(original.trustManagerAlgorithm);
        this.sslProtocol = original.sslProtocol == null ? null : new StringExpression(original.sslProtocol);
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

        if (keystoreType == null) {
            throw new Exception("security-realm: [" + name + "] keystore-type must be specified");
        }
        try {
            KeyStore.getInstance(keystoreType.get().toString());
        } catch (Exception e) {
            throw new Exception("security-realm: [" + name + "] keystore-type [" + keystoreType.get()
                    + "] is invalid. You may want to use the VM default of ["
                    + KeyStore.getDefaultType() + "]", e);
        }

        if (keyManagerAlgorithm == null) {
            throw new Exception("security-realm: [" + name + "] key-manager-algorithm must be specified");
        }
        try {
            KeyManagerFactory.getInstance(keyManagerAlgorithm.get().toString());
        } catch (Exception e) {
            throw new Exception("security-realm: [" + name + "] key-manager-algorithm [" + keyManagerAlgorithm.get()
                    + "] is invalid. You may want to use the VM default of ["
                    + KeyManagerFactory.getDefaultAlgorithm() + "]", e);
        }

        if (trustManagerAlgorithm == null) {
            throw new Exception("security-realm: [" + name + "] trust-manager-algorithm must be specified");
        }
        try {
            TrustManagerFactory.getInstance(trustManagerAlgorithm.get().toString());
        } catch (Exception e) {
            throw new Exception(
                    "security-realm: [" + name + "] trust-manager-algorithm [" + trustManagerAlgorithm.get()
                            + "] is invalid. You may want to use the VM default of ["
                            + TrustManagerFactory.getDefaultAlgorithm() + "]",
                    e);
        }

        if (sslProtocol == null) {
            throw new Exception("security-realm: [" + name + "] ssl-protocol must be specified");
        }
        try {
            SSLContext.getInstance(sslProtocol.get().toString());
        } catch (Exception e) {
            throw new Exception("security-realm: [" + name + "] ssl-protocol [" + sslProtocol.get()
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
        return keystoreType == null ? null : keystoreType.get().toString();
    }

    public void setKeystoreType(String keystoreType) {
        if (this.keystoreType != null) {
            this.keystoreType.set(new StringValue(keystoreType));
        } else {
            this.keystoreType = new StringExpression(new StringValue(keystoreType));
        }
    }

    public String getKeyManagerAlgorithm() {
        return keyManagerAlgorithm == null ? null : keyManagerAlgorithm.get().toString();
    }

    public void setKeyManagerAlgorithm(String keyManagerAlgorithm) {
        if (this.keyManagerAlgorithm != null) {
            this.keyManagerAlgorithm.set(new StringValue(keyManagerAlgorithm));
        } else {
            this.keyManagerAlgorithm = new StringExpression(new StringValue(keyManagerAlgorithm));
        }
    }

    public String getTrustManagerAlgorithm() {
        return trustManagerAlgorithm == null ? null : trustManagerAlgorithm.get().toString();
    }

    public void setTrustManagerAlgorithm(String trustManagerAlgorithm) {
        if (this.trustManagerAlgorithm != null) {
            this.trustManagerAlgorithm.set(new StringValue(trustManagerAlgorithm));
        } else {
            this.trustManagerAlgorithm = new StringExpression(new StringValue(trustManagerAlgorithm));
        }
    }

    public String getSslProtocol() {
        return sslProtocol == null ? null : sslProtocol.get().toString();
    }

    public void setSslProtocol(String sslProtocol) {
        if (this.sslProtocol != null) {
            this.sslProtocol.set(new StringValue(sslProtocol));
        } else {
            this.sslProtocol = new StringExpression(new StringValue(sslProtocol));
        }
    }
}
