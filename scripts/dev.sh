#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"

cd "$ROOT/backend"
./mvnw -q -DskipTests spring-boot:run &
API_PID=$!

cd "$ROOT/frontend"
if [[ ! -d node_modules ]]; then
  npm install
fi
npm run dev -- -p 43123 -H 0.0.0.0 &
WEB_PID=$!

cleanup() {
  kill "$API_PID" "$WEB_PID" 2>/dev/null || true
}
trap cleanup EXIT INT TERM

echo "API  http://127.0.0.1:18081"
echo "Web  http://127.0.0.1:43123"
echo "Demo thabo@ndlovulaw.co.za / password  (firm ndlovu-partners)"
echo "     john@smithlaw.com / password      (firm smith-associates)"
wait
