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
    public void testSetSysProp() {
        Assert.assertEquals("false", StringPropertyReplacer.replaceProperties("${<set>my.sysprop}"));
        Assert.assertEquals("false", StringPropertyReplacer.replaceProperties("${doesntexist,<set>my.sysprop}"));
        System.setProperty("my.sysprop", "xyz");
        Assert.assertEquals("true", StringPropertyReplacer.replaceProperties("${<set>my.sysprop}"));
        Assert.assertEquals("true", StringPropertyReplacer.replaceProperties("${doesntexist,<set>my.sysprop}"));
        System.clearProperty("my.sysprop");

        try {
            StringPropertyReplacer.replaceProperties("${<set>my.sysprop,foo}");
            Assert.fail("Should not be able to specify composite key when using set");
        } catch (IllegalArgumentException expected) {
        }
        try {
            StringPropertyReplacer.replaceProperties("${<set>my.sysprop:blah}");
            Assert.fail("Should not be able to specify default value when using set");
        } catch (IllegalArgumentException expected) {
        }
    }

    @Test
    public void testSetEnv() {
        Assert.assertEquals("false", StringPropertyReplacer.replaceProperties("${<set>env._DOESNT_EXIST}"));
        Assert.assertEquals("false", StringPropertyReplacer.replaceProperties("${notexist,<set>env._DOESNT_EXIST}"));
        String envvar = System.getenv().keySet().iterator().next(); // just pick a env var to use
        Assert.assertEquals("true",
                StringPropertyReplacer.replaceProperties(String.format("${<set>env.%s}", envvar)));
        Assert.assertEquals("true",
                StringPropertyReplacer.replaceProperties(String.format("${notexist,<set>env.%s}", envvar)));

        try {
            StringPropertyReplacer.replaceProperties("${<set>env._DOESNT_EXIST,foo}");
            Assert.fail("Should not be able to specify composite key when using set");
        } catch (IllegalArgumentException expected) {
        }
        try {
            StringPropertyReplacer.replaceProperties("${<set>env._DOESNT_EXIST:blah}");
            Assert.fail("Should not be able to specify default value when using set");
        } catch (IllegalArgumentException expected) {
        }
    }

    @Test
    public void testNotSetSysProp() {
        Assert.assertEquals("true", StringPropertyReplacer.replaceProperties("${<notset>my.sysprop}"));
        Assert.assertEquals("true", StringPropertyReplacer.replaceProperties("${doesntexist,<notset>my.sysprop}"));
        System.setProperty("my.sysprop", "xyz");
        Assert.assertEquals("false", StringPropertyReplacer.replaceProperties("${<notset>my.sysprop}"));
        Assert.assertEquals("false", StringPropertyReplacer.replaceProperties("${doesntexist,<notset>my.sysprop}"));
        System.clearProperty("my.sysprop");

        try {
            StringPropertyReplacer.replaceProperties("${<notset>my.sysprop,foo}");
            Assert.fail("Should not be able to specify composite key when using notset");
        } catch (IllegalArgumentException expected) {
        }
        try {
            StringPropertyReplacer.replaceProperties("${<notset>my.sysprop:blah}");
            Assert.fail("Should not be able to specify default value when using notset");
        } catch (IllegalArgumentException expected) {
        }
    }

    @Test
    public void testNotSetEnv() {
        Assert.assertEquals("true", StringPropertyReplacer.replaceProperties("${<notset>env._DOESNT_EXIST}"));
        Assert.assertEquals("true", StringPropertyReplacer.replaceProperties("${notexist,<notset>env._DOESNT_EXIST}"));
        String envvar = System.getenv().keySet().iterator().next(); // just pick a env var to use
        Assert.assertEquals("false",
                StringPropertyReplacer.replaceProperties(String.format("${<notset>env.%s}", envvar)));
        Assert.assertEquals("false",
                StringPropertyReplacer.replaceProperties(String.format("${notexist,<notset>env.%s}", envvar)));

        try {
            StringPropertyReplacer.replaceProperties("${<notset>env._DOESNT_EXIST,foo}");
            Assert.fail("Should not be able to specify composite key when using notset");
        } catch (IllegalArgumentException expected) {
        }
        try {
            StringPropertyReplacer.replaceProperties("${<notset>env._DOESNT_EXIST:blah}");
            Assert.fail("Should not be able to specify default value when using notset");
        } catch (IllegalArgumentException expected) {
        }
    }

    @Test
    public void testReplaceMultipleKeysWithSysPropAndEnv() {
        // neither env var or sysprop is set - get default
        Assert.assertEquals("abc", StringPropertyReplacer.replaceProperties("${env._DOES_NOT_EXIST_,my.sysprop:abc}"));
        Assert.assertEquals("${env._X_,_x_}", StringPropertyReplacer.replaceProperties("${env._X_,_x_}"));

        // env var set but not sys prop
        Map.Entry<String, String> envvar = System.getenv().entrySet().iterator().next(); // just pick a env var to use
        String envFirst = String.format("${env.%s,my.sysprop:abc}", envvar.getKey());
        String syspropFirst = String.format("${my.sysprop,env.%s:abc}", envvar.getKey());
        String envFirstNoDefault = String.format("${env.%s,my.sysprop}", envvar.getKey());
        String syspropFirstNoDefault = String.format("${my.sysprop,env.%s}", envvar.getKey());
        Assert.assertEquals(envvar.getValue(), StringPropertyReplacer.replaceProperties(envFirst));
        Assert.assertEquals(envvar.getValue(), StringPropertyReplacer.replaceProperties(syspropFirst));
        Assert.assertEquals(envvar.getValue(), StringPropertyReplacer.replaceProperties(envFirstNoDefault));
        Assert.assertEquals(envvar.getValue(), StringPropertyReplacer.replaceProperties(syspropFirstNoDefault));

        // sys prop set but not env var
        System.setProperty("my.sysprop", "xyz");
        envFirst = "${env._DOES_NOT_EXIST_,my.sysprop:abc}";
        syspropFirst = "${my.sysprop,env._DOES_NOT_EXIST_:abc}";
        envFirstNoDefault = "${env._DOES_NOT_EXIST_,my.sysprop}";
        syspropFirstNoDefault = "${my.sysprop,env._DOES_NOT_EXIST_}";
        Assert.assertEquals("xyz", StringPropertyReplacer.replaceProperties(envFirst));
        Assert.assertEquals("xyz", StringPropertyReplacer.replaceProperties(syspropFirst));
        Assert.assertEquals("xyz", StringPropertyReplacer.replaceProperties(envFirstNoDefault));
        Assert.assertEquals("xyz", StringPropertyReplacer.replaceProperties(syspropFirstNoDefault));

        // env var set AND sys prop set
        envFirst = String.format("${env.%s,my.sysprop:abc}", envvar.getKey());
        syspropFirst = String.format("${my.sysprop,env.%s:abc}", envvar.getKey());
        envFirstNoDefault = String.format("${env.%s,my.sysprop}", envvar.getKey());
        syspropFirstNoDefault = String.format("${my.sysprop,env.%s}", envvar.getKey());
        Assert.assertEquals(envvar.getValue(), StringPropertyReplacer.replaceProperties(envFirst));
        Assert.assertEquals("xyz", StringPropertyReplacer.replaceProperties(syspropFirst));
        Assert.assertEquals(envvar.getValue(), StringPropertyReplacer.replaceProperties(envFirstNoDefault));
        Assert.assertEquals("xyz", StringPropertyReplacer.replaceProperties(syspropFirstNoDefault));

        System.clearProperty("my.sysprop");
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

        String token = String.format("${env.%s}", envvar.getKey());
        String tokenAsFirst = String.format("${env.%s,does-not-exist}", envvar.getKey());
        String tokenAsSecond = String.format("${does-not-exist,env.%s}", envvar.getKey());
        String tokenDefault = String.format("${env.%s:abc}", envvar.getKey());
        String tokenDefaultAsFirst = String.format("${env.%s,does-not-exist:abc}", envvar.getKey());
        String tokenDefaultAsSecond = String.format("${does-not-exist,env.%s:abc}", envvar.getKey());

        Assert.assertEquals(envvar.getValue(), StringPropertyReplacer.replaceProperties(token));
        Assert.assertEquals(envvar.getValue(), StringPropertyReplacer.replaceProperties(tokenAsFirst));
        Assert.assertEquals(envvar.getValue(), StringPropertyReplacer.replaceProperties(tokenAsSecond));
        Assert.assertEquals(envvar.getValue(), StringPropertyReplacer.replaceProperties(tokenDefault));
        Assert.assertEquals(envvar.getValue(), StringPropertyReplacer.replaceProperties(tokenDefaultAsFirst));
        Assert.assertEquals(envvar.getValue(), StringPropertyReplacer.replaceProperties(tokenDefaultAsSecond));

        Assert.assertEquals("abc", StringPropertyReplacer.replaceProperties("${env.DoEs_NoT_ExIsT:abc}"));
    }
}
