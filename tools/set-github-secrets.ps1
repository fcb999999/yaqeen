# يضع أسرار الإصدار في مستودع GitHub من الملفات المحلية (لا يطبع أي قيمة).
# يُشغَّل مرة واحدة بيد المالك:  powershell -File tools\set-github-secrets.ps1
# المتطلبات: gh مسجَّل الدخول بالحساب مالك المستودع، ووجود:
#   ~\.yaqeen\keystore.properties  (storeFile, storePassword, keyAlias, keyPassword)
#   app\google-services.json
param(
    [string]$Repo = "fcb999999/yaqeen",
    [string]$PropsFile = "$env:USERPROFILE\.yaqeen\keystore.properties"
)
$ErrorActionPreference = "Stop"

$props = @{}
Get-Content $PropsFile | Where-Object { $_ -match "=" } | ForEach-Object {
    $i = $_.IndexOf("=")
    $props[$_.Substring(0, $i).Trim()] = $_.Substring($i + 1).Trim()
}
foreach ($k in "storeFile", "storePassword", "keyAlias", "keyPassword") {
    if (-not $props[$k]) { throw "المفتاح $k غير موجود في $PropsFile" }
}
$gsj = Join-Path (Split-Path $PSScriptRoot -Parent) "app\google-services.json"
if (-not (Test-Path $gsj)) { throw "الملف غير موجود: $gsj" }

[Convert]::ToBase64String([IO.File]::ReadAllBytes($props["storeFile"])) | gh secret set ANDROID_KEYSTORE_BASE64 --repo $Repo
$props["storePassword"] | gh secret set ANDROID_KEYSTORE_PASSWORD --repo $Repo
$props["keyAlias"]      | gh secret set ANDROID_KEY_ALIAS --repo $Repo
$props["keyPassword"]   | gh secret set ANDROID_KEY_PASSWORD --repo $Repo
Get-Content $gsj -Raw   | gh secret set GOOGLE_SERVICES_JSON --repo $Repo

"الأسرار المضبوطة الآن (أسماء فقط):"
gh secret list --repo $Repo
