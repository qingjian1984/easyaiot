package com.basiclab.iot.sink.protocol.polling;

import com.basiclab.iot.sink.polling.CollectorConfigErrorCode;

/** Closed failure from local collector state validation or persistence. */
public final class CollectorConfigStateException extends RuntimeException {
    private final CollectorConfigErrorCode errorCode;

    public CollectorConfigStateException(CollectorConfigErrorCode errorCode) {
        super(errorCode.name());
        this.errorCode = errorCode;
    }

    public CollectorConfigStateException(CollectorConfigErrorCode errorCode, Throwable cause) {
        super(errorCode.name(), cause);
        this.errorCode = errorCode;
    }

    public CollectorConfigErrorCode errorCode() {
        return errorCode;
    }
}
