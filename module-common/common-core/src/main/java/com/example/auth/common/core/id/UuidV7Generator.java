package com.example.auth.common.core.id;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 시각 단조 UUIDv7(RFC 9562)을 생성한다.
 *
 * <p>상위 48비트는 Unix epoch 밀리초, 이어 버전(0b0111)과 난수로 채운다. 앱에서 ID를 확정하므로 DB
 * 왕복 없이 시간 정렬성과 분산 전환 대비를 얻는다(docs/entity-persistence.md).
 */
public final class UuidV7Generator {

    private UuidV7Generator() {}

    /**
     * 새 UUIDv7 값을 반환한다.
     */
    public static UUID generate() {
        long timestampMillis = System.currentTimeMillis();
        long randomA = ThreadLocalRandom.current().nextLong();
        long randomB = ThreadLocalRandom.current().nextLong();

        long mostSignificantBits = (timestampMillis & 0xFFFF_FFFF_FFFFL) << 16;
        mostSignificantBits |= 0x7000L; // version 7 (bits 15..12)
        mostSignificantBits |= randomA & 0x0FFFL; // rand_a (bits 11..0)

        long leastSignificantBits = randomB & 0x3FFF_FFFF_FFFF_FFFFL; // clear top 2 bits
        leastSignificantBits |= 0x8000_0000_0000_0000L; // variant 0b10 (bits 63..62)

        return new UUID(mostSignificantBits, leastSignificantBits);
    }
}
