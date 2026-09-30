#!/bin/sh
# 切换到 Go 版后端；失败自动回滚到 Node 版
set -u

APP=melos-backend
DIR=/opt/melos-backend
BIN="$DIR/melos-backend"
TS=$(date +%Y%m%d_%H%M%S)
HEALTH=http://127.0.0.1:8787/api/health

echo "===== 0) preflight ====="
if [ ! -x "$BIN" ]; then
  echo "!! $BIN 不存在或不可执行，中止"
  exit 2
fi
BEFORE_USERS=$(sqlite3 "$DIR/data/music.db" "select count(*) from users" 2>/dev/null || echo "?")
echo "切换前 users=$BEFORE_USERS"

echo "===== 1) backup db ====="
cp -a "$DIR/data/music.db" "$DIR/data/music.db.bak_go_$TS"
ls -la "$DIR/data/" | sed -n '1,20p'

echo "===== 2) ensure apk dir ====="
mkdir -p "$DIR/data/apk"

echo "===== 3) stop node version ====="
pm2 delete "$APP" >/dev/null 2>&1 || true
sleep 1

echo "===== 4) start go version ====="
cd "$DIR" || exit 3
pm2 start ./melos-backend --name "$APP" --interpreter none >/dev/null 2>&1
sleep 3

echo "===== 5) health check ====="
ok=0
i=1
while [ $i -le 12 ]; do
  body=$(curl -s -m 3 "$HEALTH" 2>/dev/null || true)
  echo "  try $i: $body"
  case "$body" in *'"ok":true'*) ok=1; break;; esac
  sleep 1
  i=$((i+1))
done

if [ "$ok" != "1" ]; then
  echo "!! Go 版本健康检查失败，回滚到 Node 版本"
  pm2 delete "$APP" >/dev/null 2>&1 || true
  cd "$DIR" || exit 4
  pm2 start src/start.js --name "$APP" >/dev/null 2>&1
  sleep 3
  echo "回滚后健康检查："
  curl -s -m 3 "$HEALTH" 2>/dev/null || true
  echo
  echo "!! 已回滚，Node 版本继续运行"
  exit 1
fi

echo "===== 6) data / feature checks ====="
AFTER_USERS=$(sqlite3 "$DIR/data/music.db" "select count(*) from users" 2>/dev/null || echo "?")
echo "切换后 users=$AFTER_USERS (切换前 $BEFORE_USERS)"
echo "tables: $(sqlite3 "$DIR/data/music.db" '.tables' 2>/dev/null | tr -s ' ' ' ')"
echo -n "app_versions 表: "
sqlite3 "$DIR/data/music.db" "select count(*) from app_versions" >/dev/null 2>&1 && echo "OK" || echo "MISSING"
echo -n "/api/app/version/latest: "
curl -s -m 3 http://127.0.0.1:8787/api/app/version/latest 2>/dev/null || true
echo
echo -n "GET / : "
curl -s -m 3 http://127.0.0.1:8787/ 2>/dev/null || true
echo

echo "===== 7) pm2 save + status ====="
pm2 save >/dev/null 2>&1 || true
pm2 list | sed -n '1,12p'

echo "===== 8) recent logs ====="
tail -n 15 /root/.pm2/logs/"$APP"-out.log 2>/dev/null
tail -n 15 /root/.pm2/logs/"$APP"-error.log 2>/dev/null

echo "===== DONE: Go 版本已上线 ====="
