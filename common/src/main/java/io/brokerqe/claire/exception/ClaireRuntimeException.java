/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.claire.exception;

public class ClaireRuntimeException extends RuntimeException {

    public ClaireRuntimeException(String message) {
        super(message);
    }
    public ClaireRuntimeException(String message, Throwable cause) {
        super(message, cause);
    }

}
