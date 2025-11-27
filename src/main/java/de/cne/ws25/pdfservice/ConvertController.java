package de.cne.ws25.pdfservice;

import de.cne.ws25.pdfservice.jobs.PdfJobMessage;
import de.cne.ws25.pdfservice.jobs.PdfJobPublisher;
import de.cne.ws25.pdfservice.storage.GcsStorageService;
import de.cne.ws25.pdfservice.storage.StoredFile;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

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

    @PostMapping(value = "/convert", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<String> uploadAndEnqueue(@RequestParam("file") MultipartFile file) {
        System.out.println("### ConvertController LIVE VERSION ###");
        try {
            // 1. Upload ins Input-Bucket
            StoredFile stored = storageService.store(file);

            // 2. Job-ID erzeugen
            String jobId = UUID.randomUUID().toString();

            // 3. Job-Nachricht bauen
            PdfJobMessage job = new PdfJobMessage(
                    jobId,
                    stored.bucket(),       // inputBucket
                    stored.objectName(),   // inputObject
                    outputBucket,          // outputBucket
                    "IMAGE_TO_PDF"         // type
            );

            // 4. In Pub/Sub Topic schicken
            jobPublisher.publish(job);

            // 5. Pfad der späteren PDF (muss zum Worker passen!)
            String outputObject = "jobs/" + jobId + "/output.pdf";

            // einfacher (potentiell öffentlicher) GCS-Link
            String pdfUrl = String.format(
                    "https://storage.googleapis.com/%s/%s",
                    outputBucket,
                    outputObject
            );

            // 6. HTML-Antwort mit Link zurückgeben
            String html = """
                    <!doctype html>
                    <html lang="de">
                    <head>
                      <meta charset="UTF-8">
                      <title>Job angelegt</title>
                    </head>
                    <body>
                      <h1>Job angelegt</h1>
                      <p><strong>Job-ID:</strong> %s</p>
                      <p>Dein PDF wird im Hintergrund erzeugt.</p>
                      <p>Sobald die Verarbeitung fertig ist, ist es hier erreichbar:</p>
                      <p><a href="%s" target="_blank">%s</a></p>
                      <p><a href="/">Neues Bild hochladen</a></p>
                    </body>
                    </html>
                    """.formatted(jobId, pdfUrl, pdfUrl);

            return ResponseEntity
                    .accepted()
                    .contentType(MediaType.TEXT_HTML)
                    .body(html);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError()
                    .contentType(MediaType.TEXT_PLAIN)
                    .body("Fehler beim Anlegen des Jobs: " + e.getMessage());
        }
    }
}
