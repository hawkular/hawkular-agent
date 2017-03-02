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

import org.hawkular.agent.javaagent.Util;
import org.junit.Assert;
import org.junit.Test;

public class UtilTest {

    public static class ClassWithCopyConstructor {
        private int number = 0;
        private String string = "";

        public ClassWithCopyConstructor(int n, String s) {
            this.number = n;
            this.string = s;
        }

        public ClassWithCopyConstructor(ClassWithCopyConstructor original) {
            this.number = original.number;
            this.string = original.string;
        }

        @Override
        public String toString() {
            return "" + number + ":" + string;
        }
    }

    @Test
    public void testCloneArray() throws Exception {
        Assert.assertNull(Util.cloneArray(null));
        Assert.assertEquals(0, Util.cloneArray(new ClassWithCopyConstructor[0]).length);

        ClassWithCopyConstructor[] arr = new ClassWithCopyConstructor[3];
        arr[0] = new ClassWithCopyConstructor(1, "one");
        arr[1] = new ClassWithCopyConstructor(2, "two");
        arr[2] = new ClassWithCopyConstructor(3, "three");
        ClassWithCopyConstructor[] dup = Util.cloneArray(arr);
        Assert.assertEquals(arr.length, dup.length);
        Assert.assertNotSame(arr, dup);
        for (int i = 0; i < arr.length; i++) {
            Assert.assertEquals(arr[i].toString(), dup[i].toString());
            Assert.assertNotSame(arr[i], dup[i]);
        }
    }
}