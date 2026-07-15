package com.example.auth.domain.user.service;

import com.example.auth.common.core.crypto.BlindIndexer;
import com.example.auth.domain.user.entity.Contact;
import com.example.auth.domain.user.entity.Profile;
import com.example.auth.domain.user.entity.User;
import com.example.auth.domain.user.repository.UserRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원 생성을 담당한다.
 *
 * <p>전화 blind index를 평문 번호에서 계산해 엔티티에 넣는다(암호화 컬럼과 동등 조회용 인덱스의 정합을
 * 서비스가 소유). 이 슬라이스는 User 애그리거트 하나만 생성한다 — 온보딩 완료의 크로스스키마 원자 생성
 * ({@code CreateUser})은 다음 슬라이스가 이 진입점을 감싼다.
 */
@Service
public class UserAppender {

    private final UserRepository repository;
    private final BlindIndexer blindIndexer;

    public UserAppender(UserRepository repository, BlindIndexer blindIndexer) {
        this.repository = repository;
        this.blindIndexer = blindIndexer;
    }

    /**
     * 프로필·연락처·CI 해시로 활성 회원을 생성하고 그 {@code UserId}를 반환한다.
     */
    @Transactional
    public UUID register(Profile profile, Contact contact, String ciHash) {
        String phoneBidx = blindIndexer.blindIndex(contact.contactPhone().number());
        User user = User.create(profile, contact, ciHash, phoneBidx);
        repository.save(user);
        return user.getId();
    }
}
