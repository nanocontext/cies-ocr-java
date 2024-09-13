package gov.va.med.cies.ocr;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The base class the Lambda functions.
 * This creates an instance of the DocumentExtractManager class, which implements all of the
 * application capabilities
 */
public abstract class AbstractBaseLambda {
    private final Logger logger = LoggerFactory.getLogger(AbstractBaseLambda.class);
    private final String sourceBucketName;
    private final String destinationBucketName;
    private final String textractServiceRole;
    private final String textractStatusTopic;
    private final String region;
    private final DocumentExtractManager dxm;

    public AbstractBaseLambda() {
        sourceBucketName = System.getenv("SOURCE_BUCKET");
        destinationBucketName = System.getenv("DESTINATION_BUCKET");
        textractServiceRole = System.getenv("TEXTRACT_SERVICE_ROLE");
        textractStatusTopic = System.getenv("TEXTRACT_STATUS_TOPIC");
        region = System.getenv("AWS_REGION");

        logger.info("AbstractBaseLambda, creating DocumentExtractManager({}, {}, {}, {}, {})",
                region,
                sourceBucketName, destinationBucketName,
                textractServiceRole, textractStatusTopic
        );
        dxm = new DocumentExtractManager(
                region,
                sourceBucketName, destinationBucketName,
                textractServiceRole, textractStatusTopic
        );
    }

    public String getSourceBucketName() {
        return sourceBucketName;
    }

    public String getDestinationBucketName() {
        return destinationBucketName;
    }

    public String getTextractServiceRole() {
        return textractServiceRole;
    }

    public String getTextractStatusTopic() {
        return textractStatusTopic;
    }

    public String getRegion() {
        return region;
    }

    public DocumentExtractManager getDocumentExtractManager() {
        return dxm;
    }
}
