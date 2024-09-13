package gov.va.med.cies.ocr;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.*;
import com.amazonaws.services.s3.model.S3Object;
import gov.va.med.cies.ocr.exceptions.AbstractApplicationException;
import gov.va.med.cies.ocr.exceptions.InstanceValidationException;
import gov.va.med.cies.ocr.model.CanonicalDocument;
import gov.va.med.cies.ocr.model.CanonicalRequest;
import gov.va.med.cies.ocr.model.CanonicalResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.HttpRequestBase;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import software.amazon.awssdk.services.textract.TextractAsyncClient;
import software.amazon.awssdk.services.textract.TextractClient;
import software.amazon.awssdk.services.textract.model.*;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.doReturn;

public class DocumentExtractManagerTest {
    private final String SOURCE_BUCKET_NAME = "sourceBucket";
    private final String DESTINATION_BUCKET_NAME = "destinationBucket";
    private final String TEXTRACT_SERVICE_ROLE = "arn:aws:role:655321";
    private final String STATUS_TOPIC_NAME = "notification";

    private final Logger logger = LoggerFactory.getLogger(DocumentExtractManagerTest.class);

    @DataProvider(name = "PresignedURLDataProvider")
    public Object[][] presignedUrlDataProvider() throws MalformedURLException {
        return new Object[][]{
                {"655321", new URL("https://localhost:443/success")}
        };
    }

    @Test(dataProvider = "PresignedURLDataProvider")
    public void testGeneratePresignedPostURL(final String identifier, final URL expected)
            throws AbstractApplicationException {
        DocumentExtractManager dxm = createTestSubject();
        mockGeneratePresignedUrl(dxm, SOURCE_BUCKET_NAME, identifier, expected);
        URL actual = dxm.generatePresignedPostURL(identifier);

        Assert.assertNotNull(actual);
        Mockito.verify(dxm.getAmazonS3())
                .generatePresignedUrl(Mockito.eq(SOURCE_BUCKET_NAME), Mockito.eq(identifier), Mockito.any(Date.class));
        Assert.assertEquals(actual, expected);
    }

    @DataProvider(name = "GetDocumentMetadataProvider")
    public Object[][] getDocumentMetadataProvider() throws InstanceValidationException {
        return new Object[][] {
                {
                    // mocking data
                    createObjectMetadata(
                            "655321", "filename001",
                            "application/pdf", Integer.valueOf(123655),
                            DocumentExtractStatus.New, "job001"),
                    // the request to invoke
                    CanonicalRequest.builder()
                            .withMethod("HEAD")
                            .withCanonicalDocument(CanonicalDocument.builder()
                                    .withIdentifier("655321")
                                    .build())
                            .build(),
                    // expected results
                    "application/pdf", Integer.valueOf(123655), "filename001"
                }
        };
    }

    @Test(dataProvider = "GetDocumentMetadataProvider")
    public void testGetDocumentMetadata(
            final ObjectMetadata mockedObjectMetadata,
            final CanonicalRequest request,
            final String expectedContentType, final Integer expectedContentLength,
            final String expectedFilename)
    {
        DocumentExtractManager dxm = createTestSubject();
        final String identifier = request.getCanonicalDocument().getIdentifier();

        // mock the S3 metadata and tagging response
        mockGetObjectMetadataAndGetObjectTagging(dxm, mockedObjectMetadata);

        // make the subject call
        CanonicalResponse actualResponse = dxm.getDocumentMetadata(request);

        // assert that the response is NOT null and that S3 was called to get the object metadata and tagging
        Assert.assertNotNull(actualResponse);
        Mockito.verify(dxm.getAmazonS3())
                .getObjectMetadata(Mockito.eq(SOURCE_BUCKET_NAME), Mockito.eq(identifier));
        Mockito.verify(dxm.getAmazonS3())
                .getObjectMetadata(Mockito.eq(SOURCE_BUCKET_NAME), Mockito.eq(identifier));

        Assert.assertEquals(actualResponse.getDocumentCount(), 1);
        Assert.assertNotNull(actualResponse.getDocuments());
        CanonicalDocument responseDocument = actualResponse.getDocuments().get(0);
        Assert.assertNotNull(responseDocument);
        Assert.assertEquals(responseDocument.getContentType(), expectedContentType);
        Assert.assertEquals(responseDocument.getContentLength(), expectedContentLength);
        Assert.assertEquals(responseDocument.getFilename(), expectedFilename);
    }

