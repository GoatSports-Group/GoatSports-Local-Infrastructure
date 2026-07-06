package com.goatsports.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.keycloak.events.Event;
import org.keycloak.events.EventListenerProvider;
import org.keycloak.events.admin.AdminEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

public record KafkaEventListenerProvider(KafkaProducer<String, String> producer, String topic) implements EventListenerProvider {

    private static final ObjectMapper mapper = new ObjectMapper();
    private static final Logger log = Logger.getLogger(KafkaEventListenerProvider.class.getName());

    @Override
    public void onEvent(Event event) {

        switch (event.getType()) {
            case REGISTER -> sendToKafka(event.getUserId(), event.getDetails().get("email"), "CREATE", "USER");
            case DELETE_ACCOUNT -> sendToKafka(event.getUserId(), null, "DELETE", "USER");
            default -> {
            }
        }

    }

    @Override
    public void onEvent(AdminEvent adminEvent, boolean includeRepresentation) {

        if (adminEvent.getResourceType().name().startsWith("USER")) {
            String userId = extractUserId(adminEvent.getResourcePath());
            switch (adminEvent.getOperationType()) {
                case CREATE -> {
                    String defaultMail = "admin-" + System.currentTimeMillis();
                    String extractedEmail = null;

                    if (includeRepresentation && adminEvent.getRepresentation() != null) {
                        extractedEmail = extractEmailFromRep(adminEvent.getRepresentation());
                    }

                    String finalEmail = (extractedEmail != null) ? extractedEmail : defaultMail;
                    sendToKafka(userId, finalEmail, "CREATE", "ADMIN");
                }

                case DELETE -> {
                }

                case UPDATE -> {

                }

                default -> {
                }

            }
        }

    }

    @Override
    public void close() {
    }

    private String extractUserId(String path) {
        return path.startsWith("users/") ? path.substring(6) : path;
    }

    private String extractEmailFromRep(String representation) {
        if (representation == null || representation.isEmpty()) {
            return null;
        }
        try {
            JsonNode node = mapper.readTree(representation);

            if (node.has("email")) return node.get("email").asText();
        } catch (Exception e) {
            log.warning("Could not parse admin event representation: " + e.getMessage());
        }

        return null;
    }

    private void sendToKafka(String userId, String email, String action, String source) {
        try {
            Map<String, String> data = new HashMap<>();
            data.put("userId", userId);
            data.put("email", email != null ? email : "null");
            data.put("action", action);
            data.put("source", source);

            String payload = mapper.writeValueAsString(data);

            producer.send(new ProducerRecord<>(topic, userId, payload));
        } catch (Exception e) {
            log.severe("Kafka Error (Jackson): " + e.getMessage());
        }
    }

}
