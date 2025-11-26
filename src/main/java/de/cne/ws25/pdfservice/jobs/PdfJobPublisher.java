package de.cne.ws25.pdfservice.jobs;

import com.google.cloud.pubsub.v1.Publisher;
import com.google.protobuf.ByteString;
import com.google.pubsub.v1.ProjectTopicName;
import com.google.pubsub.v1.PubsubMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class PdfJobPublisher {

    private final String projectId;
    private final String topicId;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public PdfJobPublisher(
            @Value("${spring.cloud.gcp.project-id:}") String projectIdEnv,
            @Value("${app.pubsub.topic}") String topicId
    ) {
        // wenn du spring.cloud.gcp.project-id nicht hast, kannst du auch
        // Project ID fest verdrahten oder aus Umgebungsvariable lesen
        this.projectId = projectIdEnv.isBlank() ? "cne-ws25" : projectIdEnv;
        this.topicId = topicId;
    }

    public void publishJob(PdfJobMessage message) throws Exception {
        ProjectTopicName topicName = ProjectTopicName.of(projectId, topicId);
        Publisher publisher = null;
        try {
            publisher = Publisher.newBuilder(topicName).build();

            String json = objectMapper.writeValueAsString(message);
            ByteString data = ByteString.copyFromUtf8(json);

            PubsubMessage pubsubMessage = PubsubMessage.newBuilder()
                    .setData(data)
                    .build();

            publisher.publish(pubsubMessage).get(); // blockend, reicht hier
        } finally {
            if (publisher != null) {
                publisher.shutdown();
            }
        }
    }
}
