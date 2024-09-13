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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URL;
import java.util.Map;

/**
 * This Lambda gets "presigned" S3 URLs so that client can POST directly to the source bucket.
 */
public class PresignedUrlLambda
        extends AbstractApplicationLoadBalancerLambda {
    private final Logger logger = LoggerFactory.getLogger(PresignedUrlLambda.class);

    public PresignedUrlLambda() {
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
        if (canonicalRequest == null
                || canonicalRequest.getCanonicalDocument() == null
                || canonicalRequest.getCanonicalDocument().getIdentifier() == null
                || canonicalRequest.getCanonicalDocument().getIdentifier().isEmpty())
            resultBuilder.badRequest("identifier not provided");
        else {
            try {
                URL response = getDocumentExtractManager().generatePresignedPostURL(canonicalRequest.getCanonicalDocument().getIdentifier());
                resultBuilder.body(response.toString());
                resultBuilder.ok();
            } catch (AbstractApplicationException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
