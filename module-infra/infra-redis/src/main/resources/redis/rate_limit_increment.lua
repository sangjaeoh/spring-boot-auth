-- 고정 창 레이트리밋 카운터. 단일 키(rl:<dimension>)라 슬롯 무관하게 원자적이다.
-- KEYS[1]=counterKey; ARGV[1]=windowMillis
-- 반환: 증가 후 카운트. 창의 첫 증가가 만료를 세팅한다(창 경과 시 키 자동 소멸).

local count = redis.call('INCR', KEYS[1])
if count == 1 then
  redis.call('PEXPIRE', KEYS[1], tonumber(ARGV[1]))
end
return count
