package gov.va.med.cies.ocr;

/**
 * The inconsistent capitalization of the values must not be changed.
 */
public enum DocumentExtractStatus {
    New,
    Submitted,
    SUCCEEDED,
    FAILED;

    public static DocumentExtractStatus of(String ocrStatus) {
        try {
            return ocrStatus == null ? New : DocumentExtractStatus.valueOf(ocrStatus);
        } catch (IllegalArgumentException iaX) {
            return New;
        }
    }
}
