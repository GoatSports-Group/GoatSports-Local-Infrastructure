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

public class CustomResetPasswordVerifyOtp implements Authenticator, AuthenticatorFactory {

    public static final String PROVIDER_ID = "custom-reset-password-verify-otp";

    private static final String USER_SERVICE_VERIFY_URL = System.getenv().get("USER_SERVICE_VERIFY_URL");
    private static final String USER_SERVICE_RESEND_URL = System.getenv().get("USER_SERVICE_RESEND_URL");

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    @Override
    public void authenticate(AuthenticationFlowContext context) {
        String email = context.getAuthenticationSession().getAuthNote("REGISTERED_EMAIL");
        renderVerifyPage(context, email, "Mã OTP khôi phục mật khẩu đã được gửi về Email.");
    }

    @Override
    public void action(AuthenticationFlowContext context) {
        MultivaluedMap<String, String> formData = context.getHttpRequest().getDecodedFormParameters();
        String action = formData.getFirst("action");
        String email = context.getAuthenticationSession().getAuthNote("REGISTERED_EMAIL");

        // --- TRƯỜNG HỢP 1: XỬ LÝ NÚT GỬI LẠI MÃ ---
        if ("resend".equals(action)) {
            try {
                String backendResendUrl = USER_SERVICE_RESEND_URL + "?email=" + java.net.URLEncoder.encode(email, java.nio.charset.StandardCharsets.UTF_8);
                HttpRequest request = HttpRequest.newBuilder().uri(URI.create(backendResendUrl)).POST(HttpRequest.BodyPublishers.noBody()).build();
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200 || response.statusCode() == 204) {
                    renderVerifyPage(context, email, "Mã OTP mới đã được gửi thành công! Vui lòng kiểm tra lại hộp thư.");
                } else {
                    context.failureChallenge(AuthenticationFlowError.INTERNAL_ERROR,
                            context.form().setError("Không thể gửi lại mã kích hoạt. Vui lòng thử lại.").createForm("login-verify-email.ftl"));
                }
            } catch (Exception e) {
                context.failureChallenge(AuthenticationFlowError.INTERNAL_ERROR,
                        context.form().setError("Không thể kết nối đến hệ thống xử lý.").createForm("login-verify-email.ftl"));
            }
            return;
        }

        // --- TRƯỜNG HỢP 2: XỬ LÝ SUBMIT MÃ OTP ---
        String otpCode = formData.getFirst("otpCode");
        if (otpCode == null || otpCode.isBlank()) {
            context.failureChallenge(AuthenticationFlowError.INVALID_CREDENTIALS,
                    context.form().addError(new FormMessage("otpCode", "Vui lòng nhập mã xác thực OTP.")).createForm("login-verify-email.ftl"));
            return;
        }

        String jsonPayload = String.format("{\"email\":\"%s\",\"verificationCode\":\"%s\"}", email, otpCode.trim());
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(USER_SERVICE_VERIFY_URL))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200 || response.statusCode() == 204) {
                context.getAuthenticationSession().removeAuthNote("REGISTERED_EMAIL");
                context.success(); // Xác thực thành công hoàn tất luồng, quay về màn hình đăng nhập
            } else {
                context.failureChallenge(AuthenticationFlowError.INVALID_CREDENTIALS,
                        context.form().setError("Mã xác thực không chính xác hoặc đã hết hạn.").createForm("login-verify-email.ftl"));
            }
        } catch (Exception e) {
            context.failureChallenge(AuthenticationFlowError.INTERNAL_ERROR,
                    context.form().setError("Lỗi kết nối hệ thống.").createForm("login-verify-email.ftl"));
        }
    }

    private void renderVerifyPage(AuthenticationFlowContext context, String email, String successMessage) {
        UserModel dummyUser = new org.keycloak.models.utils.ReadOnlyUserModelDelegate(null) {
            @Override
            public String getEmail() { return email != null ? email : ""; }
        };
        Response challenge = context.form()
                .setUser(dummyUser)
                .setSuccess(successMessage)
                .createForm("login-verify-email.ftl");
        context.challenge(challenge);
    }

    @Override public boolean requiresUser() { return false; }

    @Override public boolean configuredFor(KeycloakSession session, RealmModel realm, UserModel user) { return true; }

    @Override public void setRequiredActions(KeycloakSession session, RealmModel realm, UserModel user) {}

    @Override public String getDisplayType() { return "Custom Reset Password - Verify OTP Form"; }

    @Override public String getReferenceCategory() { return "reset-credentials"; }

    @Override public boolean isConfigurable() { return false; }

    @Override public AuthenticationExecutionModel.Requirement[] getRequirementChoices() { return REQUIREMENT_CHOICES; }

    @Override public boolean isUserSetupAllowed() { return false; }
    @Override public String getHelpText() { return "Chặn luồng bắt nhập OTP từ login-verify-email.ftl cho luồng Quên mật khẩu."; }

    @Override public List<ProviderConfigProperty> getConfigProperties() { return Collections.emptyList(); }

    @Override public Authenticator create(KeycloakSession session) { return this; }

    @Override public void init(Config.Scope config) {}

    @Override public void postInit(KeycloakSessionFactory factory) {}

    @Override public void close() {}

    @Override public String getId() { return PROVIDER_ID; }
}