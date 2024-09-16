package gov.va.med.cies.ocr;

import com.amazonaws.services.s3.model.S3Object;
import gov.va.med.cies.ocr.exceptions.*;
import com.amazonaws.SdkClientException;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.*;
import gov.va.med.cies.ocr.exceptions.BaseServiceException;
import gov.va.med.cies.ocr.model.CanonicalDocument;
import gov.va.med.cies.ocr.model.CanonicalRequest;
import gov.va.med.cies.ocr.model.CanonicalResponse;
import gov.va.med.cies.ocr.model.ExtractedPdfText;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.textract.TextractClient;
import software.amazon.awssdk.services.textract.model.*;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

public class DocumentExtractManager {
    public static final String DETECTION_MODE = "DETECTION";
    public static final String ANALYSIS_MODE = "ANALYSIS";

    public static final int ASSUMED_MAX_CHAR_PER_PAGE = 528;    // used for

    private final Logger logger = LoggerFactory.getLogger(DocumentExtractManager.class);

    private final Region defaultRegion;
    private final AmazonS3 amazonS3;
    private final TextractClient textractClient;

    private final String sourceBucketName;
    private final String destinationBucketName;
    private final String textractServiceRoleArn;
    private final String textractStatusTopicName;
    private final int presignedUrlExpiration;

    private final String textractMode;
    private final int largeFileThreshold;
    private final int minimumTextPercentageToSkipTextract;

    public DocumentExtractManager(
            final String region,
            final String sourceBucketName, final String destinationBucketName,
            final String textractServiceRoleArn, final String textractStatusTopicName
    ) {
        this(
                region,
                sourceBucketName, destinationBucketName,
                textractServiceRoleArn, textractStatusTopicName,
                AmazonS3ClientBuilder.defaultClient(),
                TextractClient.builder().region(Region.of(region)).build()
        );
    }

    public DocumentExtractManager(
            final String region,
            final String sourceBucketName, final String destinationBucketName,
            final String textractServiceRoleArn, final String textractStatusTopicName,
            final AmazonS3 amazonS3,
            final TextractClient textractClient
    ) {
        presignedUrlExpiration = Integer.valueOf(ApplicationProperties.getSingleton().getProperty("PresignedUrlExpiration", "120"));
        textractMode = ApplicationProperties.getSingleton().getProperty(ApplicationProperties.TEXTRACT_MODE, DETECTION_MODE);
        minimumTextPercentageToSkipTextract = Integer.valueOf(ApplicationProperties.getSingleton().getProperty(ApplicationProperties.PDF_TEXT_CONTENT_THRESHOLD, "1046528"));
        largeFileThreshold = Integer.valueOf(ApplicationProperties.getSingleton().getProperty(ApplicationProperties.LARGE_FILE_THRESHOLD, "50"));

        this.defaultRegion = Region.of(region);
        this.sourceBucketName = sourceBucketName;
        this.destinationBucketName = destinationBucketName;
        this.textractServiceRoleArn = textractServiceRoleArn;
        this.textractStatusTopicName = textractStatusTopicName;

        this.amazonS3 = amazonS3;
        this.textractClient = textractClient;
    }

    // allow access so that a mocked instance can have behavior added
    public AmazonS3 getAmazonS3() {
        return amazonS3;
    }

    // allow access so that a mocked instance can have behavior added
    public TextractClient getTextractClient() {
        return textractClient;
    }

    public URL generatePresignedPostURL(final String identifier)
            throws AbstractApplicationException {

        URL result = null;
        logger.debug("generatePresignedPostURL({}), sourceBucket is [{}], presignedUrlExpiration=[{}]",
                identifier, this.sourceBucketName, this.presignedUrlExpiration
        );
        try {
            Date expirationDate = Date.from(Instant.now().plus(this.presignedUrlExpiration, ChronoUnit.SECONDS));
            result = amazonS3.generatePresignedUrl(this.sourceBucketName, identifier, expirationDate);
        } catch (SdkClientException sdkcX) {
            logger.error("Error generating presigned post URL: {}", sdkcX);
        }

        return result;
    }

    /**
     * Get document metadata, including the status and Job ID
     *
     * @param request
     * @return
     */
    public CanonicalResponse getDocumentMetadata(final CanonicalRequest request) {
        CanonicalResponse.Builder responseBuilder = CanonicalResponse.builder();

        try {
            CanonicalDocument metadata = getDocumentMetadataInternal(request.getCanonicalDocument().getIdentifier());
            responseBuilder.addDocument(metadata);
            responseBuilder.success();
        } catch (BaseServiceException bsX) {
            responseBuilder.serviceException(bsX);
        }

        return responseBuilder.build();
    }

