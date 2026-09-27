#!/bin/sh
TS=$(date +%Y%m%d_%H%M%S)
cd /opt/melos-backend || exit 2

echo "== 归档并清理 Node 专用配置文件 =="
EXTRA=""
for p in .env.local .env.example; do
  [ -e "$p" ] && EXTRA="$EXTRA $p"
done
if [ -n "$EXTRA" ]; then
  tar czf "/opt/melos-backend-node-extra_$TS.tar.gz" $EXTRA
  echo "archived:$EXTRA -> /opt/melos-backend-node-extra_$TS.tar.gz"
  rm -f .env.local .env.example
else
  echo "(无)"
fi

echo
echo "== /opt/melos-backend 最终状态 =="
ls -la /opt/melos-backend

echo
echo "== /var/www/music-admin 最终状态 =="
ls -la /var/www/music-admin /var/www/music-admin/assets

echo
echo "== 备份文件 =="
ls -la /opt/melos-backend-node-legacy_*.tar.gz /opt/melos-backend-node-extra_*.tar.gz /var/www/music-admin.bak_*.tar.gz 2>/dev/null
ls -la /opt/melos-backend/data/music.db*

echo
echo "== 服务健康 =="
echo -n "backend  : "; curl -s -m 5 http://127.0.0.1:8787/api/health; echo
echo -n "admin /  : "; curl -s -m 5 -o /dev/null -w "%{http_code}\n" http://127.0.0.1/
echo -n "admin api: "; curl -s -m 5 http://127.0.0.1/api/app/version/latest; echo
echo -n "pm2      : "; pm2 pid melos-backend 2>/dev/null | tr -d '\n'; echo " (pid)"
pgrep -f '/opt/melos-backend/melos-backend' >/dev/null && echo "go binary: running" || echo "go binary: NOT RUNNING"

echo
echo "== 清理临时文件 =="
rm -f /tmp/cleanup-backend.sh /tmp/cleanup-deploy-admin.sh /tmp/melos-*.sh
rm -rf /tmp/music-admin-dist
echo cleaned
echo "== DONE =="
