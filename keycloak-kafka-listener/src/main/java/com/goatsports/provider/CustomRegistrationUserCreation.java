package com.goatsports.provider;

import org.keycloak.Config;
import org.keycloak.authentication.FormAction;
import org.keycloak.authentication.FormActionFactory;
import org.keycloak.authentication.FormContext;
import org.keycloak.authentication.ValidationContext;
import org.keycloak.forms.login.LoginFormsProvider;
import org.keycloak.models.AuthenticationExecutionModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.utils.FormMessage;
import org.keycloak.provider.ProviderConfigProperty;
import org.keycloak.services.messages.Messages;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Collections;
import java.util.List;

public class CustomRegistrationUserCreation implements FormAction, FormActionFactory {

    public static final String PROVIDER_ID = "custom-registration-user-creation";
    private static final String USER_SERVICE_REGISTER_URL = System.getenv().get("USER_SERVICE_REGISTER_URL");

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    @Override
    public void validate(ValidationContext context) {
        MultivaluedMap<String, String> formData = context.getHttpRequest().getDecodedFormParameters();

        String username = formData.getFirst("username");
        String email = formData.getFirst("email");
        String password = formData.getFirst("password");
        String passwordConfirm = formData.getFirst("password-confirm");
        String fullName = formData.getFirst("fullName");

        if (username == null || username.isBlank() ||
                email == null || email.isBlank() ||
                password == null || password.isBlank() ||
                fullName == null || fullName.isBlank()) {

            context.error("invalid_registration");
            context.validationError(formData,
                    List.of(new FormMessage(null, "Vui lòng điền đầy đủ tất cả các trường thông tin.")));
            return;
        }

        if (!password.equals(passwordConfirm)) {
            context.error("password_confirm_error");
            context.validationError(formData, List.of(new FormMessage("password-confirm", Messages.NOTMATCH_PASSWORD)));
            return;
        }

        RegisterRequest registerRequest = new RegisterRequest(username, fullName.trim(), email, password, passwordConfirm);

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(USER_SERVICE_REGISTER_URL))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(registerRequest.toJson()))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200 && response.statusCode() != 201) {
                context.error("registration_rejected");
                context.validationError(formData,
                        List.of(new FormMessage(null, "Đăng ký thất bại: " + response.body())));
                return;
            }
        } catch (Exception e) {
            context.error("internal_error");
            context.validationError(formData, List.of(new FormMessage(null, "Không thể kết nối đến hệ thống xử lý.")));
            return;
        }

        context.success();
    }

    @Override
    public void success(FormContext context) {
        MultivaluedMap<String, String> formData = context.getHttpRequest().getDecodedFormParameters();
        String email = formData.getFirst("email");

        context.getAuthenticationSession().setAuthNote("REGISTERED_EMAIL", email);
    }

    @Override
    public void buildPage(FormContext context, LoginFormsProvider form) {
    }

    @Override
    public boolean requiresUser() {
        return false;
    }

    @Override
    public boolean configuredFor(KeycloakSession session, RealmModel realm, UserModel user) {
        return true;
    }

    @Override
    public void setRequiredActions(KeycloakSession session, RealmModel realm, UserModel user) {
    }

    @Override
    public String getDisplayType() {
        return "Custom Registration (REST API - Full Name & Confirm Password)";
    }

    @Override
    public String getReferenceCategory() {
        return "registration";
    }

    @Override
    public boolean isConfigurable() {
        return false;
    }

    @Override
    public AuthenticationExecutionModel.Requirement[] getRequirementChoices() {
        return REQUIREMENT_CHOICES;
    }

    @Override
    public boolean isUserSetupAllowed() {
        return false;
    }

    @Override
    public String getHelpText() {
        return "Gọi REST API sang User Service để đăng ký.";
    }

    @Override
    public List<ProviderConfigProperty> getConfigProperties() {
        return Collections.emptyList();
    }

    @Override
    public FormAction create(KeycloakSession session) {
        return this;
    }

    @Override
    public void init(Config.Scope config) {
    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {
    }

    @Override
    public void close() {
    }

    @Override
    public String getId() {
        return PROVIDER_ID;
    }
}