package gov.va.med.cies.ocr.exceptions;

/**
 * Any exception that is reported to the client as a Bad Request (4xx) exception
 */
public class BaseClientException extends AbstractApplicationException {
    public BaseClientException() {
    }

    public BaseClientException(String message) {
        super(message);
    }

    public BaseClientException(String message, Throwable cause) {
        super(message, cause);
    }

    public BaseClientException(Throwable cause) {
        super(cause);
    }

    public BaseClientException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
