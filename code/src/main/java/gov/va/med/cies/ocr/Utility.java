package gov.va.med.cies.ocr;

import org.apache.http.HttpStatus;

import java.net.URLConnection;
import java.security.InvalidParameterException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class Utility {
    private static Map<Integer, String> statusDescriptionMap;

    static {
        statusDescriptionMap = new HashMap<>();

        statusDescriptionMap.put(HttpStatus.SC_CONTINUE, "Continue");
        statusDescriptionMap.put(HttpStatus.SC_PROCESSING, "Processing");
        statusDescriptionMap.put(HttpStatus.SC_SWITCHING_PROTOCOLS, "Switching protocols");

        statusDescriptionMap.put(HttpStatus.SC_OK, "Ok");
        statusDescriptionMap.put(HttpStatus.SC_CREATED, "Created");
        statusDescriptionMap.put(HttpStatus.SC_ACCEPTED, "Accepted");
        statusDescriptionMap.put(HttpStatus.SC_NO_CONTENT, "No content");
        statusDescriptionMap.put(HttpStatus.SC_RESET_CONTENT, "Reset content");
        statusDescriptionMap.put(HttpStatus.SC_PARTIAL_CONTENT, "Partial content");
        statusDescriptionMap.put(HttpStatus.SC_MULTI_STATUS, "Multi status");

        statusDescriptionMap.put(HttpStatus.SC_MULTIPLE_CHOICES, "Multiple choices");
        statusDescriptionMap.put(HttpStatus.SC_MOVED_PERMANENTLY, "Moved permanently");
        statusDescriptionMap.put(HttpStatus.SC_MOVED_TEMPORARILY, "Moved temporarily");
        statusDescriptionMap.put(HttpStatus.SC_SEE_OTHER, "See other");
        statusDescriptionMap.put(HttpStatus.SC_NOT_MODIFIED, "Not modified");
        statusDescriptionMap.put(HttpStatus.SC_USE_PROXY, "Use proxy");
        statusDescriptionMap.put(HttpStatus.SC_TEMPORARY_REDIRECT, "Temporary redirect");

        statusDescriptionMap.put(HttpStatus.SC_BAD_REQUEST, "Bad request");     //400
        statusDescriptionMap.put(HttpStatus.SC_UNAUTHORIZED, "Unauthorized");       // 401
        statusDescriptionMap.put(HttpStatus.SC_PAYMENT_REQUIRED, "Payment required");       // 402
        statusDescriptionMap.put(HttpStatus.SC_FORBIDDEN, "Forbidden");         // 403
        statusDescriptionMap.put(HttpStatus.SC_NOT_FOUND, "Not found");         // 404
        statusDescriptionMap.put(HttpStatus.SC_METHOD_NOT_ALLOWED, "Method not allowed");       // 405
        statusDescriptionMap.put(HttpStatus.SC_NOT_ACCEPTABLE, "Not acceptable");       //406
        statusDescriptionMap.put(HttpStatus.SC_PROXY_AUTHENTICATION_REQUIRED, "Proxy authentication required");       // 407
        statusDescriptionMap.put(HttpStatus.SC_REQUEST_TIMEOUT, "Request timeout");       // 408
        statusDescriptionMap.put(HttpStatus.SC_CONFLICT, "Conflict");       // 409
        statusDescriptionMap.put(HttpStatus.SC_GONE, "Gone");       // 410
        statusDescriptionMap.put(HttpStatus.SC_LENGTH_REQUIRED, "Length required");       // 411
        statusDescriptionMap.put(HttpStatus.SC_PRECONDITION_FAILED, "Precondition failed");       // 412
        statusDescriptionMap.put(HttpStatus.SC_REQUEST_TOO_LONG, "Request too long");       // 413
        statusDescriptionMap.put(HttpStatus.SC_REQUEST_URI_TOO_LONG, "Request URI too long");       // 414
        statusDescriptionMap.put(HttpStatus.SC_UNSUPPORTED_MEDIA_TYPE, "Unsupported media type");       // 415
        statusDescriptionMap.put(HttpStatus.SC_REQUESTED_RANGE_NOT_SATISFIABLE, "Request range not satisfiable");       // 416
        statusDescriptionMap.put(HttpStatus.SC_EXPECTATION_FAILED, "Expectation failed");       // 417
        statusDescriptionMap.put(HttpStatus.SC_INSUFFICIENT_SPACE_ON_RESOURCE, "Insufficient space on resource");       // 419
        statusDescriptionMap.put(HttpStatus.SC_METHOD_FAILURE, "Method failure");       // 420
        statusDescriptionMap.put(HttpStatus.SC_UNPROCESSABLE_ENTITY, "Unprocessable entity");       // 422
        statusDescriptionMap.put(HttpStatus.SC_LOCKED, "Locked");       // 423
        statusDescriptionMap.put(HttpStatus.SC_FAILED_DEPENDENCY, "Failed dependency");     // 424
        statusDescriptionMap.put(HttpStatus.SC_TOO_MANY_REQUESTS, "Too many requests");       // 429

        statusDescriptionMap.put(HttpStatus.SC_INTERNAL_SERVER_ERROR, "Internal server error");       // 500
        statusDescriptionMap.put(HttpStatus.SC_NOT_IMPLEMENTED, "Not implemented");       // 501
        statusDescriptionMap.put(HttpStatus.SC_BAD_GATEWAY, "Bad gateway");       // 502
        statusDescriptionMap.put(HttpStatus.SC_SERVICE_UNAVAILABLE, "Service unavailable");       // 503
        statusDescriptionMap.put(HttpStatus.SC_GATEWAY_TIMEOUT, "Gateway timeout");       // 504
        statusDescriptionMap.put(HttpStatus.SC_HTTP_VERSION_NOT_SUPPORTED, "HTTP version not supported");       // 505
        statusDescriptionMap.put(HttpStatus.SC_INSUFFICIENT_STORAGE, "Insufficient storage");       // 507

        statusDescriptionMap = Collections.unmodifiableMap(statusDescriptionMap);
    }


    public static String removeLeadingSlash(final String in) {
        String result = in;

        if (in != null && in.startsWith("/"))
            result = in.substring(1);
        return result;
    }

    /**
     * given a slash delimited list, return the last element
     */
    public static String returnLastPathElement(final String path){
        String result = null;
        // the path is expected to be something like: /text/<job_id>, where text is a constant
        String[] pathElements = path == null ? new String[0] : path.split("/");
        if(pathElements.length > 0)
            result = pathElements[pathElements.length - 1];
        return result;
    }

    /**
     * Returns the MIME type based on the file extension.
     * Args: filename (str): The name of the file, including the extension.
     * Returns: str: The MIME type corresponding to the file extension.
     */
    public static String getMimeType(final String filename) {
        String mimeType = "application/octet-stream";

        if(filename != null)
            mimeType = URLConnection.guessContentTypeFromName(filename);

        return mimeType;
    }

    public static String createTextResultId(final String identifier) {
        if (identifier == null)
            throw new InvalidParameterException("identifier cannot be null");
        return identifier.endsWith(".txt") ? identifier : identifier + ".txt";
    }

    public static String createJsonResultId(final String identifier) {
        if (identifier == null)
            throw new InvalidParameterException("identifier cannot be null");
        return identifier.endsWith(".json") ? identifier : identifier + ".json";
    }

    public static String createTempPdfFileName(final String identifier) {
        if (identifier == null)
            throw new InvalidParameterException("identifier cannot be null");
        return "/tmp" + identifier + ".pdf";
    }

    public static String getDocumentIdFromResultId(final String resultId) {
        if (resultId == null)
            throw new InvalidParameterException("resultId cannot be null");
        return resultId.split(".")[0];
    }

    public static String getDescriptionFromHttpStatus(final int httpStatusCode) {
        return Utility.statusDescriptionMap.get(httpStatusCode);
    }
}
