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

import java.io.IOException;
import java.io.PrintWriter;

import javax.inject.Inject;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@WebServlet(value = "/HelloWorld", loadOnStartup = 1)
public class HelloWorldServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;

    @Inject
    private SimpleMXBeanImpl mbean;

    @Override
    public void init(ServletConfig config) throws ServletException {
        super.init(config);
        mbean.setTestInteger(0);
        log("Test MBean has been registered: " + SimpleMXBeanImpl.OBJECT_NAME);
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        Integer num = Integer.valueOf(mbean.getTestInteger().intValue() + 1);
        mbean.setTestInteger(num);
        resp.setContentType("text/html");
        PrintWriter writer = resp.getWriter();
        writer.println("<html><head><title>helloworld</title></head><body><h1>");
        writer.println(mbean.getTestString());
        writer.println(" #");
        writer.println(num);
        writer.println("</h1></body></html>");
        writer.close();
    }
}
