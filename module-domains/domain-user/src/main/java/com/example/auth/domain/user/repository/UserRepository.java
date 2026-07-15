package com.example.auth.domain.user.repository;

import com.example.auth.domain.user.entity.User;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 회원 애그리거트 리포지토리다.
 *
 * <p>{@code contactPhoneBidx}는 <b>비유니크</b>(같은 전화가 재가입·번호 재할당으로 여러 행에 실재 가능 —
 * 활성 1인 불변식은 phone이 아니라 CiRegistry가 강제)라 조회는 다건을 반환한다.
 */
public interface UserRepository extends JpaRepository<User, UUID> {

    List<User> findByContactPhoneBidx(String contactPhoneBidx);
}
