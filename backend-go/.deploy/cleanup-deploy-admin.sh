#!/bin/sh
# 部署最新前端，并清掉被淘汰的旧静态文件与历史备份
set -u
SRC=/tmp/music-admin-dist
DST=/var/www/music-admin
TS=$(date +%Y%m%d_%H%M%S)

echo "== 0) uploaded build =="
if [ ! -f "$SRC/index.html" ]; then echo "!! 缺少 $SRC/index.html"; exit 2; fi
ls -la "$SRC" "$SRC/assets"

echo "== 1) backup current site =="
tar czf "/var/www/music-admin.bak_$TS.tar.gz" -C /var/www music-admin
ls -la /var/www/music-admin.bak_*.tar.gz

echo "== 2) deploy: assets first, index.html last =="
mkdir -p "$DST/assets"
cp -f "$SRC"/assets/* "$DST/assets/"
if [ -f "$SRC/favicon.ico" ]; then cp -f "$SRC/favicon.ico" "$DST/favicon.ico"; fi
cp -f "$SRC/index.html" "$DST/index.html"

echo "== 3) drop superseded static files =="
NEWJS=$(grep -o -E 'assets/[A-Za-z0-9._-]+\.js' "$DST/index.html" | head -1 | sed 's|assets/||')
NEWCSS=$(grep -o -E 'assets/[A-Za-z0-9._-]+\.css' "$DST/index.html" | head -1 | sed 's|assets/||')
echo "keep: js=$NEWJS css=$NEWCSS"
for f in "$DST"/assets/*; do
  [ -e "$f" ] || continue
  base=$(basename "$f")
  if [ "$base" != "$NEWJS" ] && [ "$base" != "$NEWCSS" ]; then
    echo "  rm assets/$base"
    rm -f "$f"
  fi
done
for old in "$DST"/index.html.bak "$DST"/index.html.bak_* "$DST"/server.js; do
  [ -e "$old" ] && { echo "  rm $(basename "$old")"; rm -f "$old"; }
done

echo "== 4) keep only the newest backup =="
COUNT=$(ls -1 /var/www/music-admin.bak_*.tar.gz 2>/dev/null | wc -l)
if [ "$COUNT" -gt 1 ]; then
  ls -1t /var/www/music-admin.bak_*.tar.gz | tail -n +2 | while read -r old; do
    echo "  rm $old"
    rm -f "$old"
  done
fi

echo "== 5) verify =="
ls -la "$DST"
ls -la "$DST/assets"
echo -n "GET /                 -> "
curl -s -m 5 -o /dev/null -w "%{http_code} %{content_type}\n" http://127.0.0.1/
ASSET=$(grep -o -E '/assets/[A-Za-z0-9._-]+\.js' "$DST/index.html" | head -1)
echo -n "GET $ASSET -> "
curl -s -m 8 -o /dev/null -w "%{http_code} size=%{size_download}\n" "http://127.0.0.1$ASSET"
echo -n "asset has relative /api -> "
curl -s -m 8 "http://127.0.0.1$ASSET" | grep -c '"/api"' || true
echo -n "GET /versions (SPA)   -> "
curl -s -m 5 -o /dev/null -w "%{http_code}\n" http://127.0.0.1/versions
echo -n "API /api/health       -> "
curl -s -m 5 http://127.0.0.1/api/health
echo
echo -n "已下线的旧资源 index-2Ol9XiEy.js -> "
curl -s -m 5 -o /dev/null -w "%{http_code} (404=已清理)\n" http://127.0.0.1/assets/index-2Ol9XiEy.js
echo "== DONE =="
