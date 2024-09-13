package gov.va.med.cies.ocr.model;

import com.amazonaws.util.StringInputStream;
import gov.va.med.cies.ocr.DocumentExtractStatus;
import gov.va.med.cies.ocr.Utility;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.util.Arrays;

/**
 *
 */
public class CanonicalDocument {
    final String identifier;
    final String filename;
    final String contentType;
    final Integer contentLength;
    final String body;
    final InputStream bodyStream;
    final boolean bodyIsBase64Encoded;
    final DocumentExtractStatus documentExtractStatus;
    final String jobId;

    private CanonicalDocument(
            String identifier, String filename,
            String contentType, Integer contentLength,
            String body, InputStream bodyStream, boolean bodyIsBase64Encoded,
            DocumentExtractStatus documentExtractStatus,
            String jobId) {
        this.identifier = identifier;
        this.filename = filename;
        this.contentType = contentType;
        this.contentLength = contentLength;
        this.body = body;
        this.bodyStream = bodyStream;
        this.bodyIsBase64Encoded = bodyIsBase64Encoded;
        this.documentExtractStatus = documentExtractStatus;
        this.jobId = jobId;
    }

    public String getIdentifier() {
        return identifier;
    }

    public String getFilename() {
        return filename;
    }

    public String getContentType() {
        return contentType;
    }

    public Integer getContentLength() {
        return contentLength;
    }

    public boolean hasBody() {
        return body != null || bodyStream != null;
    }

    public String getBody() throws IOException {
        if (body != null) {
            return body;
        } else if (bodyStream != null){
            try (InputStreamReader isReader = new InputStreamReader(bodyStream)) {
                StringBuilder sb = new StringBuilder();
                char[] buffy = new char[2048];
                for (int charRead = isReader.read(buffy); charRead >= 0; charRead = isReader.read(buffy)) {
                    sb.append(Arrays.copyOfRange(buffy, 0, charRead));
                }
                return sb.toString();
            }
        } else {
            return null;
        }
    }

    public InputStream getBodyStream() throws UnsupportedEncodingException {
        if (bodyStream != null) {
            return bodyStream;
        } else if (body != null) {
            return new StringInputStream(body);
        } else {
            return null;
        }
    }

    public boolean isBodyIsBase64Encoded() {
        return bodyIsBase64Encoded;
    }

    public DocumentExtractStatus getDocumentExtractStatus() {
        return documentExtractStatus;
    }

    public String getJobId() {
        return jobId;
    }

    @Override
    public String toString() {
        return "CanonicalDocument{" +
                "identifier='" + identifier + '\'' +
                ", filename='" + filename + '\'' +
                ", contentType='" + contentType + '\'' +
                ", contentLength=" + contentLength +
                ", documentExtractStatus=" + documentExtractStatus +
                ", jobId=" + jobId +
                ", body='" + (body == null ? "null" : "not null") + '\'' +
                ", bodyStream=" + (bodyStream == null ? "null" : "not null") +
                ", bodyIsBase64Encoded=" + bodyIsBase64Encoded +
                "}";
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String identifier;
        private String filename;
        private String contentType;
        private Integer contentLength;
        private String body;
        private InputStream bodyStream;
        private boolean bodyIsBase64Encoded;
        private DocumentExtractStatus documentExtractStatus;
        private String jobId;

        public Builder() {
        }

        public Builder with(final CanonicalDocument other) {
            this.identifier = other.identifier;
            this.filename = other.filename;
            this.contentType = other.contentType;
            this.contentLength = other.contentLength;
            this.body = other.body;
            this.bodyStream = other.bodyStream;
            this.bodyIsBase64Encoded = other.bodyIsBase64Encoded;
            this.documentExtractStatus = other.documentExtractStatus;
            this.jobId = other.jobId;

            return this;
        }

        // copies the content of the given CanonicalDocument to this Builder,
        // without the body (body, bodyStream and bodyIsBase64Encoded fields)
        public Builder withMetadata(final CanonicalDocument other) {
            this.identifier = other.identifier;
            this.filename = other.filename;
            this.contentType = other.contentType;
            this.contentLength = other.contentLength;
            this.documentExtractStatus = other.documentExtractStatus;
            this.jobId = other.jobId;

            return this;
        }

        public Builder withFilenameDefaultIfNull() {
            if (this.filename == null || this.filename.length() == 0)
                withFilename(this.identifier);
            return this;
        }

        public Builder withContentTypeDefaultIfNull() {
            if (contentType == null || contentType.isEmpty() && filename != null) {
                contentType = Utility.getMimeType(filename);
            }
            return this;
        }

        public Builder withStatusDefaultIfNull() {
            if (documentExtractStatus == null) {
                documentExtractStatus = DocumentExtractStatus.New;
            }
            return this;
        }

        public Builder withIdentifier(String identifier) {
            this.identifier = identifier;
            return this;
        }

        public Builder withIdentifierIfNull(String identifier) {
            if (this.identifier == null)
                this.identifier = identifier;
            return this;
        }

        public Builder withFilename(String filename) {
            this.filename = filename;
            return this;
        }

        public Builder withContentType(String contentType) {
            this.contentType = contentType;
            return this;
        }

        public Builder withContentLength(Integer contentLength) {
            this.contentLength = contentLength;
            return this;
        }

        public Builder withBody(String body) {
            this.body = body;
            return this;
        }

        public Builder withBodyStream(InputStream bodyStream) {
            this.bodyStream = bodyStream;
            return this;
        }

        public Builder withBodyIsBase64Encoded(boolean bodyIsBase64Encoded) {
            this.bodyIsBase64Encoded = bodyIsBase64Encoded;
            return this;
        }

        public Builder withDocumentExtractStatus(DocumentExtractStatus documentExtractStatus) {
            this.documentExtractStatus = documentExtractStatus;
            return this;
        }

        public Builder withJobId(String jobId) {
            this.jobId = jobId;
            return this;
        }

        public CanonicalDocument build() {
            return new CanonicalDocument(identifier, filename, contentType, contentLength, body, bodyStream, bodyIsBase64Encoded, documentExtractStatus, jobId);
        }
    }
}
