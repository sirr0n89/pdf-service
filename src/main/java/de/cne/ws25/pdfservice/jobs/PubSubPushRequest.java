package de.cne.ws25.pdfservice.jobs;

import java.util.Map;

public class PubSubPushRequest {

    public static class Message {
        public String data;
        public Map<String, String> attributes;
        public String messageId;
        public String publishTime;
    }

    public Message message;
    public String subscription;
}
