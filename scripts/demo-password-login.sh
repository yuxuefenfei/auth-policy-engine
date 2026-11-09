#!/usr/bin/env bash
set -euo pipefail
BASE="${BASE:-http://localhost:8080}"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
parse() { python3 -c "import json,sys;print(json.load(sys.stdin)$1)"; }

echo '[1] 发布新策略：只需 PASSWORD（演示策略热更新；不要在生产使用弱策略）'
DRAFT=$(curl -fsS -u admin:Admin@123456 -H 'Content-Type: application/json' \
  -d '{"op":"FACTOR","id":"password","factor":"PASSWORD"}' \
  "$BASE/api/admin/policies/WEB_LOGIN/draft")
VERSION=$(printf '%s' "$DRAFT" | parse "['version']")
curl -fsS -u admin:Admin@123456 -X POST \
  "$BASE/api/admin/policies/WEB_LOGIN/publish/$VERSION" >/dev/null

echo '[2] 创建登录流程并保存 HttpOnly cookie'
ATTEMPT=$(curl -fsS -c "$WORK/cookies.txt" -H 'Content-Type: application/json' \
  -d '{"username":"alice"}' "$BASE/api/login/attempts")
ID=$(printf '%s' "$ATTEMPT" | parse "['attemptId']")
echo "attemptId=$ID; policyVersion=$VERSION"

echo '[3] 校验密码'
curl -fsS -b "$WORK/cookies.txt" -H 'Content-Type: application/json' \
  -d '{"credential":"Demo@123456"}' \
  "$BASE/api/login/attempts/$ID/factors/password/verify" | python3 -m json.tool

echo '[4] 原子消费 Attempt 并签发 Bearer session'
COMPLETE=$(curl -fsS -b "$WORK/cookies.txt" -X POST "$BASE/api/login/attempts/$ID/complete")
TOKEN=$(printf '%s' "$COMPLETE" | parse "['accessToken']")
echo "tokenType=$(printf '%s' "$COMPLETE" | parse "['tokenType']")"

echo '[5] 使用新令牌访问 /api/me'
curl -fsS -H "Authorization: Bearer $TOKEN" "$BASE/api/me" | python3 -m json.tool

echo '[6] 尝试重复消费（预期 HTTP 403）'
STATUS=$(curl -s -o "$WORK/replay.json" -w '%{http_code}' -b "$WORK/cookies.txt" \
  -X POST "$BASE/api/login/attempts/$ID/complete")
echo "replayStatus=$STATUS"
[[ "$STATUS" == '403' ]]
echo '演示成功'
