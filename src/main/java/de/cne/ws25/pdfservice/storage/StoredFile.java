package de.cne.ws25.pdfservice.storage;

public record StoredFile(String bucket, String objectName) {

    public String gcsPath() {
        return "gs://" + bucket + "/" + objectName;
    }
}