    /**
     * This function saves the file to the S3 bucket along with whatever metadata is provided.
     * The job status is saved to an S3 tag.
     * The S3 bucket has an event listener lambda, which submits the document to Textract for OCR
     * NOTE: the Metadata is stored with the S3 object with the prefix "x-amz-meta-" added.
     * i.e. site_id becomes x-amz-meta-site_id in S3
     */
    public CanonicalResponse saveDocumentToSourceBucket(final CanonicalRequest canonicalRequest)
            throws AbstractApplicationException {
        logger.debug("saveDocumentToSourceBucket({})", canonicalRequest);

        CanonicalResponse.Builder canonicalResponseBuilder = CanonicalResponse.builder();

        CanonicalDocument requestDocument = canonicalRequest.getCanonicalDocument();
        if (requestDocument == null) {
            canonicalResponseBuilder.serviceException(new BaseServiceException("requestDocument cannot be null or an empty string"));
        } else if (canonicalRequest.getMethod() == "PUT" && (requestDocument.getIdentifier() == null || requestDocument.getIdentifier().isEmpty())) {
            canonicalResponseBuilder.serviceException(new BaseServiceException("identifier cannot be null or an empty string"));
        } else if (! requestDocument.hasBody()) {
            canonicalResponseBuilder.serviceException(new BaseServiceException("body cannot be null or a empty"));
        } else {
            CanonicalDocument.Builder effectiveDocumentBuilder = CanonicalDocument.builder()
                    .with(requestDocument)
                    .withIdentifierIfNull(UUID.randomUUID().toString())
                    .withFilenameDefaultIfNull()
                    .withContentTypeDefaultIfNull()
                    .withStatusDefaultIfNull();
            CanonicalDocument effectiveDocument = effectiveDocumentBuilder.build();
            logger.debug("effectiveDocument: {}", effectiveDocument);

            try {
                // POST or PUT the document body and metadata
                CanonicalDocument result = saveDocumentAndMetadataToSourceBucket(effectiveDocument);
                // Mutable properties (status and job ID) are stored as tags in S3
                updateStatusAndJobId(effectiveDocument.getIdentifier(), DocumentExtractStatus.New, null);
                canonicalResponseBuilder.addDocument(result);
                canonicalResponseBuilder.success();
            } catch (SdkClientException sdkcX) {
                canonicalResponseBuilder.serviceException(sdkcX);
            }
        }
        return canonicalResponseBuilder.build();
    }

    /**
     * This function retrieves the original document given the document_id
     */
    public CanonicalResponse getDocumentFromSourceBucket(final CanonicalRequest canonicalRequest)
            throws AbstractApplicationException {
        logger.debug("getDocumentFromSourceBucket({})", canonicalRequest);
        CanonicalResponse.Builder canonicalResponseBuilder = CanonicalResponse.builder();

        if (canonicalRequest == null || canonicalRequest.getCanonicalDocument() == null || canonicalRequest.getCanonicalDocument().getIdentifier() == null) {
            canonicalResponseBuilder.genericBadRequestException(new BaseClientException("identifier must not be null"));
        } else {
            final String identifier = canonicalRequest.getCanonicalDocument().getIdentifier();

            final S3Object s3Object = amazonS3.getObject(sourceBucketName, identifier);
            final ObjectMetadata objectMetadata = amazonS3.getObjectMetadata(sourceBucketName, identifier);

            if (s3Object != null && objectMetadata != null) {
                canonicalResponseBuilder.success();

                CanonicalDocument.Builder documentBuilder = CanonicalDocument.builder();
                documentBuilder.withBodyStream(s3Object.getObjectContent());
                documentBuilder.withContentType(objectMetadata.getContentType());
                documentBuilder.withContentLength((int) objectMetadata.getContentLength());
                documentBuilder.withIdentifier(identifier);
                documentBuilder.withDocumentExtractStatus(DocumentExtractStatus.of(objectMetadata.getUserMetaDataOf(HeaderTags.TAG_KEY_STATUS)));

                canonicalResponseBuilder.addDocument(documentBuilder.build());
            } else {
                canonicalResponseBuilder.notFound();
            }
        }
        return canonicalResponseBuilder.build();
    }

