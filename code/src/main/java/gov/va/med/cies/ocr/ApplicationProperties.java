package gov.va.med.cies.ocr;

import java.io.IOException;
import java.util.Properties;

public class ApplicationProperties extends Properties {
    private static ApplicationProperties singleton;

    public static final String PRESIGNED_URL_EXPIRATION = "PresignedUrlExpiration";
    public static final String LARGE_FILE_THRESHOLD = "LargeFileThreshold";
    public static final String PDF_TEXT_CONTENT_THRESHOLD = "MinimumTextPercentageToSkipTextract";
    public static final String TEXTRACT_MODE = "TextractMode";

    static {
        singleton = new ApplicationProperties();
    }

    public static final ApplicationProperties getSingleton() {
        return singleton;
    }

    private ApplicationProperties() {
        try {
            this.load(ApplicationProperties.class.getResourceAsStream("/application.properties"));
        } catch (IOException e) {
            throw new ExceptionInInitializerError("Failed to load application properties");
        }
    }
}
