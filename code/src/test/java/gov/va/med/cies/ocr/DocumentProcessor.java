package gov.va.med.cies.ocr;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.*;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.FileEntity;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.entity.mime.content.StringBody;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonReader;
import java.io.*;
import java.net.URL;
import java.nio.file.Files;
import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 *
 */
public class DocumentProcessor implements Callable<DocumentProcessorResult> {
    private static final String STATUS_SUCCEEDED = "SUCCEEDED";
    public final static Duration STATUS_RETRY_DELAY = Duration.parse("PT5S");
    public final static int STATUS_RETRY = 60;
    public final static int POST_SOCKET_TIMEOUT_MILLIS = 5000;
    public final static int POST_CONNECT_TIMEOUT_MILLIS = 5000;
    public final static int POST_CONNECTIONREQUEST_TIMEOUT_MILLIS = 30000;

    private static Log LOGGER = LogFactory.getLog(DocumentProcessor.class);
    private final String identifier;
    private final URL hostUrl;
    private final File documentFile;
    private final CloseableHttpClient httpClient;

    private final AtomicReference<String> status = new AtomicReference<>(null);
    private final AtomicLong statusTime = new AtomicLong(0L);
    private final AtomicBoolean complete = new AtomicBoolean(false);

    /**
     *
     * @param identifier
     * @param hostUrl
     * @param documentFile
     */
    public DocumentProcessor(final String identifier, final URL hostUrl, final File documentFile) {
        if (identifier == null || hostUrl == null || documentFile == null)
            throw new IllegalArgumentException("Both hostUrl and documentFile must be non null");

        this.identifier = identifier;
        this.hostUrl = hostUrl;
        this.documentFile = documentFile;

        httpClient = HttpClients.createDefault();
    }

    public DocumentProcessorResult getResult() {
        return new DocumentProcessorResult(
                complete.get(),
                identifier,
                documentFile,
                statusTime.get(),
                status.get()
        );
    }

    @Override
    public DocumentProcessorResult call() throws Exception {
        processDocumentFile();
        return getResult();
    }

