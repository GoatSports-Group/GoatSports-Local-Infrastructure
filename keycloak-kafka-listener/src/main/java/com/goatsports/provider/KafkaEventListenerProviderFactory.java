package com.goatsports.provider;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.keycloak.Config;
import org.keycloak.events.EventListenerProvider;
import org.keycloak.events.EventListenerProviderFactory;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.provider.ProviderConfigProperty;
import org.keycloak.provider.ProviderConfigurationBuilder;

import java.util.List;
import java.util.Properties;

public class KafkaEventListenerProviderFactory implements EventListenerProviderFactory {

    private KafkaProducer<String, String> producer;
    private String topic;

    private final String BOOTSTRAP_SERVERS_KEY = "bootstrapServers";
    private final String TOPIC_KEY = "topic";

    private final String BOOTSTRAP_SERVERS_DEFAULT_VALUE = "kafka:19092";
    private final String TOPIC_KEY_DEFAULT_VALUE = "user-registration-topic";

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
                .name(TOPIC_KEY)
                .label("Kafka Topic")
                .helpText("Tên topic")
                .type(ProviderConfigProperty.STRING_TYPE)
                .defaultValue(TOPIC_KEY_DEFAULT_VALUE)
                .add()
                .build();
    }

    @Override
    public void init(Config.Scope scope) {
        String bootstrapServers = scope.get(BOOTSTRAP_SERVERS_KEY);
        String configuredTopic = scope.get(TOPIC_KEY);

        if (bootstrapServers == null || bootstrapServers.isBlank()) {
            bootstrapServers = System.getenv("KAFKA_BOOTSTRAP_SERVER");
        }
        if (bootstrapServers == null || bootstrapServers.isBlank()) {
            bootstrapServers = BOOTSTRAP_SERVERS_DEFAULT_VALUE;
        }

        if (configuredTopic == null || configuredTopic.isBlank()) {
            configuredTopic = System.getenv("KEYCLOAK_KAFKA_TOPIC");
        }
        if (configuredTopic == null || configuredTopic.isBlank()) {
            configuredTopic = TOPIC_KEY_DEFAULT_VALUE;
        }

        this.topic = configuredTopic;

        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());

        this.producer = new KafkaProducer<>(props);
    }

    @Override
    public EventListenerProvider create(KeycloakSession keycloakSession) {
        return new KafkaEventListenerProvider(producer, topic);
    }

    @Override
    public void close() {
        if (producer != null) producer.close();
    }

    @Override
    public String getId() {
        return "goatsports-kafka-listener";
    }

    @Override
    public void postInit(KeycloakSessionFactory keycloakSessionFactory) {
    }
}
