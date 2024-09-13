package gov.va.med.cies.ocr.model;

public class ExtractedPdfText {
    private final String text;
    private final int pageCount;

    public ExtractedPdfText(String text, int pageCount) {
        this.text = text;
        this.pageCount = pageCount;
    }

    public String getText() {
        return text;
    }

    public int getPageCount() {
        return pageCount;
    }
}
