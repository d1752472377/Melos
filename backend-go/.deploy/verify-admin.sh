#!/bin/sh
API=http://127.0.0.1/api
resp=$(curl -s -m 8 -X POST "$API/auth/login" -H 'Content-Type: application/json' -d '{"username":"admin","password":"12346"}')
code=$(printf '%s' "$resp" | sed -n 's/.*"code":\([0-9]*\).*/\1/p')
echo "login code = $code"
token=$(printf '%s' "$resp" | sed -n 's/.*"token":"\([^"]*\)".*/\1/p')

if [ -z "$token" ]; then
  echo "(未拿到 token：管理员密码可能已改，跳过需鉴权的检查)"
  printf '%s' "$resp" | cut -c1-200
  echo
  exit 0
fi

echo "token 已获取（不回显）"
echo "-- GET /api/admin/versions?page=1&limit=10 --"
curl -s -m 8 -H "Authorization: Bearer $token" "$API/admin/versions?page=1&limit=10"
echo
echo "-- GET /api/admin/users?page=1&limit=2 (截断) --"
curl -s -m 8 -H "Authorization: Bearer $token" "$API/admin/users?page=1&limit=2" | cut -c1-260
echo
echo "-- 未鉴权访问 /api/admin/versions (应为 401) --"
curl -s -o /dev/null -w "status=%{http_code}\n" -m 8 "$API/admin/versions"
