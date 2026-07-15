plugins {
    id("convention.external-module")
}

// external-geoip는 domain-auth가 소유한 GeoIpLookup 포트를 구현한다(dev/test는 Mock — 실 GeoIP 벤더
// (MaxMind 등) 어댑터는 조달 시 설정 스위치로 추가).
dependencies {
    implementation(project(":module-domains:domain-auth"))
    implementation(libs.spring.context)
}
