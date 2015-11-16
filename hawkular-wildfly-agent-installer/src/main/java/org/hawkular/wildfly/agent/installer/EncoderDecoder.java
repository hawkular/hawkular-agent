/*
 * Copyright 2015 Red Hat, Inc. and/or its affiliates
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
package org.hawkular.wildfly.agent.installer;

import java.util.Base64;

/**
 * Provides a simple encoder/decoder. It requires an encryption key to encode and then later decode a string.
 * This is not very strong encryption, but is more than obfuscation since it does require a secret key to
 * both encode and decode.
 */
public final class EncoderDecoder {

    // make it uninstantiable
    private EncoderDecoder() {
    }

    /**
     * Given a secret key, this will encode the given clear text message and return the encoded string.
     * In order to decode the string, pass the returned string with the same key to {@link #decode(String, String)}.
     *
     * @param key the secret key used to encrypt the clear text string (must not be null)
     * @param clearText the message to encrypt (if null, then null is returned)
     * @return the encrypted message
     * @throws Exception on error
     */
    public static final String encode(String key, String clearText) throws Exception {
        if (clearText == null) {
            return null;
        }

        if (key == null || key.isEmpty()) {
            throw new IllegalArgumentException("You must provide a key to encrypt the string");
        }

        StringBuilder encodedString = new StringBuilder();
        for (int i = 0; i < clearText.length(); i++) {
            char keyChar = key.charAt(i % key.length());
            char encChar = (char) ((clearText.charAt(i) + keyChar) % ((char) 256));
            encodedString.append(encChar);
        }
        return new String(Base64.getEncoder().encode(encodedString.toString().getBytes()), "UTF-8");
    }

    /**
     * Decrypts the given encoded string using the given secret key. The encoded string must have
     * been encoded via the {@link #encode(String, String)} method with the same key in order
     * for the decode to work.
     *
     * @param key the key that was used to encode the string (must not be null)
     * @param encodedString the encoded mesage (if null, then null is returned)
     * @return the original, clear text message that was encoded
     * @throws Exception on error
     */
    public static final String decode(String key, String encodedString) throws Exception {
        if (encodedString == null) {
            return null;
        }

        if (key == null || key.isEmpty()) {
            throw new IllegalArgumentException("You must provide a key to decrypt the string");
        }

        StringBuilder clearText = new StringBuilder();
        encodedString = new String(Base64.getDecoder().decode(encodedString), "UTF-8");
        for (int i = 0; i < encodedString.length(); i++) {
            int keyChar = key.charAt(i % key.length());
            char decChar = (char) ((encodedString.charAt(i) - keyChar) % ((char) 256));
            clearText.append(decChar);
        }
        return clearText.toString();
    }

}