    // for each file:
    // post it to be submitted for text extraction
    //   handle rejection when the file is too big, get pre-signed URL and post there
    // head the status until it is successful
    // get the extracted text
    //   handle redirection result
    public void processDocumentFile() {
        try {
            // submit the document for text extraction
            final int postResult = postDocument();
            if (postResult >= 200 && postResult < 300) {
                // poll and wait for a change in document status
                final boolean headResult = waitForDocumentStatus();
                // if the status indicates successful then retrieve the text
                if (headResult) {
                    final InputStream responseBody = getText();
                    LOGGER.info(responseBody);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * @return
     * @throws IOException
     */
    private int postDocument()
            throws IOException {
        URL postDocumentUrl = new URL(hostUrl, createDocumentPath(identifier));
        HttpPost postDocumentMethod = new HttpPost(postDocumentUrl.toExternalForm());
        postDocumentMethod.addHeader("Content-Type", "application/pdf");
        FileInputStream bodyInputStream = null;
        try {
            HttpEntity body = new InputStreamEntity(Files.newInputStream(documentFile.toPath()));
            postDocumentMethod.setEntity(body);
        } catch (IOException ioX) {
            System.err.println("Error reading from " + documentFile.getAbsolutePath() + ", ignoring");
            return -1;
        }
        CloseableHttpResponse result = httpClient.execute(postDocumentMethod);
        int resultCode = result.getStatusLine().getStatusCode();

        if (resultCode == 202) {
            // the happy path result is a '202' Accepted
            LOGGER.info("Successfully submitted [" + documentFile.getAbsolutePath() + "] as document identifier [" + identifier + "]");
        } else if (resultCode == 413) {
            // the file is too long for ALB, need to POST directly to S3
            resultCode = putThroughPresignedURL();
        } else {
            // else, something broke
            throw new IOException("Failed to POST '" + documentFile.getAbsolutePath() + "' to OCR with response code " + result);
        }

        return resultCode;
    }

    private int postThroughPresignedURL()
            throws IOException {
        URL getPresignedURL = new URL(hostUrl, createPresignedURLPath(identifier) + "?method=POST");
        HttpGet getPresignedUrlMethod = new HttpGet(getPresignedURL.toExternalForm());
        HttpResponse result = httpClient.execute(getPresignedUrlMethod);

        int resultCode = result.getStatusLine().getStatusCode();
        if (resultCode == 200) {
            final JsonReader jsonReader = Json.createReader(result.getEntity().getContent());
            JsonObject root = jsonReader.readObject();

            final URL destinationUrl = new URL(root.getString("url"));

            LOGGER.info("destinationUrl = [" + destinationUrl + "]");
            final JsonObject presignedUrlFields = root.getJsonObject("fields");

            final String key = presignedUrlFields.getString("key");
            LOGGER.info("key = [" + key + "]");
            final String algorithm = presignedUrlFields.getString("x-amz-algorithm");
            LOGGER.info("algorithm = [" + algorithm + "]");
            final String credential = presignedUrlFields.getString("x-amz-credential");
            LOGGER.info("credential = [" + credential + "]");
            final String date = presignedUrlFields.getString("x-amz-date");
            LOGGER.info("date = [" + date + "]");
            final String token = presignedUrlFields.getString("x-amz-security-token");
            LOGGER.info("token = [" + token + "]");
            final String policy = presignedUrlFields.getString("policy");
            LOGGER.info("policy = [" + policy + "]");
            final String signature = presignedUrlFields.getString("x-amz-signature");
            LOGGER.info("signature = [" + signature + "]");

            // construct a POST request directly to S3 as multipart/form-data
            HttpPost filePost = new HttpPost(destinationUrl.toExternalForm());
            RequestConfig requestConfig = RequestConfig.custom()
                    .setSocketTimeout(POST_SOCKET_TIMEOUT_MILLIS)
                    .setConnectTimeout(POST_CONNECT_TIMEOUT_MILLIS)
                    .setConnectionRequestTimeout(POST_CONNECTIONREQUEST_TIMEOUT_MILLIS)
                    .build();
            filePost.setConfig(requestConfig);

            try (final InputStream inputStream = Files.newInputStream(documentFile.toPath())) {
                HttpEntity reqEntity = MultipartEntityBuilder.create()
                        //.setMode(HttpMultipartMode.RFC6532)
                        .addPart("Content-Type", new StringBody("application/pdf", ContentType.MULTIPART_FORM_DATA))
                        .addPart("key", new StringBody(key, ContentType.MULTIPART_FORM_DATA))
                        .addPart("algorithm", new StringBody(algorithm, ContentType.MULTIPART_FORM_DATA))
                        .addPart("AWSAccessKeyId", new StringBody(credential, ContentType.MULTIPART_FORM_DATA))
                        .addPart("x-amz-security-token", new StringBody(token, ContentType.MULTIPART_FORM_DATA))
                        .addPart("policy", new StringBody(policy, ContentType.MULTIPART_FORM_DATA))
                        .addPart("signature", new StringBody(signature, ContentType.MULTIPART_FORM_DATA))
                        //.addBinaryBody("file", new byte[]{0x01, 0x02, 0x03})
                        .addBinaryBody("file", inputStream, ContentType.create("application/pdf"), identifier)
                        .build();

                filePost.setEntity(reqEntity);

                try (CloseableHttpClient client = HttpClientBuilder.create().build()) {
                    LOGGER.info("Submitting [" + documentFile.getAbsolutePath() + "] as document identifier [" + identifier + "] to [" + destinationUrl + "]");
                    result = client.execute(filePost, response -> {
                        LOGGER.info("S3 submission result is " + response);
                        return response;
                    });
                }
                LOGGER.info("S3 submission result is " + result);
            }

        }

        return result.getStatusLine().getStatusCode();
    }

    private int putThroughPresignedURL()
            throws IOException {
        URL getPresignedURL = new URL(hostUrl, createPresignedURLPath(identifier) + "?method=PUT&contenttype=application/pdf");
        HttpGet getPresignedUrlMethod = new HttpGet(getPresignedURL.toExternalForm());
        LOGGER.info("Getting presigned URL from [" + getPresignedURL.toExternalForm() + "]");
        HttpResponse result = httpClient.execute(getPresignedUrlMethod);
        LOGGER.info("Presigned URL request result [" + result.getStatusLine() + "]");

        int resultCode = result.getStatusLine().getStatusCode();
        if (resultCode == 200) {
            String s3PutDestination = readToString(result.getEntity().getContent());
            LOGGER.info("before [" + s3PutDestination + "]");
            if (s3PutDestination.startsWith("\"") && s3PutDestination.endsWith("\""))
                s3PutDestination = s3PutDestination.substring(1, s3PutDestination.length() - 1);
            LOGGER.info("after [" + s3PutDestination + "]");

            // construct a PUT request directly to S3

            HttpEntity requestEntity = new FileEntity(documentFile);
            HttpPut put = new HttpPut(s3PutDestination);
            put.addHeader("Content-Type", "application/pdf");
            put.setEntity(requestEntity);
            HttpResponse putResponse = httpClient.execute(put);
            int putResponseCode = putResponse.getStatusLine().getStatusCode();
            if (putResponseCode < 200 || putResponseCode >= 300) {
                LOGGER.warn("Failed to PUT [" + identifier + "] response was [" + putResponseCode + "]");
                LOGGER.warn(putResponse.getStatusLine().toString());
                String errorMessage = readToString(putResponse.getEntity().getContent());
                LOGGER.warn(errorMessage);
            } else {
                LOGGER.info("PUT [" + identifier + "] successfully (" + putResponseCode + ")");
            }
            resultCode = putResponseCode;
        } else {
            LOGGER.warn("Unable to get a presigned URL");
        }

        return resultCode;
    }

    /**
     * @return
     */
    private boolean waitForDocumentStatus()
            throws IOException {
        String status = null;
        for (int retryCount = 0; retryCount < STATUS_RETRY; ++retryCount) {
            status = getDocumentStatus();
            if (STATUS_SUCCEEDED.equals(status))
                break;
            try {
                Thread.sleep(STATUS_RETRY_DELAY.toMillis());
            } catch (InterruptedException e) {
                break;
            }
        }

        return STATUS_SUCCEEDED.equals(status);
    }

    private String getDocumentStatus() throws IOException {
        URL headDocumentUrl = new URL(hostUrl, createDocumentPath(identifier));
        String status = null;

        HttpHead headDocumentMethod = new HttpHead(headDocumentUrl.toExternalForm());
        LOGGER.info("Requesting status on [" + headDocumentUrl.toExternalForm() + "]");
        HttpResponse result = httpClient.execute(headDocumentMethod);
        int resultCode = result.getStatusLine().getStatusCode();
        if (resultCode >= 200 && resultCode < 300) {
            Header statusHeader = result.getFirstHeader("ocr-status");
            if (statusHeader != null) {
                status = statusHeader.getValue();
                LOGGER.info("Status is [" + status + "]");
            } else {
                LOGGER.warn("Status was not included in HEAD response for document [" + identifier + "]");
            }
        } else {
            LOGGER.warn("Unable to retrieve status for document [" + identifier + "], result was [" + result + "]");
        }

        return status;
    }

    /**
     * Get the extracted text
     * @return
     */
    private InputStream getText()
            throws IOException {
        InputStream result = null;
        URL getTextUrl = new URL(hostUrl, createTextPath(identifier));
        HttpGet getTextMethod = new HttpGet(getTextUrl.toExternalForm());
        getTextMethod.addHeader("Accept", "text/plain");
        LOGGER.info("Getting text on [" + getTextUrl.toExternalForm() + "]");
        HttpResponse getMethodResult = httpClient.execute(getTextMethod);
        int responseCode = getMethodResult.getStatusLine().getStatusCode();
        if (responseCode >= 200 && responseCode < 300) {
            result = getMethodResult.getEntity().getContent();
        }
        return result;
    }

    /**
     * Get the extracted text along with the entire Textract generated JSON
     * @return
     */
    private InputStream getExtractedJSON()
            throws IOException {
        InputStream result = null;
        URL getTextUrl = new URL(hostUrl, createTextPath(identifier));
        HttpGet getTextMethod = new HttpGet(getTextUrl.toExternalForm());
        getTextMethod.addHeader("Accept", "application/json");
        LOGGER.info("Getting JSON on [" + getTextUrl.toExternalForm() + "]");
        HttpResponse getMethodResult = httpClient.execute(getTextMethod);
        int resultCode = getMethodResult.getStatusLine().getStatusCode();
        if (resultCode >= 200 && resultCode < 300) {
            result = getMethodResult.getEntity().getContent();
        }
        return result;
    }

    /**
     * Reads the content of the InputStream into a String
     *
     * @param inputStream
     * @return
     */
    private String readToString(final InputStream inputStream) {
        String result = null;
        try (InputStreamReader inStreamReader = new InputStreamReader(inputStream)) {
            StringBuilder sb = new StringBuilder();
            char[] buffy = new char[4096];
            for (int read = inStreamReader.read(buffy); read >= 0; read = inStreamReader.read(buffy)) {
                sb.append(buffy, 0, read);
            }
            result = sb.toString();
        } catch (IOException ioX) {
            LOGGER.warn(ioX);
            throw new RuntimeException(ioX);
        }

        return result;
    }

    // =================================================================================================
    // There are three paths, one for the source document, one for large document submission and one to get the
    // extracted text
    // =================================================================================================
    private static String createDocumentPath(final String identifier) {
        return identifier;
    }

    private static String createPresignedURLPath(final String identifier) {
        return "presignedurl/" + identifier;
    }

    private static String createTextPath(final String identifier) {
        return "text/" + identifier;
    }
}
