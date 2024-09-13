package gov.va.med.cies.ocr;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.ApplicationLoadBalancerRequestEvent;
import com.amazonaws.services.lambda.runtime.events.ApplicationLoadBalancerResponseEvent;
import gov.va.med.cies.ocr.exceptions.AbstractApplicationException;
import gov.va.med.cies.ocr.exceptions.BaseClientException;
import gov.va.med.cies.ocr.model.CanonicalDocument;
import gov.va.med.cies.ocr.model.CanonicalRequest;
import gov.va.med.cies.ocr.model.CanonicalResponse;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class DocumentLambda implements RequestHandler<ApplicationLoadBalancerRequestEvent, ApplicationLoadBalancerResponseEvent> {
    private final Logger logger = LoggerFactory.getLogger(DocumentLambda.class);
    private final DocumentExtractManager dxm;

    public DocumentLambda() {
        final String sourceBucketName = System.getenv("SOURCE_BUCKET");
        final String destinationBucketName = System.getenv("DESTINATION_BUCKET");
        final String textractServiceRole = System.getenv("TEXTRACT_SERVICE_ROLE");
        final String textractStatusTopic = System.getenv("TEXTRACT_STATUS_TOPIC");
        final String region = System.getenv("AWS_REGION");

        dxm = new DocumentExtractManager(
                region,
                sourceBucketName, destinationBucketName,
                textractServiceRole, textractStatusTopic
        );
    }

    public ApplicationLoadBalancerResponseEvent handleRequest(ApplicationLoadBalancerRequestEvent request, Context context) {
        logger.debug("handleRequest({}, {})", request, context);

        ApplicationLoadBalancerResponseEventBuilder resultBuilder = new ApplicationLoadBalancerResponseEventBuilder();

        CanonicalResponse response = null;
        CanonicalRequest canonicalRequest = null;
        try {
            canonicalRequest = parse(request);
        } catch (BaseClientException e) {
            resultBuilder.badRequest("Failed to parse ApplicationLoadBalancerRequestEvent");
            return resultBuilder.build();
        }

        switch(canonicalRequest.getMethod()) {
            case "HEAD":
                processHeadRequest(canonicalRequest, resultBuilder);
                break;
            case "GET":
                processGetRequest(canonicalRequest, resultBuilder);
                break;
            case "POST":
                processPostRequest(canonicalRequest, resultBuilder);
                break;
            case "PUT":
                processPutRequest(canonicalRequest, resultBuilder);
                break;
            case "DELETE":
                processDeleteRequest(canonicalRequest, resultBuilder);
                break;
            default:
                resultBuilder.methodNotAllowed(canonicalRequest.getMethod() + " method not handled");
        }

        return resultBuilder.build();
    }

    private void processHeadRequest(final CanonicalRequest canonicalRequest, final ApplicationLoadBalancerResponseEventBuilder resultBuilder) {
        CanonicalResponse response = dxm.getDocumentMetadata(canonicalRequest);
        if (response == null || response.getDocumentCount() == 0) {
            resultBuilder.notFound(canonicalRequest.getCanonicalDocument().getIdentifier());
        }
        else if (response.getDocumentCount() == 1) {
            // there must be exactly one document for a successful response
            CanonicalDocument document = response.getDocuments().get(0);
            resultBuilder.addHeaders(document);
            resultBuilder.ok();
        } else {
            resultBuilder.serverError("Multiple documents found for " + canonicalRequest.getCanonicalDocument().getIdentifier());
        }
    }

    private void processGetRequest(final CanonicalRequest canonicalRequest, final ApplicationLoadBalancerResponseEventBuilder resultBuilder) {
        try {
            CanonicalResponse response = dxm.getDocumentFromSourceBucket(canonicalRequest);
            if (response == null || response.getDocumentCount() == 0) {
                resultBuilder.notFound(canonicalRequest.getCanonicalDocument().getIdentifier());
            }
            else if (response.getDocumentCount() == 1) {
                // there must be exactly one document for a successful response
                CanonicalDocument document = response.getDocuments().get(0);
                resultBuilder.addHeaders(document);
                resultBuilder.body(document.getBody());
                resultBuilder.ok();
            } else {
                resultBuilder.serverError("Multiple documents found for " + canonicalRequest.getCanonicalDocument().getIdentifier());
            }
        } catch (AbstractApplicationException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void processPostRequest(final CanonicalRequest canonicalRequest, final ApplicationLoadBalancerResponseEventBuilder resultBuilder) {
        CanonicalResponse metadataResponse = dxm.getDocumentMetadata(canonicalRequest);
        if (metadataResponse != null && metadataResponse.getDocumentCount() > 0) {
            resultBuilder.conflict(canonicalRequest.getCanonicalDocument().getIdentifier() + " already exists");
        } else {
            try {
                CanonicalResponse response = dxm.saveDocumentToSourceBucket(canonicalRequest);
                if (response.getDocumentCount() == 1) {
                    // there must be exactly one document for a successful response
                    CanonicalDocument document = response.getDocuments().get(0);
                    resultBuilder.addHeaders(document);
                    resultBuilder.ok();
                } else {
                    resultBuilder.serverError("Multiple documents found for " + canonicalRequest.getCanonicalDocument().getIdentifier());
                }
            } catch (AbstractApplicationException aaX) {
                resultBuilder.serverError(aaX.getMessage());
            }
        }
    }

    private void processPutRequest(final CanonicalRequest canonicalRequest, final ApplicationLoadBalancerResponseEventBuilder resultBuilder) {
        CanonicalResponse metadataResponse = dxm.getDocumentMetadata(canonicalRequest);
        if (metadataResponse == null || metadataResponse.getDocumentCount() < 1) {
            resultBuilder.notFound(canonicalRequest.getCanonicalDocument().getIdentifier() + " not found");
        } else {
            CanonicalResponse response = null;
            try {
                response = dxm.saveDocumentToSourceBucket(canonicalRequest);
                if (response.getDocumentCount() == 1) {
                    // there must be exactly one document for a successful response
                    CanonicalDocument document = response.getDocuments().get(0);
                    resultBuilder.addHeaders(document);
                    resultBuilder.ok();
                } else {
                    resultBuilder.serverError("Multiple documents found for " + canonicalRequest.getCanonicalDocument().getIdentifier());
                }
            } catch (AbstractApplicationException aaX) {
                resultBuilder.serverError(aaX.getMessage());
            }
        }
    }

    private void processDeleteRequest(final CanonicalRequest canonicalRequest, final ApplicationLoadBalancerResponseEventBuilder resultBuilder) {
        CanonicalResponse response = null;
        try {
            response = dxm.deleteDocumentFromSourceBucket(canonicalRequest);
            if (response.getResult() == HttpStatus.SC_OK) {
                CanonicalDocument document = response.getDocuments().get(0);
                resultBuilder.addHeaders(document);
                resultBuilder.ok();
            } else if (response.getResult() == HttpStatus.SC_NOT_FOUND){
                resultBuilder.notFound("Unable to delete " + canonicalRequest.getCanonicalDocument().getIdentifier() + ", not found");
            } else {
                resultBuilder.serverError("Unable to delete " + canonicalRequest.getCanonicalDocument().getIdentifier() + ", unexpected response");
            }
        } catch (AbstractApplicationException aaX) {
            resultBuilder.serverError(aaX.getMessage());
        }

    }

    private CanonicalRequest parse(ApplicationLoadBalancerRequestEvent event)
            throws BaseClientException {
        // the full path may include:
        // / - the root e.g. "/"
        // /{identifier} - just an identifier e.g. "/655321"
        // /{identifier}/{revision-specification} - an identifier and a revision specification e.g. "/655321/-2"
        CanonicalRequest.Builder builder = CanonicalRequest.builder();
        CanonicalDocument.Builder documentBuilder = CanonicalDocument.builder();

        builder.withMethod(event.getHttpMethod());

        // get the path elements, the possible paths (that this code expects) are like:
        // null, "/", "/identifier" and "/identifier/revision"
        // where the path is null or "/" (the root) then the identifier and revision are null
        String path = event.getPath();
        if (path != null && path.startsWith("/"))
            path = path.substring(1);
        final String[] pathElements = (
                path == null
                        ? new String[0]
                        : path.split("/")
        );

        if (pathElements.length > 0)
            documentBuilder.withIdentifier(pathElements[0]);

        // grab all the headers we may be interested in
        final Map<String, String> headers = event.getHeaders();
        if (headers != null) {
            if (headers.get(HttpHeaders.CONTENT_TYPE) != null)
                documentBuilder.withContentType(headers.get(HttpHeaders.CONTENT_TYPE));
            if (headers.get(HttpHeaders.CONTENT_LENGTH) != null)
                documentBuilder.withContentLength(Integer.valueOf(headers.get(HttpHeaders.CONTENT_LENGTH)));
            if (headers.get(HeaderTags.METADATA_KEY_FILE_NAME) != null)
                documentBuilder.withFilename(headers.get(HeaderTags.METADATA_KEY_FILE_NAME));
        }

        if (event.getBody() != null)
            documentBuilder.withBody(event.getBody());
        if(event.getIsBase64Encoded())
            documentBuilder.withBodyIsBase64Encoded(true);

        builder.withCanonicalDocument(documentBuilder.build());
        return builder.build();
    }

    private ApplicationLoadBalancerResponseEvent createResponse(
            final CanonicalRequest canonicalRequest,
            final CanonicalResponse canonicalResponse) throws IOException {
        logger.info("createResponse({}, {})", canonicalRequest, canonicalResponse);
        // get the identifier of the document from the response if its available, else
        // from the request
        final String identifier = canonicalResponse != null
                && canonicalResponse.getDocumentCount() == 1
                && canonicalResponse.getDocuments().get(0) != null
                ? canonicalResponse.getDocuments().get(0).getIdentifier()
                : canonicalRequest != null
                    && canonicalRequest.getCanonicalDocument() != null
                    ? canonicalRequest.getCanonicalDocument().getIdentifier()
                    : null;

        ApplicationLoadBalancerResponseEventBuilder responseBuilder = new ApplicationLoadBalancerResponseEventBuilder();

        // default the status code and description, method specific handling may override these values
        responseBuilder.statusCode(canonicalResponse.getResult());

        Map<String, String> headers = new HashMap<>();
        // the request method informs the format of the response
        switch (canonicalRequest.getMethod().toUpperCase()) {
            case "POST":
            case "PUT":
            case "DELETE":
            case "HEAD":
                // POST, PUT, DELETE and HEAD response has one document without a body
                CanonicalDocument document = canonicalResponse.getDocuments().get(0);
                logger.debug("Body-less response using document {}", document);
                responseBuilder.addHeaders(document);
                break;
            case "GET":
                if (canonicalResponse.getDocumentCount() == 0) {
                    responseBuilder.notFound(identifier);
                    responseBuilder.addHeader(HeaderTags.METADATA_KEY_IDENTIFIER, canonicalRequest.getCanonicalDocument().getIdentifier());
                } else if (canonicalResponse.getDocumentCount() == 1) {
                    CanonicalDocument getDocument = canonicalResponse.getDocuments().get(0);

                    responseBuilder.addHeaders(getDocument);
                    responseBuilder.body(getDocument.getBody());
                } else {
                    responseBuilder.statusCode(HttpStatus.SC_MULTIPLE_CHOICES);
                    responseBuilder.addHeader(HeaderTags.METADATA_KEY_IDENTIFIER, identifier);
                }

                break;
        }

        ApplicationLoadBalancerResponseEvent response = responseBuilder.build();

        logger.info("createResponse(...), returning response [{}]", response);
        return response;
    }
}
