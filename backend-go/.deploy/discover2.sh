#!/bin/sh
echo "=== .env keys (values hidden) ==="
if [ -f /opt/melos-backend/.env ]; then
  sed -e 's/=.*/=<hidden>/' /opt/melos-backend/.env | grep -v '^$'
else
  echo "(no .env)"
fi
echo "=== DB_URL actual ==="
if [ -f /opt/melos-backend/.env ]; then
  grep -E '^DB_URL=' /opt/melos-backend/.env || echo "(DB_URL not set -> default local file)"
else
  echo "(no .env -> default local file)"
fi
echo "=== DB_AUTH_TOKEN set? ==="
if [ -f /opt/melos-backend/.env ] && grep -qE '^DB_AUTH_TOKEN=.+' /opt/melos-backend/.env; then
  echo "DB_AUTH_TOKEN=SET"
else
  echo "DB_AUTH_TOKEN=empty"
fi
echo "=== listening ports ==="
ss -lntp 2>/dev/null | head -20
echo "=== nginx ==="
nginx -v 2>&1 || echo "nginx not installed"
ls -la /etc/nginx/conf.d/ 2>/dev/null
grep -rl "8787" /etc/nginx/ 2>/dev/null | head -5
echo "=== reverse proxy hint ==="
grep -rn "proxy_pass" /etc/nginx/ 2>/dev/null | head -10
echo "=== source hashes ==="
find /opt/melos-backend/src -type f -name '*.js' 2>/dev/null | sort | xargs sha256sum 2>/dev/null
echo "=== package.json version/scripts ==="
grep -E '"(name|version)"' /opt/melos-backend/package.json 2>/dev/null | head -4
echo "=== git ==="
if [ -d /opt/melos-backend/.git ]; then
  git -C /opt/melos-backend rev-parse HEAD 2>/dev/null
  git -C /opt/melos-backend status --porcelain 2>/dev/null | head -10
else
  echo "(not a git repo)"
fi
echo "=== db size / files ==="
ls -la /opt/melos-backend/data/ 2>/dev/null
echo "=== app_versions present? ==="
which sqlite3 2>/dev/null && sqlite3 /opt/melos-backend/data/music.db ".tables" 2>/dev/null || echo "sqlite3 CLI not available"
