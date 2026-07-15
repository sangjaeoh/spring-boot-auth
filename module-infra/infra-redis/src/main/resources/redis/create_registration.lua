-- 온보딩 세션 생성: 필드 세팅과 TTL 부여를 한 스크립트로 원자화한다.
-- HSET 후 별도 EXPIRE는 그 사이 실패 시 TTL 없는 키(파기 불변식 위반)를 영구 잔존시킨다.
-- ARGV[1] = TTL(ms), 이후는 field/value 쌍의 나열이다.
for i = 2, #ARGV, 2 do
  redis.call('HSET', KEYS[1], ARGV[i], ARGV[i + 1])
end
redis.call('PEXPIRE', KEYS[1], ARGV[1])
return 1
