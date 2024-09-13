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

/**
 * This Lambda handles the interaction with the Source Bucket (POST, PUT, GET, DELETE)
 */
public class DocumentLambda
        extends AbstractApplicationLoadBalancerLambda {
    private final Logger logger = LoggerFactory.getLogger(DocumentLambda.class);

    public DocumentLambda() {
        super();
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
        CanonicalResponse response = getDocumentExtractManager().getDocumentMetadata(canonicalRequest);
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
            CanonicalResponse response = getDocumentExtractManager().getDocumentFromSourceBucket(canonicalRequest);
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
        CanonicalResponse metadataResponse = getDocumentExtractManager().getDocumentMetadata(canonicalRequest);
        if (metadataResponse != null && metadataResponse.getDocumentCount() > 0) {
            resultBuilder.conflict(canonicalRequest.getCanonicalDocument().getIdentifier() + " already exists");
        } else {
            try {
                CanonicalResponse response = getDocumentExtractManager().saveDocumentToSourceBucket(canonicalRequest);
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
        CanonicalResponse metadataResponse = getDocumentExtractManager().getDocumentMetadata(canonicalRequest);
        if (metadataResponse == null || metadataResponse.getDocumentCount() < 1) {
            resultBuilder.notFound(canonicalRequest.getCanonicalDocument().getIdentifier() + " not found");
        } else {
            CanonicalResponse response = null;
            try {
                response = getDocumentExtractManager().saveDocumentToSourceBucket(canonicalRequest);
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
            response = getDocumentExtractManager().deleteDocumentFromSourceBucket(canonicalRequest);
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
}
