<#import "template.ftl" as layout>
<@layout.registrationLayout displayMessage=!messagesPerField.existsError('email','password','password-confirm'); section>
    <#if section = "header">
        Quên mật khẩu GoatSports
    <#elseif section = "form">
        <form id="kc-reset-password-form" action="${url.loginAction}" method="post">
            
            <div class="form-group">
                <label for="email">Email</label>
                <div class="input-wrapper">
                    <i class="input-icon" data-lucide="mail"></i>
                    <input id="email" 
                           class="<#if messagesPerField.existsError('email')>input-error</#if>" 
                           name="email" 
                           value="${(auth.attemptedUsername!'')}" 
                           type="text" 
                           placeholder="Nhập email" 
                           required 
                           autofocus />
                </div>
                <#if messagesPerField.existsError('email')>
                    <span class="error-text">${kcSanitize(messagesPerField.getFirstError('email'))?no_esc}</span>
                </#if>
            </div>

            <div class="form-group">
                <label for="password">Mật khẩu mới</label>
                <div class="input-wrapper">
                    <i class="input-icon" data-lucide="lock"></i>
                    <input id="password" 
                           class="<#if messagesPerField.existsError('password')>input-error</#if>" 
                           name="password" 
                           type="password" 
                           placeholder="Nhập mật khẩu mới của bạn" 
                           required />
                </div>
                <#if messagesPerField.existsError('password')>
                    <span class="error-text">${kcSanitize(messagesPerField.getFirstError('password'))?no_esc}</span>
                </#if>
            </div>

            <div class="form-group">
                <label for="password-confirm">Xác nhận mật khẩu mới</label>
                <div class="input-wrapper">
                    <i class="input-icon" data-lucide="shield-check"></i>
                    <input id="password-confirm" 
                           class="<#if messagesPerField.existsError('password-confirm')>input-error</#if>" 
                           name="password-confirm" 
                           type="password" 
                           placeholder="Xác nhận lại mật khẩu mới" 
                           required />
                </div>
                <#if messagesPerField.existsError('password-confirm')>
                    <span class="error-text">${kcSanitize(messagesPerField.getFirstError('password-confirm'))?no_esc}</span>
                </#if>
            </div>

            <button type="submit">Cập nhật mật khẩu</button>
        </form>
    <#elseif section = "info">
        <div class="footer-wrapper">
            Quay lại <a href="${url.loginUrl}">Đăng nhập</a> [cite: 27]
        </div>
    </#if>
</@layout.registrationLayout>