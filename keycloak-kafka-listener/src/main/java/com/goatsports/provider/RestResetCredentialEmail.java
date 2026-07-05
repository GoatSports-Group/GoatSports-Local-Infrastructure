package com.goatsports.provider;

import org.keycloak.Config;
import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.AuthenticationFlowError;
import org.keycloak.authentication.Authenticator;
import org.keycloak.authentication.AuthenticatorFactory;
import org.keycloak.models.AuthenticationExecutionModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.utils.FormMessage;
import org.keycloak.provider.ProviderConfigProperty;

import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Collections;
import java.util.List;

public class RestResetCredentialEmail implements Authenticator, AuthenticatorFactory {

    public static final String PROVIDER_ID = "rest-reset-credential-email";
    private static final String FORGOT_PASSWORD_URL = System.getenv().get("USER_SERVICE_FORGOT_PASSWORD_URL");

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    @Override
    public void authenticate(AuthenticationFlowContext context) {
        Response challenge = context.form().createForm("login-reset-password.ftl");
        context.challenge(challenge);
    }

    @Override
    public void action(AuthenticationFlowContext context) {
        MultivaluedMap<String, String> formData = context.getHttpRequest().getDecodedFormParameters();

        String emailInput = formData.getFirst("email");
        String password = formData.getFirst("password");
        String passwordConfirm = formData.getFirst("password-confirm");

        if (emailInput == null || emailInput.isBlank() ||
                password == null || password.isBlank() ||
                passwordConfirm == null || passwordConfirm.isBlank()) {

            context.failureChallenge(AuthenticationFlowError.INVALID_CREDENTIALS,
                    context.form().setError("Vui lòng nhập đầy đủ thông tin yêu cầu.").createForm("login-reset-password.ftl"));
            return;
        }

        if (!password.equals(passwordConfirm)) {
            context.failureChallenge(AuthenticationFlowError.INVALID_CREDENTIALS,
                    context.form().addError(new FormMessage("password-confirm", "Mật khẩu xác nhận không trùng khớp."))
                            .createForm("login-reset-password.ftl"));
            return;
        }

        String jsonPayload = String.format(
                "{\"email\":\"%s\",\"password\":\"%s\",\"confirmPassword\":\"%s\"}",
                emailInput.trim().replace("\"", "\\\""),
                password.replace("\"", "\\\""),
                passwordConfirm.replace("\"", "\\\"")
        );

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(FORGOT_PASSWORD_URL))
                    .header("Content-Type", "application/json")
                    .PUT(HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200 || response.statusCode() == 204) {
                context.getAuthenticationSession().setAuthNote("REGISTERED_EMAIL", emailInput.trim());
                context.success();

            } else {
                context.failureChallenge(AuthenticationFlowError.INVALID_USER,
                        context.form().setError("Yêu cầu thất bại: " + response.body()).createForm("login-reset-password.ftl"));
            }
        } catch (Exception e) {
            context.failureChallenge(AuthenticationFlowError.INTERNAL_ERROR,
                    context.form().setError("Không thể kết nối đến máy chủ xử lý trung tâm.").createForm("login-reset-password.ftl"));
        }
    }

    @Override public boolean requiresUser() { return false; }

    @Override public boolean configuredFor(KeycloakSession session, RealmModel realm, UserModel user) { return true; }

    @Override public void setRequiredActions(KeycloakSession session, RealmModel realm, UserModel user) {}

    @Override public String getDisplayType() { return "REST Send Reset Email Link"; }

    @Override public String getReferenceCategory() { return "reset-credentials"; }

    @Override public boolean isConfigurable() { return false; }

    @Override public AuthenticationExecutionModel.Requirement[] getRequirementChoices() { return REQUIREMENT_CHOICES; }

    @Override public boolean isUserSetupAllowed() { return false; }

    @Override public String getHelpText() { return "Gọi API đổi mật khẩu tạm thời, kích hoạt gửi OTP và nhường quyền cho luồng Verify."; }

    @Override public List<ProviderConfigProperty> getConfigProperties() { return Collections.emptyList(); }

    @Override public Authenticator create(KeycloakSession session) { return this; }

    @Override public void init(Config.Scope config) {}

    @Override public void postInit(KeycloakSessionFactory factory) {}

    @Override public void close() {}

    @Override public String getId() { return PROVIDER_ID; }
}