    /**
     * This function retrieves the extracted text given the document_id
     */
    public CanonicalResponse getTextFromDestinationBucket(final CanonicalRequest canonicalRequest)
            throws AbstractApplicationException {
        logger.debug("getTextFromDestinationBucket({})", canonicalRequest);
        return getFromDestinationBucket(Utility.createTextResultId(canonicalRequest.getCanonicalDocument().getIdentifier()));
    }

    /**
     * This function retrieves the entire extracted document given the document_id
     */
    public CanonicalResponse getJsonFromDestinationBucket(final CanonicalRequest canonicalRequest)
            throws AbstractApplicationException {
        logger.debug("getJsonFromDestinationBucket({})", canonicalRequest);
        return getFromDestinationBucket(Utility.createJsonResultId(canonicalRequest.getCanonicalDocument().getIdentifier()));
    }

    public CanonicalResponse deleteDocumentFromSourceBucket(final CanonicalRequest canonicalRequest)
            throws AbstractApplicationException {
        logger.debug("deleteDocumentFromSourceBucket({})", canonicalRequest);
        CanonicalResponse.Builder canonicalResponseBuilder = CanonicalResponse.builder();

        if (canonicalRequest == null || canonicalRequest.getCanonicalDocument() == null || canonicalRequest.getCanonicalDocument().getIdentifier() == null) {
            canonicalResponseBuilder.genericBadRequestException(new BaseClientException("identifier must not be null"));
        } else {
            final String identifier = canonicalRequest.getCanonicalDocument().getIdentifier();

            // retrieve the object metadata, which will be used to populate the response
            final ObjectMetadata objectMetadata = amazonS3.getObjectMetadata(sourceBucketName, identifier);

            if (objectMetadata != null) {
                // if the object metadata was found, then the object exists to delete
                amazonS3.deleteObject(sourceBucketName, identifier);

                canonicalResponseBuilder.success();

                CanonicalDocument.Builder documentBuilder = CanonicalDocument.builder();
                documentBuilder.withContentType(objectMetadata.getContentType());
                documentBuilder.withContentLength((int) objectMetadata.getContentLength());
                documentBuilder.withIdentifier(identifier);
                documentBuilder.withDocumentExtractStatus(DocumentExtractStatus.of(objectMetadata.getUserMetaDataOf(HeaderTags.TAG_KEY_STATUS)));

                canonicalResponseBuilder.addDocument(documentBuilder.build());
            } else {
                // the object does not exist, return a not found response
                canonicalResponseBuilder.notFound();
            }
        }
        return canonicalResponseBuilder.build();
    }

    /**
     * Submit a document to Textract for either text detection or text analysis, depending on the application properties.
     * This method is called by a Lambda which is triggered when a new document is added to the source S3 bucket.
     * This method may invoke either Textract text analysis or detection, depending on application mode.
     * Text detection extracts the text from the document.
     * Text analysis extracts the structure of the document, including the text,
     */
    public CanonicalResponse submitDocumentForTextExtraction(final CanonicalRequest canonicalRequest) {
        CanonicalResponse.Builder canonicalResponseBuilder = CanonicalResponse.builder();

        logger.debug("submitDocumentForTextExtraction({})", canonicalRequest);
        if (canonicalRequest == null || canonicalRequest.getCanonicalDocument() == null || canonicalRequest.getCanonicalDocument().getIdentifier() == null) {
            canonicalResponseBuilder.genericBadRequestException(new BaseClientException("identifier must not be null"));
        } else {
            final String identifier = canonicalRequest.getCanonicalDocument().getIdentifier();
            try {
                // if the PDF has enough text (not images of text) then extract that, save it and skip the OCR
                if (evaluatePdfTextAndShortcut(identifier)) {
                    canonicalResponseBuilder.success();

                } else {
                    software.amazon.awssdk.services.textract.model.S3Object s3Object = software.amazon.awssdk.services.textract.model.S3Object.builder()
                            .bucket(this.sourceBucketName)
                            .name(identifier)
                            .build();

                    DocumentLocation location = DocumentLocation.builder()
                            .s3Object(s3Object)
                            .build();

                    NotificationChannel notificationChannel = NotificationChannel.builder()
                            .snsTopicArn(textractStatusTopicName)
                            .roleArn(textractServiceRoleArn)
                            .build();


                    String jobId = null;
                    if (DETECTION_MODE.equals(textractMode)) {
                        jobId = submitDocumentToTextDetection(s3Object, location, notificationChannel, identifier);
                    } else if (ANALYSIS_MODE.equals(textractMode)) {
                        jobId = submitDocumentToTextAnalysis(s3Object, location, notificationChannel, identifier);
                    }

                    updateStatusAndJobId(identifier, DocumentExtractStatus.Submitted, jobId);
                    canonicalResponseBuilder.success();
                }
            } catch (BaseServiceException bsX) {
                canonicalResponseBuilder.serviceException(bsX);
            }
        }

        return canonicalResponseBuilder.build();
    }

