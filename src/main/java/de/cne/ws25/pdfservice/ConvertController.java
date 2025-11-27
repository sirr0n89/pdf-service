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
                String html = """
                    <!doctype html>
                    <html lang="de">
                    <head>
                      <meta charset="UTF-8">
                      <title>PDF wird erstellt</title>
                      <link rel="stylesheet" href="https://code.getmdl.io/1.3.0/material.indigo-pink.min.css">
                      <script defer src="https://code.getmdl.io/1.3.0/material.min.js"></script>
                      <link rel="stylesheet" href="https://fonts.googleapis.com/css?family=Roboto:300,400,500">
                      <link rel="stylesheet" href="https://fonts.googleapis.com/icon?family=Material+Icons">
                      <!-- alle 3 Sekunden neu laden -->
                      <meta http-equiv="refresh" content="3">
                      <style>
                        body { font-family: 'Roboto', sans-serif; }
                        .page-container { max-width: 600px; margin: 40px auto; }
                        .center { text-align: center; }
                      </style>
                    </head>
                    <body class="mdl-color--grey-100">
                      <div class="page-container">
                        <div class="mdl-card mdl-shadow--4dp mdl-color--white" style="width: 100%%;">
                          <div class="mdl-card__title mdl-color--primary mdl-color-text--white">
                            <h2 class="mdl-card__title-text">PDF wird erstellt…</h2>
                          </div>
                          <div class="mdl-card__supporting-text center">
                            <p><strong>Job-ID:</strong> %s</p>
                            <p>Dein PDF wird verarbeitet. Diese Seite aktualisiert sich automatisch.</p>
                            <div class="mdl-spinner mdl-js-spinner is-active"></div>
                            <p style="margin-top: 16px;">
                              <a class="mdl-button mdl-js-button mdl-button--primary" href="/job/%s">
                                Manuell neu laden
                              </a>
                            </p>
                            <p>
                              <a class="mdl-button mdl-js-button" href="/">
                                Neues Bild hochladen
                              </a>
                            </p>
                          </div>
                        </div>
                      </div>
                    </body>
                    </html>
                    """.formatted(jobId, jobId);

                return ResponseEntity
                        .ok()
                        .contentType(MediaType.TEXT_HTML)
                        .body(html);
            }

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
                  <link rel="stylesheet" href="https://code.getmdl.io/1.3.0/material.indigo-pink.min.css">
                  <script defer src="https://code.getmdl.io/1.3.0/material.min.js"></script>
                  <link rel="stylesheet" href="https://fonts.googleapis.com/css?family=Roboto:300,400,500">
                  <link rel="stylesheet" href="https://fonts.googleapis.com/icon?family=Material+Icons">
                  <style>
                    body { font-family: 'Roboto', sans-serif; }
                    .page-container { max-width: 600px; margin: 40px auto; }
                    .center { text-align: center; }
                  </style>
                </head>
                <body class="mdl-color--grey-100">
                  <div class="page-container">
                    <div class="mdl-card mdl-shadow--4dp mdl-color--white" style="width: 100%%;">
                      <div class="mdl-card__title mdl-color--primary mdl-color-text--white">
                        <h2 class="mdl-card__title-text">PDF ist fertig</h2>
                      </div>
                      <div class="mdl-card__supporting-text center">
                        <p><strong>Job-ID:</strong> %s</p>
                        <p>Dein PDF ist jetzt verfügbar:</p>
                        <p>
                          <a class="mdl-button mdl-js-button mdl-button--raised mdl-button--accent"
                             href="%s" target="_blank">
                            <i class="material-icons">picture_as_pdf</i>
                            PDF öffnen
                          </a>
                        </p>
                        <p>
                          <a class="mdl-button mdl-js-button" href="/">
                            Neues Bild hochladen
                          </a>
                        </p>
                      </div>
                    </div>
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
