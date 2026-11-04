<#
.SYNOPSIS
  يرفع رقم إصدار يقين في gradle.properties ويضيف قالب إدخال جديد في أعلى CHANGELOG.md.

.DESCRIPTION
  - yaqeen.versionName  : يُحسب من -Bump (patch|minor|major) أو يُعطى صراحة بـ -Version.
  - yaqeen.versionCode  : يزيد بمقدار 1 دائمًا (أو يُعطى بـ -VersionCode ويجب أن يكون أكبر من الحالي).
  - CHANGELOG.md        : يُنشأ إن لم يوجد، ويُضاف قسم "## [x.y.z] - التاريخ" بقالب (الجديد/التحسينات/الإصلاحات).
  لا ينفّذ أي أمر git؛ يطبع الأوامر التالية فقط. -DryRun يعرض ما سيتغير دون كتابة أي ملف.

.EXAMPLE
  powershell -File tools/release.ps1 -Bump minor -DryRun
  powershell -File tools/release.ps1 -Version 1.0.0
#>
[CmdletBinding()]
param(
    [ValidateSet('patch', 'minor', 'major')][string]$Bump = 'patch',
    [string]$Version,
    [int]$VersionCode,
    [string]$Date = (Get-Date -Format 'yyyy-MM-dd'),
    [switch]$DryRun
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$propsPath = Join-Path $root 'gradle.properties'
$changelogPath = Join-Path $root 'CHANGELOG.md'
$utf8NoBom = New-Object System.Text.UTF8Encoding($false)

function Get-NextVersion([string]$current, [string]$bump) {
    if ($current -notmatch '^(\d+)\.(\d+)\.(\d+)$') { throw "رقم الإصدار الحالي '$current' ليس بصيغة x.y.z" }
    $maj = [int]$Matches[1]; $min = [int]$Matches[2]; $pat = [int]$Matches[3]
    switch ($bump) {
        'major' { return "$($maj + 1).0.0" }
        'minor' { return "$maj.$($min + 1).0" }
        default { return "$maj.$min.$($pat + 1)" }
    }
}

if (-not (Test-Path $propsPath)) { throw "لم يُعثر على $propsPath" }
$props = [System.IO.File]::ReadAllText($propsPath)
$props = $props.TrimStart([char]0xFEFF)   # إزالة BOM إن وُجد (gradle.properties يجب أن يكون ASCII)

if ($props -notmatch '(?m)^yaqeen\.versionName=(.+?)\s*$') { throw 'yaqeen.versionName غير موجود في gradle.properties' }
$curName = $Matches[1].Trim()
if ($props -notmatch '(?m)^yaqeen\.versionCode=(\d+)\s*$') { throw 'yaqeen.versionCode غير موجود في gradle.properties' }
$curCode = [int]$Matches[1]

$newName = if ($Version) { $Version.TrimStart('v') } else { Get-NextVersion $curName $Bump }
if ($newName -notmatch '^\d+\.\d+\.\d+$') { throw "الإصدار الجديد '$newName' ليس بصيغة x.y.z" }
$newCode = if ($VersionCode) { $VersionCode } else { $curCode + 1 }
if ($newCode -le $curCode) { throw "versionCode الجديد ($newCode) يجب أن يكون أكبر من الحالي ($curCode)" }
if ($newName -eq $curName) { throw "الإصدار الجديد مطابق للحالي ($curName)" }

$props = [regex]::Replace($props, '(?m)^yaqeen\.versionName=.*$', "yaqeen.versionName=$newName")
$props = [regex]::Replace($props, '(?m)^yaqeen\.versionCode=.*$', "yaqeen.versionCode=$newCode")

$entry = @"
## [$newName] - $Date

### الجديد
-

### التحسينات
-

### الإصلاحات
-

"@

$header = "# سجل التغييرات`n`nتُنشر كل قسم من هذا الملف كملاحظات للإصدار المقابل على GitHub (العنوان بصيغة ``## [x.y.z] - التاريخ``).`n`n"
if (Test-Path $changelogPath) {
    $existing = [System.IO.File]::ReadAllText($changelogPath).TrimStart([char]0xFEFF) -replace "`r`n", "`n"
    if ($existing -match "(?m)^##\s+\[?v?$([regex]::Escape($newName))\]?(\s|$)") { throw "CHANGELOG.md يحتوي أصلًا على قسم للإصدار $newName" }
    # القسم الجديد يوضع فوق أحدث إصدار منشور (وتحت قسم "غير منشور" إن وُجد).
    $verHeading = '(?m)^##\s+\[?v?\d'
    $idx = [regex]::Match($existing, $verHeading).Index
    if ($existing -match $verHeading) { $newChangelog = $existing.Substring(0, $idx) + $entry.Replace("`r`n", "`n") + "`n" + $existing.Substring($idx) }
    else { $newChangelog = $existing.TrimEnd() + "`n`n" + $entry.Replace("`r`n", "`n") }
} else {
    $newChangelog = $header + $entry.Replace("`r`n", "`n")
}

Write-Host "الإصدار:   $curName ($curCode)  ->  $newName ($newCode)"
if ($DryRun) {
    Write-Host '--- DryRun: لن تُكتب ملفات ---'
    Write-Host "gradle.properties: yaqeen.versionName=$newName ; yaqeen.versionCode=$newCode"
    Write-Host "CHANGELOG.md: سيُضاف القسم التالي:`n"
    Write-Host $entry
    exit 0
}

[System.IO.File]::WriteAllText($propsPath, $props, $utf8NoBom)
[System.IO.File]::WriteAllText($changelogPath, $newChangelog, $utf8NoBom)

Write-Host ''
Write-Host 'تم. الخطوات التالية (تنفّذها أنت):'
Write-Host "  1) عدّل قسم [$newName] في CHANGELOG.md واملأ الملاحظات (تصبح نص الإصدار على GitHub)."
Write-Host "  2) git add gradle.properties CHANGELOG.md"
Write-Host "  3) git commit -m `"release: v$newName`""
Write-Host "  4) git tag v$newName"
Write-Host "  5) git push origin main v$newName      # الوسم يشغّل workflow الإصدار"
