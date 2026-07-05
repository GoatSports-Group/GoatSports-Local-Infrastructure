<#import "template.ftl" as layout>
<@layout.registrationLayout displayMessage=!messagesPerField.existsError('username','email','fullName','password','password-confirm'); section>
    <#if section = "header">
        Đăng ký tài khoản GoatSports
    <#elseif section = "form">
        <form id="kc-register-form" action="${url.registrationAction}" method="post">
            
            <!-- Họ và Tên (fullName) -->
            <div class="form-group">
                <label for="fullName">Họ và Tên</label>
                <div class="input-wrapper">
                    <i class="input-icon" data-lucide="user-check"></i>
                    <input id="fullName" 
                           class="<#if messagesPerField.existsError('fullName')>input-error</#if>" 
                           name="fullName" 
                           value="${(register.formData.fullName!'')}" 
                           type="text" 
                           placeholder="Nhập họ và tên của bạn" 
                           required />
                </div>
                <#if messagesPerField.existsError('fullName')>
                    <span class="error-text">${kcSanitize(messagesPerField.getFirstError('fullName'))?no_esc}</span>
                </#if>
            </div>

            <!-- Tên đăng nhập (username) -->
            <div class="form-group">
                <label for="username">Tên đăng nhập</label>
                <div class="input-wrapper">
                    <i class="input-icon" data-lucide="user"></i>
                    <input id="username" 
                           class="<#if messagesPerField.existsError('username')>input-error</#if>" 
                           name="username" 
                           value="${(register.formData.username!'')}" 
                           type="text" 
                           autocomplete="username"
                           placeholder="Nhập tên đăng nhập" 
                           required />
                </div>
                <#if messagesPerField.existsError('username')>
                    <span class="error-text">${kcSanitize(messagesPerField.getFirstError('username'))?no_esc}</span>
                </#if>
            </div>

            <!-- Địa chỉ Email (email) -->
            <div class="form-group">
                <label for="email">Địa chỉ Email</label>
                <div class="input-wrapper">
                    <i class="input-icon" data-lucide="mail"></i>
                    <input id="email" 
                           class="<#if messagesPerField.existsError('email')>input-error</#if>" 
                           name="email" 
                           value="${(register.formData.email!'')}" 
                           type="email" 
                           autocomplete="email"
                           placeholder="Nhập địa chỉ email của bạn" 
                           required />
                </div>
                <#if messagesPerField.existsError('email')>
                    <span class="error-text">${kcSanitize(messagesPerField.getFirstError('email'))?no_esc}</span>
                </#if>
            </div>

            <!-- Mật khẩu (password) -->
            <div class="form-group">
                <label for="password">Mật khẩu</label>
                <div class="input-wrapper">
                    <i class="input-icon" data-lucide="lock"></i>
                    <input id="password" 
                           class="<#if messagesPerField.existsError('password')>input-error</#if>" 
                           name="password" 
                           type="password" 
                           autocomplete="new-password"
                           placeholder="Tạo mật khẩu an toàn" 
                           required />
                </div>
                <#if messagesPerField.existsError('password')>
                    <span class="error-text">${kcSanitize(messagesPerField.getFirstError('password'))?no_esc}</span>
                </#if>
            </div>

            <!-- Xác nhận mật khẩu (password-confirm) -->
            <div class="form-group">
                <label for="password-confirm">Xác nhận mật khẩu</label>
                <div class="input-wrapper">
                    <i class="input-icon" data-lucide="shield-check"></i>
                    <input id="password-confirm" 
                           class="<#if messagesPerField.existsError('password-confirm')>input-error</#if>" 
                           name="password-confirm" 
                           type="password" 
                           autocomplete="new-password"
                           placeholder="Nhập lại mật khẩu để xác nhận" 
                           required />
                </div>
                <#if messagesPerField.existsError('password-confirm')>
                    <span class="error-text">${kcSanitize(messagesPerField.getFirstError('password-confirm'))?no_esc}</span>
                </#if>
            </div>

            <!-- Submit -->
            <button type="submit">Đăng ký tài khoản</button>
        </form>
    <#elseif section = "info">
        <div class="footer-wrapper">
            Bạn đã có tài khoản? <a href="${url.loginUrl}">Đăng nhập ngay</a>
        </div>
    </#if>
</@layout.registrationLayout>
