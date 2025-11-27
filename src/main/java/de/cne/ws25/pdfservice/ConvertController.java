package de.cne.ws25.pdfservice;

import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import de.cne.ws25.pdfservice.jobs.PdfJobMessage;
import de.cne.ws25.pdfservice.jobs.PdfJobPublisher;
import de.cne.ws25.pdfservice.storage.GcsStorageService;
import de.cne.ws25.pdfservice.storage.StoredFile;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
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
    private final Storage storage; // für Job-Status

    public ConvertController(
            GcsStorageService storageService,
            PdfJobPublisher jobPublisher,
            @Value("${app.bucket.output}") String outputBucket
    ) {
        this.storageService = storageService;
        this.jobPublisher = jobPublisher;
        this.outputBucket = outputBucket;
        this.storage = StorageOptions.getDefaultInstance().getService();
    }

    @PostMapping(value = "/convert", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Void> uploadAndEnqueue(@RequestParam("file") MultipartFile file) {
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

            // 5. Browser direkt auf Warteseite /job/{jobId} schicken
            String statusUrl = "/job/" + jobId;

            return ResponseEntity
                    .status(303) // See Other – Browser macht dann ein GET auf /job/{jobId}
                    .header(HttpHeaders.LOCATION, statusUrl)
                    .build();

        } catch (Exception e) {
            e.printStackTrace();
            // Bei Fehler normale 500er-Textantwort
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/job/{jobId}")
    public ResponseEntity<String> jobStatus(@PathVariable String jobId) {
        try {
            String outputObject = "jobs/" + jobId + "/output.pdf";

            BlobId blobId = BlobId.of(outputBucket, outputObject);
            Blob blob = storage.get(blobId);

            boolean exists = (blob != null && blob.exists());

            if (!exists) {
                // Noch nicht fertig: einfache „Warteseite“ mit Auto-Refresh
                String html = """
                        <!doctype html>
                        <html lang="de">
                        <head>
                          <meta charset="UTF-8">
                          <title>Job läuft noch</title>
                          <!-- alle 3 Sekunden neu laden -->
                          <meta http-equiv="refresh" content="3">
                        </head>
                        <body>
                          <h1>Job läuft noch</h1>
                          <p><strong>Job-ID:</strong> %s</p>
                          <p>Dein PDF wird noch erzeugt. Diese Seite aktualisiert sich automatisch.</p>
                          <p><a href="/job/%s">Manuell neu laden</a></p>
                          <p><a href="/">Neues Bild hochladen</a></p>
                        </body>
                        </html>
                        """.formatted(jobId, jobId);

                return ResponseEntity
                        .ok()
                        .contentType(MediaType.TEXT_HTML)
                        .body(html);
            }

            // PDF existiert – Link bauen
            String pdfUrl = String.format(
                    "https://storage.googleapis.com/%s/%s",
                    outputBucket,
                    outputObject
            );

            String html = """
                    <!doctype html>
                    <html lang="de">
                    <head>
                      <meta charset="UTF-8">
                      <title>PDF fertig</title>
                    </head>
                    <body>
                      <h1>PDF fertig</h1>
                      <p><strong>Job-ID:</strong> %s</p>
                      <p>Dein PDF ist jetzt verfügbar:</p>
                      <p><a href="%s" target="_blank">%s</a></p>
                      <p><a href="/">Neues Bild hochladen</a></p>
                    </body>
                    </html>
                    """.formatted(jobId, pdfUrl, pdfUrl);

            return ResponseEntity
                    .ok()
                    .contentType(MediaType.TEXT_HTML)
                    .body(html);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError()
                    .contentType(MediaType.TEXT_PLAIN)
                    .body("Fehler beim Lesen des Job-Status: " + e.getMessage());
        }
    }
}
