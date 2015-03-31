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
package org.hawkular.dmrclient;

import org.jboss.dmr.ModelNode;

/**
 * Indicates a failed client request.
 *
 * @author John Mazzitelli
 */
public class FailureException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private static final String GENERIC_ERROR = "Failed request";

    public FailureException(ModelNode failureNode) {
        super(buildErrorMessage(GENERIC_ERROR, failureNode));
    }

    public FailureException(ModelNode failureNode, String errMsg) {
        super(buildErrorMessage(errMsg, failureNode));
    }

    public FailureException(ModelNode failureNode, Throwable cause) {
        super(buildErrorMessage(GENERIC_ERROR, failureNode), cause);
    }

    public FailureException(ModelNode failureNode, String errMsg, Throwable cause) {
        super(buildErrorMessage(errMsg, failureNode), cause);
    }

    public FailureException(String errMsg, Throwable cause) {
        super((errMsg != null) ? errMsg : GENERIC_ERROR, cause);
    }

    public FailureException(String errMsg) {
        super((errMsg != null) ? errMsg : GENERIC_ERROR);
    }

    public FailureException(Throwable cause) {
        super(GENERIC_ERROR, cause);
    }

    public FailureException() {
        super(GENERIC_ERROR);
    }

    private static String buildErrorMessage(String errMsg, ModelNode failureNode) {
        if (errMsg == null) {
            errMsg = GENERIC_ERROR;
        }

        String description = JBossASClient.getFailureDescription(failureNode);
        if (description != null) {
            errMsg += ": " + description;
        }

        return errMsg;
    }
}
