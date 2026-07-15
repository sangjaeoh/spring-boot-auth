package com.example.auth.infra.redis;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.ClusterSlotHashUtil;

/**
 * 세션 키 슬롯 불변식: 한 사용자의 애그리거트 키가 전부 단일 Cluster 슬롯에 놓임을 강제한다.
 *
 * <p>회전 Lua는 같은 슬롯의 다중 키에서만 원자적이다(CROSSSLOT EVAL 불가). {@link ClusterSlotHashUtil}은
 * Redis와 동일한 CRC16+해시태그 규칙이라 라이브 클러스터 없이 결정적으로 검증한다.
 */
class SessionKeysTest {

    private static final UUID USER = UUID.fromString("0191c2a0-1111-7abc-8def-000000000001");
    private static final UUID SESSION = UUID.fromString("0191c2a0-2222-7abc-8def-000000000002");
    private static final String JTI = "0f7c1e9b2a4d6f8091a3c5e7b9d1f3a5c7e9b1d3f5a7091b3d5f7a9c1e3b5d7f9";

    @Test
    void allSessionAggregateKeysShareOneSlot() {
        // 회전 Lua가 KEYS로 받는 sessKey(앵커)·idxKey와 ARGV 접두로 내부에서 만지는 키(grace SET·REUSE의
        // DEL 루프 sessPrefix+sid)가 전부 동일 슬롯 → 단일 슬롯 EVAL이라 Cluster에서도 원자적이다.
        int slot = ClusterSlotHashUtil.calculateSlot(SessionKeys.sessionKey(USER, SESSION));

        assertThat(ClusterSlotHashUtil.calculateSlot(SessionKeys.indexKey(USER)))
                .isEqualTo(slot);
        assertThat(ClusterSlotHashUtil.calculateSlot(SessionKeys.gracePrefix(USER) + JTI))
                .isEqualTo(slot);
        assertThat(ClusterSlotHashUtil.calculateSlot(SessionKeys.sessionPrefix(USER) + SESSION))
                .isEqualTo(slot);
    }

    @Test
    void refIndexCarriesNoHashTagSoItStaysOffTheAggregateSlot() {
        // refidx는 jti→userId를 resolve해야 해 태그를 담을 수 없다 → 의도적으로 슬롯 밖이며 Lua 원자
        // 경계에 넣지 않고 단일 키 GET/SET으로만 쓴다(REDIS_HA.md). 해시태그 부재를 구조적으로 강제한다.
        assertThat(SessionKeys.refIndexKey(JTI)).doesNotContain("{");
    }
}
