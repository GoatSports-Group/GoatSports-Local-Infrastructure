<#macro registrationLayout bodyClass="" displayInfo=false displayMessage=true displayRequiredFields=false showAnotherWayIfPresent=true>
<!DOCTYPE html>
<html lang="vi">
<head>
    <meta charset="utf-8">
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
    <meta name="robots" content="noindex, nofollow">
    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
    <title>GoatSports - SSO Authentication</title>
    
    <#if properties.styles?has_content>
        <#list properties.styles?split(' ') as style>
            <link href="${url.resourcesPath}/${style}" rel="stylesheet" />
        </#list>
    </#if>
    
    <!-- Lucide Icons CDN -->
    <script src="https://unpkg.com/lucide@latest"></script>
</head>
<body>
    <div class="login-container">
        <!-- Logo and Heading -->
        <div class="header-wrapper">
            <img class="brand-logo" src="${url.resourcesPath}/images/Goat.png" alt="GoatSports Logo" onerror="this.style.display='none'; document.getElementById('brand-text-fallback').style.display='block';"/>
            <div id="brand-text-fallback" style="display: none;" class="brand-title">GOATSPORTS</div>
            <div class="brand-subtitle">
                <#nested "header">
            </div>
        </div>

        <!-- Global Alert Messages (Error, Success, etc.) -->
        <#if displayMessage && message?? && (message.type != 'warning' || !isAppInitiatedAction??)>
            <div class="alert alert-${message.type}">
                <#if message.type = 'success'>
                    <i class="alert-icon" data-lucide="check-circle-2"></i>
                <#elseif message.type = 'warning'>
                    <i class="alert-icon" data-lucide="alert-triangle"></i>
                <#elseif message.type = 'error'>
                    <i class="alert-icon" data-lucide="alert-circle"></i>
                <#else>
                    <i class="alert-icon" data-lucide="info"></i>
                </#if>
                <span>${kcSanitize(message.summary)?no_esc}</span>
            </div>
        </#if>

        <!-- Nested Content (Form inputs, action buttons, links) -->
        <#nested "form">

        <!-- Additional info links -->
        <#nested "info">

        <!-- Footer -->
        <div class="footer-wrapper">
            <p>&copy; ${.now?string('yyyy')} GoatSports. All rights reserved.</p>
        </div>
    </div>

    <!-- Initialize Lucide Icons -->
    <script>
        lucide.createIcons();
    </script>
</body>
</html>
</#macro>
