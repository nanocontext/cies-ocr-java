package gov.va.med.cies.ocr.model;

import com.amazonaws.services.s3.model.ExpectedBucketOwnerRequest;
import gov.va.med.cies.ocr.exceptions.InstanceValidationException;

public class CanonicalRequest {
    final String method;
    final CanonicalDocument canonicalDocument;

    private CanonicalRequest(String method, final CanonicalDocument canonicalDocument) {
        this.method = method;
        this.canonicalDocument = canonicalDocument;
    }

    public String getMethod() {
        return method;
    }

    public CanonicalDocument getCanonicalDocument() {
        return canonicalDocument;
    }

    @Override
    public String toString() {
        return "CanonicalRequest{" +
                "method='" + method + '\'' +
                ", canonicalDocument=" + canonicalDocument +
                '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String method;
        private CanonicalDocument canonicalDocument;

        public Builder withMethod(String method) {
            this.method = method;
            return this;
        }

        public Builder withCanonicalDocument(CanonicalDocument canonicalDocument) {
            this.canonicalDocument = canonicalDocument;
            return this;
        }

        public Builder with(CanonicalRequest canonicalRequest) {
            withMethod(canonicalRequest.method);
            withCanonicalDocument(canonicalRequest.canonicalDocument);

            return this;
        }

        public CanonicalRequest build() throws InstanceValidationException {
            InstanceValidationException.Builder xBuilder = InstanceValidationException.builder();
            xBuilder.withContext("Building CanonicalRequest");

            if (method == null || method.isEmpty())
                xBuilder.withValidationFailure("method", "null or empty");
            else {
                switch(method) {
                    case "POST":
                        if (canonicalDocument == null)
                            xBuilder.withValidationFailure("canonicalDocument", "null");
                        else {
                            if (!canonicalDocument.hasBody())
                                xBuilder.withValidationFailure("body", "null");
                        }
                        break;
                    case "PUT":
                        if (canonicalDocument == null)
                            xBuilder.withValidationFailure("canonicalDocument", "null");
                        else {
                            if (canonicalDocument.getIdentifier() == null || canonicalDocument.getIdentifier().isEmpty())
                                xBuilder.withValidationFailure("identifier", "null or empty");
                            if (!canonicalDocument.hasBody())
                                xBuilder.withValidationFailure("body", "null");
                        }
                        break;
                    case "GET":
                    case "DELETE":
                    case "HEAD":
                        if (canonicalDocument == null)
                            xBuilder.withValidationFailure("canonicalDocument", "null");
                        else {
                            if (canonicalDocument.getIdentifier() == null || canonicalDocument.getIdentifier().isEmpty())
                                xBuilder.withValidationFailure("identifier", "null or empty");
                        }
                        break;
                    default:
                        break;
                }
            }

            if (xBuilder.includesValidationFailures())
                throw xBuilder.build();

            return new CanonicalRequest(method, canonicalDocument);
        }

    }
}
