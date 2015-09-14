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
package org.hawkular.dmrclient.modules;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.hawkular.dmrclient.modules.AddModuleRequest.ModuleResource;
import org.junit.Assert;
import org.junit.Test;

/**
 * @author <a href="https://github.com/ppalaga">Peter Palaga</a>
 */
public class ModulesTest {
    private static AddModuleRequest createComplicated() throws IOException {

        Map<String, String> props = new LinkedHashMap<>();
        props.put("k1", "v1");
        props.put("k2", "v2");

        ByteArrayInputStream in = new ByteArrayInputStream("deadbeef".getBytes("utf-8"));
        ModuleResource resource = new AddModuleRequest.ModuleResource(in, "complicated.jar");

        return new AddModuleRequest("complicated", "custom-slot", "complicated.Main",
                new HashSet<ModuleResource>(Arrays.asList(resource)),
                new HashSet<String>(Arrays.asList("javax.api", "javax.transaction.api")), props);
    }

    /**
     * @return
     */
    private static AddModuleRequest createMinimal() {
        return new AddModuleRequest("minimal", null, null, null, null, null);
    }

    private static File createTempDir(String key) throws IOException {
        return Files.createTempDirectory(ModulesTest.class.getName() + "." + key + ".").toFile();
    }

    private static AddModuleRequest createUsual() throws IOException {
        ByteArrayInputStream in = new ByteArrayInputStream("deadbeef".getBytes("utf-8"));
        ModuleResource resource = new AddModuleRequest.ModuleResource(in, "usual.jar");

        return new AddModuleRequest("usual", null, null,
                new HashSet<ModuleResource>(Arrays.asList(resource)),
                new HashSet<String>(Arrays.asList("javax.api", "javax.transaction.api")), null);
    }

    private void assertAdd(AddModuleRequest request) throws Exception {
        File jbossHome = createTempDir("testAdd.jbossHome");
        File modulesDir = new File(jbossHome, "modules");

        Modules modules = new Modules(modulesDir);
        modulesDir.mkdirs();
        Assert.assertTrue(modulesDir.exists());
        modules.add(request);

        InputStream expectedIn = this.getClass().getResourceAsStream(request.getModuleName() + ".xml");

        String slot = request.getSlot() == null ? "main" : request.getSlot();
        File moduleDir = new File(modulesDir, request.getModuleName() + "/" + slot);
        File foundFile = new File(moduleDir, "module.xml");

        String expected = IOUtils.toString(expectedIn, "utf-8");
        String found = FileUtils.readFileToString(foundFile, "utf-8");
        Assert.assertEquals(expected, found);

        for (ModuleResource resourceName : request.getResources()) {
            File f = new File(moduleDir, resourceName.getFileName());
            Assert.assertTrue(f.getAbsolutePath() + " does not exist", f.exists());
        }
        Assert.assertEquals(request.getResources().size() + 1, moduleDir.listFiles().length);
    }

    @Test
    public void testAddBadModulesDir() throws Exception {
        File jbossHome = createTempDir("testValidate.jbossHome");
        File modulesDir = new File(jbossHome, "modules");

        Modules modules = new Modules(modulesDir);
        AddModuleRequest minimal = createMinimal();

        /* modulesDir does not exist yet */
        Assert.assertFalse(modulesDir.exists());
        try {
            modules.add(minimal);
            Assert.fail("Exception expected");
        } catch (IllegalArgumentException expected) {
        }
    }

    @Test
    public void testAddComplicated() throws Exception {
        assertAdd(createComplicated());
    }

    @Test
    public void testAddMinimal() throws Exception {
        assertAdd(createMinimal());
    }

    @Test
    public void testAddUsual() throws Exception {
        assertAdd(createUsual());
    }
}