    /**
     * Notification that Textract has completed extraction
     */
    public CanonicalResponse moveExtractedTextToDestination(final CanonicalRequest canonicalRequest) {
        CanonicalResponse.Builder canonicalResponseBuilder = CanonicalResponse.builder();

        if (canonicalRequest == null || canonicalRequest.getCanonicalDocument() == null || canonicalRequest.getCanonicalDocument().getIdentifier() == null) {
            canonicalResponseBuilder.genericBadRequestException(new BaseClientException("identifier must not be null"));
        } else if (canonicalRequest == null || canonicalRequest.getCanonicalDocument() == null || canonicalRequest.getCanonicalDocument().getDocumentExtractStatus() == null) {
            canonicalResponseBuilder.genericBadRequestException(new BaseClientException("document status must not be null"));
        } else {
            CanonicalDocument requestDocument = canonicalRequest.getCanonicalDocument();
            String identifier = requestDocument.getIdentifier();
            String jobId = requestDocument.getJobId();

            switch (requestDocument.getDocumentExtractStatus()) {
                case SUCCEEDED:
                    logger.info("{document_id} OCR status SUCCEEDED, moving results to output bucket", identifier);
                    try {
                        moveTextToDestinationBucket(jobId, identifier);
                        updateStatusAndJobId(identifier, DocumentExtractStatus.SUCCEEDED, null);
                        canonicalResponseBuilder.success();
                    } catch (BaseServiceException bsX) {
                        canonicalResponseBuilder.serviceException(bsX);
                    }
                    break;
                case FAILED:
                    logger.info("{document_id} OCR status FAILED, no results available", identifier);
                    try {
                        updateStatusAndJobId(identifier, DocumentExtractStatus.FAILED, null);
                        canonicalResponseBuilder.success();     // "success" as in the event was handled successfully
                    } catch (BaseServiceException bsX) {
                        canonicalResponseBuilder.serviceException(bsX);
                    }
                    break;
                default:
                    logger.warn("Invalid status [{}] received in Extract completion event for document [{}]", requestDocument.getDocumentExtractStatus(), identifier);
                    break;
            }
        }

        return canonicalResponseBuilder.build();
    }

    /*
     * ====================================================================================================
     * Private helpers
     * ====================================================================================================
     */

    /**
     * Get the identified document from the destination bucket
     *
     * @param identifier
     * @return
     * @throws AbstractApplicationException
     */
    private CanonicalResponse getFromDestinationBucket(final String identifier)
            throws AbstractApplicationException {
        logger.debug("getFromDestinationBucket({})", identifier);
        CanonicalResponse.Builder canonicalResponseBuilder = CanonicalResponse.builder();

        final S3Object s3Object = amazonS3.getObject(destinationBucketName, identifier);
        final ObjectMetadata objectMetadata = amazonS3.getObjectMetadata(destinationBucketName, identifier);

        if (s3Object != null && objectMetadata != null) {
            canonicalResponseBuilder.success();

            CanonicalDocument.Builder documentBuilder = CanonicalDocument.builder();
            documentBuilder.withBodyStream(s3Object.getObjectContent());
            documentBuilder.withContentType(objectMetadata.getContentType());
            documentBuilder.withContentLength((int) objectMetadata.getContentLength());
            documentBuilder.withIdentifier(identifier);

            canonicalResponseBuilder.addDocument(documentBuilder.build());
        } else {
            canonicalResponseBuilder.notFound();
        }

        return canonicalResponseBuilder.build();
    }


    /**
     *
     * @param identifier
     * @return
     * @throws BaseServiceException
     */
    private boolean evaluatePdfTextAndShortcut(String identifier) throws BaseServiceException {
        boolean result = false;

        CanonicalDocument documentMetadata = getDocumentMetadataInternal(identifier);
        if ("application/pdf".equals(documentMetadata.getContentType())) {
            ExtractedPdfText pdfText = extractTextFromPdf(identifier);

            if (pdfText.getText().length() >
                    (int)((this.minimumTextPercentageToSkipTextract / 100.0) * pdfText.getPageCount() * ASSUMED_MAX_CHAR_PER_PAGE)) {
                // shortcut
                // according to our criteria, there is enough text that the image content is likely not part of
                // the report text (i.e. there is text in the PDF and not a scanned document)
                saveTextToDestinationBucket(identifier, pdfText.getText());
                updateStatusAndJobId(identifier, DocumentExtractStatus.SUCCEEDED, null);
                result = true;
            }
        }

        return result;
    }

