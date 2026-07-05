package com.goatsports.provider;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.keycloak.component.ComponentModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.provider.ProviderConfigProperty;

import java.util.List;

public class UserStorageProviderFactory
        implements org.keycloak.storage.UserStorageProviderFactory<UserStorageProvider> {

    public static final String PROVIDER_ID = "custom-db-user-provider";

    public static final String CONFIG_KEY_JDBC_URL = "jdbcUrl";
    public static final String CONFIG_KEY_DB_USERNAME = "username";
    public static final String CONFIG_KEY_DB_PASSWORD = "password";
    public static final String CONFIG_KEY_POOL_SIZE = "poolSize";

    private HikariDataSource dataSource;

    @Override
    public UserStorageProvider create(KeycloakSession session, ComponentModel model) {
        if (dataSource == null) {
            dataSource = createDataSource(model);
        }

        UserRepository repository = new UserRepository(dataSource);

        return new UserStorageProvider(session, model, repository);
    }

    private HikariDataSource createDataSource(ComponentModel model) {
        HikariConfig config = new HikariConfig();

        config.setJdbcUrl(model.get(CONFIG_KEY_JDBC_URL));
        config.setUsername(model.get(CONFIG_KEY_DB_USERNAME));
        config.setPassword(model.get(CONFIG_KEY_DB_PASSWORD));

        String poolSize = model.get(CONFIG_KEY_POOL_SIZE);
        config.setMaximumPoolSize(poolSize == null ? 5 : Integer.parseInt(poolSize));
        config.setDriverClassName("org.postgresql.Driver");

        return new HikariDataSource(config);
    }

    @Override
    public String getId() {
        return PROVIDER_ID;
    }

    @Override
    public List<ProviderConfigProperty> getConfigProperties() {
        ProviderConfigProperty jdbcUrl = new ProviderConfigProperty();
        jdbcUrl.setName(CONFIG_KEY_JDBC_URL);
        jdbcUrl.setLabel("JDBC URL");
        jdbcUrl.setType(ProviderConfigProperty.STRING_TYPE);

        ProviderConfigProperty username = new ProviderConfigProperty();
        username.setName(CONFIG_KEY_DB_USERNAME);
        username.setLabel("Database Username");
        username.setType(ProviderConfigProperty.STRING_TYPE);

        ProviderConfigProperty password = new ProviderConfigProperty();
        password.setName(CONFIG_KEY_DB_PASSWORD);
        password.setLabel("Database Password");
        password.setType(ProviderConfigProperty.PASSWORD);

        ProviderConfigProperty poolSize = new ProviderConfigProperty();
        poolSize.setName(CONFIG_KEY_POOL_SIZE);
        poolSize.setLabel("Connection Pool Size");
        poolSize.setType(ProviderConfigProperty.STRING_TYPE);

        return List.of(jdbcUrl, username, password, poolSize);
    }

    @Override
    public void close() {
        if (dataSource != null) {
            dataSource.close();
        }
    }
}