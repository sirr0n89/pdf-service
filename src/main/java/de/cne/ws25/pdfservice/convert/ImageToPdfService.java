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

@Service
public class ImageToPdfService {

    private final Storage storage = StorageOptions.getDefaultInstance().getService();

    public String convertImageObjectToPdf(
            String inputBucket,
            String inputObject,
            String outputBucket,
            String jobId
    ) throws Exception {

        Blob blob = storage.get(inputBucket, inputObject);
        if (blob == null) {
            throw new IllegalArgumentException("Input object not found: " + inputObject);
        }

        byte[] imageBytes = blob.getContent();

        try (PDDocument doc = new PDDocument();
             ByteArrayInputStream in = new ByteArrayInputStream(imageBytes);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            // 🔁 Bildformat automatisch erkennen (PNG, JPEG, …)
            BufferedImage bufferedImage = ImageIO.read(in);
            if (bufferedImage == null) {
                throw new IllegalArgumentException(
                        "Unsupported or unreadable image format for object: " + inputObject
                );
            }

            PDRectangle pageSize = PDRectangle.A4;
            PDPage page = new PDPage(pageSize);
            doc.addPage(page);

            // LosslessFactory kann PNG/JPEG usw. verarbeiten
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
