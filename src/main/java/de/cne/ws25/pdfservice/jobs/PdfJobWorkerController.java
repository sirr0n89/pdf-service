package de.cne.ws25.pdfservice.jobs;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.cne.ws25.pdfservice.convert.ImageToPdfService;
import org.apache.commons.codec.binary.Base64;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
public class PdfJobWorkerController {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ImageToPdfService imageToPdfService;

    public PdfJobWorkerController(ImageToPdfService imageToPdfService) {
        this.imageToPdfService = imageToPdfService;
    }

    @PostMapping("/pubsub/push")
    public ResponseEntity<String> handlePubSubPush(@RequestBody PubSubPushRequest request) {
        try {
            System.out.println(">>> Pub/Sub Push empfangen");
            System.out.println(">>> Raw message: " + request);

            String data = request.message.data;
            String json = new String(Base64.decodeBase64(data));
            System.out.println(">>> Decoded JSON: " + json);

            PdfJobMessage job = objectMapper.readValue(json, PdfJobMessage.class);
            System.out.println(">>> Job geladen: " + job);

            if (!"IMAGE_TO_PDF".equals(job.type())) {
                System.out.println(">>> Job-Typ ignoriert: " + job.type());
                return ResponseEntity.ok("Job-Typ ignoriert: " + job.type());
            }

            String outputPath = imageToPdfService.convertImageObjectsToPdf(
                    job.inputBucket(),
                    job.inputObjects(),
                    job.outputBucket(),
                    job.jobId()
            );

            System.out.println(">>> Job " + job.jobId() + " fertig. Output: " + outputPath);

            return ResponseEntity.ok("OK");
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError()
                    .body("Fehler bei der Verarbeitung: " + e.getMessage());
        }
    }
}
