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
package org.hawkular.agent.javaagent.config;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.hawkular.agent.monitor.log.AgentLoggers;
import org.hawkular.agent.monitor.log.MsgLogger;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

/**
 * This manages the configuration file.
 */
public class ConfigManager {
    private static final MsgLogger log = AgentLoggers.getLogger(ConfigManager.class);

    private final File configFile;
    private final ReadWriteLock configurationLock = new ReentrantReadWriteLock(true);
    private Configuration configuration;

    /**
     * Creates a manager. This does not load the configuration, since it might not exist yet.
     * Call {@link #getConfiguration()} to get the current configuration or
     * call {@link #updateConfiguration(Configuration)} to write the configuration.
     *
     * @param file the configuration file
     */
    public ConfigManager(File file) {
        this.configFile = file;
    }

    /**
     * @return the file where the configuration does or will exist
     */
    public File getConfigFile() {
        return configFile;
    }

    /**
     * @return true if a configuration has been successfully loaded,
     *         false if a configuration has not yet been loaded or failed to be loaded
     */
    public boolean hasConfiguration() {
        Lock lock = this.configurationLock.readLock();
        lock.lock();
        try {
            return this.configuration != null;
        } finally {
            lock.unlock();
        }

    }

    /**
     * @return a copy of the configuration that has already been loaded; if no config has been loaded, returns null.
     * @see #hasConfiguration()
     */
    public Configuration getConfiguration() {
        Lock lock = this.configurationLock.readLock();
        lock.lock();
        try {
            return this.configuration == null ? null : new Configuration(this.configuration);
        } finally {
            lock.unlock();
        }
    }

    /**
     * @param if true the data from the config file will be reloaded, even if it was loaded before (this ensures
     *        the in-memory configuration is refreshed from the file).
     * @return a copy of the configuration; if not yet loaded, it will be loaded from the {@link #getConfigFile() file}
     * @throws Exception if the configuration file is invalid
     */
    public Configuration getConfiguration(boolean forceLoad) throws Exception {
        Lock lock = this.configurationLock.writeLock();
        lock.lock();
        try {
            if (this.configuration == null || forceLoad) {
                Configuration newConfig = load(this.configFile);
                newConfig.validate();
                this.configuration = newConfig;
            }
            return new Configuration(this.configuration);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Updates the configuration and writes it to the {@link #getConfigFile() file}, overwriting
     * the previous content of the file.
     *
     * @param config the new configuration
     * @param createBackup if true a .bak file is copied from the original as a backup
     * @throws Exception if the new configuration cannot be written to the file
     */
    public void updateConfiguration(Configuration config, boolean createBackup) throws Exception {
        if (config == null) {
            throw new IllegalArgumentException("config must not be null");
        }
        Lock lock = this.configurationLock.writeLock();
        lock.lock();
        try {
            save(this.configFile, config, createBackup);
            this.configuration = new Configuration(config);
        } finally {
            lock.unlock();
        }
    }

    private void save(File file, Configuration config, boolean createBackup) throws Exception {
        if (createBackup) {
            backup(file);
        }

        if (!file.exists()) {
            file.createNewFile();
        }

        if (!file.canWrite()) {
            throw new FileNotFoundException("Config file [" + file + "] cannot be created or is not writable");
        }

        ObjectMapper mapper = createObjectMapper();
        mapper.writeValue(file, config);
    }

    private Configuration load(File file) throws Exception {
        if (!file.canRead()) {
            throw new FileNotFoundException("Config file [" + file + "] does not exist or cannot be read");
        }

        ObjectMapper mapper = createObjectMapper();
        Configuration config = mapper.readValue(file, Configuration.class);
        return config;
    }

    private void backup(File file) {
        if (file.canRead()) {
            Path source = file.toPath();
            Path destination = new File(file.getAbsolutePath() + ".bak").toPath();
            try {
                Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                log.debugf(e, "Cannot backup config file [%s]", file);
            }
        }
    }

    private ObjectMapper createObjectMapper() {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        return mapper;
    }
}
