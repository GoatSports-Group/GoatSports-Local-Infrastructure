package com.goatsports.provider;

import org.keycloak.component.ComponentModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.storage.StorageId;
import org.keycloak.storage.adapter.AbstractUserAdapterFederatedStorage;
import java.util.UUID;

public class UserAdapter extends AbstractUserAdapterFederatedStorage {

    private final UUID keycloakId;
    private final UserRecord user;

    public UserAdapter(KeycloakSession session, RealmModel realm, ComponentModel model, UserRecord user) {
        super(session, realm, model);
        this.user = user;
        this.keycloakId = user.keycloakId();
    }

    @Override
    public String getId() {
        return keycloakId.toString();
    }

    @Override
    public String getUsername() {
        return user.username();
    }

    @Override
    public void setUsername(String username) {
    }

    @Override
    public String getEmail() {
        return user.email();
    }

    @Override
    public void setEmail(String email) {
    }

    @Override
    public String getFirstName() {
        return user.fullName();
    }

    @Override
    public void setFirstName(String firstName) {
    }

    @Override
    public String getLastName() {
        return "";
    }

    @Override
    public void setLastName(String lastName) {
    }

    @Override
    public boolean isEnabled() {
        return user.status().trim().equals("ACTIVE");
    }

    @Override
    public void setEnabled(boolean enabled) {
    }
}