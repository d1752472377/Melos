#!/bin/sh
echo "== pm2 app definition =="
pm2 describe melos-backend 2>/dev/null | grep -E "script path|exec cwd|interpreter|exec mode|status|restarts" || true
echo "== pm2 startup (survive reboot) =="
systemctl is-enabled pm2-root 2>/dev/null || echo "pm2-root not enabled"
echo "== cleanup temp scripts =="
rm -f /tmp/melos-d.sh /tmp/melos-discover.sh /tmp/melos-discover2.sh /tmp/melos-switch.sh /tmp/melos-verify.sh
echo cleaned
echo "== rollback material present =="
ls -d /opt/melos-backend/src /opt/melos-backend/node_modules /opt/melos-backend/data/music.db.bak_go_20260921_222904 2>/dev/null
echo "== final health =="
curl -s -m 3 http://127.0.0.1:8787/api/health
echo
