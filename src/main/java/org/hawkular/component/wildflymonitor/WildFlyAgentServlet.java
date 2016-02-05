/*
 * Copyright 2015-2016 Red Hat, Inc. and/or its affiliates
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
package org.hawkular.component.wildflymonitor;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.spec.KeySpec;
import java.util.Base64;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import javax.annotation.security.PermitAll;
import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Serves the Hawkular WildFly Agent download that is stored in the server's download area.
 */
@PermitAll
@WebServlet(urlPatterns = { "/download", "/installer" }, loadOnStartup = 1)
public class WildFlyAgentServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    // the system property that defines how many concurrent downloads we will allow (0 == disable)
    private static final String SYSPROP_AGENT_DOWNLOADS_LIMIT = "hawkular.wildfly.agent.downloads.limit";

    // if the system property is not set or invalid, this is the default limit for number of concurrent downloads
    private static final int DEFAULT_AGENT_DOWNLOADS_LIMIT = 100;

    // name of the property file stored in the root of the installer jar file
    private static final String AGENT_INSTALLER_PROPERTIES_FILE_NAME = "hawkular-wildfly-agent-installer.properties";

    // optional key the user can provide - if specified, we'll encode the passwords in the installer
    static final String AGENT_INSTALLER_ENCRYPTION_KEY = "encryption-key";
    static final String AGENT_INSTALLER_ENCRYPTION_SALT = "encryption-salt";
    static final String AGENT_INSTALLER_ENCRYPTION_WEAK = "encryption-weak";

    // the options the user can set in the installer properties config file
    private static final String AGENT_INSTALLER_PROPERTY_ENABLED = "enabled";
    private static final String AGENT_INSTALLER_PROPERTY_TARGET_LOCATION = "target-location";
    private static final String AGENT_INSTALLER_PROPERTY_MODULE_DIST = "module-dist";
    private static final String AGENT_INSTALLER_PROPERTY_SERVER_URL = "server-url";
    private static final String AGENT_INSTALLER_PROPERTY_MANAGED_SERVER_NAME = "managed-server-name";
    private static final String AGENT_INSTALLER_PROPERTY_USERNAME = "username";
    private static final String AGENT_INSTALLER_PROPERTY_PASSWORD = "password";
    private static final String AGENT_INSTALLER_PROPERTY_SECURITY_KEY = "security-key";
    private static final String AGENT_INSTALLER_PROPERTY_SECURITY_SECRET = "security-secret";
    private static final String AGENT_INSTALLER_PROPERTY_KEYSTORE_PATH = "keystore-path";
    private static final String AGENT_INSTALLER_PROPERTY_KEYSTORE_PASSWORD = "keystore-password";
    private static final String AGENT_INSTALLER_PROPERTY_KEY_PASSWORD = "key-password";
    private static final String AGENT_INSTALLER_PROPERTY_KEY_ALIAS = "key-alias";

    // the error code that will be returned if the server has been configured to disable agent updates
    private static final int ERROR_CODE_AGENT_UPDATE_DISABLED = HttpServletResponse.SC_FORBIDDEN;

    // the error code that will be returned if the server has too many agents downloading the agent update binary
    private static final int ERROR_CODE_TOO_MANY_DOWNLOADS = HttpServletResponse.SC_SERVICE_UNAVAILABLE;

    private static AtomicInteger numActiveDownloads = null;

    private File moduleDownloadFile = null;
    private File installerDownloadFile = null;

    @Override
    public void init() throws ServletException {
        log("Starting the WildFly Agent download servlet");
        numActiveDownloads = new AtomicInteger(0);
        try {
            log("Agent Module File: " + getAgentModuleDownloadFile());
        } catch (Throwable t) {
            throw new ServletException("Missing Hawkular WildFly Agent module download file", t);
        }
        try {
            log("Agent Installer File: " + getAgentInstallerDownloadFile());
        } catch (Throwable t) {
            throw new ServletException("Missing Hawkular WildFly Agent installer download file", t);
        }
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        doIt(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        doIt(req, resp);
    }

    protected void doIt(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {

        disableBrowserCache(resp);

        String servletPath = req.getServletPath();
        if (servletPath != null) {
            if (servletPath.endsWith("download") || servletPath.endsWith("installer")) {
                try {
                    numActiveDownloads.incrementAndGet();
                    getDownload(req, resp);
                } finally {
                    numActiveDownloads.decrementAndGet();
                }
            } else {
                resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid servlet path [" + servletPath
                        + "] - please contact administrator");
            }
        } else {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid servlet path - please contact administrator");
        }
    }

    private void downloadAgentModule(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        try {
            File agentModuleZip = getAgentModuleDownloadFile();
            resp.setContentType("application/octet-stream");
            resp.setHeader("Content-Disposition", "attachment; filename=" + agentModuleZip.getName());
            resp.setContentLength((int) agentModuleZip.length());
            resp.setDateHeader("Last-Modified", agentModuleZip.lastModified());
            try (FileInputStream agentModuleZipStream = new FileInputStream(agentModuleZip)) {
                copy(agentModuleZipStream, resp.getOutputStream());
            }
        } catch (Throwable t) {
            String clientAddr = getClientAddress(req);
            log("Failed to stream file to remote client [" + clientAddr + "]", t);
            disableBrowserCache(resp);
            resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Failed to stream file");
        }
    }

    private void downloadAgentInstaller(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        try {
            File agentInstallerJar = getAgentInstallerDownloadFile();
            resp.setContentType("application/octet-stream");
            resp.setHeader("Content-Disposition", "attachment; filename=" + agentInstallerJar.getName());
            resp.setDateHeader("Last-Modified", agentInstallerJar.lastModified());

            HashMap<String, String> newProperties = new HashMap<>();

            String serverUrl = getValueFromRequestParam(req, AGENT_INSTALLER_PROPERTY_SERVER_URL, null);
            // we only call getDefaultHawkularServerUrl if we need to - no sense doing it if we were given a URL
            if (serverUrl == null) {
                serverUrl = getDefaultHawkularServerUrl(req);
            }
            // strip any ending slash in the url since we don't want it
            if (serverUrl.endsWith("/")) {
                serverUrl = serverUrl.substring(0, serverUrl.length() - 1);
            }
            new URL(serverUrl); // validates the URL - this throws an exception if the URL is invalid

            newProperties.put(AGENT_INSTALLER_PROPERTY_SERVER_URL, serverUrl);
            newProperties.put(AGENT_INSTALLER_PROPERTY_MANAGED_SERVER_NAME,
                    getValueFromRequestParam(req, AGENT_INSTALLER_PROPERTY_MANAGED_SERVER_NAME, null));
            newProperties.put(AGENT_INSTALLER_PROPERTY_MODULE_DIST,
                    getValueFromRequestParam(req, AGENT_INSTALLER_PROPERTY_MODULE_DIST,
                            serverUrl + "/hawkular/wildfly-agent/download"));
            newProperties.put(AGENT_INSTALLER_PROPERTY_TARGET_LOCATION,
                    getValueFromRequestParam(req, AGENT_INSTALLER_PROPERTY_TARGET_LOCATION, null));
            newProperties.put(AGENT_INSTALLER_PROPERTY_ENABLED,
                    getValueFromRequestParam(req, AGENT_INSTALLER_PROPERTY_ENABLED, null));
            newProperties.put(AGENT_INSTALLER_PROPERTY_USERNAME,
                    getValueFromRequestParam(req, AGENT_INSTALLER_PROPERTY_USERNAME, null));
            newProperties.put(AGENT_INSTALLER_PROPERTY_PASSWORD,
                    getValueFromRequestParam(req, AGENT_INSTALLER_PROPERTY_PASSWORD, null));
            newProperties.put(AGENT_INSTALLER_PROPERTY_SECURITY_KEY,
                    getValueFromRequestParam(req, AGENT_INSTALLER_PROPERTY_SECURITY_KEY, null));
            newProperties.put(AGENT_INSTALLER_PROPERTY_SECURITY_SECRET,
                    getValueFromRequestParam(req, AGENT_INSTALLER_PROPERTY_SECURITY_SECRET, null));
            newProperties.put(AGENT_INSTALLER_PROPERTY_KEYSTORE_PATH,
                    getValueFromRequestParam(req, AGENT_INSTALLER_PROPERTY_KEYSTORE_PATH, null));
            newProperties.put(AGENT_INSTALLER_PROPERTY_KEY_ALIAS,
                    getValueFromRequestParam(req, AGENT_INSTALLER_PROPERTY_KEY_ALIAS, null));
            newProperties.put(AGENT_INSTALLER_PROPERTY_KEYSTORE_PASSWORD,
                    getValueFromRequestParam(req, AGENT_INSTALLER_PROPERTY_KEYSTORE_PASSWORD, null));
            newProperties.put(AGENT_INSTALLER_PROPERTY_KEY_PASSWORD,
                    getValueFromRequestParam(req, AGENT_INSTALLER_PROPERTY_KEY_PASSWORD, null));

            // If an encryption key was provided, encode the passwords in the .properties file.
            // The installer must be given this encryption key by the user in order to install the agent.
            String encryptionKey = getValueFromRequestParam(req, AGENT_INSTALLER_ENCRYPTION_KEY, null);
            String encryptionSalt = getValueFromRequestParam(req, AGENT_INSTALLER_ENCRYPTION_SALT, null);
            String encryptionWeak = getValueFromRequestParam(req, AGENT_INSTALLER_ENCRYPTION_WEAK, null);
            boolean useWeakEncryption = "true".equalsIgnoreCase(encryptionWeak);

            if (encryptionKey != null) {
                if (encryptionSalt == null) {
                    encryptionSalt = encryptionKey;
                }

                encode(newProperties, AGENT_INSTALLER_PROPERTY_KEYSTORE_PASSWORD,
                        encryptionKey, encryptionSalt, useWeakEncryption);
                encode(newProperties, AGENT_INSTALLER_PROPERTY_KEY_PASSWORD,
                        encryptionKey, encryptionSalt, useWeakEncryption);
                encode(newProperties, AGENT_INSTALLER_PROPERTY_PASSWORD,
                        encryptionKey, encryptionSalt, useWeakEncryption);
                encode(newProperties, AGENT_INSTALLER_PROPERTY_SECURITY_SECRET,
                        encryptionKey, encryptionSalt, useWeakEncryption);
            }

            int contentLength = 0;

            try (ZipFile agentInstallerZip = new ZipFile(agentInstallerJar);
                    ZipOutputStream zos = new ZipOutputStream(resp.getOutputStream(), StandardCharsets.UTF_8)) {

                for (Enumeration<? extends ZipEntry> e = agentInstallerZip.entries(); e.hasMoreElements();) {
                    ZipEntry entryIn = e.nextElement();
                    if (!entryIn.getName().equalsIgnoreCase(AGENT_INSTALLER_PROPERTIES_FILE_NAME)) {
                        // skip everything else
                        zos.putNextEntry(entryIn);
                        try (InputStream is = agentInstallerZip.getInputStream(entryIn)) {
                            byte[] buf = new byte[4096];
                            int len;
                            while ((len = (is.read(buf))) > 0) {
                                zos.write(buf, 0, len);
                                contentLength += len;
                            }
                        }
                    } else {
                        zos.putNextEntry(new ZipEntry(AGENT_INSTALLER_PROPERTIES_FILE_NAME));
                        try (BufferedReader br = new BufferedReader(
                                new InputStreamReader(agentInstallerZip.getInputStream(entryIn),
                                        StandardCharsets.UTF_8))) {
                            String line;
                            while ((line = br.readLine()) != null) {
                                // for each new prop, see if current line sets it; if so, set the prop to our new value
                                for (Map.Entry<String, String> entry : newProperties.entrySet()) {
                                    String newLine = getNewPropertyLine(line, entry.getKey(), entry.getValue());
                                    if (!line.equals(newLine)) {
                                        line = newLine;
                                        break; // found the property, no need to keep going; go write the new line now
                                    }
                                }
                                byte[] buf = (line + '\n').getBytes(StandardCharsets.UTF_8);
                                zos.write(buf);
                                contentLength += buf.length;
                            }
                        }
                    }
                    zos.closeEntry();
                }
            }

            // I don't think this will work, because if the content is large enough, we will have flushed
            // the resp outputStream, at which time you can no longer send headers such as content length
            // resp.setContentLength(contentLength);
            log("Sending Hawkular WildFly Agent installer with content length of: " + contentLength);

        } catch (Throwable t) {
            String clientAddr = getClientAddress(req);
            log("Failed to stream file to remote client [" + clientAddr + "]", t);
            disableBrowserCache(resp);
            resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Failed to stream file: " + t);
        }
    }

    /**
     * Encodes the value of the given property found in the given properties map.
     *
     * @param properties the map where the property is found; this will be updated with the newly encoded value
     * @param propertyName the name of the property that needs to be encoded
     */
    private void encode(HashMap<String, String> properties, String propertyName, String encryptionKey, String
            encryptionSalt, boolean useWeakEncryption) throws Exception {
        String clearText = properties.get(propertyName);
        if (null == clearText) {
            return;
        }

        String finalPropertyValue;
        if (useWeakEncryption) {
            finalPropertyValue = doEncodeWeak(clearText, encryptionKey, encryptionSalt);
        } else {
            finalPropertyValue = doEncode(clearText, encryptionKey, encryptionSalt);
        }

        properties.put(propertyName, finalPropertyValue);
    }

    private String doEncodeWeak(String clearText, String encryptionKey, String encryptionSalt) throws Exception {
        byte[] salt = encryptionSalt.getBytes("UTF-8");

        Cipher cipher = Cipher.getInstance("DES");
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        KeySpec spec = new PBEKeySpec(encryptionKey.toCharArray(), salt, 80_000, 64);
        SecretKeySpec keySpec = new SecretKeySpec(factory.generateSecret(spec).getEncoded(), "DES");
        cipher.init(Cipher.ENCRYPT_MODE, keySpec);
        byte[] encryptedData = cipher.doFinal(clearText.getBytes("UTF-8"));
        return new String(Base64.getEncoder().encode(encryptedData), "UTF-8");
    }

    private String doEncode(String clearText, String encryptionKey, String encryptionSalt) throws Exception {
        byte[] salt = encryptionSalt.getBytes("UTF-8");
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        KeySpec spec = new PBEKeySpec(encryptionKey.toCharArray(), salt, 80_000, 256);
        SecretKeySpec keySpec = new SecretKeySpec(factory.generateSecret(spec).getEncoded(), "AES");
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5PADDING");

        try {
            cipher.init(Cipher.ENCRYPT_MODE, keySpec);
        } catch (InvalidKeyException invalidKeyException) {
            // user needs the unlimited jurisdiction files...
            log("WARNING: Server does not support strong encryption. Using weak encryption.");
            return doEncodeWeak(clearText, encryptionKey, encryptionSalt);
        }

        byte[] encryptedData = cipher.doFinal(clearText.getBytes("UTF-8"));
        String ivAsString = new String(Base64.getEncoder().encode(cipher.getIV()), "UTF-8");
        String encryptedAsString = new String(Base64.getEncoder().encode(encryptedData), "UTF-8");
        return ivAsString + "$" + encryptedAsString;
    }

    /**
     * Given a single line of a properties file, this will look to see if it contains a property we are looking for.
     * If it does, we'll set it to the given new value.
     * Note that this will look to see if the property is explicitly set
     * or if it is commented out - if it is either, we'll modify the line regardless.
     *
     * If newPropValue is null, and the property is found in the line,
     * the line returned will be a commented out property
     *
     * For example, if you pass in a lineToModify of "#target-location=/opt/wildfly",
     * propNameToFind of "target-location", and newPropValue of "/usr/bin/wf", this method will return
     * "wildfly-home=/usr/bin/wf". Notice that the given lineToModify was a commented out property - this method will
     * detect that and still modify the line. This allows us to "uncomment" a property and set it to the new value.
     *
     * @param lineToModify the line to check if its what we want - we'll modify it and returned that modified string
     * @param propNameToFind the property to search in the line
     * @param newPropValue the new value to set the property to.
     *
     * @return if the line has the property we are looking for, a new line is returned with the property set to the
     *         given new value; otherwise, lineToModify is returned as-is
     */
    private String getNewPropertyLine(String lineToModify, String propNameToFind, String newPropValue) {
        // Look for the property (even if its commented out) and ignore spaces before and after the property name.
        // We also don't care what the value was (doesn't matter what is after the = character).
        Matcher m = Pattern.compile("#? *" + propNameToFind + " *=.*").matcher(lineToModify);
        if (m.matches()) {
            if (newPropValue != null) {
                lineToModify = m.replaceAll(propNameToFind + "=" + newPropValue);
            } else {
                lineToModify = m.replaceAll("#" + propNameToFind + "=");
            }
        }
        return lineToModify;
    }

    private String getDefaultHawkularServerUrl(HttpServletRequest request) {
        try {
            URL url = new URL(request.getRequestURL().toString());

            // the request URL has path info that we need to strip - we want only protocol, host, and port
            String protocol = url.getProtocol();
            String hostname = url.getHost();
            int port = url.getPort();
            if (port > 0) {
                return String.format("%s://%s:%d", protocol, hostname, port);
            } else {
                return String.format("%s://%s", protocol, hostname);
            }
        } catch (Exception e) {
            log("Cannot determine request URL; will use http://localhost:8080 as default: " + e);
            return "http://localhost:8080";
        }
    }

    private String getValueFromRequestParam(HttpServletRequest req, String key, String
            defaultValue) throws IOException {
        String value = req.getParameter(key);
        if (value == null || value.isEmpty()) {
            return defaultValue;
        }
        return value;
    }

    private void getDownload(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        int limit = getDownloadLimit();
        if (limit <= 0) {
            sendErrorDownloadsDisabled(resp);
            return;
        } else if (limit < numActiveDownloads.get()) {
            sendErrorTooManyDownloads(resp);
            return;
        }
        if (req.getServletPath().endsWith("installer")) {
            downloadAgentInstaller(req, resp);
        } else {
            downloadAgentModule(req, resp);
        }
    }

    private int getDownloadLimit() {
        String limitStr = System.getProperty(SYSPROP_AGENT_DOWNLOADS_LIMIT,
                String.valueOf(DEFAULT_AGENT_DOWNLOADS_LIMIT));
        int limit;
        try {
            limit = Integer.parseInt(limitStr);
        } catch (Exception e) {
            limit = DEFAULT_AGENT_DOWNLOADS_LIMIT;
            log("Agent downloads limit system property [" + SYSPROP_AGENT_DOWNLOADS_LIMIT
                    + "] is invalid [" + limitStr + "] - limit will be [" + limit + "].");
        }

        return limit;
    }

    private void disableBrowserCache(HttpServletResponse resp) {
        resp.setHeader("Cache-Control", "no-cache, no-store");
        resp.setHeader("Expires", "-1");
        resp.setHeader("Pragma", "no-cache");
    }

    private void sendErrorDownloadsDisabled(HttpServletResponse resp) throws IOException {
        disableBrowserCache(resp);
        resp.sendError(ERROR_CODE_AGENT_UPDATE_DISABLED, "Downloads have been disabled");
    }

    private void sendErrorTooManyDownloads(HttpServletResponse resp) throws IOException {
        disableBrowserCache(resp);
        resp.setHeader("Retry-After", "30");
        resp.sendError(ERROR_CODE_TOO_MANY_DOWNLOADS, "Maximum limit exceeded - try again later");
    }

    private String getClientAddress(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("HTTP_X_FORWARDED_FOR");
            if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
                ip = String.format("%s (%s)", request.getRemoteHost(), request.getRemoteAddr());
            }
        }
        return ip;
    }

    private void copy(InputStream input, OutputStream output) throws IOException {
        byte[] buffer = new byte[32768];
        input = new BufferedInputStream(input, buffer.length);
        for (int bytesRead = input.read(buffer); bytesRead != -1; bytesRead = input.read(buffer)) {
            output.write(buffer, 0, bytesRead);
        }
        output.flush();
    }

    private File getAgentModuleDownloadFile() throws Exception {
        if (moduleDownloadFile != null) {
            if (moduleDownloadFile.exists()) {
                return moduleDownloadFile;
            } else {
                moduleDownloadFile = null; // the file was removed recently - let's look for a new one
            }
        }

        File configDir = new File(System.getProperty("jboss.server.config.dir"));
        for (File file : configDir.listFiles()) {
            if (file.getName().startsWith("hawkular-wildfly-agent-wf-extension") && file.getName().endsWith(".zip")) {
                moduleDownloadFile = file;
                return moduleDownloadFile;
            }
        }
        throw new FileNotFoundException("Cannot find agent module download in: " + configDir);
    }

    private File getAgentInstallerDownloadFile() throws Exception {
        if (installerDownloadFile != null) {
            if (installerDownloadFile.exists()) {
                return installerDownloadFile;
            } else {
                installerDownloadFile = null; // the file was removed recently - let's look for a new one
            }
        }

        File configDir = new File(System.getProperty("jboss.server.config.dir"));
        for (File file : configDir.listFiles()) {
            if (file.getName().startsWith("hawkular-wildfly-agent-installer") && file.getName().endsWith(".jar")) {
                installerDownloadFile = file;
                return installerDownloadFile;
            }
        }
        throw new FileNotFoundException("Cannot find agent installer download in: " + configDir);
    }
}
