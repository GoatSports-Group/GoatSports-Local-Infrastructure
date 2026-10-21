package com.goatsports.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.keycloak.Config;
import org.keycloak.events.Event;
import org.keycloak.events.EventListenerProvider;
import org.keycloak.events.EventListenerProviderFactory;
import org.keycloak.events.admin.AdminEvent;
import org.keycloak.models.*;
import org.keycloak.provider.ProviderConfigProperty;
import org.keycloak.provider.ProviderConfigurationBuilder;

import java.time.Duration;
import java.util.*;
import java.util.logging.Logger;

public class KafkaEventListenerProviderFactory implements EventListenerProviderFactory {
    private final Logger log = Logger.getLogger(KafkaEventListenerProviderFactory.class.getName());

    private String consumeTopic;
    private String produceTopic;
    private String bootstrapServers;
    private String realmName;
    private Thread consumerThread;
    private static final ObjectMapper mapper = new ObjectMapper();

    private final String BOOTSTRAP_SERVERS_KEY = "bootstrapServers";
    private final String CONSUME_TOPIC_KEY = "consumeTopic";
    private final String PRODUCE_TOPIC_KEY = "produceTopic";
    private final String REALM_KEY = "realm";

    private final String BOOTSTRAP_SERVERS_DEFAULT_VALUE = "kafka:19092";
    private final String CONSUME_TOPIC_DEFAULT_VALUE = "user-registration-topic";
    private final String PRODUCE_TOPIC_DEFAULT_VALUE = "user-sync-topic";
    private final String REALM_DEFAULT_VALUE = "goatsports";

    @Override
    public List<ProviderConfigProperty> getConfigMetadata() {
        return ProviderConfigurationBuilder
                .create()
                .property()
                .name(BOOTSTRAP_SERVERS_KEY)
                .label("Kafka Bootstrap Servers")
                .helpText("Địa chỉ Kafka")
                .type(ProviderConfigProperty.STRING_TYPE)
                .defaultValue(BOOTSTRAP_SERVERS_DEFAULT_VALUE)
                .add()
                .property()
                .name(CONSUME_TOPIC_KEY)
                .label("Kafka Consume Topic")
                .helpText("Tên topic nhận event đăng ký/verify")
                .type(ProviderConfigProperty.STRING_TYPE)
                .defaultValue(CONSUME_TOPIC_DEFAULT_VALUE)
                .add()
                .property()
                .name(PRODUCE_TOPIC_KEY)
                .label("Kafka Produce Topic")
                .helpText("Tên topic gửi event đã sync")
                .type(ProviderConfigProperty.STRING_TYPE)
                .defaultValue(PRODUCE_TOPIC_DEFAULT_VALUE)
                .add()
                .property()
                .name(REALM_KEY)
                .label("Keycloak Realm")
                .helpText("Tên realm để đồng bộ user")
                .type(ProviderConfigProperty.STRING_TYPE)
                .defaultValue(REALM_DEFAULT_VALUE)
                .add()
                .build();
    }

    @Override
    public void init(Config.Scope scope) {
        String servers = scope.get(BOOTSTRAP_SERVERS_KEY);
        String configuredConsumeTopic = scope.get(CONSUME_TOPIC_KEY);
        String configuredProduceTopic = scope.get(PRODUCE_TOPIC_KEY);
        String configuredRealm = scope.get(REALM_KEY);

        if (servers == null || servers.isBlank()) {
            servers = System.getenv("KAFKA_BOOTSTRAP_SERVER");
        }
        if (servers == null || servers.isBlank()) {
            servers = BOOTSTRAP_SERVERS_DEFAULT_VALUE;
        }
        this.bootstrapServers = servers;

        if (configuredConsumeTopic == null || configuredConsumeTopic.isBlank()) {
            configuredConsumeTopic = System.getenv("KEYCLOAK_KAFKA_CONSUME_TOPIC");
        }
        if (configuredConsumeTopic == null || configuredConsumeTopic.isBlank()) {
            configuredConsumeTopic = System.getenv("KEYCLOAK_KAFKA_TOPIC");
        }
        if (configuredConsumeTopic == null || configuredConsumeTopic.isBlank()) {
            configuredConsumeTopic = CONSUME_TOPIC_DEFAULT_VALUE;
        }
        this.consumeTopic = configuredConsumeTopic;

        if (configuredProduceTopic == null || configuredProduceTopic.isBlank()) {
            configuredProduceTopic = System.getenv("KEYCLOAK_KAFKA_PRODUCE_TOPIC");
        }
        if (configuredProduceTopic == null || configuredProduceTopic.isBlank()) {
            configuredProduceTopic = PRODUCE_TOPIC_DEFAULT_VALUE;
        }
        this.produceTopic = configuredProduceTopic;

        if (configuredRealm == null || configuredRealm.isBlank()) {
            configuredRealm = System.getenv("KEYCLOAK_REALM");
        }
        if (configuredRealm == null || configuredRealm.isBlank()) {
            configuredRealm = REALM_DEFAULT_VALUE;
        }
        this.realmName = configuredRealm;
    }

