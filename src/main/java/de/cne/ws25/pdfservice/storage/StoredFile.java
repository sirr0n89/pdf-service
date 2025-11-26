package de.cne.ws25.pdfservice.storage;

public record StoredFile(
        String bucket,
        String objectName,
        String gsPath
) {}

