package de.cne.ws25.pdfservice.jobs;

public record PdfJobMessage(
        String jobId,
        String inputBucket,
        String inputObject,
        String outputBucket,
        String type // z.B. "IMAGE_TO_PDF"
) {}

