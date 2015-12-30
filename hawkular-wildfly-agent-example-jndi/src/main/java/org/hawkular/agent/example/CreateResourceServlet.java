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
package org.hawkular.agent.example;

import java.io.IOException;

import javax.annotation.Resource;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.hawkular.agent.monitor.api.HawkularMonitorContext;

public class CreateResourceServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final String AGENT_JNDI = "java:global/hawkular/agent/api";

    @Resource(name = AGENT_JNDI)
    HawkularMonitorContext hawkularAgent;

    public void service(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        String requestParam = request.getParameter("newResourceID");
        if (requestParam != null) {
            createNewResource(request, response, requestParam);
        } else {
            throw new ServletException("Don't know what to do!");
        }
    }

    protected void createNewResource(HttpServletRequest request, HttpServletResponse response, String resourceId) {
        try {
            // use hawkularAgent to create resource ID
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
