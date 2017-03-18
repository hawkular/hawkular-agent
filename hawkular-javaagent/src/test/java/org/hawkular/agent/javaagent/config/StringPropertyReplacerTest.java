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

import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

public class StringPropertyReplacerTest {
    @Test
    public void testEmpty() {
        Assert.assertNull(StringPropertyReplacer.replaceProperties(null));
        Assert.assertTrue(StringPropertyReplacer.replaceProperties("").isEmpty());
    }

    @Test
    public void testReplaceWithSysProps() {
        Assert.assertEquals("some literal", StringPropertyReplacer.replaceProperties("some literal"));
        Assert.assertEquals("${my.sysprop}", StringPropertyReplacer.replaceProperties("${my.sysprop}"));
        Assert.assertEquals("abc", StringPropertyReplacer.replaceProperties("${my.sysprop:abc}"));
        System.setProperty("my.sysprop", "xyz");
        Assert.assertEquals("my.sysprop", StringPropertyReplacer.replaceProperties("my.sysprop"));
        Assert.assertEquals("xyz", StringPropertyReplacer.replaceProperties("${my.sysprop}"));
        Assert.assertEquals("xyz", StringPropertyReplacer.replaceProperties("${my.sysprop:abc}"));
        Assert.assertEquals("xyz", StringPropertyReplacer.replaceProperties("${my.sysprop,does-not-exist:abc}"));
        Assert.assertEquals("xyz", StringPropertyReplacer.replaceProperties("${my.sysprop,does-not-exist}"));
        Assert.assertEquals("xyz", StringPropertyReplacer.replaceProperties("${does-not-exist,my.sysprop:abc}"));
        Assert.assertEquals("xyz", StringPropertyReplacer.replaceProperties("${does-not-exist,my.sysprop}"));
        System.clearProperty("my.sysprop");
        Assert.assertEquals("abc", StringPropertyReplacer.replaceProperties("${my.sysprop:abc}"));
        Assert.assertEquals("abc", StringPropertyReplacer.replaceProperties("${unknown1,unknown2,unknown3:abc}"));
    }

    @Test
    public void testReplaceWithEnv() {
        Map.Entry<String, String> envvar = System.getenv().entrySet().iterator().next(); // just pick a env var to use

        String token = String.format("${ENV~%s}", envvar.getKey());
        String tokenAsFirst = String.format("${ENV~%s,does-not-exist}", envvar.getKey());
        String tokenAsSecond = String.format("${does-not-exist,ENV~%s}", envvar.getKey());
        String tokenDefault = String.format("${ENV~%s:abc}", envvar.getKey());
        String tokenDefaultAsFirst = String.format("${ENV~%s,does-not-exist:abc}", envvar.getKey());
        String tokenDefaultAsSecond = String.format("${does-not-exist,ENV~%s:abc}", envvar.getKey());

        Assert.assertEquals(envvar.getValue(), StringPropertyReplacer.replaceProperties(token));
        Assert.assertEquals(envvar.getValue(), StringPropertyReplacer.replaceProperties(tokenAsFirst));
        Assert.assertEquals(envvar.getValue(), StringPropertyReplacer.replaceProperties(tokenAsSecond));
        Assert.assertEquals(envvar.getValue(), StringPropertyReplacer.replaceProperties(tokenDefault));
        Assert.assertEquals(envvar.getValue(), StringPropertyReplacer.replaceProperties(tokenDefaultAsFirst));
        Assert.assertEquals(envvar.getValue(), StringPropertyReplacer.replaceProperties(tokenDefaultAsSecond));

        Assert.assertEquals("abc", StringPropertyReplacer.replaceProperties("${ENV~DoEs_NoT_ExIsT:abc}"));
    }
}
