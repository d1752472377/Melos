#!/bin/sh
echo "== 1) direct to go (127.0.0.1:8787) =="
curl -s -m 3 http://127.0.0.1:8787/api/health; echo
echo "== 2) via nginx :80 =="
curl -s -m 3 -H "Host: 39.96.39.134" http://127.0.0.1/api/health; echo
echo "== 3) via nginx :8081 =="
curl -s -m 3 -H "Host: 39.96.39.134" http://127.0.0.1:8081/api/health; echo
echo "== 4) anonymous sync/pull must be 401 =="
curl -s -o /dev/null -w "status=%{http_code}\n" -m 3 "http://127.0.0.1:8787/api/sync/pull?since=0"
echo "== 5) public latest =="
curl -s -m 3 http://127.0.0.1:8787/api/app/version/latest; echo
echo "== 6) db counts =="
sqlite3 /opt/melos-backend/data/music.db "select 'users='||count(*) from users; select 'sessions='||count(*) from sessions; select 'favorites='||count(*) from favorites;"
echo "== 7) stored password hash shape (node scrypt: salt 32 hex, hash 128 hex) =="
sqlite3 /opt/melos-backend/data/music.db "select username||' salt='||length(salt)||' hash='||length(password_hash) from users;"
echo "== 8) process =="
ps -o pid,etime,rss,cmd -p "$(pgrep -f '/opt/melos-backend/melos-backend' | head -1)" 2>/dev/null || echo "pgrep found nothing"
echo "== 9) error log tail =="
tail -n 8 /root/.pm2/logs/melos-backend-error.log 2>/dev/null
echo "== 10) recent gin access log tail =="
tail -n 8 /root/.pm2/logs/melos-backend-out.log 2>/dev/null
