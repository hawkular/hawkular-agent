# Prometheus Metrics Scraper

This provides an API and a command line utility to scrape metrics from a Prometheus protocol endpoint.

## Command Line Utility

The command line utility is run via:

````
java -jar prometheus-scraper*-cli.jar [--simple | --xml | --json] <url>
````

where `<url>` is the Prometheus protocol endpoint (typically something like `http://localhost:9090/metrics`).

## Java Scraper API

You can programmatically scrape a URL via the Java class
   `org.hawkular.agent.prometheus.PrometheusScraperUrl`
and its `scrape()` method.

It is up to the caller to process the returned Prometheus metric family data. To make it easier,
you can optionally walk the Prometheus metric family data returned by the `scrape()` method by implementing
a subclass of `org.hawkular.agent.prometheus.AbstractPrometheusMetricsWalker`.

### Maven Dependency

To obtain this Hawkular Prometheus scraper, use the following Maven dependency:

````xml
<dependency>
  <groupId>org.hawkular.agent</groupId>
  <artifactId>prometheus-scraper</artifactId>
  <version>#.#.#</version>
</dependency>
````
