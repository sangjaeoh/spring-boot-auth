package com.example.auth.domain.user.service;

import com.example.auth.domain.user.entity.Email;
import com.example.auth.domain.user.entity.User;
import com.example.auth.domain.user.exception.UserErrorCode;
import com.example.auth.domain.user.exception.UserException;
import com.example.auth.domain.user.repository.UserRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원 단일 애그리거트 전이를 담당한다.
 *
 * <p>프로필(이름·생년·성별·휴대폰)은 본인인증 결과로만 채워지는 불변이라 수정 경로를 열지 않는다 —
 * 변경 가능한 것은 연락용 이메일뿐이다.
 */
@Service
public class UserModifier {

    private final UserRepository repository;

    public UserModifier(UserRepository repository) {
        this.repository = repository;
    }

    /**
     * 연락용 이메일을 변경한다(로그인 식별자와 독립).
     *
     * @throws UserException 미존재 회원이면(404)
     * @throws IllegalArgumentException 이메일 형식이 아니면(경계 검증 백스톱)
     */
    @Transactional
    public void changeContactEmail(UUID userId, String newEmail) {
        User user = getUser(userId);
        user.changeContactEmail(Email.of(newEmail));
    }

    private User getUser(UUID userId) {
        return repository.findById(userId).orElseThrow(() -> new UserException(UserErrorCode.USER_NOT_FOUND));
    }
}
