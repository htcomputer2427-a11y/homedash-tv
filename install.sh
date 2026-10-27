#!/usr/bin/env bash
# Cài Home Dash lên tivi Android TV / Google TV qua Wi-Fi và đặt làm màn hình chính (macOS, Linux).
#
#   ./install.sh 192.168.1.50
#   ./install.sh 192.168.1.50 --wallpaper ~/anh/nen.jpg
#   ./install.sh 192.168.1.50 --app-only     # chỉ cài app, không đổi màn hình chính
#
# Cần: adb (Android SDK Platform-Tools) và Gỡ lỗi USB/ADB đã bật trên tivi. Gỡ ra: ./uninstall.sh <IP>
set -euo pipefail
PKG=vn.homedash.tv
MAIN=vn.homedash.tv/.DashboardActivity
HERE=$(cd "$(dirname "$0")" && pwd)
HOME_QUERY='cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME'

fail() { echo "LỖI: $*" >&2; exit 1; }
step() { echo "==> $*"; }

TV_IP=""; APK=""; WALLPAPER=""; APP_ONLY=0
while [ $# -gt 0 ]; do
  case "$1" in
    --apk) APK=$2; shift 2 ;;
    --wallpaper) WALLPAPER=$2; shift 2 ;;
    --app-only) APP_ONLY=1; shift ;;
    -*) fail "không hiểu tham số $1" ;;
    *) TV_IP=$1; shift ;;
  esac
done

ADB=${ADB:-}
[ -z "$ADB" ] && [ -x "$HERE/platform-tools/adb" ] && ADB="$HERE/platform-tools/adb"
[ -z "$ADB" ] && ADB=$(command -v adb || true)
[ -n "$ADB" ] || fail "không thấy adb. Tải SDK Platform-Tools tại https://developer.android.com/tools/releases/platform-tools, giải nén platform-tools vào cạnh file này."

if [ -z "$APK" ]; then
  for c in "$HERE/HomeDash.apk" "$HERE/app/build/outputs/apk/release/app-release.apk"; do
    [ -f "$c" ] && { APK=$c; break; }
  done
fi
[ -n "$APK" ] && [ -f "$APK" ] || fail "không thấy file APK. Tải HomeDash.apk ở mục Releases trên GitHub, để cạnh file này."

TARGET=()
if [ -n "$TV_IP" ]; then
  case "$TV_IP" in *:*) ;; *) TV_IP="$TV_IP:5555" ;; esac
  step "Kết nối tới tivi $TV_IP (nếu tivi hỏi 'Cho phép gỡ lỗi', chọn Luôn cho phép rồi OK)"
  "$ADB" connect "$TV_IP" || true
  TARGET=(-s "$TV_IP")
fi
tv() { "$ADB" ${TARGET[@]+"${TARGET[@]}"} shell "$1" 2>&1 | tr -d '\r'; }

state=""
for _ in $(seq 30); do
  state=$("$ADB" ${TARGET[@]+"${TARGET[@]}"} get-state 2>&1 || true)
  [ "$state" = device ] && break
  sleep 2
done
[ "$state" = device ] || fail "tivi chưa cho phép kết nối ($state). Bật Gỡ lỗi USB/ADB, bấm Cho phép trên tivi, rồi chạy lại."

sdk=$(tv 'getprop ro.build.version.sdk')
step "Tivi: $(tv 'getprop ro.product.model'), Android SDK $sdk"
[ "${sdk:-0}" -ge 26 ] || fail "Home Dash cần Android 8.0 trở lên (SDK 26); tivi này là SDK $sdk."

step "Cài $(basename "$APK")"
res=$("$ADB" ${TARGET[@]+"${TARGET[@]}"} install -r "$APK" 2>&1 || true)
echo "$res"
case "$res" in
  *INSTALL_FAILED_UPDATE_INCOMPATIBLE*) fail "trên tivi đã có một bản Home Dash ký bằng khóa khác. Gỡ bản cũ (./uninstall.sh) rồi cài lại." ;;
  *Success*) ;;
  *) fail "cài không thành công (xem dòng trên)." ;;
esac

# Thẻ nhạc đọc được bài YouTube đang phát nhờ quyền đọc thông báo.
tv "cmd notification allow_listener $PKG/$PKG.MediaListener" >/dev/null

if [ -n "$WALLPAPER" ]; then
  [ -f "$WALLPAPER" ] || fail "không thấy ảnh nền $WALLPAPER"
  step "Chép ảnh nền"
  tv "mkdir -p /sdcard/Android/data/$PKG/files" >/dev/null
  "$ADB" ${TARGET[@]+"${TARGET[@]}"} push "$WALLPAPER" "/sdcard/Android/data/$PKG/files/wallpaper.jpg"
fi

if [ "$APP_ONLY" = 1 ]; then
  step "Xong. Home Dash đã cài như một app bình thường (không đổi màn hình chính)."
  exit 0
fi

before=$(tv "$HOME_QUERY" | tail -n 1)
record="$HERE/previous-home-$(printf '%s' "$TV_IP" | tr -c 'A-Za-z0-9._-' '_').txt"
case "$before" in "$PKG"/*|"") ;; *) printf '%s\n' "$before" > "$record" ;; esac
step "Màn hình chính hiện tại: $before"

# Mở thử trước khi giao nút Home: nếu app không mở được thì màn hình chính cũ vẫn giữ nguyên.
start=$(tv "am start -W -a android.intent.action.MAIN -c android.intent.category.HOME -n $MAIN")
if ! printf '%s' "$start" | grep -qF 'Status: ok' || printf '%s' "$start" | grep -q '^Error'; then
  fail "Home Dash không mở được trên tivi này; màn hình chính cũ vẫn giữ nguyên.
$start"
fi

tv "cmd package set-home-activity --user 0 $MAIN" >/dev/null
after=$(tv "$HOME_QUERY" | tail -n 1)
case "$after" in
  "$PKG"/*) step "Xong. Bấm nút Home trên remote để về Home Dash. Lần đầu, app sẽ hỏi lời chào và nơi xem thời tiết." ;;
  *)
    echo
    echo "Home Dash đã cài, nhưng nút Home vẫn thuộc về: $after"
    echo "Trên nhiều tivi Google TV, launcher gốc giữ nút Home. Cách làm: cài CleanYourTV trước"
    echo "(https://github.com/Gathin23/cleanyourtv, nó tắt launcher gốc an toàn và có lệnh khôi phục), rồi chạy lại script này."
    echo "Trong lúc đó vẫn mở được Home Dash từ danh sách app của tivi." ;;
esac
