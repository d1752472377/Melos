#!/bin/sh
# 部署 music-admin 前端；失败可回滚（备份保留在 /var/www）
set -u

SRC=/tmp/music-admin-dist
DST=/var/www/music-admin
TS=$(date +%Y%m%d_%H%M%S)

echo "== 0) verify uploaded dist =="
if [ ! -f "$SRC/index.html" ]; then
  echo "!! $SRC/index.html 不存在，中止"
  exit 2
fi
ls -la "$SRC"
ls -la "$SRC/assets"

echo "== 1) confirm container mount for /var/www/music-admin =="
NPID=$(ps -eo pid,args | grep '[n]ginx: master' | awk '{print $1}' | head -1)
if [ -n "$NPID" ]; then
  grep -m3 "music-admin" /proc/"$NPID"/mountinfo 2>/dev/null || echo "(mountinfo 未匹配到 music-admin，按 host 目录部署)"
else
  echo "(未找到 nginx master)"
fi

echo "== 2) backup current site =="
tar czf "/var/www/music-admin.bak_$TS.tar.gz" -C /var/www music-admin
ls -la "/var/www/music-admin.bak_$TS.tar.gz"
cp -f "$DST/index.html" "$DST/index.html.bak_$TS" 2>/dev/null || true

echo "== 3) deploy new build =="
mkdir -p "$DST/assets"
cp -f "$SRC/index.html" "$DST/index.html"
cp -f "$SRC/favicon.ico" "$DST/favicon.ico" 2>/dev/null || true
cp -f "$SRC"/assets/* "$DST/assets/"
ls -la "$DST"
ls -la "$DST/assets"

echo "== 4) verify through nginx =="
echo -n "GET /                -> "
curl -s -m 5 -o /dev/null -w "%{http_code} %{content_type}\n" http://127.0.0.1/
ASSET=$(grep -o -E '/assets/[A-Za-z0-9._-]+\.js' "$DST/index.html" | head -1)
echo "referenced asset      = $ASSET"
echo -n "GET \$ASSET           -> "
curl -s -m 8 -o /dev/null -w "%{http_code} %{content_type} size=%{size_download}\n" "http://127.0.0.1$ASSET"
echo -n "asset has relative /api -> "
curl -s -m 8 "http://127.0.0.1$ASSET" | grep -c '"/api"' || true
echo -n "GET /versions (SPA)   -> "
curl -s -m 5 -o /dev/null -w "%{http_code}\n" http://127.0.0.1/versions
echo -n "API proxy /api/health -> "
curl -s -m 5 http://127.0.0.1/api/health
echo
echo -n "API proxy /api/app/version/latest -> "
curl -s -m 5 http://127.0.0.1/api/app/version/latest
echo
echo "== DONE =="
