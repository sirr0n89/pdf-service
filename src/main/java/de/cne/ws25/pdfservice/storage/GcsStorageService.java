package de.cne.ws25.pdfservice.storage;

import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

@Service
public class GcsStorageService {

    private final Storage storage;
    private final String inputBucket;

    public GcsStorageService(@Value("${app.bucket.input}") String inputBucket) {
        this.storage = StorageOptions.getDefaultInstance().getService();
        this.inputBucket = inputBucket;
    }

    public StoredFile uploadInputFile(MultipartFile file) throws IOException {
        String ext = "";
        String original = file.getOriginalFilename();
        if (original != null && original.contains(".")) {
            ext = original.substring(original.lastIndexOf('.'));
        }

        String objectName = "uploads/" + UUID.randomUUID() + ext;

        BlobId blobId = BlobId.of(inputBucket, objectName);
        BlobInfo blobInfo = BlobInfo.newBuilder(blobId)
                .setContentType(file.getContentType())
                .build();

        storage.create(blobInfo, file.getBytes());

        String gsPath = "gs://" + inputBucket + "/" + objectName;
        return new StoredFile(inputBucket, objectName, gsPath);
    }
}
