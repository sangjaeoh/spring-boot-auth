#!/usr/bin/env bash
# 로컬 개발 시크릿 부트스트랩 — 봉투암호 KEK·blind index pepper를 무작위 생성해 .dev-secrets.env(gitignored)에
# 기록한다. 앱 기동 전 `set -a; source .dev-secrets.env; set +a`로 주입한다(prod는 시크릿 매니저가 같은
# 변수명으로 주입 — docs/ops/deployment.md).
# 주의: 이 파일을 재생성하면 기존 로컬 DB의 암호문(PII·keyring 서명키)을 복호할 수 없다.
set -euo pipefail

cd "$(dirname "$0")/.."

if [[ -f .dev-secrets.env ]]; then
    echo ".dev-secrets.env가 이미 있습니다. 재생성하면 기존 로컬 DB 암호문을 복호할 수 없어 중단합니다." >&2
    echo "정말 재생성하려면 .dev-secrets.env를 지우고 로컬 DB 볼륨도 함께 초기화하세요(docker compose down -v)." >&2
    exit 1
fi

umask 177
cat > .dev-secrets.env <<EOF
CRYPTO_ENVELOPE_KEYS=1:$(openssl rand -base64 32)
CRYPTO_BLIND_INDEX_PEPPERS=1:$(openssl rand -base64 32)
EOF

echo ".dev-secrets.env 생성 완료. 기동 전: set -a; source .dev-secrets.env; set +a"
