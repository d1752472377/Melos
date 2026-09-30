#!/bin/sh
# 放开 nginx 上传体积限制并调大超时，然后平滑 reload
set -u
CONF=/opt/music-admin-nginx.conf
TS=$(date +%Y%m%d_%H%M%S)

echo "== 0) 备份当前配置 =="
cp -a "$CONF" "$CONF.bak_$TS"
ls -la "$CONF".bak_*

echo "== 1) 当前限制 =="
grep -n "client_max_body_size\|proxy_request_buffering\|proxy_read_timeout" "$CONF" || echo "(未设置 client_max_body_size -> nginx 默认 1m)"

echo "== 2) 写入新配置 =="
cat > "$CONF" <<'NGINX'
server {
    listen       8081;
    listen       80;
    server_name  _;

    root   /usr/share/nginx/html;
    index  index.html;

    # APK 上传：Go 后端上限 200MB（APK_MAX_MB），这里留足余量
    client_max_body_size 256m;
    client_body_timeout  300s;

    # 后端 API 反向代理到 8787
    location /api/ {
        proxy_pass http://127.0.0.1:8787;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_read_timeout 300s;
        proxy_send_timeout 300s;
        # 大文件直传上游，不在 nginx 落盘缓冲
        proxy_request_buffering off;
    }

    # 静态资源
    location /assets/ {
        alias /usr/share/nginx/html/assets/;
        expires 7d;
        add_header Cache-Control "public, max-age=604800";
    }

    location /favicon.ico {
        alias /usr/share/nginx/html/favicon.ico;
    }

    # SPA 兜底
    location / {
        try_files $uri $uri/ /index.html;
        add_header Cache-Control "no-cache, no-store, must-revalidate";
    }

    gzip on;
    gzip_min_length 1k;
    gzip_comp_level 2;
    gzip_types text/plain text/css application/javascript application/json image/svg+xml;
    gzip_vary on;

    access_log /var/log/nginx/access.log;
    error_log /var/log/nginx/error.log;
}
NGINX

echo "== 3) 新配置生效行 =="
grep -n "client_max_body_size\|client_body_timeout\|proxy_read_timeout\|proxy_send_timeout\|proxy_request_buffering" "$CONF"

echo "== 4) 平滑 reload（SIGHUP）=="
PID=$(ps -eo pid,args | grep '[n]ginx: master' | awk '{print $1}' | head -1)
echo "master pid = $PID"
kill -HUP "$PID"
sleep 2
echo "-- reload 后的 worker --"
ps -eo pid,args | grep '[n]ginx: worker' | head -5

echo "== 5) 体积测试（应为后端响应码，而不是 nginx 413）=="
dd if=/dev/zero of=/tmp/big5.bin bs=1M count=5 2>/dev/null
echo -n "5MB  POST /api/admin/versions (无 token) -> "
curl -s -o /dev/null -w "%{http_code}\n" -m 30 -X POST -H "Content-Type: multipart/form-data; boundary=x" --data-binary @/tmp/big5.bin http://127.0.0.1/api/admin/versions
rm -f /tmp/big5.bin

dd if=/dev/zero of=/tmp/big12.bin bs=1M count=12 2>/dev/null
echo -n "12MB POST /api/admin/versions (无 token) -> "
curl -s -o /dev/null -w "%{http_code}\n" -m 60 -X POST -H "Content-Type: multipart/form-data; boundary=x" --data-binary @/tmp/big12.bin http://127.0.0.1/api/admin/versions
rm -f /tmp/big12.bin

echo "== 6) 站点与接口仍正常 =="
echo -n "GET /            -> "; curl -s -o /dev/null -w "%{http_code}\n" -m 5 http://127.0.0.1/
echo -n "GET /versions    -> "; curl -s -o /dev/null -w "%{http_code}\n" -m 5 http://127.0.0.1/versions
echo -n "GET /api/health  -> "; curl -s -m 5 http://127.0.0.1/api/health; echo
echo "== DONE =="
