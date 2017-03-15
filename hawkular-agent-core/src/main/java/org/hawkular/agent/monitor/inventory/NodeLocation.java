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
package org.hawkular.agent.monitor.inventory;

import org.hawkular.agent.monitor.protocol.Driver;

/**
 * Just a marker interface for protocol specific node locations. A node location should entail some protocol specific
 * object (call it a "path") that can be used to retrieve one or mode nodes using a protocol specific {@link Driver}.
 * <p>
 * Note that platform specific paths
 * <ul>
 * <li>can be either relative to some other base path or absolute
 * <li>can contain wildcards so that they can be used to retrieve a set of matching nodes.
 * </ul>
 * @author <a href="https://github.com/ppalaga">Peter Palaga</a>
 */
public interface NodeLocation {
}
