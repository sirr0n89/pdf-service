package de.cne.ws25.pdfservice.jobs;

import java.util.List;

public record PdfJobMessage(
        String jobId,
        String inputBucket,
        List<String> inputObjects, // mehrere Objekte
        String outputBucket,
        String type               // z.B. "IMAGE_TO_PDF"
) {}
