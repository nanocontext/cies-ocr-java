package gov.va.med.cies.ocr;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.UUID;

public class DocumentLifecycleIntegrationTest {
    private final Logger logger = LoggerFactory.getLogger(DocumentExtractManagerTest.class);

    @Test
    public void test() throws Exception {
        final String loadBalancerHost = System.getProperty("LOAD_BALANCER_HOST");
        logger.info("ALB host is {}", loadBalancerHost);

        final String identifier = UUID.randomUUID().toString();
        final URL hostUrl = new URL("http", loadBalancerHost, 80, "/");
        DocumentProcessor documentProcessor = new DocumentProcessor(identifier, hostUrl, getFileOnClasspath("PET-CT1.pdf"));
        DocumentProcessorResult result = documentProcessor.call();
        logger.info("Result is [{}]", result);
    }

    private File getFileOnClasspath(String fileName) throws IOException
    {
        ClassLoader classLoader = getClass().getClassLoader();
        URL resource = classLoader.getResource(fileName);

        if (resource == null) {
            throw new IllegalArgumentException("file is not found!");
        } else {
            return new File(resource.getFile());
        }
    }
}
