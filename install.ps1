<#
Cài Home Dash lên tivi Android TV / Google TV qua Wi-Fi và đặt làm màn hình chính.

  .\install.ps1 -TvIp 192.168.1.50
  .\install.ps1 -TvIp 192.168.1.50 -Wallpaper D:\anh\nen.jpg
  .\install.ps1 -TvIp 192.168.1.50 -AppOnly        # chỉ cài app, không đổi màn hình chính

Cần: adb (Android SDK Platform-Tools của Google) và "Gỡ lỗi USB" / "Gỡ lỗi ADB" đã bật trên tivi.
Gỡ ra: .\uninstall.ps1 -TvIp <IP>
#>
param(
    [string]$TvIp = "",
    [string]$Apk = "",
    [string]$Wallpaper = "",
    [switch]$AppOnly
)
# Continue, not Stop: Windows PowerShell 5.1 turns any adb line on stderr into an error when output is captured.
$ErrorActionPreference = "Continue"
$Pkg = "vn.homedash.tv"
$Main = "vn.homedash.tv/.DashboardActivity"
$Here = Split-Path -Parent $MyInvocation.MyCommand.Path

function Fail([string]$msg) { Write-Host "LỖI: $msg" -ForegroundColor Red; exit 1 }
function Step([string]$msg) { Write-Host "==> $msg" -ForegroundColor Cyan }

# adb: biến ADB, thư mục platform-tools cạnh script, hoặc PATH.
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
if (-not $Adb) {
    Fail "không thấy adb. Tải 'SDK Platform-Tools for Windows' tại https://developer.android.com/tools/releases/platform-tools, giải nén thư mục platform-tools vào cạnh file này rồi chạy lại."
}

# APK: tham số -Apk, HomeDash.apk cạnh script (bản tải từ Releases), hoặc bản tự build.
if (-not $Apk) {
    foreach ($c in @("$Here\HomeDash.apk", "$Here\app\build\outputs\apk\release\app-release.apk")) {
        if (Test-Path $c) { $Apk = $c; break }
    }
}
if (-not $Apk -or -not (Test-Path $Apk)) { Fail "không thấy file APK. Tải HomeDash.apk ở mục Releases trên GitHub, để cạnh file này." }

$Target = @()
if ($TvIp) {
    if ($TvIp -notmatch ":") { $TvIp = "${TvIp}:5555" }
    Step "Kết nối tới tivi $TvIp (nếu tivi hỏi 'Cho phép gỡ lỗi', chọn Luôn cho phép rồi OK)"
    & $Adb connect $TvIp | Out-Host
    $Target = @("-s", $TvIp)
}

function Tv([string]$cmd) {
    $out = & $Adb @Target shell $cmd 2>&1 | Out-String
    return $out.Trim()
}

# Chờ tivi cho phép (tối đa 60 giây).
$ok = $false
for ($i = 0; $i -lt 30; $i++) {
    $state = (& $Adb @Target get-state 2>&1 | Out-String).Trim()
    if ($state -eq "device") { $ok = $true; break }
    Start-Sleep -Seconds 2
}
if (-not $ok) { Fail "tivi chưa cho phép kết nối ($state). Bật Gỡ lỗi USB/ADB, bấm Cho phép trên tivi, rồi chạy lại." }

$sdk = [int](Tv "getprop ro.build.version.sdk")
$model = Tv "getprop ro.product.model"
Step "Tivi: $model, Android SDK $sdk"
if ($sdk -lt 26) { Fail "Home Dash cần Android 8.0 trở lên (SDK 26); tivi này là SDK $sdk." }

Step "Cài $([IO.Path]::GetFileName($Apk))"
$res = & $Adb @Target install -r $Apk 2>&1 | Out-String
Write-Host $res.Trim()
if ($res -match "INSTALL_FAILED_UPDATE_INCOMPATIBLE") {
    Fail "trên tivi đã có một bản Home Dash ký bằng khóa khác. Gỡ bản cũ (.\uninstall.ps1) rồi cài lại."
}
if ($res -notmatch "Success") { Fail "cài không thành công (xem dòng trên)." }

# Thẻ nhạc đọc được bài YouTube đang phát nhờ quyền đọc thông báo.
Tv "cmd notification allow_listener $Pkg/$Pkg.MediaListener" | Out-Null

if ($Wallpaper) {
    if (-not (Test-Path $Wallpaper)) { Fail "không thấy ảnh nền $Wallpaper" }
    Step "Chép ảnh nền"
    Tv "mkdir -p /sdcard/Android/data/$Pkg/files" | Out-Null
    & $Adb @Target push $Wallpaper "/sdcard/Android/data/$Pkg/files/wallpaper.jpg" | Out-Host
}

if ($AppOnly) {
    Step "Xong. Home Dash đã cài như một app bình thường (không đổi màn hình chính)."
    exit 0
}

$homeQuery = "cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME"
$before = ((Tv $homeQuery) -split "`n")[-1].Trim()
$record = Join-Path $Here ("previous-home-" + ($TvIp -replace "[^A-Za-z0-9._-]", "_") + ".txt")
if ($before -and $before -notlike "$Pkg/*") { Set-Content -Path $record -Value $before -Encoding ASCII }
Step "Màn hình chính hiện tại: $before"

# Mở thử trước khi giao nút Home: nếu app không mở được thì màn hình chính cũ vẫn giữ nguyên.
$start = Tv "am start -W -a android.intent.action.MAIN -c android.intent.category.HOME -n $Main"
if ($start -notmatch "Status: ok" -or $start -match "(?m)^Error") {
    Fail "Home Dash không mở được trên tivi này; màn hình chính cũ vẫn giữ nguyên.`n$start"
}

Tv "cmd package set-home-activity --user 0 $Main" | Out-Null
$after = ((Tv $homeQuery) -split "`n")[-1].Trim()
if ($after -like "$Pkg/*") {
    Step "Xong. Bấm nút Home trên remote để về Home Dash. Lần đầu, app sẽ hỏi lời chào và nơi xem thời tiết."
} else {
    Write-Host ""
    Write-Host "Home Dash đã cài, nhưng nút Home vẫn thuộc về: $after" -ForegroundColor Yellow
    Write-Host "Trên nhiều tivi Google TV, launcher gốc giữ nút Home. Cách làm: cài CleanYourTV trước"
    Write-Host "(https://github.com/Gathin23/cleanyourtv, nó tắt launcher gốc an toàn và có lệnh khôi phục), rồi chạy lại script này."
    Write-Host "Trong lúc đó vẫn mở được Home Dash từ danh sách app."
}
