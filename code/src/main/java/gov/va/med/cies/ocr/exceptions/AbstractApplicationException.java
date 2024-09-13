package gov.va.med.cies.ocr.exceptions;

/**
 * The abstract parent of all application exceptions
 */
public abstract class AbstractApplicationException extends Exception {
    public AbstractApplicationException() {
    }

    public AbstractApplicationException(String message) {
        super(message);
    }

    public AbstractApplicationException(String message, Throwable cause) {
        super(message, cause);
    }

    public AbstractApplicationException(Throwable cause) {
        super(cause);
    }

    public AbstractApplicationException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
