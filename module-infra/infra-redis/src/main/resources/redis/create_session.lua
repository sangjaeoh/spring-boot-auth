-- 세션 생성 + 동시 세션 상한 원자 판정. 모든 키는 {u:userId} 해시태그로 단일 슬롯이라 Cluster에서도 원자적이다.
-- KEYS[1]=sessKey(sess:{u}:sid), KEYS[2]=idxKey(sessidx:{u})
-- ARGV: 1=sessionId 2=deviceId 3=ip 4=ua 5=jtiHash 6=now(ms) 7=sessionTtlMs 8=maxSessions 9=sessPrefix
-- 반환: 축출된 sessionId를 ','로 이은 문자열(없으면 '')

local sessKey = KEYS[1]
local idxKey = KEYS[2]
local sessionId = ARGV[1]
local deviceId = ARGV[2]
local ip = ARGV[3]
local ua = ARGV[4]
local jtiHash = ARGV[5]
local now = tonumber(ARGV[6])
local sessionTtlMs = tonumber(ARGV[7])
local maxSessions = tonumber(ARGV[8])
local sessPrefix = ARGV[9]

redis.call(
  'HSET', sessKey,
  'status', 'ACTIVE',
  'deviceId', deviceId,
  'ip', ip,
  'ua', ua,
  'issuedAt', now,
  'lastAccessedAt', now,
  'expiresAt', now + sessionTtlMs,
  'refreshJtiHash', jtiHash,
  'prevJtiHash', '',
  'prevRotatedAt', '0')
redis.call('PEXPIRE', sessKey, sessionTtlMs)
redis.call('ZADD', idxKey, now, sessionId)
redis.call('PEXPIRE', idxKey, sessionTtlMs)

-- 인덱스에서 실체 없는 잔여 멤버(TTL 만료 세션)를 걷어내 상한 카운트를 실 활성으로 만든다.
local members = redis.call('ZRANGE', idxKey, 0, -1)
local active = {}
for i = 1, #members do
  if redis.call('EXISTS', sessPrefix .. members[i]) == 1 then
    table.insert(active, members[i])
  else
    redis.call('ZREM', idxKey, members[i])
  end
end

-- 상한 초과분을 발급 오래된 순(ZSET score=issuedAt)으로 축출한다. 방금 만든 세션은 대상에서 제외한다.
local evicted = {}
local overflow = #active - maxSessions
if overflow > 0 then
  for i = 1, #active do
    if overflow == 0 then
      break
    end
    if active[i] ~= sessionId then
      redis.call('DEL', sessPrefix .. active[i])
      redis.call('ZREM', idxKey, active[i])
      table.insert(evicted, active[i])
      overflow = overflow - 1
    end
  end
end
return table.concat(evicted, ',')
