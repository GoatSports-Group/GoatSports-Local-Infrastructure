<#import "template.ftl" as layout>
<@layout.registrationLayout displayInfo=true displayMessage=!messagesPerField.existsError('otpCode'); section>
    <#if section = "header">
        Xác thực địa chỉ Email
    <#elseif section = "form">
        <div class="text-center" style="display: flex; flex-direction: column; align-items: center; justify-content: center;">
            <div class="verify-icon-container" style="margin-bottom: 1.5rem;">
                <div class="verify-icon-bg">
                    <i data-lucide="mail-check"></i> [cite: 29]
                </div>
            </div>
            
            <p style="font-size: 0.93rem; color: var(--text-dark); font-weight: 500; margin-bottom: 1.25rem; line-height: 1.6; text-align: center;">
                Hệ thống đã gửi một email xác thực đến địa chỉ:<br>
                <strong style="color: var(--primary); font-size: 1.05rem; word-break: break-all;">${(user.email!'')}</strong> [cite: 30]
            </p>
            
            <form id="kc-register-verify-otp-form" action="${url.loginAction}" method="post" style="width: 100%; max-width: 320px; margin-bottom: 1.5rem;">
                <div class="form-group" style="margin-bottom: 1rem; text-align: left;">
                    <label for="otpCode" style="display: block; margin-bottom: 0.5rem; font-weight: 600; font-size: 0.875rem;">Mã xác thực (OTP)</label>
                    <div class="input-wrapper" style="position: relative;">
                        <i class="input-icon" data-lucide="shield-check" style="position: absolute; left: 10px; top: 50%; transform: translateY(-50%); color: var(--text-muted);"></i>
                        <input id="otpCode" 
                               name="otpCode" 
                               type="text" 
                               class="<#if messagesPerField.existsError('otpCode')>input-error</#if>" 
                               placeholder="Nhập mã 6 chữ số" 
                               style="width: 100%; padding: 0.625rem 0.625rem 0.625rem 2.25rem; border: 1px solid var(--border-color); border-radius: 6px;"
                               required 
                               autofocus />
                    </div>
                    <#if messagesPerField.existsError('otpCode')>
                        <span class="error-text" style="color: var(--error-color); font-size: 0.75rem; display: block; margin-top: 0.25rem;">
                            ${kcSanitize(messagesPerField.getFirstError('otpCode'))?no_esc}
                        </span>
                    </#if>
                </div>

                <button type="submit" name="action" value="verify" style="width: 100%; padding: 0.75rem; background-color: var(--primary); color: white; border: none; border-radius: 6px; font-weight: 600; cursor: pointer; margin-bottom: 1rem;">
                    Xác nhận kích hoạt
                </button>

                <div style="border-top: 1px solid var(--border-color); padding-top: 1rem; margin-top: 1rem;">
                    <p style="margin-bottom: 0.5rem; font-size: 0.8125rem;">Bạn vẫn chưa nhận được thư?</p> [cite: 36]
                    <button type="submit" name="action" value="resend" formnovalidate style="background: none; border: none; color: var(--primary); font-size: 0.875rem; font-weight: 600; cursor: pointer; padding: 0;">
                        Gửi lại email kích hoạt
                    </button>
                </div>
            </form>

            <p style="font-size: 0.82rem; color: var(--text-muted); margin-bottom: 1.75rem; line-height: 1.6; text-align: center;">
                Vui lòng kiểm tra hộp thư đến (hoặc hộp thư rác/Spam), sao chép mã số OTP được cấp và điền vào ô phía trên để hoàn tất. 
            </p>
        </div>
    <#elseif section = "info">
        <div class="footer-wrapper" style="width: 100%;">
            <div class="footer-link-group" style="display: flex; justify-content: center; gap: 1rem; font-size: 0.8125rem;">
                <a href="${url.loginUrl}" style="display: flex; align-items: center; gap: 0.25rem;">
                    <i data-lucide="arrow-left" style="width: 14px; height: 14px;"></i> Quay lại Đăng nhập [cite: 39, 40]
                </a>
            </div>
        </div>
    </#if>
</@layout.registrationLayout>