-- 전 세션 원자 무효화. 모든 키는 {u:userId} 해시태그로 단일 슬롯이라 Cluster에서도 원자적이다.
-- 인덱스 스냅샷과 세션 삭제가 한 스크립트에서 끝나 부분 무효화 상태가 관측되지 않는다.
-- KEYS[1]=idxKey(sessidx:{u})
-- ARGV: 1=sessPrefix
-- 반환: 삭제한 세션 수

local idxKey = KEYS[1]
local sessPrefix = ARGV[1]

local members = redis.call('ZRANGE', idxKey, 0, -1)
for i = 1, #members do
  redis.call('DEL', sessPrefix .. members[i])
end
redis.call('DEL', idxKey)
return #members
