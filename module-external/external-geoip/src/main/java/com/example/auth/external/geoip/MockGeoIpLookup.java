package com.example.auth.external.geoip;

import com.example.auth.domain.auth.port.GeoIpLookup;
import com.example.auth.domain.auth.port.GeoLocation;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * 외부 호출 없는 Mock GeoIP 어댑터다(dev/test 오프라인 검증용).
 *
 * <p>결정적 판정: TEST-NET-2 대역({@code 198.51.100.*})은 해외(US), 그 외(로컬·사설망 포함)는 KR로
 * 본다 — 테스트가 신규 지역 시나리오를 IP만으로 재현한다. 실 벤더(MaxMind 등) 어댑터는 조달 시
 * 설정 스위치로 교체한다.
 */
@Component
public class MockGeoIpLookup implements GeoIpLookup {

    private static final String FOREIGN_TEST_PREFIX = "198.51.100.";

    @Override
    public Optional<GeoLocation> lookup(String ip) {
        return Optional.of(new GeoLocation(ip.startsWith(FOREIGN_TEST_PREFIX) ? "US" : "KR"));
    }
}
