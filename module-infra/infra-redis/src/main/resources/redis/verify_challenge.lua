-- 인증코드 원자 검증. 단일 키(vchal:<id>)라 슬롯 무관하게 원자적이다.
-- KEYS[1]=challengeKey; ARGV[1]=presentedCodeHash
-- 반환: 'VERIFIED\n<subjectId>' | 'MISMATCH' | 'TOO_MANY' | 'NOT_FOUND'
-- 만료는 키 TTL로 처리한다(존재 ⇒ 미만료) — NOT_FOUND에 흡수된다.

local key = KEYS[1]
local presented = ARGV[1]

if redis.call('EXISTS', key) == 0 then
  return 'NOT_FOUND'
end

-- 시도상한 도달 시 코드 대조 없이 거부한다(잠금). 잔여 챌린지는 TTL로 정리된다 — 즉시 삭제하면
-- TOO_MANY를 못 알리고 재시도 여지를 NOT_FOUND로 흐린다.
local attempts = tonumber(redis.call('HGET', key, 'attempts'))
local maxAttempts = tonumber(redis.call('HGET', key, 'maxAttempts'))
if attempts >= maxAttempts then
  return 'TOO_MANY'
end

if redis.call('HGET', key, 'codeHash') == presented then
  local subjectId = redis.call('HGET', key, 'subjectId')
  redis.call('DEL', key)  -- 성공 시 소진(single-use)
  return 'VERIFIED\n' .. subjectId
end

redis.call('HINCRBY', key, 'attempts', 1)
return 'MISMATCH'
