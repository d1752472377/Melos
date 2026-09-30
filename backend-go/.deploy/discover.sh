#!/bin/sh
echo "=== pm2 describe ==="
pm2 describe melos-backend 2>/dev/null | sed -n '1,60p'
echo "=== pm2 env (selected) ==="
pm2 describe melos-backend 2>/dev/null | grep -E "exec cwd|script path|script args|node args|instances|exec mode|status|restarts" || true
echo "=== music.db locations ==="
find / -maxdepth 6 -name "music.db" -not -path "*/node_modules/*" 2>/dev/null | head -10
echo "=== backend package.json locations ==="
find / -maxdepth 5 -name "package.json" -not -path "*/node_modules/*" 2>/dev/null | grep -i -E "backend|melos" | head -10
echo "=== node ==="
node -v 2>/dev/null || echo "node missing"
echo "=== disk ==="
df -h / | tail -1
