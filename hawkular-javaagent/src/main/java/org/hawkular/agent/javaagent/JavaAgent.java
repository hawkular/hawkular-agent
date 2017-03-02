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

import java.io.File;
import java.util.concurrent.atomic.AtomicInteger;

public class JavaAgent {
    private static boolean started = false;

    // an agent can be started in its own VM as the main class
    public static void main(String[] args) {
        try {
            start(args);
        } catch (Exception e) {
            System.err.println("Hawkular Java Agent failed at startup");
            e.printStackTrace(System.err);
            return;
        }

        // so main doesn't exit
        synchronized (JavaAgent.class) {
            try {
                JavaAgent.class.wait();
            } catch (InterruptedException e) {
            }
        }
    }

    // an agent can be started in a VM as a javaagent allowing it to be embedded with some other main app
    public static void premain(String args) {
        if (args == null) {
            args = "config=config.yaml";
        }

        try {
            start(args.split(","));
        } catch (Exception e) {
            System.err.println("Hawkular Java Agent failed at startup");
            e.printStackTrace(System.err);
        }
    }

    private static synchronized void start(String[] args) throws Exception {

        if (started) {
            return; // agent is already started
        }

        // Process the arguments.
        //   config=<file path> : the config file to load (default is ./config.yaml)
        //   delay=<num seconds>: number of seconds before the agent will start up

        if (args == null || args.length == 0) {
            args = new String[] { "config=config.yaml" };
        }
        String configFilePathString = "config.yaml";
        final AtomicInteger delaySeconds = new AtomicInteger(0);
        for (String arg : args) {
            String[] nameValueArg = arg.split("=", 2);
            if (nameValueArg.length != 2) {
                continue;
            }
            if ("config".equals(nameValueArg[0])) {
                configFilePathString = nameValueArg[1];
            }
            if ("delay".equals(nameValueArg[0])) {
                delaySeconds.set(Integer.parseInt(nameValueArg[1]));
            }
        }

        // find the configuration file

        final File configFile = new File(configFilePathString);
        if (!configFile.canRead()) {
            throw new Exception("Missing configuration file: " + configFile.getAbsolutePath());
        }

        // Start the agent engine in a separate thread so premain does not block

        Thread agentThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    if (delaySeconds.get() > 0) {
                        Thread.sleep(delaySeconds.get() * 1000);
                    }
                    new JavaAgentEngine(configFile).startHawkularAgent();
                } catch (Exception e) {
                    System.err.println("Hawkular Java Agent failed at startup");
                    e.printStackTrace(System.err);
                }
            }
        }, "Hawkular Java Agent Start Thread");
        agentThread.setDaemon(true);
        agentThread.start();

        started = true;

        // Done. Agent is configured and started.
        return;
    }
}
