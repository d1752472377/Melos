#!/usr/bin/env bash
#
# Melos APK 镜像同步：把「版本信息所在的后端」（阿里云 3M 小水管）上的最新安装包
# 拉到本机（大带宽镜像），对外用 http://<mirror>/apk/<file> 直链提供下载。
#
# 为什么这样分工：
#  - 阿里云那台只有 3M 上行（实测 ~430KB/s），8.5MB 的包要 20 秒以上，弱网下经常断；
#  - 这台机器带宽大得多，但不在同一个内网里跑后端（版本库在阿里云）。
# 于是：**版本 JSON 仍由阿里云给（几百字节），安装包走本机（大文件）**。
# 客户端拿到的是绝对直链，跟哪个域名/端口无关，换镜像不用发版。
#
# 幂等：文件已存在且大小一致就跳过；只有新版本才下载；同步完只保留最新一个包。
set -euo pipefail

API_BASE="${MELOS_API_BASE:-http://39.96.39.134}"
DEST_DIR="${MELOS_APK_DIR:-/var/www/apk}"
LOCK="/tmp/melos-apk-sync.lock"

mkdir -p "$DEST_DIR"

# 单实例运行：上一次还在下（8.5MB 在 3M 上行要 20 秒以上）时不要叠加
exec 9>"$LOCK"
if ! flock -n 9; then
  echo "[$(date '+%F %T')] 上一次同步仍在进行，跳过"
  exit 0
fi

json="$(curl -fsS --max-time 25 "$API_BASE/api/app/version/latest" || true)"
if [ -z "$json" ]; then
  echo "[$(date '+%F %T')] 拉取版本信息失败，跳过本轮"
  exit 0
fi

# 用 python3 解析（系统自带），取相对路径与大小
parsed="$(printf '%s' "$json" | python3 -c '
import json, sys
d = json.load(sys.stdin).get("data") or {}
rel = d.get("apk_url_fallback") or d.get("apkUrl") or d.get("apk_url") or ""
size = d.get("file_size") or d.get("fileSize") or 0
ver = d.get("version_name") or d.get("versionName") or ""
print(rel)
print(size)
print(ver)
')"
rel="$(printf '%s\n' "$parsed" | sed -n '1p')"
size="$(printf '%s\n' "$parsed" | sed -n '2p')"
ver="$(printf '%s\n' "$parsed" | sed -n '3p')"

if [ -z "$rel" ]; then
  echo "[$(date '+%F %T')] 后端没有可用安装包路径，跳过"
  exit 0
fi

case "$rel" in
  http://*|https://*) url="$rel" ;;
  /*)                 url="$API_BASE$rel" ;;
  *)                  url="$API_BASE/$rel" ;;
esac

name="$(basename "$rel")"
target="$DEST_DIR/$name"

if [ -f "$target" ] && [ "$(stat -c%s "$target")" = "$size" ]; then
  echo "[$(date '+%F %T')] 已是最新 $name（$size 字节），无需下载"
else
  echo "[$(date '+%F %T')] 开始同步 $ver → $name（$size 字节）"
  tmp="$DEST_DIR/.$name.part"
  rm -f "$tmp"
  # 3M 上行 + 断点：失败重试 3 次，单次最多 10 分钟
  curl -fL --retry 3 --retry-delay 5 --connect-timeout 20 --max-time 600 -o "$tmp" "$url"
  if [ "$size" != "0" ] && [ "$(stat -c%s "$tmp")" != "$size" ]; then
    echo "[$(date '+%F %T')] 大小不一致，丢弃本次下载" >&2
    rm -f "$tmp"
    exit 1
  fi
  mv -f "$tmp" "$target"
  echo "[$(date '+%F %T')] 同步完成 $name"
fi

# 只保留最新一个安装包（镜像机磁盘不大）
find "$DEST_DIR" -maxdepth 1 -type f -name '*.apk' ! -name "$name" -print -delete || true
