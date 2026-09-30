#!/bin/sh
echo "== nginx master process =="
ps -eo pid,args | grep '[n]ginx' | head -5

echo "== nginx config dirs =="
for d in /etc/nginx /usr/local/nginx/conf /opt/nginx/conf /www/server/nginx/conf; do
  if [ -d "$d" ]; then
    echo "--- $d ---"
    ls -la "$d" 2>/dev/null | head -20
  fi
done

echo "== server_name / listen / root / proxy_pass (whole nginx conf tree) =="
for root in /etc/nginx /usr/local/nginx/conf /opt/nginx/conf /www/server/nginx/conf; do
  if [ -d "$root" ]; then
    grep -rn -E "server_name|listen|root |proxy_pass" "$root" 2>/dev/null | grep -v -E "^\s*[^:]*:\s*#" | head -60
  fi
done

echo "== candidate web roots (index.html outside node_modules) =="
find / -maxdepth 6 -name "index.html" -not -path "*/node_modules/*" -not -path "/opt/melos-backend/*" -not -path "/proc/*" 2>/dev/null | head -20

echo "== /var/www and /opt =="
ls -la /var/www 2>/dev/null || echo "(no /var/www)"
ls -la /opt 2>/dev/null

echo "== node / npm / pnpm =="
node -v 2>/dev/null || echo "node missing"
npm -v 2>/dev/null || echo "npm missing"
(pnpm -v 2>/dev/null || echo "pnpm missing")

echo "== baked API url in any deployed bundle =="
for f in $(find / -maxdepth 6 -path "*assets/index-*.js" -not -path "*/node_modules/*" -not -path "/proc/*" 2>/dev/null | head -6); do
  echo "-- $f"
  grep -o -E "https?://[a-zA-Z0-9._:-]+/api" "$f" 2>/dev/null | sort -u | head -5
done
