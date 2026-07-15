package com.example.auth.domain.auth.port;

/**
 * IP 지오로케이션 결과다({@link GeoIpLookup} 포트 계약).
 */
public record GeoLocation(String countryCode) {

    public GeoLocation {
        if (countryCode.isBlank()) {
            throw new IllegalArgumentException("countryCode는 비어 있을 수 없다");
        }
    }
}
