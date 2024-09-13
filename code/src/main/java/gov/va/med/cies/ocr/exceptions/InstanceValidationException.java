package gov.va.med.cies.ocr.exceptions;

import org.apache.http.HttpStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class InstanceValidationException extends BaseClientException {
    public int getHTTPResponseCode(){return HttpStatus.SC_BAD_REQUEST;};

    protected InstanceValidationException(String message) {
        super(message);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String context = null;
        private List<String> validationFailure = new ArrayList<>();

        public InstanceValidationException build() {
            StringBuilder messageBuilder = new StringBuilder(context == null ? "unknown" : context);
            if (messageBuilder.length() > 0)
                messageBuilder.append(System.lineSeparator());
            messageBuilder.append(validationFailure.stream().collect(Collectors.joining(System.lineSeparator())));
            return new InstanceValidationException(messageBuilder.toString());
        }

        public Builder withContext(final String context) {
            this.context = context;
            return this;
        }

        public Builder withValidationFailure(final String property, final String constraint) {
            validationFailure.add("Property " + property + " + does not meet constraint [" + constraint + "].");
            return this;
        }

        public boolean includesValidationFailures() {
            return validationFailure.size() > 0;
        }
    }
}
