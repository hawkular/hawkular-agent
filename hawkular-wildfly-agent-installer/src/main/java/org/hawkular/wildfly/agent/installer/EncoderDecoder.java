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

import java.security.InvalidKeyException;
import java.security.spec.KeySpec;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

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
     * In order to decode the string, pass the returned string with the same key and salt to
     * {@link #decode(String, String, byte[])}.
     *
     * @param clearText the message to encrypt (if null, then null is returned)
     * @param key the secret key used to encrypt the clear text string (must not be null)
     * @param salt random sequence to be used with the key to encrypt the clear text string (must not be null)
     * @return the encrypted message
     * @throws IllegalArgumentException if the key and/or salt is null or if the salt is of a wrong size
     * @throws Exception on error
     */
    public static String encode(String clearText, String key, byte[] salt) throws Exception {
        if (null == clearText) {
            return null;
        }

        if (null == key || null == salt) {
            throw new IllegalArgumentException("Key and salt must be specified.");
        }

        SecretKeySpec keySpec = getKeySpecForKeyAndSalt(key, salt);
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5PADDING");

        try {
            cipher.init(Cipher.ENCRYPT_MODE, keySpec);
        } catch (InvalidKeyException invalidKeyException) {
            // user needs the unlimited jurisdiction files...
            keySpec = getWeakKeySpecForKeyAndSalt(key, salt);
            cipher = Cipher.getInstance("DES");
            cipher.init(Cipher.ENCRYPT_MODE, keySpec);
        }

        byte[] encryptedData = cipher.doFinal(clearText.getBytes("UTF-8"));
        byte[] iv = cipher.getIV();

        if (null != iv) {
            String ivAsString = new String(Base64.getEncoder().encode(cipher.getIV()), "UTF-8");
            String encryptedAsString = new String(Base64.getEncoder().encode(encryptedData), "UTF-8");
            return ivAsString + "$" + encryptedAsString;
        } else {
            return new String(Base64.getEncoder().encode(encryptedData), "UTF-8");
        }
    }

    /**
     * Decrypts the given encoded string using the given secret key. The encoded string must have
     * been encoded via the {@link #encode(String, String, byte[])} method with the same key in order
     * for the decode to work.
     *
     * @param encodedString the encoded message (if null, then null is returned)
     * @param key the key that was used to encode the string (must not be null)
     * @param salt random sequence that was used to with the key to encode the string (must not be null)
     * @return the original, clear text message that was encoded
     * @throws IllegalArgumentException if the key and/or salt is null or if the salt is of a wrong size
     * @throws Exception on error
     */
    public static String decode(String encodedString, String key, byte[] salt) throws Exception {
        if (null == encodedString) {
            return null;
        }

        if (null == key || null == salt) {
            throw new IllegalArgumentException("Key and salt must be specified.");
        }

        SecretKeySpec keySpec;
        Cipher cipher;
        byte[] encodedBytes;
        if (encodedString.contains("$")) {
            keySpec = getKeySpecForKeyAndSalt(key, salt);
            cipher = Cipher.getInstance("AES/CBC/PKCS5PADDING");
            String[] parts = encodedString.split("\\$");
            byte[] iv = Base64.getDecoder().decode(parts[0]);
            encodedBytes = Base64.getDecoder().decode(parts[1]);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, new IvParameterSpec(iv));
        } else {
            keySpec = getWeakKeySpecForKeyAndSalt(key, salt);
            cipher = Cipher.getInstance("DES");
            encodedBytes = Base64.getDecoder().decode(encodedString);
            cipher.init(Cipher.DECRYPT_MODE, keySpec);
        }

        return new String(cipher.doFinal(encodedBytes), "UTF-8");
    }

    private static SecretKeySpec getKeySpecForKeyAndSalt(String key, byte[] salt) throws Exception {
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        KeySpec spec = new PBEKeySpec(key.toCharArray(), salt, 80_000, 256);
        return new SecretKeySpec(factory.generateSecret(spec).getEncoded(), "AES");
    }

    private static SecretKeySpec getWeakKeySpecForKeyAndSalt(String key, byte[] salt) throws Exception {
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        KeySpec spec = new PBEKeySpec(key.toCharArray(), salt, 80_000, 64);
        return new SecretKeySpec(factory.generateSecret(spec).getEncoded(), "DES");
    }
}