    @Override
    public EventListenerProvider create(KeycloakSession keycloakSession) {
        return new EventListenerProvider() {
            @Override
            public void onEvent(Event event) {}

            @Override
            public void onEvent(AdminEvent adminEvent, boolean includeRepresentation) {}

            @Override
            public void close() {}
        };
    }

    @Override
    public void close() {
        if (consumerThread != null) consumerThread.interrupt();
    }

    @Override
    public String getId() {
        return "goatsports-kafka-listener";
    }

    @Override
    public void postInit(KeycloakSessionFactory keycloakSessionFactory) {
        consumerThread = new Thread(() -> {
            log.info("Starting Keycloak Kafka Consumer for user sync on topic: " + consumeTopic);
            
            Properties props = new Properties();
            props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
            props.put(ConsumerConfig.GROUP_ID_CONFIG, "keycloak-user-sync-group");
            props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
            props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
            props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
            props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");


            try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
                consumer.subscribe(Collections.singletonList(consumeTopic));
                while (!Thread.currentThread().isInterrupted()) {
                    ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(1000));
                    for (ConsumerRecord<String, String> record : records) {
                        try {
                            JsonNode root = mapper.readTree(record.value());
                            String eventType = root.has("eventType") ? root.get("eventType").asText() : "";
                            
                             if (UserRegistrationEventType.USER_REGISTERED.name().equals(eventType)) {
                                JsonNode dataNode = root.get("data");
                                String userIdStr = dataNode.get("userId").asText();
                                String username = dataNode.get("username").asText();
                                String email = dataNode.get("email").asText();
                                String fullName = dataNode.get("fullName").asText();
                                String password = dataNode.get("password").asText();

                                log.info("Syncing user to Keycloak: " + username + " (ID: " + userIdStr + ")");

                                KeycloakSession session = keycloakSessionFactory.create();
                                try {
                                    session.getTransactionManager().begin();
                                    
                                    RealmModel realm = session.realms().getRealmByName(realmName);
                                    if (realm == null) {
                                        log.severe("Realm '" + realmName + "' not found! Cannot sync user.");
                                        session.getTransactionManager().rollback();
                                        continue;
                                    }
                                    session.getContext().setRealm(realm);

                                    UserModel existingUser = session.users().getUserByUsername(realm, username);
                                    if (existingUser != null) {
                                        log.warning("User '" + username + "' already exists in Keycloak. Skipping sync.");
                                        session.getTransactionManager().rollback();
                                        continue;
                                    }

                                    UserModel existingEmail = session.users().getUserByEmail(realm, email);
                                    if (existingEmail != null) {
                                        log.warning("Email '" + email + "' already exists in Keycloak. Skipping sync.");
                                        session.getTransactionManager().rollback();
                                        continue;
                                    }

                                    UserModel user = session.users().addUser(realm, null, username, true, true);
                                    user.setEmail(email);
                                    user.setEmailVerified(false);
                                    // Luôn bật: auth-service mới là nơi quyết tài khoản dùng được hay chưa (OTP, khóa).
                                    // Tắt ở đây thì đăng nhập Google kẹt ở trang lỗi "Account is disabled" của Keycloak.
                                    user.setEnabled(true);

                                    if (fullName != null && !fullName.isEmpty()) {
                                        int firstSpace = fullName.indexOf(" ");
                                        if (firstSpace != -1) {
                                            user.setFirstName(fullName.substring(0, firstSpace));
                                            user.setLastName(fullName.substring(firstSpace + 1));
                                        } else {
                                            user.setFirstName(fullName);
                                            user.setLastName("");
                                        }
                                    }

                                    user.credentialManager().updateCredential(
                                            UserCredentialModel.password(password, false)
                                    );

                                    session.getTransactionManager().commit();
                                    log.info("User '" + username + "' synced to Keycloak successfully. Keycloak ID: " + user.getId());
                                    
                                    consumer.commitSync();

                                    sendUserSyncedEvent(userIdStr, user.getId(), username, email);
                                } catch (Exception e) {
                                    log.severe("Failed to sync user '" + username + "' to Keycloak: " + e.getMessage());
                                    e.printStackTrace();
                                    session.getTransactionManager().rollback();
                                } finally {
                                    session.close();
                                }
                            } else if (UserRegistrationEventType.USER_VERIFIED.name().equals(eventType)) {
                                JsonNode dataNode = root.get("data");
                                String userIdStr = dataNode.get("userId").asText();
                                String username = dataNode.get("username").asText();
                                boolean verified = dataNode.get("verified").asBoolean();

                                log.info("Setting emailVerified to " + verified + " for user: " + username + " (ID: " + userIdStr + ")");

                                KeycloakSession session = keycloakSessionFactory.create();
                                try {
                                    session.getTransactionManager().begin();
                                    
                                    RealmModel realm = session.realms().getRealmByName(realmName);
                                    if (realm == null) {
                                        log.severe("Realm '" + realmName + "' not found! Cannot sync user verification.");
                                        session.getTransactionManager().rollback();
                                        continue;
                                    }
                                    session.getContext().setRealm(realm);

                                    UserModel user = session.users().getUserByUsername(realm, username);
                                    if (user == null) {
                                        log.warning("User '" + username + "' not found in Keycloak. Skipping verification sync.");
                                        session.getTransactionManager().rollback();
                                        continue;
                                    }

                                    user.setEmailVerified(verified);
                                    // Bỏ xác thực không tắt user Keycloak (xem USER_REGISTERED); bật lại user cũ đã bị tắt.
                                    user.setEnabled(true);

                                    session.getTransactionManager().commit();
                                    log.info("User '" + username + "' emailVerified set to " + verified + " in Keycloak.");
                                    
                                    consumer.commitSync();
                                } catch (Exception e) {
                                    log.severe("Failed to verify user '" + username + "' in Keycloak: " + e.getMessage());
                                    session.getTransactionManager().rollback();
                                } finally {
                                    session.close();
                                }
                            } else if (UserRegistrationEventType.USER_PASSWORD_UPDATED.name().equals(eventType)) {
                                 JsonNode dataNode = root.get("data");
                                 String username = dataNode.get("username").asText();
                                 String password = dataNode.get("password").asText();

                                 log.info("Syncing updated password for user: " + username);

                                 KeycloakSession session = keycloakSessionFactory.create();
                                 try {
                                     session.getTransactionManager().begin();
                                     
                                     RealmModel realm = session.realms().getRealmByName(realmName);
                                     if (realm == null) {
                                         log.severe("Realm '" + realmName + "' not found! Cannot sync password update.");
                                         session.getTransactionManager().rollback();
                                         continue;
                                     }
                                     session.getContext().setRealm(realm);

                                     UserModel user = session.users().getUserByUsername(realm, username);
                                     if (user == null) {
                                         log.warning("User '" + username + "' not found in Keycloak. Skipping password update.");
                                         session.getTransactionManager().rollback();
                                         continue;
                                     }

                                     user.credentialManager().updateCredential(
                                             UserCredentialModel.password(password, false)
                                     );

                                     session.getTransactionManager().commit();
                                     log.info("Successfully updated password for user '" + username + "' in Keycloak.");
                                     
                                     consumer.commitSync();
                                 } catch (Exception e) {
                                     log.severe("Failed to update password for user '" + username + "' in Keycloak: " + e.getMessage());
                                     session.getTransactionManager().rollback();
                                 } finally {
                                     session.close();
                                 }
                             } else if (UserRegistrationEventType.USER_UPDATED.name().equals(eventType)) {
                                 JsonNode dataNode = root.get("data");
                                 String userIdStr = dataNode.get("userId").asText();
                                 String keycloakIdStr = dataNode.has("keycloakId") && !dataNode.get("keycloakId").isNull() ? dataNode.get("keycloakId").asText() : "";
                                 String username = dataNode.get("username").asText();
                                 String fullName = dataNode.has("fullName") ? dataNode.get("fullName").asText() : "";
                                 String email = dataNode.has("email") ? dataNode.get("email").asText() : "";

                                 log.info("Updating user in Keycloak: " + username + " (ID: " + userIdStr + ", Keycloak ID: " + keycloakIdStr + ")");

                                 KeycloakSession session = keycloakSessionFactory.create();
                                 try {
                                     session.getTransactionManager().begin();
                                     
                                     RealmModel realm = session.realms().getRealmByName(realmName);
                                     if (realm == null) {
                                         log.severe("Realm '" + realmName + "' not found! Cannot update user.");
                                         session.getTransactionManager().rollback();
                                         continue;
                                     }
                                     session.getContext().setRealm(realm);

                                     UserModel user = null;
                                     if (keycloakIdStr != null && !keycloakIdStr.isEmpty() && !"null".equalsIgnoreCase(keycloakIdStr)) {
                                         user = session.users().getUserById(realm, keycloakIdStr);
                                     }
                                     if (user == null) {
                                         user = session.users().getUserByUsername(realm, username);
                                     }
                                     if (user == null && email != null && !email.isEmpty()) {
                                         user = session.users().getUserByEmail(realm, email);
                                     }

                                     if (user == null) {
                                         log.warning("User '" + username + "' not found in Keycloak. Skipping update.");
                                         session.getTransactionManager().rollback();
                                         continue;
                                     }

                                     if (username != null && !username.isEmpty() && !username.equals(user.getUsername())) {
                                         user.setUsername(username);
                                     }

                                     if (fullName != null && !fullName.isEmpty()) {
                                         int firstSpace = fullName.indexOf(" ");
                                         if (firstSpace != -1) {
                                             user.setFirstName(fullName.substring(0, firstSpace));
                                             user.setLastName(fullName.substring(firstSpace + 1));
                                         } else {
                                             user.setFirstName(fullName);
                                             user.setLastName("");
                                         }
                                     }

                                     session.getTransactionManager().commit();
                                     log.info("User '" + username + "' updated in Keycloak successfully.");
                                     
                                     consumer.commitSync();
                                 } catch (Exception e) {
                                     log.severe("Failed to update user '" + username + "' in Keycloak: " + e.getMessage());
                                     session.getTransactionManager().rollback();
                                 } finally {
                                     session.close();
                                 }
                              }
                        } catch (Exception e) {
                            log.severe("Failed to process Kafka sync message: " + e.getMessage());
                        }
                    }
                }
            } catch (Exception e) {
                log.severe("Kafka consumer error: " + e.getMessage());
            }
        });
        consumerThread.setDaemon(true);
        consumerThread.start();
    }

    private void sendUserSyncedEvent(String userId, String keycloakId, String username, String email) {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());

        try (KafkaProducer<String, String> localProducer = new KafkaProducer<>(props)) {
            java.util.Map<String, Object> event = new java.util.HashMap<>();
            event.put("eventId", java.util.UUID.randomUUID().toString());
            event.put("eventType", UserRegistrationEventType.USER_SYNCED.name());
            event.put("timestamp", java.time.LocalDateTime.now().toString());

            java.util.Map<String, String> data = new java.util.HashMap<>();
            data.put("userId", userId);
            data.put("keycloakId", keycloakId);
            data.put("username", username);
            data.put("email", email);
            event.put("data", data);

            String payload = mapper.writeValueAsString(event);
            localProducer.send(new ProducerRecord<>(produceTopic, userId, payload));
            log.info("Published USER_SYNCED event back to Kafka for user: " + username + " (Keycloak ID: " + keycloakId + ") on topic: " + produceTopic);
        } catch (Exception e) {
            log.severe("Failed to publish USER_SYNCED event back to Kafka: " + e.getMessage());
        }
    }
}
