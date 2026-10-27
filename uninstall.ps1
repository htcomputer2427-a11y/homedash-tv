<#
Gỡ Home Dash khỏi tivi và trả nút Home về màn hình chính trước đó.

  .\uninstall.ps1 -TvIp 192.168.1.50
#>
param([string]$TvIp = "")
# Continue, not Stop: Windows PowerShell 5.1 turns any adb line on stderr into an error when output is captured.
$ErrorActionPreference = "Continue"
$Pkg = "vn.homedash.tv"
$Here = Split-Path -Parent $MyInvocation.MyCommand.Path

function Fail([string]$msg) { Write-Host "LỖI: $msg" -ForegroundColor Red; exit 1 }
function Step([string]$msg) { Write-Host "==> $msg" -ForegroundColor Cyan }

$Adb = $env:ADB
if (-not $Adb) {
    foreach ($c in @("$Here\platform-tools\adb.exe", "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe")) {
        if (Test-Path $c) { $Adb = $c; break }
    }
}
if (-not $Adb) {
    $cmd = Get-Command adb -ErrorAction SilentlyContinue
    if ($cmd) { $Adb = $cmd.Source }
}
if (-not $Adb) { Fail "không thấy adb (xem README, mục Chuẩn bị)." }

$Target = @()
if ($TvIp) {
    if ($TvIp -notmatch ":") { $TvIp = "${TvIp}:5555" }
    & $Adb connect $TvIp | Out-Host
    $Target = @("-s", $TvIp)
}
function Tv([string]$cmd) { return (& $Adb @Target shell $cmd 2>&1 | Out-String).Trim() }

$state = (& $Adb @Target get-state 2>&1 | Out-String).Trim()
if ($state -ne "device") { Fail "chưa kết nối được tivi ($state)." }

# Màn hình chính cũ: bản ghi lúc cài, nếu không có thì CleanYourTV hoặc launcher gốc còn bật.
$record = Join-Path $Here ("previous-home-" + ($TvIp -replace "[^A-Za-z0-9._-]", "_") + ".txt")
$prev = ""
if (Test-Path $record) { $prev = (Get-Content $record -Raw).Trim() }
if (-not $prev) {
    $all = Tv "cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME"
    foreach ($p in @("com.gathin.tvhome", "com.google.android.apps.tv.launcherx", "com.google.android.tvlauncher", "com.google.android.leanbacklauncher", "com.mitv.tvhome")) {
        $m = [regex]::Match($all, [regex]::Escape($p) + "/[A-Za-z0-9_.$]+")
        if ($m.Success) { $prev = $m.Value; break }
    }
}
if ($prev) {
    Step "Trả nút Home về $prev"
    Tv "cmd package set-home-activity --user 0 $prev" | Out-Null
}

Step "Gỡ Home Dash"
$res = (& $Adb @Target uninstall $Pkg 2>&1 | Out-String).Trim()
Write-Host $res
if ($res -notmatch "Success") { Fail "gỡ không thành công (xem dòng trên)." }
if (Test-Path $record) { Remove-Item $record }
$now = ((Tv "cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME") -split "`n")[-1].Trim()
Step "Xong. Nút Home giờ mở: $now"
