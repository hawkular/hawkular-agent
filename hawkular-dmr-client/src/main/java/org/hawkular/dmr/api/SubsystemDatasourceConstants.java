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
package org.hawkular.dmr.api;

/**
 * Constants specific to datasources subsystem.
 *
 * @author <a href="https://github.com/ppalaga">Peter Palaga</a>
 */
public interface SubsystemDatasourceConstants {
    public interface DatasourceNodeCommonConstants {
        String DRIVER_NAME = "driver-name";
        String JNDI_NAME = "jndi-name";
        String NAME = "name";
        String PASSWORD = "password";
        String USER_NAME = "user-name";
        String STATISTICS_ENABLED = "statistics-enabled";
    }

    public interface DatasourceNodeConstants extends DatasourceNodeCommonConstants {
        String CONNECTION_URL = "connection-url";
        String DRIVER_CLASS = "driver-class";
    }

    public interface XaDatasourceNodeConstants extends DatasourceNodeCommonConstants {
        String SECURITY_DOMAIN = "security-domain";
        String XA_DATASOURCE_CLASS = "xa-datasource-class";
    }

    public interface JdbcDriverNodeConstants {
        String DRIVER_NAME = "driver-name";
        String DRIVER_CLASS_NAME = "driver-class-name";
        String DRIVER_MODULE_NAME = "driver-module-name";
        String DRIVER_MAJOR_VERSION = "driver-major-version";
        String DRIVER_MINOR_VERSION = "driver-minor-version";
        String DRIVER_XA_DATASOURCE_CLASS_NAME = "driver-xa-datasource-class-name";
        String XA_DATASOURCE_CLASS = "xa-datasource-class";
        String JDBC_COMPLIANT = "jdbc-compliant";
    }

    String CONNECTION_PROPERTIES = "connection-properties";
    String DATASOURCE = "data-source";
    String DATASOURCES = "datasources";

    String JDBC_DRIVER = "jdbc-driver";
    String XA_DATASOURCE = "xa-data-source";

    String XA_DATASOURCE_PROPERTIES = "xa-datasource-properties";

}
