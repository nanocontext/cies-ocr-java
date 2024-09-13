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

public class NewDocumentLambda
        extends AbstractBaseLambda
        implements RequestHandler<S3Event, Void>  {
    private final Logger logger = LoggerFactory.getLogger(DocumentLambda.class);

    public NewDocumentLambda() {
        super();
    }

    public Void handleRequest(S3Event s3Event, Context context) {
        logger.debug("handleRequest({}, {})", s3Event, context);

        s3Event.getRecords().stream()
                .forEach(eventNotificationRecord -> {
                    S3EventNotification.S3Entity s3ObjectRef = eventNotificationRecord.getS3();
                    S3EventNotification.S3BucketEntity bucketEntity = s3ObjectRef.getBucket();
                    S3EventNotification.S3ObjectEntity objectEntity = s3ObjectRef.getObject();

                    if (getSourceBucketName().equals(bucketEntity.getName()))
                        logger.info("Unable to submit document for processing from bucket [{}], only documents from [{}] can be processed",
                                bucketEntity.getName(), getSourceBucketName());
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
                            CanonicalResponse response = getDocumentExtractManager().submitDocumentForTextExtraction(submitDocumentRequest);
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
