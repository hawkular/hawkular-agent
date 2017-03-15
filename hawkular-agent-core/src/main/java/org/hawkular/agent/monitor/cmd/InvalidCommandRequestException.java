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
package org.hawkular.agent.monitor.cmd;

/**
 * An exception that commands can throw if they receive an invalid request.
 *
 * @author <a href="https://github.com/ppalaga">Peter Palaga</a>
 */
public class InvalidCommandRequestException extends Exception {

    private static final long serialVersionUID = -3482329012488512371L;

    public InvalidCommandRequestException() {
        super();
    }

    public InvalidCommandRequestException(String message) {
        super(message);
    }

    public InvalidCommandRequestException(String message, Throwable cause) {
        super(message, cause);
    }

    public InvalidCommandRequestException(String message, Throwable cause, boolean enableSuppression,
            boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }

    public InvalidCommandRequestException(Throwable cause) {
        super(cause);
    }

}
