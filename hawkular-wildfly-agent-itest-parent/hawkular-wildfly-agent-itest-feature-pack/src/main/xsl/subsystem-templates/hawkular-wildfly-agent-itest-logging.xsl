<?xml version="1.0" encoding="UTF-8"?>
<!--

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

-->

<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xalan="http://xml.apache.org/xalan" version="2.0" exclude-result-prefixes="xalan">

  <xsl:output method="xml" version="1.0" encoding="UTF-8" indent="yes" xalan:indent-amount="4" standalone="no" />
  <xsl:strip-space elements="*" />
  <xsl:variable name="dollar"><xsl:text disable-output-escaping="yes" >$</xsl:text></xsl:variable>

  <xsl:template match="//*[local-name()='config']/*[local-name()='supplement' and @name='default']/*[local-name()='replacement' and @placeholder='LOGGERS']">
    <xsl:copy>
      <xsl:apply-templates select="@*|node()" />
      <logger category="org.hawkular">
        <level name="${{hawkular.log:INFO}}" />
      </logger>
      <logger category="org.hawkular.accounts">
        <level name="${{hawkular.log.accounts:INFO}}" />
      </logger>
      <logger category="org.hawkular.agent">
        <level name="${{hawkular.log.agent:INFO}}" />
      </logger>
      <logger category="org.hawkular.cmdgw">
        <level name="${{hawkular.log.cmdgw:INFO}}" />
      </logger>
      <logger category="org.hawkular.inventory">
        <level name="${{hawkular.log.inventory:INFO}}" />
      </logger>
      <logger category="org.hawkular.inventory.rest.requests">
        <level name="${{hawkular.log.inventory.rest.requests:INFO}}" />
      </logger>
      <logger category="org.hawkular.inventory.ws">
        <level name="${{hawkular.log.inventory.ws:INFO}}" />
      </logger>
      <logger category="org.hawkular.metrics">
        <level name="${{hawkular.log.metrics:INFO}}" />
      </logger>
      <logger category="com.datastax.driver">
        <level name="${{hawkular.log.datastax.driver:INFO}}" />
      </logger>
      <logger category="liquibase">
        <level name="${{hawkular.log.liquibase:WARN}}" />
      </logger>
    </xsl:copy>
  </xsl:template>

  <!-- copy everything else as-is -->
  <xsl:template match="node()|@*">
    <xsl:copy>
      <xsl:apply-templates select="node()|@*" />
    </xsl:copy>
  </xsl:template>

</xsl:stylesheet>
