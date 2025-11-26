package de.cne.ws25.pdfservice.jobs;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.pubsub.v1.Publisher;
import com.google.protobuf.ByteString;
import com.google.pubsub.v1.ProjectTopicName;
import com.google.pubsub.v1.PubsubMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class PdfJobPublisher {

    private final String projectId;
    private final String topicId;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public PdfJobPublisher(
            @Value("${spring.cloud.gcp.project-id}") String projectId,
            @Value("${app.pubsub.topic}") String topicId
    ) {
        this.projectId = projectId;
        this.topicId = topicId;
    }

    // ⭐️ WICHTIG! Diese Methode fehlt bei dir bisher.
    public void publish(PdfJobMessage job) throws Exception {

        ProjectTopicName topicName = ProjectTopicName.of(projectId, topicId);

        Publisher publisher = Publisher.newBuilder(topicName).build();

        try {
            String json = objectMapper.writeValueAsString(job);
            ByteString data = ByteString.copyFromUtf8(json);

            PubsubMessage message = PubsubMessage.newBuilder()
                    .setData(data)
                    .build();

            publisher.publish(message).get(); // wartet auf Bestätigung

        } finally {
            publisher.shutdown();
        }
    }
}
