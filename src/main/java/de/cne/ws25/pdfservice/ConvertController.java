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
import java.util.List;

import java.util.UUID;

@RestController
public class ConvertController {

    private final GcsStorageService storageService;
    private final PdfJobPublisher jobPublisher;
    private final String outputBucket;
    private final Storage storage;

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
    public ResponseEntity<Void> uploadAndEnqueue(@RequestParam("file") MultipartFile[] files) {
        System.out.println("### ConvertController LIVE VERSION (multi-image) ###");
        try {
            if (files == null || files.length == 0) {
                return ResponseEntity.badRequest().build();
            }

            // 1. Job-ID erzeugen (brauchen wir für Status /job/{id}, nicht fürs Uploaden)
            String jobId = UUID.randomUUID().toString();

            // 2. Alle Dateien ins Input-Bucket laden
            List<String> objectNames = new java.util.ArrayList<>();
            String inputBucket = null;

            for (MultipartFile file : files) {
                if (file.isEmpty()) continue;

                StoredFile stored = storageService.store(file);
                objectNames.add(stored.objectName());
                if (inputBucket == null) {
                    inputBucket = stored.bucket();
                }
            }

            if (objectNames.isEmpty()) {
                return ResponseEntity.badRequest().build();
            }

            // 3. Job für Worker bauen: Bucket + Liste von Objekten
            PdfJobMessage job = new PdfJobMessage(
                    jobId,
                    inputBucket,
                    objectNames,     // alle Bilder
                    outputBucket,
                    "IMAGE_TO_PDF"
            );

            jobPublisher.publish(job);

            // 4. Redirect auf /job/{jobId}
            String statusUrl = "/job/" + jobId;
            return ResponseEntity
                    .status(303)
                    .header(HttpHeaders.LOCATION, statusUrl)
                    .build();

        } catch (Exception e) {
            e.printStackTrace();
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
                // Warten-Ansicht
                String html = """
                        <!doctype html>
                        <html lang="de">
                        <head>
                          <meta charset="UTF-8">
                          <title>CNSE Convert – PDF wird erstellt</title>
                          <link rel="stylesheet" href="/css/style.css">
                          <link rel="stylesheet" href="https://fonts.googleapis.com/css?family=Roboto:300,400,500">
                          <link rel="stylesheet" href="https://fonts.googleapis.com/icon?family=Material+Icons">
                          <!-- alle 3 Sekunden neu laden -->
                          <meta http-equiv="refresh" content="3">
                        </head>
                        <body>
                          <div class="page-wrapper">
                            <header class="page-header">
                              <div class="brand">CNSE Convert</div>
                              <div class="subtitle">Your PDF is being prepared…</div>
                            </header>

                            <main>
                              <div class="card center">
                                <div class="job-id">Job-ID: %s</div>
                                <p>Dein Bild wurde hochgeladen und wird nun im Hintergrund in ein PDF konvertiert.</p>
                                <p>Diese Seite aktualisiert sich automatisch, sobald das PDF fertig ist.</p>
                                <div class="spinner"></div>
                                <div class="links">
                                  <a class="btn-link" href="/job/%s">Manuell neu laden</a>
                                  ·
                                  <a class="btn-link" href="/">Neues Bild hochladen</a>
                                </div>
                              </div>
                            </main>
                          </div>
                        </body>
                        </html>
                        """.formatted(jobId, jobId);

                return ResponseEntity
                        .ok()
                        .contentType(MediaType.TEXT_HTML)
                        .body(html);
            }

            // Fertig-Ansicht
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
                      <title>CNSE Convert – PDF fertig</title>
                      <link rel="stylesheet" href="/css/style.css">
                      <link rel="stylesheet" href="https://fonts.googleapis.com/css?family=Roboto:300,400,500">
                      <link rel="stylesheet" href="https://fonts.googleapis.com/icon?family=Material+Icons">
                    </head>
                    <body>
                      <div class="page-wrapper">
                        <header class="page-header">
                          <div class="brand">CNSE Convert</div>
                          <div class="subtitle">Your PDF is ready.</div>
                        </header>

                        <main>
                          <div class="card center">
                            <div class="job-id">Job-ID: %s</div>
                            <p>Dein PDF wurde erfolgreich erstellt und steht jetzt zum Download bereit.</p>
                            <p>
                              <a class="primary-btn" href="%s" target="_blank">
                                <span class="material-icons" style="font-size:18px;">picture_as_pdf</span>
                                PDF öffnen
                              </a>
                            </p>
                            <div class="links">
                              <a class="btn-link" href="/">Neues Bild hochladen</a>
                            </div>
                          </div>
                        </main>
                      </div>
                    </body>
                    </html>
                    """.formatted(jobId, pdfUrl);

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
