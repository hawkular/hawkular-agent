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

import org.junit.Assert;
import org.junit.Test;

public class EncoderDecoderTest {

    @Test
    public void testEncoderDecoderWithNull() throws Exception {
        Assert.assertNull(EncoderDecoder.encode("useless", null));
        Assert.assertNull(EncoderDecoder.decode("useless", null));
        try {
            EncoderDecoder.encode(null, "");
            Assert.fail("null key should not be allowed in encode");
        } catch (Exception expected) {
        }
        try {
            EncoderDecoder.decode(null, "");
            Assert.fail("null key should not be allowed in decode");
        } catch (Exception expected) {
        }
        try {
            EncoderDecoder.encode("", "");
            Assert.fail("empty key should not be allowed in encode");
        } catch (Exception expected) {
        }
        try {
            EncoderDecoder.decode("", "");
            Assert.fail("empty key should not be allowed in decode");
        } catch (Exception expected) {
        }
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
        String encodedString = EncoderDecoder.encode(key, clearText);
        String decodedString = EncoderDecoder.decode(key, encodedString);
        System.out.printf("key=[%s], clearText=[%s], encoded=[%s], decoded=[%s]\n",
                key, clearText, encodedString, decodedString);
        Assert.assertEquals(clearText, decodedString);
    }

}
