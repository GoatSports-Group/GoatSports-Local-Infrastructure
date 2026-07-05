package com.goatsports.provider;

import org.keycloak.component.ComponentModel;
import org.keycloak.credential.CredentialInput;
import org.keycloak.credential.CredentialInputValidator;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.credential.PasswordCredentialModel;
import org.keycloak.storage.StorageId;
import org.keycloak.storage.user.UserLookupProvider;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class UserStorageProvider implements
        org.keycloak.storage.UserStorageProvider,
        UserLookupProvider,
        CredentialInputValidator {

    private final KeycloakSession session;
    private final ComponentModel model;
    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public UserStorageProvider(
            KeycloakSession session,
            ComponentModel model,
            UserRepository userRepository) {
        this.session = session;
        this.model = model;
        this.userRepository = userRepository;
    }

    @Override
    public void close() {
    }

    @Override
    public UserModel getUserById(RealmModel realm, String id) {
        String externalId = StorageId.externalId(id);

        return userRepository.findByKeycloakId(externalId)
                .map(user -> new UserAdapter(session, realm, model, user))
                .orElse(null);
    }

    @Override
    public UserModel getUserByUsername(RealmModel realm, String username) {
        return userRepository.findByUsernameOrEmail(username)
                .filter(user -> user.keycloakId() != null)
                .map(user -> new UserAdapter(session, realm, model, user))
                .orElse(null);
    }

    @Override
    public UserModel getUserByEmail(RealmModel realm, String email) {
        return userRepository.findByUsernameOrEmail(email)
                .filter(user -> user.keycloakId() != null)
                .map(user -> new UserAdapter(session, realm, model, user))
                .orElse(null);
    }

    @Override
    public boolean supportsCredentialType(String credentialType) {
        return PasswordCredentialModel.TYPE.equals(credentialType);
    }

    @Override
    public boolean isConfiguredFor(RealmModel realm, UserModel user, String credentialType) {
        return supportsCredentialType(credentialType);
    }

    @Override
    public boolean isValid(RealmModel realm, UserModel user, CredentialInput input) {
        if (!supportsCredentialType(input.getType())) {
            return false;
        }

        String rawPassword = input.getChallengeResponse();

        return userRepository.findByUsernameOrEmail(user.getUsername())
                .map(record -> passwordEncoder.matches(rawPassword, record.password()))
                .orElse(false);
    }
}