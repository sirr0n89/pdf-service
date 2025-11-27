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
    public ResponseEntity<Void> uploadAndEnqueue(@RequestParam("file") MultipartFile file) {
        System.out.println("### ConvertController LIVE VERSION ###");
        try {
            // 1. Datei ins Input-Bucket laden
            StoredFile stored = storageService.store(file);

            // 2. Job-ID erzeugen
            String jobId = UUID.randomUUID().toString();

            // 3. Pub/Sub-Job bauen und publishen
            PdfJobMessage job = new PdfJobMessage(
                    jobId,
                    stored.bucket(),       // inputBucket
                    stored.objectName(),   // inputObject
                    outputBucket,          // outputBucket
                    "IMAGE_TO_PDF"         // type
            );
            jobPublisher.publish(job);

            // 4. Browser direkt auf /job/{jobId} weiterleiten
            String statusUrl = "/job/" + jobId;
            return ResponseEntity
                    .status(303) // See Other
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
