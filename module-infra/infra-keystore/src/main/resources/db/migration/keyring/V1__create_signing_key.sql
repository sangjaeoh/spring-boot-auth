create schema if not exists keyring;

-- RS256 서명키 원장(재시작·다중 인스턴스 공유 진실원본). jwk_encrypted는 개인키 포함 JWK JSON의
-- 봉투 암호문(EnvelopeCipher — 키 버전 동봉). 최신(created_at) 행이 현재 서명키이고, 이전 행은 후속 키
-- 생성 시각 + 유예 창 동안 검증·게시에 남다가 회전 시 정리된다(행 수 상시 수 개).
create table keyring.signing_key (
    kid           varchar(36)  primary key,
    jwk_encrypted text         not null,
    created_at    timestamptz  not null
);
