package de.cne.ws25.pdfservice;

import de.cne.ws25.pdfservice.storage.GcsStorageService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class ConvertController {

    private final GcsStorageService storageService;

    public ConvertController(GcsStorageService storageService) {
        this.storageService = storageService;
    }

    @PostMapping(
            value = "/convert",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<?> uploadAndConvert(@RequestPart("file") MultipartFile file) {
        try {
            String gcsPath = storageService.uploadInputFile(file);
            return ResponseEntity.ok("Datei empfangen.\nGCS-Pfad: " + gcsPath);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError()
                    .body("Fehler beim Upload: " + e.getMessage());
        }
    }
}
