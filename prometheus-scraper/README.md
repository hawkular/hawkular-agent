# Prometheus Metrics Scraper

This provides an API and a command line utility to scrape metrics from a Prometheus protocol endpoint.
It supports both binary and text Prometheus data formats.

## Command Line Utility

The command line utility is run via:

````
java -jar prometheus-scraper*-cli.jar [--simple | --xml | --json] <url>
````

where `<url>` is the Prometheus protocol endpoint (typically something like `http://localhost:9090/metrics`).
Content negotiation will be used to establish the actual format to use.

## Java Scraper API

You can programmatically scrape a URL via the Java class
   `org.hawkular.agent.prometheus.PrometheusScraper`
and its `parse()` method.

### Maven Dependency

To obtain this Hawkular Prometheus scraper, use the following Maven dependency:

````xml
<dependency>
  <groupId>org.hawkular.agent</groupId>
  <artifactId>prometheus-scraper</artifactId>
  <version>#.#.#</version>
</dependency>
````
