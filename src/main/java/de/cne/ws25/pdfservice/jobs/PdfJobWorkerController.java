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
            String data = request.message.data;
            String json = new String(Base64.decodeBase64(data));
            PdfJobMessage job = objectMapper.readValue(json, PdfJobMessage.class);

            if (!"IMAGE_TO_PDF".equals(job.type())) {
                return ResponseEntity.ok("Job-Typ ignoriert: " + job.type());
            }

            String outputPath = imageToPdfService.convertImageObjectToPdf(
                    job.inputBucket(),
                    job.inputObject(),
                    job.outputBucket(),
                    job.jobId()
            );

            System.out.println("Job " + job.jobId() + " fertig. Output: " + outputPath);

            // wichtig: 2xx → Pub/Sub ist zufrieden
            return ResponseEntity.ok("OK");
        } catch (Exception e) {
            e.printStackTrace();
            // 5xx → Pub/Sub versucht es später nochmal
            return ResponseEntity.internalServerError()
                    .body("Fehler bei der Verarbeitung: " + e.getMessage());
        }
    }
}
