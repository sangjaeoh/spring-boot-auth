-- 알림 수신 설정(NotificationPreference): 회원당 1행 + 채널×카테고리 opt-in 매트릭스 자식.
-- 발송 판정은 제네릭 알림이 이 설정과 기기 권한을 AND 한다(설정 소유=유저, 집행=제네릭).
create table usr.notification_preference (
    user_id    uuid        primary key,
    created_at timestamptz not null,
    updated_at timestamptz not null
);

create table usr.notification_preference_entry (
    id                 uuid        primary key,
    preference_user_id uuid        not null,
    channel            varchar(10) not null,
    category           varchar(20) not null,
    allowed            boolean     not null,
    created_at         timestamptz not null,
    updated_at         timestamptz not null
);

-- (preference, channel, category) 유니크 — 선두 컬럼이 부모 조회 인덱스를 겸한다(물리 FK 금지).
create unique index uq_notification_preference_entry
    on usr.notification_preference_entry (preference_user_id, channel, category);