    @DataProvider(name = "SaveDocumentToSourceBucketProvider")
    public Object[][] saveDocumentToSourceBucketProvider() throws InstanceValidationException {
        return new Object[][] {
                {CanonicalRequest.builder()
                        .withMethod("POST")
                        .withCanonicalDocument(CanonicalDocument.builder()
                                .withContentType("text/plain")
                                .withContentLength(Integer.valueOf(17))
                                .withBody("Document Body 001")
                                .build())
                        .build()
                }
        };
    }

    @Test(dataProvider = "SaveDocumentToSourceBucketProvider")
    public void testSaveDocumentToSourceBucket(final CanonicalRequest canonicalRequest) {
        DocumentExtractManager dxm = createTestSubject();
        mockPutObject(dxm, canonicalRequest.getCanonicalDocument().getContentType(), canonicalRequest.getCanonicalDocument().getContentLength());

        CanonicalResponse actualResponse = null;
        try {
            actualResponse = dxm.saveDocumentToSourceBucket(canonicalRequest);
            Assert.assertNotNull(actualResponse);
            Assert.assertEquals(actualResponse.getDocumentCount(), 1);
            Assert.assertNotNull(actualResponse.getDocuments());
            CanonicalDocument document = actualResponse.getDocuments().get(0);
            Assert.assertNotNull(document);
            Assert.assertFalse(document.hasBody());
            Assert.assertNotNull(document.getContentLength());
            Assert.assertTrue(document.getContentLength().intValue() > 0);
        } catch (AbstractApplicationException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void testGetDocumentFromSourceBucket() throws AbstractApplicationException {
        // Arrange
        DocumentExtractManager dxm = createTestSubject();

        final String documentId = UUID.randomUUID().toString();

        CanonicalDocument canonicalDocument = CanonicalDocument.builder()
                .withIdentifier(documentId)
                .build();
        CanonicalRequest canonicalRequest = CanonicalRequest.builder()
                .withMethod("GET")
                .withCanonicalDocument(canonicalDocument)
                .build();

        S3Object s3Object = Mockito.mock(S3Object.class);
        ObjectMetadata objectMetadata = Mockito.mock(ObjectMetadata.class);
        S3ObjectInputStream inputStream = new S3ObjectInputStream(
                new ByteArrayInputStream("document content".getBytes()),
                new HttpRequestBase(){
                    @Override
                    public String getMethod() {
                        return "GET";
                    }
                }
        );

        Mockito.when(dxm.getAmazonS3().getObject(SOURCE_BUCKET_NAME, documentId)).thenReturn(s3Object);
        Mockito.when(s3Object.getObjectContent()).thenReturn(inputStream);
        Mockito.when(dxm.getAmazonS3().getObjectMetadata(SOURCE_BUCKET_NAME, documentId)).thenReturn(objectMetadata);
        Mockito.when(objectMetadata.getContentType()).thenReturn("application/pdf");
        Mockito.when(objectMetadata.getContentLength()).thenReturn(123L);
        Mockito.when(objectMetadata.getUserMetaDataOf(HeaderTags.TAG_KEY_STATUS)).thenReturn("New");

        // Act
        CanonicalResponse response = dxm.getDocumentFromSourceBucket(canonicalRequest);

        // Assert
        Assert.assertNotNull(response);
        Assert.assertEquals(response.getResult(), HttpStatus.SC_OK);
        Assert.assertEquals(response.getDocumentCount(), 1);
        CanonicalDocument responseDocument = response.getDocuments().get(0);
        Assert.assertEquals(responseDocument.getIdentifier(), documentId);
        Assert.assertEquals(responseDocument.getContentType(), "application/pdf");
        Assert.assertEquals(responseDocument.getContentLength(), 123);
    }

    @Test
    public void testGetTextFromDestinationBucket() throws AbstractApplicationException {
        // Arrange
        DocumentExtractManager dxm = createTestSubject();
        final String documentID = UUID.randomUUID().toString();
        mockDestinationTextObject(dxm, documentID);

        CanonicalDocument document = CanonicalDocument.builder().withIdentifier(documentID).build();
        CanonicalRequest request = CanonicalRequest.builder().withMethod("GET").withCanonicalDocument(document).build();

        // Act
        CanonicalResponse response = dxm.getTextFromDestinationBucket(request);

        // Assert
        Assert.assertNotNull(response);
        Assert.assertEquals(response.getResult(), HttpStatus.SC_OK);
        Assert.assertEquals(response.getDocumentCount(), 1);
    }

    @Test
    public void testGetJsonFromDestinationBucket() throws AbstractApplicationException {
        // Arrange
        DocumentExtractManager dxm = createTestSubject();
        final String documentID = UUID.randomUUID().toString();
        mockDestinationJsonObject(dxm, documentID);

        CanonicalDocument document = CanonicalDocument.builder().withIdentifier(documentID).build();
        CanonicalRequest request = CanonicalRequest.builder().withMethod("GET").withCanonicalDocument(document).build();

        // Act
        CanonicalResponse response = dxm.getJsonFromDestinationBucket(request);

        // Assert
        Assert.assertNotNull(response);
        Assert.assertEquals(response.getResult(), HttpStatus.SC_OK);
        Assert.assertEquals(response.getDocumentCount(), 1);
    }

    @Test
    public void testDeleteDocumentFromSourceBucket() throws AbstractApplicationException {
        // Arrange
        DocumentExtractManager dxm = createTestSubject();
        final String documentId = UUID.randomUUID().toString();

        CanonicalDocument document = CanonicalDocument.builder().withIdentifier(documentId).build();
        CanonicalRequest request = CanonicalRequest.builder().withMethod("DELETE").withCanonicalDocument(document).build();

        mockObjectMetadata(dxm, documentId);

        // Act
        CanonicalResponse response = dxm.deleteDocumentFromSourceBucket(request);

        // Assert
        Assert.assertEquals(response.getResult(), HttpStatus.SC_OK);
        Assert.assertEquals(response.getDocuments().size(), 1);
        Assert.assertEquals(response.getDocuments().get(0).getIdentifier(), documentId);
        Mockito.verify(dxm.getAmazonS3()).deleteObject(SOURCE_BUCKET_NAME, documentId);
    }

    // Submitting a valid document for text extraction returns a successful response
    @Test
    public void testValidSubmitDocumentForTextExtraction() throws InstanceValidationException {
        // Arrange
        DocumentExtractManager dxm = createTestSubject();
        final String documentId = UUID.randomUUID().toString();

        // the document start detection and analysis must return something to avoid and NPE
        mockStartDocumentTextDetection(dxm);
        mockStartDocumentTextAnalysis(dxm);

        CanonicalDocument document = CanonicalDocument.builder()
                .withIdentifier(documentId)
                .build();
        CanonicalRequest request = CanonicalRequest.builder()
                .withMethod("NewDocument")
                .withCanonicalDocument(document)
                .build();

        // Act
        CanonicalResponse response = dxm.submitDocumentForTextExtraction(request);

        // Assert
        Assert.assertEquals(response.getResult(), HttpStatus.SC_OK);
    }

    // TODO: add more complex tests
    // the following test method invokes only the simplest positive case
    @Test
    public void testOcrComplete() throws InstanceValidationException {
        // Arrange
        DocumentExtractManager dxm = createTestSubject();
        final String documentId = UUID.randomUUID().toString();
        final String jobId = UUID.randomUUID().toString();
        mockGetDocumentTextDetection(dxm);

        CanonicalDocument document = CanonicalDocument.builder()
                .withIdentifier(documentId)
                .withDocumentExtractStatus(DocumentExtractStatus.SUCCEEDED)
                .withJobId(jobId)
                .build();
        CanonicalRequest request = CanonicalRequest.builder()
                .withMethod("DocumentExtractComplete")
                .withCanonicalDocument(document)
                .build();

        // Act
        CanonicalResponse response = dxm.moveExtractedTextToDestination(request);

        // Assert
        Assert.assertEquals(response.getResult(), HttpStatus.SC_OK);
    }

    // ==============================================================================================
    //
    // ==============================================================================================
    private DocumentExtractManager createTestSubject() {
        AmazonS3 amazonS3 = Mockito.mock(AmazonS3.class);
        TextractClient textractClient = Mockito.mock(TextractClient.class);

        DocumentExtractManager subject = new DocumentExtractManager(
                "region1",
                SOURCE_BUCKET_NAME, DESTINATION_BUCKET_NAME,
                TEXTRACT_SERVICE_ROLE, STATUS_TOPIC_NAME,
                amazonS3,
                textractClient
        );

        return subject;
    }

    private void mockGeneratePresignedUrl(
            DocumentExtractManager dxm,
            final String bucket, final String identifier,
            final URL response) {
        AmazonS3 mockS3 = dxm.getAmazonS3();

        doReturn(response)
                .when(mockS3)
                .generatePresignedUrl(Mockito.eq(bucket), Mockito.eq(identifier), Mockito.any(Date.class));
    }

    // adds a response to the S3 mock object to return a populated response to a getObjectMetadata() call
    private void mockGetObjectMetadataAndGetObjectTagging(
            DocumentExtractManager dxm,
            ObjectMetadata objectMetadata) {
        AmazonS3 mockS3 = dxm.getAmazonS3();

        final String identifier = objectMetadata.getUserMetaDataOf(HeaderTags.METADATA_KEY_IDENTIFIER);
        doReturn(objectMetadata)
                .when(mockS3)
                .getObjectMetadata(Mockito.eq(SOURCE_BUCKET_NAME), Mockito.eq(identifier));

        List<Tag> tagSet = new ArrayList<>();
        if (objectMetadata.getUserMetaDataOf(HeaderTags.TAG_JOB_ID) != null)
            tagSet.add(new Tag(HeaderTags.TAG_JOB_ID, objectMetadata.getUserMetaDataOf(HeaderTags.TAG_JOB_ID)));
        if (objectMetadata.getUserMetaDataOf(HeaderTags.TAG_KEY_STATUS) != null)
            tagSet.add(new Tag(HeaderTags.TAG_KEY_STATUS, objectMetadata.getUserMetaDataOf(HeaderTags.TAG_KEY_STATUS)));

        GetObjectTaggingResult getResult = new GetObjectTaggingResult(tagSet);
        doReturn(getResult)
                .when(mockS3)
                .getObjectTagging(Mockito.any(GetObjectTaggingRequest.class));
    }

    // mocks putObject to return a result containing the given content type and length
    // on any call to putObject(PutObjectRequest)
    private void mockPutObject(
            DocumentExtractManager dxm,
            final String mockContentType,
            final Integer mockContentLength
    ){
        AmazonS3 mockS3 = dxm.getAmazonS3();

        PutObjectResult mockResult = new PutObjectResult();

        ObjectMetadata mockMetadata = new ObjectMetadata();
        if (mockContentType != null)
            mockMetadata.setContentType(mockContentType);
        if (mockContentLength != null)
            mockMetadata.setContentLength(mockContentLength);
        mockResult.setMetadata(mockMetadata);

        doReturn(mockResult)
                .when(mockS3)
                .putObject(Mockito.any(PutObjectRequest.class));
    }

    private void mockSourceDocumentObject(DocumentExtractManager dxm, final String documentId) {
        mockDestinationObject(dxm, SOURCE_BUCKET_NAME, documentId, "application/pdf");
    }

    private void mockDestinationTextObject(DocumentExtractManager dxm, final String documentId) {
        mockDestinationObject(dxm, DESTINATION_BUCKET_NAME, Utility.createTextResultId(documentId), "text/plain");
    }

    private void mockDestinationJsonObject(DocumentExtractManager dxm, final String documentId) {
        mockDestinationObject(dxm, DESTINATION_BUCKET_NAME, Utility.createJsonResultId(documentId), "application/json");
    }

    private void mockDestinationObject(DocumentExtractManager dxm, final String bucket, final String documentId, final String documentType) {
        S3Object s3Object = Mockito.mock(S3Object.class);
        final String documentContent = "document content " + documentId;

        ObjectMetadata objectMetadata = Mockito.mock(ObjectMetadata.class);
        S3ObjectInputStream inputStream = new S3ObjectInputStream(
                new ByteArrayInputStream(documentContent.getBytes()),
                new HttpRequestBase(){
                    @Override
                    public String getMethod() {
                        return "GET";
                    }
                }
        );

        Mockito.when(dxm.getAmazonS3().getObject(bucket, documentId)).thenReturn(s3Object);
        Mockito.when(s3Object.getObjectContent()).thenReturn(inputStream);

        Mockito.when(dxm.getAmazonS3().getObjectMetadata(bucket, documentId)).thenReturn(objectMetadata);
        Mockito.when(objectMetadata.getContentType()).thenReturn(documentType);
        Mockito.when(objectMetadata.getContentLength()).thenReturn((long)documentContent.length());
        Mockito.when(objectMetadata.getUserMetaDataOf(HeaderTags.TAG_KEY_STATUS)).thenReturn("New");

    }

    private void mockObjectMetadata(final DocumentExtractManager dxm, final String documentId) {
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentType("application/pdf");
        metadata.setContentLength(12345);
        Mockito.when(dxm.getAmazonS3().getObjectMetadata(SOURCE_BUCKET_NAME, documentId)).thenReturn(metadata);
    }

    private void mockStartDocumentTextDetection(final DocumentExtractManager dxm) {
        StartDocumentTextDetectionResponse textractResponse = StartDocumentTextDetectionResponse.builder()
                .jobId(UUID.randomUUID().toString())
                .build();

        Mockito.doReturn(textractResponse)
                .when(dxm.getTextractClient())
                .startDocumentTextDetection(Mockito.any(StartDocumentTextDetectionRequest.class));
    }

    private void mockStartDocumentTextAnalysis(final DocumentExtractManager dxm) {
        StartDocumentAnalysisResponse textractResponse = StartDocumentAnalysisResponse.builder()
                .jobId(UUID.randomUUID().toString())
                .build();

        Mockito.doReturn(textractResponse)
                .when(dxm.getTextractClient())
                .startDocumentAnalysis(Mockito.any(StartDocumentAnalysisRequest.class));
    }

    private void mockGetDocumentTextDetection(final DocumentExtractManager dxm) {
        Block textBlock = Block.builder().text("block001").blockType(BlockType.PAGE).build();
        GetDocumentTextDetectionResponse getTextDetectionResponse = GetDocumentTextDetectionResponse.builder()
                .blocks(textBlock)
                .build();

        Mockito.doReturn(getTextDetectionResponse)
                .when(dxm.getTextractClient()).getDocumentTextDetection(Mockito.any(GetDocumentTextDetectionRequest.class));
    }

    // create an ObjectMetadata instance with content
    private ObjectMetadata createObjectMetadata(
            final String identifier,
            final String fileName,
            final String contentType,
            final Integer contentLength,
            final DocumentExtractStatus status,
            final String jobId) {
        ObjectMetadata result = new ObjectMetadata();

        if (identifier != null)
            result.addUserMetadata(HeaderTags.METADATA_KEY_IDENTIFIER, identifier);
        if (fileName != null)
            result.addUserMetadata(HeaderTags.METADATA_KEY_FILE_NAME, fileName);
        if (contentType != null)
            result.setContentType(contentType);
        if (contentLength != null)
            result.setContentLength(contentLength);
        if (status != null)
            result.addUserMetadata(HeaderTags.TAG_KEY_STATUS, status.toString());
        if (jobId != null)
            result.addUserMetadata(HeaderTags.TAG_JOB_ID, jobId);

        return result;
    }

}