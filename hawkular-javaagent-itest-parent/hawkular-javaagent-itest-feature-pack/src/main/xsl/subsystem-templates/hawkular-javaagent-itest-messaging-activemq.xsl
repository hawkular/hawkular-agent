<?xml version="1.0" encoding="UTF-8"?>
<!--

    Copyright 2015-2017 Red Hat, Inc. and/or its affiliates
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

  <xsl:template match="//*[local-name()='config']/*[local-name()='subsystem']/*[local-name()='server' and @name='default']/*[local-name()='jms-topic' or local-name()='jms-queue'][last()]">
    <xsl:copy>
      <xsl:apply-templates select="node()|comment()|@*" />
    </xsl:copy>
    <jms-queue name="hawkular/metrics/gauges/new" entries="java:/queue/hawkular/metrics/gauges/new java:jboss/exported/queue/hawkular/metrics/gauges/new"/>
    <jms-queue name="hawkular/metrics/counters/new" entries="java:/queue/hawkular/metrics/counters/new java:jboss/exported/queue/hawkular/metrics/counters/new"/>
    <jms-queue name="hawkular/metrics/availability/new" entries="java:/queue/hawkular/metrics/availability/new java:jboss/exported/queue/hawkular/metrics/availability/new"/>

    <jms-topic name="HawkularInventoryChanges" entries="java:/topic/HawkularInventoryChanges"/>
    <jms-topic name="HawkularCommandEvent" entries="java:/topic/HawkularCommandEvent"/>
    <jms-topic name="HawkularAvailData" entries="java:/topic/HawkularAvailData java:jboss/exported/topic/HawkularAvailData"/>
    <jms-topic name="HawkularMetricData" entries="java:/topic/HawkularMetricData java:jboss/exported/topic/HawkularMetricData"/>
    <jms-topic name="HawkularQueue" entries="java:/queue/HawkularQueue"/>
    <jms-topic name="HawkularTopic" entries="java:/topic/HawkularTopic"/>
  </xsl:template>

  <!-- copy everything else as-is -->
  <xsl:template match="node()|comment()|@*">
    <xsl:copy>
      <xsl:apply-templates select="node()|comment()|@*" />
    </xsl:copy>
  </xsl:template>

</xsl:stylesheet>
