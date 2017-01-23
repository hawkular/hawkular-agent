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
package org.hawkular.agent.monitor.util;

import java.lang.Thread.UncaughtExceptionHandler;
import java.util.concurrent.ThreadFactory;

import org.jboss.threads.JBossThreadFactory;

/**
 * @author John Mazzitelli
 */
public class ThreadFactoryGenerator {
    public static final ThreadFactory generateFactory(boolean daemon, String threadGroupName) {
        String namePattern = "%G-%t";
        UncaughtExceptionHandler uncaughtExceptionHandler = null;
        Integer initialPriority = null;
        Long stackSize = null;
        return new JBossThreadFactory(
                new ThreadGroup(threadGroupName),
                daemon,
                initialPriority,
                namePattern,
                uncaughtExceptionHandler,
                stackSize,
                null); // this last param is ignored according to docs.
        // see: https://github.com/jbossas/jboss-threads/blob/2.2/src/main/java/org/jboss/threads/JBossThreadFactory.java#L90
    }
}
