#!/usr/bin/env bash
# Gỡ Home Dash khỏi tivi và trả nút Home về màn hình chính trước đó.   ./uninstall.sh 192.168.1.50
set -euo pipefail
PKG=vn.homedash.tv
HERE=$(cd "$(dirname "$0")" && pwd)
fail() { echo "LỖI: $*" >&2; exit 1; }
step() { echo "==> $*"; }

TV_IP=${1:-}
ADB=${ADB:-}
[ -z "$ADB" ] && [ -x "$HERE/platform-tools/adb" ] && ADB="$HERE/platform-tools/adb"
[ -z "$ADB" ] && ADB=$(command -v adb || true)
[ -n "$ADB" ] || fail "không thấy adb (xem README, mục Chuẩn bị)."

TARGET=()
if [ -n "$TV_IP" ]; then
  case "$TV_IP" in *:*) ;; *) TV_IP="$TV_IP:5555" ;; esac
  "$ADB" connect "$TV_IP" || true
  TARGET=(-s "$TV_IP")
fi
tv() { "$ADB" ${TARGET[@]+"${TARGET[@]}"} shell "$1" 2>&1 | tr -d '\r'; }
state=$("$ADB" ${TARGET[@]+"${TARGET[@]}"} get-state 2>&1 || true)
[ "$state" = device ] || fail "chưa kết nối được tivi ($state)."

# Màn hình chính cũ: bản ghi lúc cài, nếu không có thì CleanYourTV hoặc launcher gốc còn bật.
record="$HERE/previous-home-$(printf '%s' "$TV_IP" | tr -c 'A-Za-z0-9._-' '_').txt"
prev=""
[ -f "$record" ] && prev=$(head -n 1 "$record")
if [ -z "$prev" ]; then
  all=$(tv 'cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME')
  for p in com.gathin.tvhome com.google.android.apps.tv.launcherx com.google.android.tvlauncher com.google.android.leanbacklauncher com.mitv.tvhome; do
    prev=$(printf '%s\n' "$all" | grep -o "$p/[A-Za-z0-9_.\$]*" | head -n 1 || true)
    [ -n "$prev" ] && break
  done
fi
if [ -n "$prev" ]; then
  step "Trả nút Home về $prev"
  tv "cmd package set-home-activity --user 0 $prev" >/dev/null
fi

step "Gỡ Home Dash"
res=$("$ADB" ${TARGET[@]+"${TARGET[@]}"} uninstall "$PKG" 2>&1 || true)
echo "$res"
case "$res" in *Success*) ;; *) fail "gỡ không thành công (xem dòng trên)." ;; esac
rm -f "$record"
step "Xong. Nút Home giờ mở: $(tv 'cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME' | tail -n 1)"
