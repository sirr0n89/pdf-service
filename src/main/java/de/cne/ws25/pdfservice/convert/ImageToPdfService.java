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
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Iterator;
import java.util.List;

@Service
public class ImageToPdfService {

    private static final int MAX_IMAGES = 20;                // hartes Limit an Seiten
    private static final long MAX_BYTES_PER_IMAGE = 10L * 1024 * 1024; // 10 MB
    private static final int MAX_DIMENSION = 2000;           // max Breite/Höhe in Pixel

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

        if (inputObjects.size() > MAX_IMAGES) {
            throw new IllegalArgumentException("Zu viele Bilder in einem Job (max. " + MAX_IMAGES + ")");
        }

        try (PDDocument doc = new PDDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            for (String objectName : inputObjects) {

                Blob blob = storage.get(inputBucket, objectName);
                if (blob == null) {
                    throw new IllegalArgumentException("Input object not found: " + objectName);
                }

                if (blob.getSize() > MAX_BYTES_PER_IMAGE) {
                    throw new IllegalArgumentException("Bild zu groß: " + objectName +
                            " (max. " + (MAX_BYTES_PER_IMAGE / (1024 * 1024)) + " MB)");
                }

                byte[] imageBytes = blob.getContent();

                try (ByteArrayInputStream in = new ByteArrayInputStream(imageBytes)) {
                    BufferedImage bufferedImage = readScaled(in, MAX_DIMENSION, MAX_DIMENSION);
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

    /**
     * Bild lesen und direkt runterskalieren, um Speicher zu sparen.
     */
    private BufferedImage readScaled(ByteArrayInputStream in, int maxWidth, int maxHeight) throws Exception {
        try (ImageInputStream iis = ImageIO.createImageInputStream(in)) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
            if (!readers.hasNext()) {
                return null;
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(iis, true, true);

                int width = reader.getWidth(0);
                int height = reader.getHeight(0);

                // für kleine Bilder: direkt lesen
                if (width <= maxWidth && height <= maxHeight) {
                    return reader.read(0);
                }

                // Downsampling-Faktor bestimmen
                double scale = Math.min(
                        (double) maxWidth / width,
                        (double) maxHeight / height
                );
                int subsampling = (int) Math.ceil(1.0 / scale);
                if (subsampling < 1) subsampling = 1;

                ImageReadParam param = reader.getDefaultReadParam();
                param.setSourceSubsampling(subsampling, subsampling, 0, 0);

                return reader.read(0, param);
            } finally {
                reader.dispose();
            }
        }
    }
}