    /**
     * Parses the PDF, extracts the text and returns the text if there is enough text to
     * meet the minimum criteria for the amount of text (50% of 80 columns by 66 lines by default).
     * @param identifier
     * @return the text within the PDF, if there is enough to meet the minimum criteria, else null
     * @throws BaseServiceException
     */
    private ExtractedPdfText extractTextFromPdf(final String identifier) throws BaseServiceException {
        ExtractedPdfText result = null;
        File tempFile = null;
        try {
            GetObjectRequest getRequest = new GetObjectRequest(this.sourceBucketName, identifier);
            S3Object s3Object = amazonS3.getObject(getRequest);
            tempFile = new File(Utility.createTempPdfFileName(identifier));
            copyS3ObjectToFile(s3Object, tempFile);
            PDDocument pdfDocument = Loader.loadPDF(tempFile);
            PDFTextStripper stripper = new PDFTextStripper();
            final String pdfText = stripper.getText(pdfDocument);
            result = new ExtractedPdfText(pdfText, pdfDocument.getNumberOfPages());
        } catch (SdkClientException sdkcX) {
            throw new BaseServiceException("Failed during analysis of PDF document.", sdkcX);
        } catch (IOException ioX) {
            throw new BaseServiceException("Failed during analysis of PDF document.", ioX);
        } finally {
            // NOTE: the temp file MUST be deleted when we're done with it else we'll rapidly use up the available 500M
            if (tempFile != null && tempFile.exists())
                tempFile.delete();
        }

        return result;
    }

    private static void copyS3ObjectToFile(S3Object s3Object, File tempFile) throws IOException {
        try (S3ObjectInputStream s3InStream = s3Object.getObjectContent()) {
            try (FileOutputStream tempFileOutStream = new FileOutputStream(tempFile)) {
                byte[] buffy = new byte[4096];
                for(int bytesRead = s3InStream.read(buffy); bytesRead >= 0; bytesRead = s3InStream.read(buffy)) {
                    tempFileOutStream.write(buffy, 0, bytesRead);
                }
            }
        }
    }

    /**
     * Submit a document to Textract for analysis.
     */
    private String submitDocumentToTextAnalysis(
            final software.amazon.awssdk.services.textract.model.S3Object s3Object,
            final DocumentLocation location,
            final NotificationChannel notificationChannel,
            final String identifier) throws BaseServiceException {
        try {
            StartDocumentAnalysisRequest documentAnalysisRequest = StartDocumentAnalysisRequest.builder()
                    .documentLocation(location)
                    .featureTypes(Arrays.asList(FeatureType.LAYOUT))
                    .notificationChannel(notificationChannel)
                    .jobTag(identifier)
                    .build();

            StartDocumentAnalysisResponse txtResponse = textractClient.startDocumentAnalysis(documentAnalysisRequest);
            return txtResponse.jobId();
        } catch(SdkClientException sdkcX) {
            throw new BaseServiceException("Failed to submit document for text analysis", sdkcX);
        }
    }

    /**
     * Submit a document to Textract for text detection only.
     */
    private String submitDocumentToTextDetection(
            software.amazon.awssdk.services.textract.model.S3Object s3Object,
            DocumentLocation location,
            NotificationChannel notificationChannel,
            final String identifier) throws BaseServiceException {
        try {
            StartDocumentTextDetectionRequest documentTextDetectionRequest = StartDocumentTextDetectionRequest.builder()
                    .documentLocation(location)
                    .notificationChannel(notificationChannel)
                    .jobTag(identifier)
                    .build();

            StartDocumentTextDetectionResponse txtResponse = textractClient.startDocumentTextDetection(documentTextDetectionRequest);
            return txtResponse != null ? txtResponse.jobId() : null;
        } catch(SdkClientException sdkcX) {
            throw new BaseServiceException("Failed to submit document for text detection", sdkcX);
        }
    }

