package com.example.auth.domain.user.service;

import com.example.auth.domain.user.entity.LifecycleStatus;
import com.example.auth.domain.user.entity.User;
import com.example.auth.domain.user.exception.UserErrorCode;
import com.example.auth.domain.user.exception.UserException;
import com.example.auth.domain.user.info.MyUserInfo;
import com.example.auth.domain.user.repository.UserRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원 조회를 담당한다.
 */
@Service
public class UserReader {

    private final UserRepository repository;

    public UserReader(UserRepository repository) {
        this.repository = repository;
    }

    /**
     * 내 정보를 마스킹 적용해 반환한다. 탈퇴 회원은 PII가 파기된 상태라 미존재로 취급한다.
     *
     * @throws UserException 미존재·탈퇴 회원이면(404)
     */
    @Transactional(readOnly = true)
    public MyUserInfo getMe(UUID userId) {
        User user = repository
                .findById(userId)
                .filter(found -> found.getStatus() != LifecycleStatus.WITHDRAWN)
                .orElseThrow(() -> new UserException(UserErrorCode.USER_NOT_FOUND));
        return MyUserInfo.from(user);
    }
}
