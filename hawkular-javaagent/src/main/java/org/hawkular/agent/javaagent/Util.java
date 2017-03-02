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
package org.hawkular.agent.javaagent;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;

public final class Util {

    /**
     * Performs a deep copy of the given array by calling a copy constructor to build clones of each array element.
     */
    @SuppressWarnings("unchecked")
    public static <T> T[] cloneArray(T[] original) {
        if (original == null) {
            return null;
        }

        try {
            Class<?> clazz = original.getClass().getComponentType();
            T[] copy = (T[]) Array.newInstance(clazz, original.length);
            if (copy.length != 0) {
                Constructor<T> copyConstructor = (Constructor<T>) clazz.getConstructor(clazz);
                int i = 0;
                for (T t : original) {
                    copy[i++] = copyConstructor.newInstance(t);
                }
            }
            return copy;
        } catch (Exception e) {
            throw new RuntimeException("Cannot copy array. Does its type have a copy-constructor? " + original, e);
        }
    }
}
