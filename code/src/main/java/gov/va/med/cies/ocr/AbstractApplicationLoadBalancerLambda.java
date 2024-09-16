package gov.va.med.cies.ocr;

import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.ApplicationLoadBalancerRequestEvent;
import com.amazonaws.services.lambda.runtime.events.ApplicationLoadBalancerResponseEvent;
import gov.va.med.cies.ocr.exceptions.BaseClientException;
import gov.va.med.cies.ocr.model.CanonicalDocument;
import gov.va.med.cies.ocr.model.CanonicalRequest;
import org.apache.http.HttpHeaders;

import java.util.Map;

public abstract class AbstractApplicationLoadBalancerLambda
        extends AbstractBaseLambda
        implements RequestHandler<ApplicationLoadBalancerRequestEvent, ApplicationLoadBalancerResponseEvent> {

    protected CanonicalRequest parse(ApplicationLoadBalancerRequestEvent event)
            throws BaseClientException {
        // the full path may include:
        // / - the root e.g. "/"
        // /{identifier} - just an identifier e.g. "/655321"
        // /{identifier}/{revision-specification} - an identifier and a revision specification e.g. "/655321/-2"
        CanonicalRequest.Builder builder = CanonicalRequest.builder();
        CanonicalDocument.Builder documentBuilder = CanonicalDocument.builder();

        builder.withMethod(event.getHttpMethod());
        documentBuilder.withIdentifier(Utility.returnLastPathElement(event.getPath()));

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
}
