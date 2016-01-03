<%--

    Copyright 2015-2016 Red Hat, Inc. and/or its affiliates
    and other contributors as indicated by the @author tags.

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.

--%>
<html>
    <head>
        <title>Example App Using Hawkular WildFly Agent JNDI</title>
    </head>
    <body>
        <form action="MyAppServlet">
            Enter an ID for a new resource:<br/>
            <textarea name="newResourceID"cols="20" rows="1"></textarea><br/>
            <input type="Submit" value="Create Resource" />
            <input type="Reset" value="Clear" />
        </form>
        <form action="MyAppServlet">
            Enter the metric identification key:<br/>
            <textarea name="metricKey"cols="20" rows="1"></textarea><br/>
            Enter the metric value:<br/>
            <textarea name="metricValue"cols="20" rows="1"></textarea><br/>
            <input type="Submit" value="Store Metric" />
            <input type="Reset" value="Clear" />
        </form>
        <form action="MyAppServlet">
            Enter the availability identification key:<br/>
            <textarea name="availKey"cols="20" rows="1"></textarea><br/>
            Enter the availability value (UP, DOWN, or UNKNOWN):<br/>
            <textarea name="availValue"cols="20" rows="1"></textarea><br/>
            <input type="Submit" value="Store Avail" />
            <input type="Reset" value="Clear" />
        </form>

        <hr/>

    </body>
</html>
