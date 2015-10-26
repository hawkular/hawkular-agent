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
package org.hawkular.agent.monitor.util;

import static java.security.AccessController.doPrivileged;

import java.lang.Thread.UncaughtExceptionHandler;
import java.security.AccessControlContext;
import java.util.concurrent.ThreadFactory;

import org.jboss.threads.JBossThreadFactory;
import org.wildfly.security.manager.action.GetAccessControlContextAction;

/**
 * @author John Mazzitelli
 */
public class ThreadFactoryGenerator {
    public static final ThreadFactory generateFactory(boolean daemon, String threadGroupName) {
        String namePattern = "%G-%t";
        UncaughtExceptionHandler uncaughtExceptionHandler = null;
        Integer initialPriority = null;
        Long stackSize = null;
        AccessControlContext acc = doPrivileged(GetAccessControlContextAction.getInstance());
        return new JBossThreadFactory(
                new ThreadGroup(threadGroupName),
                daemon,
                initialPriority,
                namePattern,
                uncaughtExceptionHandler,
                stackSize,
                acc);
    }
}
