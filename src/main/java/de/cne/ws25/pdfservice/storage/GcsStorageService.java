package de.cne.ws25.pdfservice.storage;

import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@Service
public class GcsStorageService {

    private final Storage storage;
    private final String inputBucket;

    public GcsStorageService(
            Storage storage,
            @Value("${app.bucket.input}") String inputBucket
    ) {
        this.storage = storage;
        this.inputBucket = inputBucket;
    }

    public StoredFile store(MultipartFile file) throws Exception {

        String objectName = "uploads/" + UUID.randomUUID() + "-" + file.getOriginalFilename();

        BlobId blobId = BlobId.of(inputBucket, objectName);
        BlobInfo blobInfo = BlobInfo.newBuilder(blobId).build();

        storage.create(blobInfo, file.getBytes());

        return new StoredFile(inputBucket, objectName);
    }
}
