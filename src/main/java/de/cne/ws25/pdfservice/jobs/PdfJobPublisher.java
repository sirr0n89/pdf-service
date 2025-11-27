package de.cne.ws25.pdfservice.jobs;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.core.ApiFuture;
import com.google.cloud.pubsub.v1.Publisher;
import com.google.protobuf.ByteString;
import com.google.pubsub.v1.PubsubMessage;
import com.google.pubsub.v1.TopicName;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.concurrent.ExecutionException;

@Service
public class PdfJobPublisher {

    private final String projectId;
    private final String topicId;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public PdfJobPublisher(
            @Value("${spring.cloud.gcp.project-id}") String projectIdEnv,
            @Value("${app.pubsub.topic}") String topicIdEnv
    ) {
        this.projectId = projectIdEnv;   // z.B. "cne-ws25"
        this.topicId = topicIdEnv;       // z.B. "pdf-jobs"
    }

    public void publish(PdfJobMessage job) {
        TopicName topicName = TopicName.of(projectId, topicId);

        Publisher publisher = null;
        try {
            // Publisher erzeugen
            publisher = Publisher.newBuilder(topicName).build();

            // Job als JSON serialisieren
            String json = objectMapper.writeValueAsString(job);
            ByteString data = ByteString.copyFromUtf8(json);

            PubsubMessage message = PubsubMessage.newBuilder()
                    .setData(data)
                    .build();

            // Nachricht senden
            ApiFuture<String> messageIdFuture = publisher.publish(message);
            String messageId = messageIdFuture.get();
            System.out.println("Job an Pub/Sub gesendet, Message ID: " + messageId);

        } catch (IOException | InterruptedException | ExecutionException e) {
            throw new RuntimeException("Fehler beim Senden an Pub/Sub", e);
        } finally {
            if (publisher != null) {
                publisher.shutdown();
            }
        }
    }
}
