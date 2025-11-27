package de.cne.ws25.pdfservice.convert;

import com.google.cloud.storage.*;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;

@Service
public class ImageToPdfService {

    private final Storage storage = StorageOptions.getDefaultInstance().getService();

    public String convertImageObjectsToPdf(
            String inputBucket,
            List<String> inputObjects,
            String outputBucket,
            String jobId
    ) throws Exception {

        if (inputObjects == null || inputObjects.isEmpty()) {
            throw new IllegalArgumentException("Keine Input-Objekte vorhanden");
        }

        try (PDDocument doc = new PDDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            for (String objectName : inputObjects) {

                Blob blob = storage.get(inputBucket, objectName);
                if (blob == null) {
                    throw new IllegalArgumentException("Input object not found: " + objectName);
                }

                byte[] imageBytes = blob.getContent();

                try (ByteArrayInputStream in = new ByteArrayInputStream(imageBytes)) {
                    BufferedImage bufferedImage = ImageIO.read(in);
                    if (bufferedImage == null) {
                        throw new IllegalArgumentException(
                                "Unsupported or unreadable image format for object: " + objectName
                        );
                    }

                    // neue Seite pro Bild
                    PDRectangle pageSize = PDRectangle.A4;
                    PDPage page = new PDPage(pageSize);
                    doc.addPage(page);

                    PDImageXObject image = LosslessFactory.createFromImage(doc, bufferedImage);

                    float pageWidth = pageSize.getWidth();
                    float pageHeight = pageSize.getHeight();

                    float imgWidth = image.getWidth();
                    float imgHeight = image.getHeight();
                    float scale = Math.min(pageWidth / imgWidth, pageHeight / imgHeight);

                    float drawWidth = imgWidth * scale;
                    float drawHeight = imgHeight * scale;

                    float x = (pageWidth - drawWidth) / 2;
                    float y = (pageHeight - drawHeight) / 2;

                    try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                        cs.drawImage(image, x, y, drawWidth, drawHeight);
                    }
                }
            }

            doc.save(out);

            String outputObject = "jobs/" + jobId + "/output.pdf";
            BlobId outId = BlobId.of(outputBucket, outputObject);
            BlobInfo outInfo = BlobInfo.newBuilder(outId)
                    .setContentType("application/pdf")
                    .build();

            storage.create(outInfo, out.toByteArray());

            return "gs://" + outputBucket + "/" + outputObject;
        }
    }
}
