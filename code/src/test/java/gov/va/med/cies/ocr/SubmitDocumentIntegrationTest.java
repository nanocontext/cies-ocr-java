package gov.va.med.cies.ocr;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.Test;

public class SubmitDocumentIntegrationTest {
    private final Logger logger = LoggerFactory.getLogger(DocumentExtractManagerTest.class);

    @Test
    public void test() {
        final String loadBalancerHost = System.getProperty("LOAD_BALANCER_HOST");
        final String junk = System.getProperty("JUNK");
        logger.info("ALB host is {}, JUNK is {}", loadBalancerHost, junk);
    }
}
