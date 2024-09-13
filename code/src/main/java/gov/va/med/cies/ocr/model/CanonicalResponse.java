package gov.va.med.cies.ocr.model;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.HttpStatus;

import java.util.ArrayList;
import java.util.List;

public class CanonicalResponse {
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private int result;
    private Exception exception;
    private List<CanonicalDocument> documents;

    public CanonicalResponse(final int result, final List<CanonicalDocument> documents, final Exception exception) {
        this.result = result;
        this.documents = new ArrayList<>(documents);
        this.exception = exception;
    }

    //
    public int getResult() {
        return result;
    }

    public Exception getException() {
        return exception;
    }

    public List<CanonicalDocument> getDocuments() {
        return documents;
    }

    // get the number of documents safely
    public int getDocumentCount() {
        return documents == null ? 0 : documents.size();
    }

    @Override
    public String toString() {
        return "CanonicalResponse{" +
                "result=" + result +
                ", exception=" + exception +
                ", reports=" + (documents == null || documents.size()==0 ? 0 : documents.size()) +
                '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private int result;
        private Exception exception;
        private List<CanonicalDocument> documents = new ArrayList<>();
        private String rawBody;

        private Builder() {
        }

        public Builder successWithReports(final List<CanonicalDocument> reports) {
            result = HttpStatus.SC_OK;
            this.documents.addAll(reports);
            this.rawBody = null;
            this.exception = null;
            return this;
        }

        public Builder successWithJSONBody(final Object obj) {
            try {
                successWithBody(objectMapper.writeValueAsString(obj));
            } catch (JsonProcessingException jpX) {
                result = HttpStatus.SC_INTERNAL_SERVER_ERROR;
                this.exception = jpX;
            }
            return this;
        }

        public Builder successWithBody(final String body) {
            result = HttpStatus.SC_BAD_REQUEST;
            this.documents.clear();
            this.exception = null;
            this.rawBody = body;
            return this;
        }

        public Builder success() {
            result = HttpStatus.SC_OK;
            this.exception = null;
            this.rawBody = null;
            return this;
        }

        public Builder serviceException(final Exception exception) {
            result = HttpStatus.SC_INTERNAL_SERVER_ERROR;
            this.exception = exception;
            this.documents.clear();
            return this;
        }

        public Builder genericBadRequestException(final Exception exception) {
            result = HttpStatus.SC_BAD_REQUEST;
            this.exception = exception;
            this.documents.clear();
            return this;
        }

        public Builder notFound() {
            result = HttpStatus.SC_NOT_FOUND;
            this.exception = null;
            this.documents.clear();
            return this;
        }

        public Builder forbiddenAccessException() {
            result = HttpStatus.SC_FORBIDDEN;
            this.exception = null;
            this.documents.clear();
            return this;
        }

        public Builder addDocument(CanonicalDocument canonicalDocument) {
            this.documents.add(canonicalDocument);
            return this;
        }

        public Builder removeDocument(CanonicalDocument canonicalDocument) {
            this.documents.remove(canonicalDocument);
            return this;
        }

        public Builder withRawBody(final String rawBody) {
            this.rawBody = rawBody;
            return this;
        }

        public CanonicalResponse build() {
            CanonicalResponse canonicalResponse = new CanonicalResponse(this.result, this.documents, this.exception);
            return canonicalResponse;
        }
    }
}
