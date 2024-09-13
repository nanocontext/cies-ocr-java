package gov.va.med.cies.ocr;

import org.testng.Assert;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

public class ApplicationPropertiesTest {

    @Test
    public void testGetSingleton() {
        ApplicationProperties subject = ApplicationProperties.getSingleton();
        Assert.assertNotNull(subject);

        Assert.assertEquals(subject.getProperty(ApplicationProperties.PRESIGNED_URL_EXPIRATION), "120");
        Assert.assertEquals(subject.getProperty(ApplicationProperties.TEXTRACT_MODE), "DETECTION");
        Assert.assertEquals(subject.getProperty(ApplicationProperties.PDF_TEXT_CONTENT_THRESHOLD), "50");
        Assert.assertEquals(subject.getProperty(ApplicationProperties.LARGE_FILE_THRESHOLD), "1046528");
    }
}