package gov.va.med.cies.ocr;

import java.io.File;

public class DocumentProcessorResult {
    final boolean complete;
    final String identifier;
    final File file;
    final long statusTime;
    final String status;

    public DocumentProcessorResult(final boolean complete, final String identifier, final File file, final long statusTime, final String status) {
        this.complete = complete;
        this.identifier = identifier;
        this.file = file;
        this.statusTime = statusTime;
        this.status = status;
    }

    public boolean isComplete() {
        return complete;
    }

    public String getIdentifier() {
        return identifier;
    }

    public File getFile() {
        return file;
    }

    public long getStatusTime() {
        return statusTime;
    }

    public String getStatus() {
        return status;
    }

    @Override
    public String toString() {
        return "DocumentProcessorResult{" +
                "complete=" + complete +
                ", identifier='" + identifier + '\'' +
                ", file=" + file +
                ", statusTime=" + statusTime +
                ", status='" + status + '\'' +
                '}';
    }
}
