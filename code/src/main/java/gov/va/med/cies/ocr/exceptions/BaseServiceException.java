package gov.va.med.cies.ocr.exceptions;

/**
 * Any exception that is reported to the client as a Server (5xx) exception
 */
public class BaseServiceException extends AbstractApplicationException {
    public BaseServiceException() {
    }

    public BaseServiceException(String message) {
        super(message);
    }

    public BaseServiceException(String message, Throwable cause) {
        super(message, cause);
    }

    public BaseServiceException(Throwable cause) {
        super(cause);
    }

    public BaseServiceException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
