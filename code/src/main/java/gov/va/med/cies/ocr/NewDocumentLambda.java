package gov.va.med.cies.ocr;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.S3Event;
import com.amazonaws.services.lambda.runtime.events.models.s3.S3EventNotification;
import gov.va.med.cies.ocr.exceptions.InstanceValidationException;
import gov.va.med.cies.ocr.model.CanonicalDocument;
import gov.va.med.cies.ocr.model.CanonicalRequest;
import gov.va.med.cies.ocr.model.CanonicalResponse;
import org.apache.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NewDocumentLambda implements RequestHandler<S3Event, Void>  {
    private final Logger logger = LoggerFactory.getLogger(DocumentLambda.class);
    private final DocumentExtractManager dxm;
    private String sourceBucketName;

    public NewDocumentLambda() {
        this.sourceBucketName = System.getenv("SOURCE_BUCKET");
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

    public Void handleRequest(S3Event s3Event, Context context) {
        logger.debug("handleRequest({}, {})", s3Event, context);

        s3Event.getRecords().stream()
                .forEach(eventNotificationRecord -> {
                    S3EventNotification.S3Entity s3ObjectRef = eventNotificationRecord.getS3();
                    S3EventNotification.S3BucketEntity bucketEntity = s3ObjectRef.getBucket();
                    S3EventNotification.S3ObjectEntity objectEntity = s3ObjectRef.getObject();

                    if (sourceBucketName.equals(bucketEntity.getName()))
                        logger.info("Unable to submit document for processing from bucket [{}], only documents from [{}] can be processed",
                                bucketEntity.getName(), sourceBucketName);
                    else {
                        try {
                            final String identifier = objectEntity.getKey();
                            CanonicalDocument submitDocument = CanonicalDocument.builder()
                                    .withIdentifier(identifier)
                                    .build();
                            CanonicalRequest submitDocumentRequest = CanonicalRequest.builder()
                                    .withMethod("NewDocument")      // a pseudo-method specific to this app
                                    .withCanonicalDocument(submitDocument)
                                    .build();
                            CanonicalResponse response = dxm.submitDocumentForTextExtraction(submitDocumentRequest);
                            if (HttpStatus.SC_OK != response.getResult()) {
                                logger.warn("Failed to submit [{}] for text extraction with [{}]", identifier, response.getException());
                            }
                        } catch (InstanceValidationException ivX) {
                            logger.warn("Unable to build a valid request to submit document for text extraction", ivX);
                        }
                    }
                });

        return null;        // meaningless result
    }
}
