package gov.va.med.cies.ocr;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SNSEvent;
import gov.va.med.cies.ocr.exceptions.InstanceValidationException;
import gov.va.med.cies.ocr.model.CanonicalDocument;
import gov.va.med.cies.ocr.model.CanonicalRequest;
import gov.va.med.cies.ocr.model.CanonicalResponse;
import org.apache.http.HttpStatus;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This Lambda receives SNS notification from Textract when the status of
 * document recognition changes.
 */
public class ExtractStatusNotificationLambda implements RequestHandler<SNSEvent, Void> {
    private final Logger logger = LoggerFactory.getLogger(DocumentLambda.class);
    private final DocumentExtractManager dxm;

    public ExtractStatusNotificationLambda() {
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

    @Override
    public Void handleRequest(final SNSEvent snsEvent, final Context context) {
        logger.debug("handleRequest ({}, {})", snsEvent, context);

        snsEvent.getRecords().stream()
                .forEach(snsEventRecord -> {
                    try {
                        JSONObject parsedMsg = new JSONObject(snsEventRecord.getSNS().getMessage());
                        final String identifier = parsedMsg.get("JobTag").toString();
                        final String status = parsedMsg.get("Status").toString();

                        final CanonicalDocument canonicalDocument = CanonicalDocument.builder()
                                .withIdentifier(identifier)
                                .withDocumentExtractStatus(DocumentExtractStatus.of(status))
                                .build();
                        CanonicalRequest.Builder canonicalRequestBuilder = CanonicalRequest.builder()
                                .withMethod("ExtractComplete")
                                .withCanonicalDocument(canonicalDocument);

                        CanonicalResponse response = dxm.moveExtractedTextToDestination(canonicalRequestBuilder.build());
                        if (HttpStatus.SC_OK != response.getResult()) {
                            logger.warn("Unable to move extracted text for document [{}] with [{}]", identifier, response.getException());
                        }
                    } catch (InstanceValidationException e) {
                        throw new RuntimeException(e);
                    }
                });

        return null;
    }
}
