package gov.va.med.cies.ocr;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.Test;

public class SubmitDocumentIntegrationTest {
    private final Logger logger = LoggerFactory.getLogger(DocumentExtractManagerTest.class);
    private final String albUrl = System.getenv("ALB_URL");

    @Test
    public void test() {
        logger.info("ALB URL is {}", albUrl);
    }
}