    /**
     * Copy the (text) document from the Textract result to the destination bucket
     * The identifier should be a UUID but it can be any string. When a file is copied
     * directly to the source bucket it may have any object ID.
     */
    private void moveTextToDestinationBucket(final String jobId, final String identifier) throws BaseServiceException {
        logger.debug("moveTextToDestination({})", identifier);

        if (identifier == null || identifier.isEmpty())
            throw new IllegalArgumentException("identifier must not be null or empty");

        String text = null;
        if (ANALYSIS_MODE.equals(this.textractMode))
            text = retrieveAnalysisTextResult(jobId);
        else if (DETECTION_MODE.equals(this.textractMode))
            text = retrieveDetectionTextResult(jobId);

        saveTextToDestinationBucket(identifier, text);
    }

    // Retrieves the text from a Textract analysis operation as a big String
    private String retrieveAnalysisTextResult(final String jobId) throws BaseServiceException {
        try {
            GetDocumentAnalysisRequest.Builder getDocumentAnalysisRequestBuilder = GetDocumentAnalysisRequest.builder()
                .jobId(jobId)
                .maxResults(1000)
                .nextToken(null);

            StringBuilder documentTextBuilder = new StringBuilder();

            for (String nextToken = "";
                 nextToken != null; ) {
                final GetDocumentAnalysisRequest getDocumentAnalysisRequest = getDocumentAnalysisRequestBuilder.build();
                final GetDocumentAnalysisResponse getDocumentAnalysisResponse = textractClient.getDocumentAnalysis(getDocumentAnalysisRequest);

                String pageText = getDocumentAnalysisResponse.blocks().stream()
                        .filter(block -> BlockType.PAGE.equals(block.blockType()))
                        .map(page -> page.text())
                        .collect(Collectors.joining());
                documentTextBuilder.append(pageText);

                nextToken = getDocumentAnalysisResponse.nextToken();
                getDocumentAnalysisRequestBuilder.nextToken(nextToken);
            }

            return documentTextBuilder.toString();
        } catch (SdkClientException sdkcX) {
            throw new BaseServiceException("failed to retrieve analyzed text", sdkcX);
        }
    }

    // Retrieves the text from a Textract detection operation as a big String
    private String retrieveDetectionTextResult(final String jobId) throws BaseServiceException {
        try {
            GetDocumentTextDetectionRequest.Builder requestBuilder = GetDocumentTextDetectionRequest.builder()
                    .jobId(jobId)
                    .maxResults(1000)
                    .nextToken(null);

            StringBuilder documentTextBuilder = new StringBuilder();

            for (String nextToken = "";
                 nextToken != null; ) {
                final GetDocumentTextDetectionRequest request = requestBuilder.build();
                final GetDocumentTextDetectionResponse response = textractClient.getDocumentTextDetection(request);

                String pageText = response.blocks().stream()
                        .filter(block -> BlockType.PAGE.equals(block.blockType()))
                        .map(page -> page.text())
                        .collect(Collectors.joining());
                documentTextBuilder.append(pageText);

                nextToken = response.nextToken();
                requestBuilder.nextToken(nextToken);
            }

            return documentTextBuilder.toString();
        } catch (SdkClientException sdkcX) {
            throw new BaseServiceException("failed to retrieve detected text", sdkcX);
        }
    }

    // saves the document body and metadata to the source bucket
    private CanonicalDocument saveDocumentAndMetadataToSourceBucket(final CanonicalDocument requestDocument) throws BaseServiceException {
        CanonicalDocument.Builder resultBuilder = CanonicalDocument.builder();

        try {
            PutObjectRequest putObjectRequest = new PutObjectRequest(sourceBucketName, requestDocument.getIdentifier(), requestDocument.getBody());

            // Immutable properties are stored as metadata in S3
            ObjectMetadata objectMetadata = new ObjectMetadata();
            if (requestDocument.getContentType() != null)
                objectMetadata.setContentType(requestDocument.getContentType());
            if (requestDocument.getContentLength() != null)
                objectMetadata.setContentLength(requestDocument.getContentLength());
            if (requestDocument.getFilename() != null)
                objectMetadata.addUserMetadata(HeaderTags.METADATA_KEY_FILE_NAME, requestDocument.getFilename());
            // mark the document with ocr-status as 'New'
            objectMetadata.addUserMetadata(HeaderTags.TAG_KEY_STATUS, DocumentExtractStatus.New.toString());
            putObjectRequest.withMetadata(objectMetadata);

            PutObjectResult putObjectResult = amazonS3.putObject(putObjectRequest);

            // copy the metadata from the request, and overwrite with content type and length from S3
            resultBuilder.withMetadata(requestDocument);

            // putObjectResult should never be null in production, but it may be with mock objects
            if (putObjectResult != null) {
                ObjectMetadata s3ObjectMetadata = putObjectResult.getMetadata();
                if (s3ObjectMetadata.getContentLength() > 0)
                    resultBuilder.withContentLength(Integer.valueOf((int) s3ObjectMetadata.getContentLength()));
                if (s3ObjectMetadata.getContentType() != null)
                    resultBuilder.withContentType(s3ObjectMetadata.getContentType());
            }
        } catch (SdkClientException sdkcX) {
            throw new BaseServiceException("Failed to put document and metadats to source bucket", sdkcX);
        } catch (IOException ioX) {
            throw new BaseServiceException("Failed to put document body from CanonicalDocument", ioX);
        }

        return resultBuilder.build();
    }

