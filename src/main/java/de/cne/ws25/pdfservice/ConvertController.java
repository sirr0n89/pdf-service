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
                // WARTEN-ANSICHT
                String html = """
                    <!doctype html>
                    <html lang="de">
                    <head>
                      <meta charset="UTF-8">
                      <title>CNSE Convert  – PDF wird erstellt</title>
                      <link rel="stylesheet"
                            href="https://fonts.googleapis.com/css?family=Roboto:300,400,500">
                      <link rel="stylesheet"
                            href="https://fonts.googleapis.com/icon?family=Material+Icons">
                      <!-- alle 3 Sekunden neu laden -->
                      <meta http-equiv="refresh" content="3">
                      <style>
                        * { box-sizing: border-box; }
                        body {
                          margin: 0;
                          font-family: 'Roboto', sans-serif;
                          background: #f1f1f1;
                          color: #333;
                        }
                        .page-wrapper {
                          max-width: 960px;
                          margin: 0 auto;
                          padding: 40px 16px 80px;
                        }
                        .page-header {
                          text-align: center;
                          margin-bottom: 40px;
                        }
                        .brand {
                          font-size: 40px;
                          font-weight: 300;
                          letter-spacing: 2px;
                        }
                        .brand-icon {
                          font-size: 32px;
                          vertical-align: middle;
                          margin: 0 4px;
                        }
                        .subtitle {
                          margin-top: 8px;
                          font-size: 16px;
                          color: #666;
                        }
                        .card {
                          background: #fff;
                          border-radius: 8px;
                          box-shadow: 0 3px 10px rgba(0,0,0,0.16);
                          padding: 32px 24px;
                        }
                        .center { text-align: center; }
                        .job-id {
                          font-size: 14px;
                          color: #777;
                          margin-bottom: 8px;
                        }
                        .spinner {
                          width: 40px;
                          height: 40px;
                          border-radius: 50%%;
                          border: 3px solid #ddd;
                          border-top-color: #3f51b5;
                          animation: spin 1s linear infinite;
                          margin: 20px auto;
                        }
                        @keyframes spin {
                          to { transform: rotate(360deg); }
                        }
                        .links {
                          margin-top: 24px;
                        }
                        .btn-link {
                          display: inline-block;
                          margin: 0 4px;
                          font-size: 13px;
                          color: #3f51b5;
                          text-decoration: none;
                        }
                        .btn-link:hover {
                          text-decoration: underline;
                        }
                      </style>
                    </head>
                    <body>
                      <div class="page-wrapper">
                        <header class="page-header">
                          <div class="brand">
                            MC<span class="material-icons brand-icon">compare_arrows</span>nverter
                          </div>
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

            // FERTIG-ANSICHT
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
                  <link rel="stylesheet"
                        href="https://fonts.googleapis.com/css?family=Roboto:300,400,500">
                  <link rel="stylesheet"
                        href="https://fonts.googleapis.com/icon?family=Material+Icons">
                  <style>
                    * { box-sizing: border-box; }
                    body {
                      margin: 0;
                      font-family: 'Roboto', sans-serif;
                      background: #f1f1f1;
                      color: #333;
                    }
                    .page-wrapper {
                      max-width: 960px;
                      margin: 0 auto;
                      padding: 40px 16px 80px;
                    }
                    .page-header {
                      text-align: center;
                      margin-bottom: 40px;
                    }
                    .brand {
                      font-size: 40px;
                      font-weight: 300;
                      letter-spacing: 2px;
                    }
                    .brand-icon {
                      font-size: 32px;
                      vertical-align: middle;
                      margin: 0 4px;
                    }
                    .subtitle {
                      margin-top: 8px;
                      font-size: 16px;
                      color: #666;
                    }
                    .card {
                      background: #fff;
                      border-radius: 8px;
                      box-shadow: 0 3px 10px rgba(0,0,0,0.16);
                      padding: 32px 24px;
                    }
                    .center { text-align: center; }
                    .job-id {
                      font-size: 14px;
                      color: #777;
                      margin-bottom: 8px;
                    }
                    .primary-btn {
                      display: inline-flex;
                      align-items: center;
                      gap: 6px;
                      padding: 10px 20px;
                      border-radius: 4px;
                      border: none;
                      background: #3f51b5;
                      color: #fff;
                      text-decoration: none;
                      font-size: 14px;
                      font-weight: 500;
                      box-shadow: 0 2px 4px rgba(0,0,0,0.2);
                    }
                    .primary-btn:hover {
                      box-shadow: 0 3px 6px rgba(0,0,0,0.25);
                    }
                    .links {
                      margin-top: 24px;
                    }
                    .btn-link {
                      display: inline-block;
                      margin: 0 4px;
                      font-size: 13px;
                      color: #3f51b5;
                      text-decoration: none;
                    }
                    .btn-link:hover {
                      text-decoration: underline;
                    }
                  </style>
                </head>
                <body>
                  <div class="page-wrapper">
                    <header class="page-header">
                      <div class="brand">
                        MC<span class="material-icons brand-icon">compare_arrows</span>nverter
                      </div>
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
