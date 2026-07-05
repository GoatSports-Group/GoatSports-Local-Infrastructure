<#import "template.ftl" as layout>
<@layout.registrationLayout displayInfo=social.providers?? displayMessage=!messagesPerField.existsError('username','password'); section>
    <#if section = "header">
        Đăng nhập vào GoatSports
    <#elseif section = "form">
        <form id="kc-form-login" onsubmit="login.disabled = true; return true;" action="${url.loginAction}" method="post">
            
            <!-- Tên đăng nhập hoặc Email -->
            <div class="form-group">
                <label for="username">Email hoặc Tên đăng nhập</label>
                <div class="input-wrapper">
                    <i class="input-icon" data-lucide="user"></i>
                    <input id="username" 
                           class="<#if messagesPerField.existsError('username')>input-error</#if>" 
                           name="username" 
                           value="${(login.username!'')}" 
                           type="text" 
                           autofocus 
                           autocomplete="username" 
                           placeholder="Nhập email hoặc tên đăng nhập" 
                           required />
                </div>
                <#if messagesPerField.existsError('username')>
                    <span class="error-text">${kcSanitize(messagesPerField.getFirstError('username'))?no_esc}</span>
                </#if>
            </div>

            <!-- Mật khẩu -->
            <div class="form-group">
                <label for="password">Mật khẩu</label>
                <div class="input-wrapper">
                    <i class="input-icon" data-lucide="lock"></i>
                    <input id="password" 
                           class="<#if messagesPerField.existsError('password')>input-error</#if>" 
                           name="password" 
                           type="password" 
                           autocomplete="current-password" 
                           placeholder="Nhập mật khẩu của bạn" 
                           required />
                </div>
                <#if messagesPerField.existsError('password')>
                    <span class="error-text">${kcSanitize(messagesPerField.getFirstError('password'))?no_esc}</span>
                </#if>
            </div>

            <!-- Tiện ích và Quên mật khẩu -->
            <div class="actions-row">
                <#if realm.rememberMe && !usernameEditDisabled??>
                    <label class="checkbox-label">
                        <input id="rememberMe" name="rememberMe" type="checkbox" <#if login.rememberMe??>checked</#if>>
                        Ghi nhớ đăng nhập
                    </label>
                <#else>
                    <div></div>
                </#if>
                <#if realm.resetPasswordAllowed>
                    <a href="${url.loginResetCredentialsUrl}">Quên mật khẩu?</a>
                </#if>
            </div>

            <!-- Nút submit -->
            <button id="kc-login" name="login" type="submit">Đăng nhập</button>
        </form>
    <#elseif section = "info">
        <#if realm.password && realm.registrationAllowed && !registrationDisabled??>
            <div class="footer-wrapper">
                Bạn chưa có tài khoản? <a href="${url.registrationUrl}">Đăng ký ngay</a>
            </div>
        </#if>
    </#if>
</@layout.registrationLayout>
