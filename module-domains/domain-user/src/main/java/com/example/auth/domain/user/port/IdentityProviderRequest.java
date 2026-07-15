package com.example.auth.domain.user.port;

import com.example.auth.domain.user.entity.Carrier;
import com.example.auth.domain.user.entity.Gender;
import java.time.LocalDate;

/**
 * 본인확인기관에 제출하는 실명확인 요청이다(사용자가 주장하는 정체성).
 */
public record IdentityProviderRequest(String name, LocalDate birthDate, Gender gender, Carrier carrier, String phone) {}