    /**
     * This function saves text to the destination S3 bucket, with error handling
     * and logging to help with debugging and troubleshooting.
     */
    private void saveTextToDestinationBucket(final String identifier, final String body) throws BaseServiceException {
        logger.info("saveTextToDestinationBucket({}, ...)", identifier);
        final String textIdentifier = Utility.createTextResultId(identifier);

        PutObjectRequest request = new PutObjectRequest(this.destinationBucketName, textIdentifier, body);
        try {
            PutObjectResult response = amazonS3.putObject(request);
        } catch (SdkClientException sdkcX) {
            throw new BaseServiceException("Failed to put text to destination bucket", sdkcX);
        }
    }

    /**
     * This function saves text to the destination S3 bucket, with error handling
     * and logging to help with debugging and troubleshooting.
     */
    private void saveJsonToDestinationBucket(final String identifier, final String body) throws BaseServiceException {
        logger.info("saveJsonToDestinationBucket({}, ...)", identifier);
        final String textIdentifier = Utility.createJsonResultId(identifier);

        PutObjectRequest request = new PutObjectRequest(this.destinationBucketName, textIdentifier, body);
        try {
            PutObjectResult response = amazonS3.putObject(request);
        } catch (SdkClientException sdkcX) {
            throw new BaseServiceException("Failed to put text to destination bucket", sdkcX);
        }
    }

    /**
     *
     * @param identifier
     * @param status
     * @param jobId
     */
    private void updateStatusAndJobId(final String identifier, final DocumentExtractStatus status, final String jobId)
    throws BaseServiceException {
        try {
            GetObjectTaggingRequest taggingGetRequest = new GetObjectTaggingRequest(this.sourceBucketName, identifier);
            final GetObjectTaggingResult initialObjectTagging = amazonS3.getObjectTagging(taggingGetRequest);
            List<Tag> tagUpdates = initialObjectTagging == null || initialObjectTagging.getTagSet() == null ? new ArrayList<>() : initialObjectTagging.getTagSet();

            if (status != null)
                tagUpdates.add(new Tag(HeaderTags.TAG_KEY_STATUS, DocumentExtractStatus.Submitted.toString()));
            if (jobId != null)
                tagUpdates.add(new Tag(HeaderTags.TAG_JOB_ID, jobId));
            ObjectTagging objectTagging = new ObjectTagging(tagUpdates);
            SetObjectTaggingRequest taggingUpdateRequest = new SetObjectTaggingRequest(this.sourceBucketName, identifier, objectTagging);
            amazonS3.setObjectTagging(taggingUpdateRequest);
        } catch (SdkClientException sdkcX) {
            throw new BaseServiceException("Failed to update status and/or job identifier", sdkcX);
        }
    }

    /**
     * Retrieve the document status for the given document_id
     */
     private DocumentExtractStatus getDocumentStatus(String identifier) throws BaseServiceException {
         DocumentExtractStatus result = null;
         final String status = getDocumentTagValue(identifier, HeaderTags.TAG_KEY_STATUS);
         if(status != null)
             result = DocumentExtractStatus.of(status);

         return result;
     }

    /**
     *
     * @param identifier
     * @return
     * @throws BaseServiceException
     */
    private String getDocumentJobId(String identifier) throws BaseServiceException {
        return getDocumentTagValue(identifier, HeaderTags.TAG_JOB_ID);
    }

