-- 온보딩 세션 스텝 마킹: 키가 살아 있을 때만 필드를 세팅한다.
-- HSET은 부재 키를 생성하므로, EXISTS 가드 없이는 TTL 만료(자동 파기) 후의 마킹이
-- TTL 없는 좀비 세션을 부활시킨다(영속 PENDING 금지 위반). ARGV는 field/value 쌍의 나열이다.
if redis.call('EXISTS', KEYS[1]) == 0 then
  return 0
end
for i = 1, #ARGV, 2 do
  redis.call('HSET', KEYS[1], ARGV[i], ARGV[i + 1])
end
return 1
