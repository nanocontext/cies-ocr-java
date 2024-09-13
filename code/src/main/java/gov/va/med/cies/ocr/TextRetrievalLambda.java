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
 * This Lambda handles the external interaction with the Destination Bucket (GET)
 */
public class TextRetrievalLambda
        extends AbstractApplicationLoadBalancerLambda {
    private final Logger logger = LoggerFactory.getLogger(TextRetrievalLambda.class);

    public TextRetrievalLambda() {
        super();
    }

    public ApplicationLoadBalancerResponseEvent handleRequest(ApplicationLoadBalancerRequestEvent request, Context context) {
        logger.debug("handleRequest({}, {})", request, context);

        ApplicationLoadBalancerResponseEventBuilder resultBuilder = new ApplicationLoadBalancerResponseEventBuilder();

        CanonicalRequest canonicalRequest = null;
        try {
            canonicalRequest = parse(request);
        } catch (BaseClientException e) {
            resultBuilder.badRequest("Failed to parse ApplicationLoadBalancerRequestEvent");
            return resultBuilder.build();
        }

        switch(canonicalRequest.getMethod()) {
            case "GET":
                processGetRequest(canonicalRequest, resultBuilder);
                break;
            case "POST":
            case "PUT":
            case "DELETE":
            case "HEAD":
            default:
                resultBuilder.methodNotAllowed(canonicalRequest.getMethod() + " method not handled");
        }

        return resultBuilder.build();
    }

    private void processGetRequest(final CanonicalRequest canonicalRequest, final ApplicationLoadBalancerResponseEventBuilder resultBuilder) {
        final boolean retrieveText = "text/plain".equals(canonicalRequest.getCanonicalDocument().getContentType());

        try {
            CanonicalResponse response = retrieveText
                    ? getDocumentExtractManager().getTextFromDestinationBucket(canonicalRequest)
                    : getDocumentExtractManager().getJsonFromDestinationBucket(canonicalRequest);

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
}
