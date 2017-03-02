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

package org.hawkular.agent.helloworld;

import javax.management.MXBean;

@MXBean
public interface SimpleMXBean {

    // JMX Attributes

    String getTestString();

    int     getTestIntegerPrimitive();
    Integer getTestInteger();
    void    setTestInteger(Integer i);

    boolean getTestBooleanPrimitive();
    Boolean getTestBoolean();

    long getTestLongPrimitive();
    Long getTestLong();

    double getTestDoublePrimitive();
    Double getTestDouble();

    float getTestFloatPrimitive();
    Float getTestFloat();

    short getTestShortPrimitive();
    Short getTestShort();

    char      getTestCharPrimitive();
    Character getTestChar();

    byte getTestBytePrimitive();
    Byte getTestByte();

    // JMX Operations

    void testOperationNoParams();
    String testOperationPrimitive(String s, int i, boolean b, long l, double d, float f, short h, char c, byte y);
    String testOperation(String s, Integer i, Boolean b, Long l, Double d, Float f, Short h, Character c, Byte y);

}
