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

import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Collections;
import java.util.List;

public class CustomRegistrationVerifyOtp implements FormAction, FormActionFactory {

    public static final String PROVIDER_ID = "custom-registration-verify-otp";

    private static final String USER_SERVICE_VERIFY_URL = System.getenv().get("USER_SERVICE_VERIFY_URL");
    private static final String USER_SERVICE_RESEND_URL = System.getenv().get("USER_SERVICE_RESEND_URL");

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    @Override
    public void buildPage(FormContext context, LoginFormsProvider form) {
        String email = context.getAuthenticationSession().getAuthNote("REGISTERED_EMAIL");

        UserModel dummyUser = new org.keycloak.models.utils.ReadOnlyUserModelDelegate(null) {
            @Override
            public String getEmail() {
                return email != null ? email : "";
            }
        };
        form.setUser(dummyUser);
    }

    @Override
    public void validate(ValidationContext context) {
        MultivaluedMap<String, String> formData = context.getHttpRequest().getDecodedFormParameters();
        String action = formData.getFirst("action");
        String email = context.getAuthenticationSession().getAuthNote("REGISTERED_EMAIL");

        // --- TRƯỜNG HỢP 1: XỬ LÝ NÚT GỬI LẠI MÃ (action = "resend") ---
        if ("resend".equals(action)) {
            try {
                String backendResendUrl = USER_SERVICE_RESEND_URL + "?email=" + java.net.URLEncoder.encode(email, java.nio.charset.StandardCharsets.UTF_8);

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(backendResendUrl))
                        .POST(HttpRequest.BodyPublishers.noBody())
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200 || response.statusCode() == 204) {
                    context.getEvent().detail("info", "Mã kích hoạt mới đã được gửi lại thành công.");

                    formData.remove("otpCode");

                    context.validationError(formData, List.of(new FormMessage(null, "Mã OTP mới đã được gửi thành công! Vui lòng kiểm tra lại hộp thư.")));
                    return;
                } else {
                    context.error("resend_failed");
                    context.validationError(formData, List.of(new FormMessage(null, "Không thể gửi lại mã kích hoạt. Vui lòng thử lại.")));
                    return;
                }
            } catch (Exception e) {
                context.error("backend_error");
                context.validationError(formData, List.of(new FormMessage(null, "Không thể kết nối đến hệ thống xử lý.")));
                return;
            }
        }

        // --- TRƯỜNG HỢP 2: XỬ LÝ SUBMIT MÃ OTP ---
        String otpCode = formData.getFirst("otpCode");
        if (otpCode == null || otpCode.isBlank()) {
            context.error("missing_otp");
            context.validationError(formData, List.of(new FormMessage("otpCode", "Vui lòng nhập mã xác thực OTP.")));
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

            if (response.statusCode() != 200 && response.statusCode() != 204) {
                context.error("invalid_otp");
                context.validationError(formData, List.of(new FormMessage("otpCode", "Mã xác thực không chính xác hoặc đã hết hạn.")));
                return;
            }
        } catch (Exception e) {
            context.error("backend_error");
            context.validationError(formData, List.of(new FormMessage(null, "Lỗi kết nối hệ thống.")));
            return;
        }

        context.success();
    }

    @Override
    public void success(FormContext context) {
        context.getAuthenticationSession().removeAuthNote("REGISTERED_EMAIL");
    }

    @Override
    public boolean requiresUser() { return false; }

    @Override
    public boolean configuredFor(KeycloakSession session, RealmModel realm, UserModel user) { return true; }

    @Override
    public void setRequiredActions(KeycloakSession session, RealmModel realm, UserModel user) {}

    @Override
    public String getDisplayType() { return "Custom Registration - Verify OTP Form"; }

    @Override
    public String getReferenceCategory() { return "registration"; }

    @Override
    public boolean isConfigurable() { return false; }

    @Override
    public AuthenticationExecutionModel.Requirement[] getRequirementChoices() { return REQUIREMENT_CHOICES; }

    @Override
    public boolean isUserSetupAllowed() { return false; }

    @Override
    public String getHelpText() { return "Hiển thị giao diện và chặn luồng để bắt người dùng nhập OTP xác thực với Spring Backend."; }

    @Override
    public List<ProviderConfigProperty> getConfigProperties() { return Collections.emptyList(); }

    @Override
    public FormAction create(KeycloakSession session) { return this; }

    @Override
    public void init(Config.Scope config) {}

    @Override
    public void postInit(KeycloakSessionFactory factory) {}

    @Override
    public void close() {}

    @Override
    public String getId() { return PROVIDER_ID; }
}