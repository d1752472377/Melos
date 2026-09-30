#!/bin/sh
echo "== /opt/music-admin-nginx.conf =="
cat /opt/music-admin-nginx.conf 2>/dev/null
echo
echo "== .bak =="
cat /opt/music-admin-nginx.conf.bak 2>/dev/null
echo
echo "== /var/www/music-admin tree =="
ls -la /var/www/music-admin
echo "-- assets --"
ls -la /var/www/music-admin/assets 2>/dev/null
echo
echo "== deployed index.html =="
cat /var/www/music-admin/index.html 2>/dev/null
echo
echo "== nginx runtime context =="
NPID=$(ps -eo pid,args | grep '[n]ginx: master' | awk '{print $1}' | head -1)
echo "master pid=$NPID"
cat /proc/$NPID/cgroup 2>/dev/null | head -3
ls -l /proc/$NPID/exe 2>/dev/null
echo "-- nginx conf files on disk --"
find / -maxdepth 5 -name "nginx.conf" -not -path "/proc/*" 2>/dev/null | head
find / -maxdepth 5 -type d -name "conf.d" -not -path "/proc/*" 2>/dev/null | head
echo
echo "== api url baked in deployed bundles =="
for f in /var/www/music-admin/assets/*.js; do
  echo "-- $f"
  grep -o -E "https?://[^\"']{4,60}" "$f" 2>/dev/null | sort -u | head -10
  echo "   (relative /api hits:) $(grep -c '/api' "$f" 2>/dev/null)"
done
echo
echo "== systemd nginx? =="
systemctl is-active nginx 2>/dev/null || echo "not via systemd"
