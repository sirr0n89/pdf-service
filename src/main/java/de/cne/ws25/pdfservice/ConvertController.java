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
    public ResponseEntity<String> uploadAndEnqueue(@RequestParam("file") MultipartFile file) {
        try {
            // 1. Upload ins Input-Bucket
            StoredFile stored = storageService.store(file);

            // 2. Job-ID erzeugen
            String jobId = UUID.randomUUID().toString();

            // 3. Job-Nachricht bauen
            PdfJobMessage job = new PdfJobMessage(
                    jobId,
                    "IMAGE_TO_PDF",
                    stored.bucket(),
                    stored.objectName(),
                    outputBucket
            );

            // 4. In Pub/Sub Topic schicken
            jobPublisher.publish(job);

            // 5. 202 zurückgeben (async)
            String body = """
                    Job angelegt.
                    jobId: %s
                    input: %s
                    outputBucket: %s
                    (Worker macht den Rest async über Pub/Sub)
                    """.formatted(jobId, stored.gcsPath(), outputBucket);

            return ResponseEntity.accepted().body(body);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError()
                    .body("Fehler beim Anlegen des Jobs: " + e.getMessage());
        }
    }
}
