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

import java.lang.management.ManagementFactory;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.enterprise.context.ApplicationScoped;
import javax.management.MBeanServer;
import javax.management.ObjectName;

@ApplicationScoped
public class SimpleMXBeanImpl implements SimpleMXBean {

    public static final String OBJECT_NAME = "org.hawkular.agent.itest:type=simple";

    @PostConstruct
    protected void register() {
        try {
            MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
            ObjectName on = new ObjectName(OBJECT_NAME);
            mbs.registerMBean(this, on);
        } catch (Exception e) {
            throw new RuntimeException("Failed to register the SimplMXBean");
        }
    }

    @PreDestroy
    protected void unregister() {
        try {
            MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
            ObjectName on = new ObjectName(OBJECT_NAME);
            mbs.unregisterMBean(on);
        } catch (Exception e) {
            throw new RuntimeException("Failed to unregister the SimplMXBean");
        }
    }

    private Integer i = new Integer(54321);

    // JMX Attributes

    public String getTestString()            { return "Hello World"; }

    public int     getTestIntegerPrimitive() { return 12345; }
    public Integer getTestInteger()          { return i; }
    public void    setTestInteger(Integer i) { this.i = i; }

    public boolean getTestBooleanPrimitive() { return false; }
    public Boolean getTestBoolean()          { return true; }

    public long getTestLongPrimitive()       { return 123456789L; }
    public Long getTestLong()                { return 987654321L; }

    public double getTestDoublePrimitive()   { return (double) 1.23456789; }
    public Double getTestDouble()            { return (double) 9.87654321; }

    public float getTestFloatPrimitive()     { return (float) 3.14; }
    public Float getTestFloat()              { return (float) 6.28; }

    public short getTestShortPrimitive()     { return (short) 12; }
    public Short getTestShort()              { return (short) 21; }

    public char      getTestCharPrimitive()  { return (char) 'a'; }
    public Character getTestChar()           { return (char) 'z'; }

    public byte getTestBytePrimitive()       { return (byte) 1; }
    public Byte getTestByte()                { return (byte) 2; }


    // JMX Operations

    @Override
    public void testOperationNoParams() {
        System.out.println("JMX operation testOperationNoParams has been invoked.");
    }

    @Override
    public String testOperationPrimitive(String s, int i, boolean b, long l, double d, float f, short h, char c,
            byte y) {
        System.out.println("JMX operation testOperationPrimitive has been invoked.");
        return String.format("string=%s, int=%s, boolean=%s, long=%s, double=%s, float=%s, short=%s, char=%s, byte=%s",
                s,
                String.valueOf(i),
                String.valueOf(b),
                String.valueOf(l),
                String.valueOf(d),
                String.valueOf(f),
                String.valueOf(h),
                String.valueOf(c),
                String.valueOf(y));
    }

    @Override
    public String testOperation(String s, Integer i, Boolean b, Long l, Double d, Float f, Short h, Character c,
            Byte y) {
        System.out.println("JMX operation testOperation has been invoked.");
        return String.format("String=%s, Int=%s, Boolean=%s, Long=%s, Double=%s, Float=%s, Short=%s, Char=%s, Byte=%s",
                s,
                (i == null) ? "null" : i.toString(),
                (b == null) ? "null" : b.toString(),
                (l == null) ? "null" : l.toString(),
                (d == null) ? "null" : d.toString(),
                (f == null) ? "null" : f.toString(),
                (h == null) ? "null" : h.toString(),
                (c == null) ? "null" : c.toString(),
                (y == null) ? "null" : y.toString());
    }
}