    /**
     *
     * @param identifier
     * @param tagKey
     * @return
     * @throws BaseServiceException
     */
    private String getDocumentTagValue(final String identifier, final String tagKey) throws BaseServiceException {
        String result = null;

        if (identifier == null || identifier.isEmpty())
            throw new IllegalArgumentException("identifier must be a non-null String");
        if (tagKey == null || tagKey.isEmpty())
            throw new IllegalArgumentException("tagKey must be a non-null String");

        try {
            GetObjectTaggingRequest objectTaggingRequest = new GetObjectTaggingRequest(this.sourceBucketName, identifier);
            GetObjectTaggingResult objectTaggingResponse = amazonS3.getObjectTagging(objectTaggingRequest);
            if (objectTaggingResponse != null) {
                List<Tag> tags = objectTaggingResponse.getTagSet();
                if (tags != null) {
                    String tagValue = tags.stream()
                            .filter(tag -> tagKey.equals(tag.getKey()))
                            .findFirst()
                            .get()
                            .getValue();
                    result = tagValue;
                }
            }
        } catch (IllegalArgumentException iaX) {
            throw new BaseServiceException("Invalid for document OCR status");
        } catch (SdkClientException sdkcX) {
            throw new BaseServiceException("Error communicating with S3, unable to retrieve tags", sdkcX);
        }

        return result;
    }


    /**
     * This function retrieves document information from the S3 metadata and tags using the document_id
     * with error handling and logging to help with debugging and troubleshooting.
     * see https://boto3.amazonaws.com/v1/documentation/api/latest/reference/services/s3/client/head_object.html
     * for the returned format
     * The parts of the return format from metadata that we care about is something like:
     * {
     *   'ArchiveStatus': 'ARCHIVE_ACCESS'|'DEEP_ARCHIVE_ACCESS',
     *   'LastModified': datetime(2015, 1, 1),
     *   'ContentLength': 123,
     *   'Metadata': {
     *       'string': 'string'
     *   }
     * }
     * Note: the document status is stored as a Tag so that it can be mutated
     * Note: the result MUST not have any values of None, which confuses ALB
     **/
     private CanonicalDocument getDocumentMetadataInternal(final String identifier) throws BaseServiceException {
         CanonicalDocument.Builder resultBuilder = CanonicalDocument.builder();

         if (identifier == null || identifier.isEmpty())
             throw new IllegalArgumentException("identifier must be a non-null String");
         try {
             ObjectMetadata objectMetadata = amazonS3.getObjectMetadata(this.sourceBucketName, identifier);
             if (objectMetadata != null) {
                 resultBuilder.withFilename(objectMetadata.getUserMetaDataOf(HeaderTags.METADATA_KEY_FILE_NAME));
                 resultBuilder.withIdentifier(identifier);
                 resultBuilder.withContentType(objectMetadata.getContentType());
                 resultBuilder.withContentLength(Integer.valueOf((int) objectMetadata.getContentLength()));
             }

             GetObjectTaggingRequest objectTaggingRequest = new GetObjectTaggingRequest(this.sourceBucketName, identifier);
             GetObjectTaggingResult objectTaggingResponse = amazonS3.getObjectTagging(objectTaggingRequest);
             if (objectTaggingResponse != null) {
                 List<Tag> tags = objectTaggingResponse.getTagSet();
                 tags.stream()
                         .forEach(tag -> {
                             switch (tag.getKey()) {
                                 case HeaderTags.TAG_KEY_STATUS:
                                     resultBuilder.withDocumentExtractStatus(DocumentExtractStatus.of(tag.getValue()));
                                     break;
                                 case HeaderTags.TAG_JOB_ID:
                                     resultBuilder.withJobId(tag.getValue());
                                     break;
                             }
                         });
             }
         } catch (SdkClientException sdkcX) {
             throw new BaseServiceException("Error communicating woth S3, unable to retrieve metadata and/or tags", sdkcX);
         }

         return resultBuilder.build();
     }

    /**
     * Log the first maxLength characters of a message. This is used to log
     * requests and responses without including all of a multi-megabyte body.
     * @param prefix
     * @param message
     * @param maxLength
     */
    private void logLimitedLengthMessage(final String prefix, final String message, final int maxLength) {
        final String effectiveMessage = message == null ? "null" : message;
        final String effectivePrefix = prefix == null ? "" : prefix;
        final int effectiveMaxLength = maxLength <= 0 ? 32 : maxLength;

        logger.debug("{}:{}", effectivePrefix, effectiveMessage.length() > effectiveMaxLength
                ? effectiveMessage.substring(0, effectiveMaxLength-1)
                : effectiveMessage);
    }
}
