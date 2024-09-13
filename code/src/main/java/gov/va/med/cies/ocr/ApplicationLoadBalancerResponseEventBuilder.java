package gov.va.med.cies.ocr;

import com.amazonaws.services.lambda.runtime.events.ApplicationLoadBalancerResponseEvent;
import gov.va.med.cies.ocr.model.CanonicalDocument;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpStatus;

import java.util.HashMap;
import java.util.Map;

public class ApplicationLoadBalancerResponseEventBuilder {
    private int statusCode;
    private String statusDescription;
    private Map<String, String> headers = new HashMap<>();
    private String body;

    public ApplicationLoadBalancerResponseEventBuilder statusCode(int result) {
        statusCode = result;
        statusDescription = Utility.getDescriptionFromHttpStatus(result);

        return this;
    }


    public ApplicationLoadBalancerResponseEventBuilder ok() {
        return statusCode(HttpStatus.SC_OK);
    }

    public ApplicationLoadBalancerResponseEventBuilder accepted() {
        return statusCode(HttpStatus.SC_ACCEPTED);
    }

    public ApplicationLoadBalancerResponseEventBuilder badRequest(final String msg) {
        statusCode(HttpStatus.SC_BAD_REQUEST);
        if (msg != null) {
            body = msg;
        }

        return this;
    }

    public ApplicationLoadBalancerResponseEventBuilder notFound(final String msg) {
        statusCode(HttpStatus.SC_NOT_FOUND);
        if (msg != null) {
            body = msg;
        }

        return this;
    }

    public ApplicationLoadBalancerResponseEventBuilder conflict(final String msg) {
        statusCode(HttpStatus.SC_CONFLICT);
        if (msg != null) {
            body = msg;
        }

        return this;
    }

    public ApplicationLoadBalancerResponseEventBuilder serverError(final String msg) {
        statusCode(HttpStatus.SC_INTERNAL_SERVER_ERROR);
        if (msg != null) {
            body = msg;
        }

        return this;
    }

    public ApplicationLoadBalancerResponseEventBuilder methodNotAllowed(final String msg) {
        statusCode(HttpStatus.SC_METHOD_NOT_ALLOWED);
        if (msg != null) {
            body = msg;
        }

        return this;
    }

    /**
     * The HyperText Transfer Protocol (HTTP) 302 Found redirect status response code indicates
     * that the resource requested has been temporarily moved to the URL given by the Location header.
     * @param destination
     * @return
     */
    public ApplicationLoadBalancerResponseEventBuilder redirect(final String destination) {
        statusCode(HttpStatus.SC_MOVED_TEMPORARILY);

        headers.put(HttpHeaders.LOCATION, destination);

        return this;
    }


    public ApplicationLoadBalancerResponseEventBuilder clearHeaders() {
        headers.clear();
        return this;
    }

    public ApplicationLoadBalancerResponseEventBuilder addHeader(final String key, String value) {
        if (key == null)
            throw new IllegalArgumentException("key cannot be null");
        if (value == null)
            throw new IllegalArgumentException("value cannot be null");

        headers.put(key, value);
        return this;
    }

    public ApplicationLoadBalancerResponseEventBuilder addHeaders(final Map<String, String> headers) {
        if (headers != null)
            headers.entrySet().forEach(entry -> this.headers.put(entry.getKey(), entry.getValue()));

        return this;
    }

    public ApplicationLoadBalancerResponseEventBuilder addHeaders(final CanonicalDocument document) {
        if (document.getContentType() != null)
            headers.put(HttpHeaders.CONTENT_TYPE, document.getContentType());
        if (document.getContentLength() != null)
            headers.put(HttpHeaders.CONTENT_LENGTH, document.getContentLength().toString());
        if (document.getIdentifier() != null)
            headers.put(HeaderTags.METADATA_KEY_FILE_NAME, document.getFilename());
        if (document.getDocumentExtractStatus() != null)
            headers.put(HeaderTags.TAG_KEY_STATUS, document.getDocumentExtractStatus().toString());
        if (document.getJobId() != null)
            headers.put(HeaderTags.TAG_JOB_ID, document.getJobId());
        if (document.getFilename() != null)
            headers.put(HeaderTags.METADATA_KEY_FILE_NAME, document.getFilename());

        return this;
    }

    public ApplicationLoadBalancerResponseEventBuilder body(final String body) {
        this.body = body;
        return this;
    }


    public ApplicationLoadBalancerResponseEvent build() {
        ApplicationLoadBalancerResponseEvent response = new ApplicationLoadBalancerResponseEvent();

        response.setHeaders(headers);
        response.setStatusCode(statusCode);
        response.setStatusDescription(statusDescription);
        if (body != null)
            response.setBody(body);

        return response;
    }

    /**
     * # Creates a response object with the status code and body, something like this:
     * # {
     * #     "statusCode": 200,
     * #     "statusDescription": "200 OK",
     * #     "isBase64Encoded": False,
     * #     "headers": {
     * #         "Content-Type": "application/json"
     * #     },
     * #     "body": "body"
     * # }
     * # Given a Dict of header keys and values, create a 200 response including the given headers
     * def format_200_response(headers: dict, body:  str):
     *     result = {}
     *
     *     result["statusCode"] = 200
     *     result["statusDescription"] = "200 OK"
     *
     *     if headers:
     *         result["headers"] = headers
     *
     *     if body:
     *         result["body"] = body
     *     return result
     *
     * def format_200_head_response(headers: dict):
     *     result = {}
     *
     *     result["statusCode"] = 200
     *     result["statusDescription"] = "200 OK"
     *
     *     if headers:
     *         result["headers"] = headers
     *
     *     return result
     *
     * def format_202_response(document_id : str) -> str:
     *     result = {}
     *
     *     result["statusCode"] = 202
     *     result["statusDescription"] = "202 Accepted"
     *
     *     return result
     *
     * # The HyperText Transfer Protocol (HTTP) 302 Found redirect status response code indicates
     * # that the resource requested has been temporarily moved to the URL given by the Location header.
     * def format_302_response(destination : str) -> str:
     *     result = {}
     *
     *     result["statusCode"] = 302
     *     result["statusDescription"] = "302 Found"
     *
     *     result["headers"] = {
     *         "Location": destination
     *     }
     *
     *     return result
     *
     * def format_400_response(err_msg : str):
     *     result = {}
     *
     *     result["statusCode"] = 400
     *     result["statusDescription"] = "400 Bad Request"
     *
     *     if err_msg:
     *         result["body"] = err_msg
     *
     *     return result
     *
     * def format_404_response(document_id : str):
     *     result = {}
     *
     *     result["statusCode"] = 404
     *     result["statusDescription"] = "404 Not Found"
     *
     *     if document_id:
     *         result["body"] = f"Document {document_id} not found"
     *
     *     return result
     *
     * def format_409_response(document_id : str):
     *     result = {}
     *
     *     result["statusCode"] = 409
     *     result["statusDescription"] = "409 Conflict"
     *
     *     if document_id:
     *         result["body"] = f"Document {document_id} already exists and may not be re-created"
     *     else:
     *         result["body"] = f"Document already exists and may not be re-created"
     *
     *     return result
     *
     * def format_500_response(err_msg : str):
     *     result = {}
     *
     *     result["statusCode"] = 500
     *     result["statusDescription"] = "500 Internal Server Error"
     *
     *     if err_msg:
     *         result["body"] = err_msg
     *
     *     return result
     */


}
