#!/bin/sh
echo "== 路由是否已注册（401=存在但需鉴权，404=未注册）=="
for p in /api/admin/versions /api/admin/users /api/app/version/latest /api/health; do
  echo -n "$p -> "
  curl -s -o /dev/null -w "%{http_code}\n" -m 5 "http://127.0.0.1$p"
done
echo
echo "== 前端静态资源缓存头 =="
ASSET=$(grep -o -E '/assets/[A-Za-z0-9._-]+\.js' /var/www/music-admin/index.html | head -1)
echo -n "index.html  -> "; curl -s -o /dev/null -D - -m 5 http://127.0.0.1/ | grep -i -E "^cache-control|^content-type" | tr -d '\r' | paste -sd' ' -
echo -n "$ASSET -> "; curl -s -o /dev/null -D - -m 5 "http://127.0.0.1$ASSET" | grep -i -E "^cache-control|^content-type" | tr -d '\r' | paste -sd' ' -
echo
echo "== 上线文件清单 =="
ls -la /var/www/music-admin /var/www/music-admin/assets
echo
echo "== 备份 =="
ls -la /var/www/music-admin.bak_*.tar.gz
