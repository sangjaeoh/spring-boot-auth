-- 리프레시 회전 원자 판정. 모든 키는 {u:userId} 해시태그로 단일 슬롯이라 Cluster에서도 원자적이다.
-- KEYS[1]=sessKey(sess:{u}:sid), KEYS[2]=idxKey(sessidx:{u})
-- ARGV: 1=presentedJti 2=newJti 3=newPlain 4=now(ms) 5=graceMs 6=sessionTtlMs 7=gracePrefix 8=sessPrefix
-- 반환: 'ROTATED' | 'GRACE_REPLAY\n<cachedPlain>' | 'REUSE' | 'INVALID'

local sessKey = KEYS[1]
local idxKey = KEYS[2]
local presented = ARGV[1]
local newJti = ARGV[2]
local newPlain = ARGV[3]
local now = tonumber(ARGV[4])
local graceMs = tonumber(ARGV[5])
local sessionTtlMs = tonumber(ARGV[6])
local gracePrefix = ARGV[7]
local sessPrefix = ARGV[8]

if redis.call('EXISTS', sessKey) == 0 then
  return 'INVALID'
end
if redis.call('HGET', sessKey, 'status') ~= 'ACTIVE' then
  return 'INVALID'
end

local current = redis.call('HGET', sessKey, 'refreshJtiHash')
local prev = redis.call('HGET', sessKey, 'prevJtiHash')
local prevRotatedAt = tonumber(redis.call('HGET', sessKey, 'prevRotatedAt'))
if prevRotatedAt == nil then
  prevRotatedAt = 0
end

if presented == current then
  -- 슬라이딩 만료: 키 TTL·expiresAt 필드·인덱스 TTL을 함께 now+ttl로 갱신한다(세 만료 표기가 어긋나면
  -- validate와 refresh가 불일치하고 revokeAll이 세션을 못 찾는다).
  redis.call(
    'HSET', sessKey,
    'prevJtiHash', current,
    'prevRotatedAt', now,
    'refreshJtiHash', newJti,
    'expiresAt', now + sessionTtlMs)
  redis.call('PEXPIRE', sessKey, sessionTtlMs)
  redis.call('PEXPIRE', idxKey, sessionTtlMs)
  -- graceMs=0이면 유예 창이 없다(SET PX 0은 Redis 오류) → 유예 캐시를 두지 않는다.
  if graceMs > 0 then
    redis.call('SET', gracePrefix .. current, newPlain, 'PX', graceMs)
  end
  return 'ROTATED'
end

if prev ~= false and prev ~= '' and presented == prev and (now - prevRotatedAt) <= graceMs then
  local cached = redis.call('GET', gracePrefix .. prev)
  if cached then
    return 'GRACE_REPLAY\n' .. cached
  end
end

-- 재사용(탈취): 제시 토큰이 활성 세션으로 resolve되나 현재/유효 직전 토큰이 아님 → 패밀리 전멸.
local members = redis.call('ZRANGE', idxKey, 0, -1)
for i = 1, #members do
  redis.call('DEL', sessPrefix .. members[i])
end
redis.call('DEL', idxKey)
return 'REUSE'
