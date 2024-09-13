package gov.va.med.cies.ocr;

public class HeaderTags {
    // S3 prefixes user metadata with "x-amz-meta-"
    public static final String S3_METADATA_PREFIX = "x-amz-meta-";
    public static final String METADATA_KEY_FILE_NAME = "file-name";
    public static final String METADATA_KEY_IDENTIFIER = "identifier";
    public static final String METADATA_KEY_USER_ID = "user-id";
    public static final String METADATA_KEY_SITE_ID = "site-id";
    public static final String TAG_KEY_STATUS = "ocr-status";
    public static final String TAG_JOB_ID = "job-id";

    public static final String S3_METADATA_KEY_FILE_NAME = S3_METADATA_PREFIX + METADATA_KEY_FILE_NAME;
    public static final String S3_METADATA_KEY_USER_ID = S3_METADATA_PREFIX + METADATA_KEY_USER_ID;
    public static final String S3_METADATA_KEY_SITE_ID = S3_METADATA_PREFIX + METADATA_KEY_SITE_ID;
    public static final String S3_METADATA_KEY_STATUS = S3_METADATA_PREFIX + TAG_KEY_STATUS;
    public static final String S3_METADATA_KEY_JOB_ID = S3_METADATA_PREFIX + TAG_JOB_ID;
}
