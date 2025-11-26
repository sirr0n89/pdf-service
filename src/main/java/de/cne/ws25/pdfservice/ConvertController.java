package de.cne.ws25.pdfservice;

import de.cne.ws25.pdfservice.jobs.PdfJobMessage;
import de.cne.ws25.pdfservice.jobs.PdfJobPublisher;
import de.cne.ws25.pdfservice.storage.GcsStorageService;
import de.cne.ws25.pdfservice.storage.StoredFile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.beans.factory.annotation.Value;

import java.util.UUID;

@RestController
public class ConvertController {

    private final GcsStorageService storageService;
    private final PdfJobPublisher jobPublisher;

    private final String outputBucket;

    public ConvertController(
            GcsStorageService storageService,
            PdfJobPublisher jobPublisher,
            @Value("${app.bucket.output}") String outputBucket
    ) {
        this.storageService = storageService;
        this.jobPublisher = jobPublisher;
        this.outputBucket = outputBucket;
    }

    @PostMapping("/convert")
    public ResponseEntity<?> uploadAndEnqueue(@RequestPart("file") MultipartFile file) {
        try {
            StoredFile stored = storageService.uploadInputFile(file);

            String jobId = UUID.randomUUID().toString();
            PdfJobMessage msg = new PdfJobMessage(
                    jobId,
                    stored.bucket(),
                    stored.objectName(),
                    outputBucket,
                    "IMAGE_TO_PDF"
            );

            jobPublisher.publishJob(msg);

            // Async-Response
            String response = """
                    Job angelegt.
                    jobId: %s
                    input: %s
                    outputBucket: %s
                    (Worker macht den Rest async über Pub/Sub)
                    """.formatted(jobId, stored.gsPath(), outputBucket);

            return ResponseEntity.accepted().body(response);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError()
                    .body("Fehler beim Anlegen des Jobs: " + e.getMessage());
        }
    }
}
