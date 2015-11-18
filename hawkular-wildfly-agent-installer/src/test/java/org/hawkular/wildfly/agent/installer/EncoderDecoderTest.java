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

import java.security.SecureRandom;

import org.junit.Assert;
import org.junit.Test;

public class EncoderDecoderTest {

    @Test(expected = IllegalArgumentException.class)
    public void testEncoderDecoderWithNullKey() throws Exception {
        byte[] salt = SecureRandom.getSeed(8);
        EncoderDecoder.encode("useless", null, salt);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testEncoderDecoderWithNullSalt() throws Exception {
        EncoderDecoder.encode("useless", "mahkey", null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testEncoderDecoderWithNullKeyAndSalt() throws Exception {
        EncoderDecoder.encode("useless", null, null);
    }

    @Test
    public void testEncoderDecoderWithNullMessage() throws Exception {
        byte[] salt = SecureRandom.getSeed(8);
        Assert.assertNull(EncoderDecoder.encode(null, "mahkey", salt));
    }

    @Test
    public void testEncoderDecoder() throws Exception {
        assertEncodeDecode(" ", "");
        assertEncodeDecode("  keykey  ", "clearclear");
        assertEncodeDecode("~key!", "cleartextvalue`1234567890-=~!@#$%^&*()_+[]{}|;':,./<>?");
        assertEncodeDecode("`1234567890-=~!@#$%^&*()_+[]{}|;':,./<>?",
                "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ");
    }

    private void assertEncodeDecode(String key, String clearText) throws Exception {
        // the salt is a 8-byte random sequence, provided by the consumer.
        // it's used for generating the actual key (our "key" here is actually the "password" for the key)
        byte[] salt = SecureRandom.getSeed(6);

        String encodedString = EncoderDecoder.encode(clearText, key, salt);
        String decodedString = EncoderDecoder.decode(encodedString, key, salt);
        System.out.printf("key=[%s], clearText=[%s], encoded=[%s], decoded=[%s]\n",
                key, clearText, encodedString, decodedString);
        Assert.assertEquals(clearText, decodedString);
    }
}
