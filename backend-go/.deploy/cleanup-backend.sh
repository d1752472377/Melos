#!/bin/sh
# 清理旧的 Node 后端：先打包归档，再删除（保留二进制、.env、data/）
set -u
DIR=/opt/melos-backend
TS=$(date +%Y%m%d_%H%M%S)
ARCHIVE=/opt/melos-backend-node-legacy_$TS.tar.gz

echo "== 0) before =="
ls -la "$DIR"

echo "== 1) archive node artifacts =="
cd "$DIR" || exit 2
ITEMS=""
for p in src scripts api node_modules package.json package-lock.json vercel.json .vercelignore README.md .gitignore; do
  if [ -e "$p" ]; then ITEMS="$ITEMS $p"; fi
done
if [ -z "$ITEMS" ]; then
  echo "(没有可归档的 Node 文件)"
else
  echo "archiving:$ITEMS"
  tar czf "$ARCHIVE" $ITEMS
  ls -la "$ARCHIVE"
fi

echo "== 2) remove node artifacts =="
for p in src scripts api node_modules package.json package-lock.json vercel.json .vercelignore README.md .gitignore; do
  rm -rf "$p"
done

echo "== 3) after =="
ls -la "$DIR"
echo "-- data/ --"
ls -la "$DIR/data"

echo "== 4) go backend health =="
curl -s -m 5 http://127.0.0.1:8787/api/health
echo
pgrep -f '/opt/melos-backend/melos-backend' >/dev/null && echo "go process: running" || echo "go process: NOT RUNNING"
echo "== 5) disk =="
df -h / | tail -1
echo "== DONE =="
