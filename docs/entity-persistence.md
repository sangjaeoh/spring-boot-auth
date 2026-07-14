# Entity & Persistence

## 언제

- `entity` 패키지에 `@Entity`를 만들거나, 필드·상태 enum을 추가·수정할 때.
- 엔티티의 `@Id`·버전 컬럼·논리 참조를 설계할 때.
- 애그리거트 내부 연관을 매핑하거나 자식 엔티티의 캐스케이드 생명주기를 정할 때(리포지토리 접근 범위는 → [architecture](architecture.md)).

## 규칙

### 엔티티 골격

- `BaseTimeEntity`를 상속한다.
  - `created_at`·`updated_at`은 JPA Auditing이 채운다. 시각 필드를 손으로 선언하지 않는다.
  - 행위자 감사(`@CreatedBy`·`@LastModifiedBy`)는 기본으로 두지 않는다. 필요한 도메인만 opt-in한다.
- 모든 영속 non-null 필드에 매핑 애노테이션(`@Id`·`@Column`·`@Enumerated`·`@Convert`·`@Embedded`·`@ManyToOne` 등)을 명시하고, nullable 값만 `@Nullable`로 표기한다. 무애노테이션 non-null basic 필드도 `@Column`을 붙인다.
  - NullAway 초기화 검사·제외 메커니즘은 → [code-quality](code-quality.md)가 소유한다. 매핑 애노테이션이 붙은 필드만 제외되므로, 무애노테이션 필드를 남기면 빌드가 깨진다.
  - `BaseTimeEntity`가 `Persistable`을 구현해 merge penalty를 방어한다. 수동 `@Id`는 JPA가 detached로 오해해 `save()` 시 불필요한 `SELECT`(merge)를 유발하므로, `createdAt == null`이면 신규로 판정해 `persist()`로 직행한다.
  - 수정은 dirty checking으로만 한다. `save()` 명시 호출은 관리 엔티티엔 무의미한 merge이고, detached 엔티티엔 `SELECT`를 동반한 merge penalty를 재발시킨다.
  - 기존 id로 엔티티를 새로 조립해 `save()`하면 `createdAt == null`이라 신규로 판정돼 flush 시점에 PK 중복으로 실패한다. 수정은 리포지토리로 조회한 관리 엔티티에만 한다.

```java
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseTimeEntity<ID extends Serializable> implements Persistable<ID> {
    @CreatedDate @Column(updatable = false) private Instant createdAt;   // @Column 제외 대상
    @LastModifiedDate @Column private Instant updatedAt;                 // Auditing이 insert 시에도 채움 — non-null

    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    @Override public boolean isNew() { return this.createdAt == null; } // merge penalty 방어
    @Override public abstract ID getId();   // 구현체에서 final 금지 — 프록시가 가로채는 경로다

    // 식별자 기반 동등성 — 수동 UUIDv7이 create()에서 확정돼 non-null·불변이라
    // orphanRemoval Set·LAZY 프록시·detached 비교에서 안전하다.
    // null 분기는 영속 전 프록시·미확정 상태 방어용이다.
    @Override public final boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BaseTimeEntity<?> that)) return false;   // 프록시도 instanceof true
        return getId() != null && getId().equals(that.getId());
    }
    @Override public final int hashCode() { return getId() == null ? 0 : getId().hashCode(); }
}
```

### 엔티티 ID

- `@Id`는 `create()` 팩토리에서 `UuidV7Generator.generate()`로 확정한다. `@GeneratedValue`를 붙이지 않는다.
  - `UuidV7Generator`(common-core)는 정적 `generate()`가 `UUID`를 반환한다. DB 왕복 없이 ID를 확정한다. 시퀀스·DB생성 식별자를 기각한 이유: 분산 전환 시 재작업이 되기 때문. 앱 생성 UUIDv7은 분산 전환에도 대비된다.
- 이 UUIDv7을 응답에서 문자열로 전송한다(명령이 ID 등 최소 결과만 반환하는 경계 원칙은 → [architecture](architecture.md)).

### 값 객체 매핑

- 값 객체(VO)는 컬럼 형상으로 매핑 방식을 가른다.
  - 단일 컬럼 VO(`Email`·`Money` 등 값 하나)는 `AttributeConverter` + `@Convert`로 매핑한다.
  - 다중 컬럼 VO(`Address`=우편번호+도로명 등 여러 값)는 `@Embeddable` record + `@Embedded`로 매핑한다. `AttributeConverter`로 억지로 직렬화하지 않는다 — 컬럼별 조회·인덱싱이 막힌다.
    - `@Embeddable` record는 로드 시 canonical 생성자를 호출해 compact constructor 검증이 매 조회마다 돈다. 레거시 데이터가 현재 불변식을 어기면 조회가 예외로 깨진다.
  - 판별 유니온 VO(형이 여럿이고 형마다 필드가 다른 값)는 단일 `@Embeddable` record로 평탄화하는 것이 기본이다 — 형 판별 enum 컬럼 + 형별 nullable 값 컬럼으로 매핑하고, 형↔필드 정합(불법 조합 배제)은 compact constructor가 강제하고 행위는 형 판별값으로 분기한다.
    - 평탄화의 타입 안전 손실(전수성이 컴파일타임에서 런타임 검사로 이동)은 record 하나에 국소로 갇히고, 대신 컬럼이 실재해 조회·인덱싱·집계를 연다.
    - 형별 nullable 값 컬럼은 컬럼 하나가 뜻 하나이고 형 판별 컬럼이 형을 구분하므로 컬럼 오버로딩이 아니다. 앱 밖 writer(raw SQL·마이그레이션)까지 정합을 강제하려면 엔티티 테이블 레벨 DB CHECK를 얹는다.
    - sealed 하위 타입 + `AttributeConverter`(JSON 등) 직렬화는 컬럼 안에 직렬화 포맷을 소유하는 비용(스키마 진화·불투명·다형 역직렬화 설정)을 지운다. 형·필드가 많아 평탄 테이블이 넓고 희소해질 때만 감수하고, 작은 유니온에선 평탄화가 낫다.
    - sealed 계층을 그대로 쓰지 못하는 이유: 임베더블은 다형성이 없어 sealed 인터페이스를 타입 컬럼으로 매핑할 수 없고, `@Inheritance`(엔티티 상속)는 값 객체를 엔티티로 승격시킨다.
- VO·`AttributeConverter`의 패키지 배치는 → [architecture](architecture.md)의 도메인 모듈 구조가 소유한다.

### 상태 전이

- 상태 enum은 `@Enumerated(EnumType.STRING)`으로 매핑한다.
  - ordinal은 enum 순서가 바뀌면 저장값이 깨진다.
- 상태 전이는 엔티티의 의도 동사 메서드(`confirm()`·`cancel()`)가 소유한다. setter로 상태를 바꾸지 않는다.
  - 허용 전이는 명시적 가드로 표현한다: 전이 메서드가 현재 상태를 검사해 불가하면 도메인 예외를 던진다(예: `if (status != PENDING) throw ...`). 전이가 많으면 `EnumMap<State, Set<State>>` 상수로 모은다.
  - 단일 애그리거트 내부 전이는 `Modifier`가 엔트리로 엔티티 메서드를 호출한다. 여러 애그리거트·부수효과를 조율하는 흐름의 엔트리는 `Processor`이되, 상태 변경 자체는 엔티티 메서드가 강제한다.
  - `Processor`의 한 트랜잭션도 하나의 애그리거트만 바꾼다(한 트랜잭션 하나의 애그리거트 원칙 → [architecture](architecture.md)의 리포지토리 접근 범위). 다른 애그리거트 변경은 별도 트랜잭션·후속 처리로 분리한다.

### 물리 FK 금지

- 물리 FK 없이 참조 정합은 애플리케이션이 책임진다.
  - 연관에 `@JoinColumn(foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))`를 명시하고 마이그레이션 DDL도 물리 FK를 만들지 않는다. 락 경합 방지·MSA 분리 용이.
  - 애노테이션은 의도 문서화·이중 방어다. `ddl-auto=validate`에선 애노테이션이 DDL을 만들지 않으므로 실제 차단은 Flyway DDL이 한다.
- 애그리거트·도메인 경계를 넘는 참조는 순수 `UUID xxxId` 필드로 보관한다. 객체 연관(`@ManyToOne`·`@OneToMany`)은 같은 애그리거트 내부에만 쓴다.
- 논리 FK 인덱스: 물리 FK가 없으면 인덱스가 자동 생성되지 않는다. 모든 `xxx_id` 컬럼의 인덱스는 Flyway 마이그레이션이 생성한다.
  - `ddl-auto=validate`는 테이블·컬럼만 검증하고 인덱스는 검증하지 않는다. `@Table(indexes)`만 믿고 Flyway에 빠뜨리면 인덱스 없이 통과해 운영에서 풀스캔이 된다.

### 연관

- 연관은 단방향·다대일을 기본으로 한다.
- 모든 연관에 `fetch = FetchType.LAZY`를 명시한다.
  - to-one(`@ManyToOne`·`@OneToOne`)의 기본값이 EAGER라 누락 시 배치 로딩 방어가 무력화된다. to-many는 기본이 LAZY지만 명시로 의도를 고정한다.
- 경계를 넘는·비캐스케이드 양방향(`mappedBy`)을 지양한다. 순환참조·직렬화 무한루프·삭제 순서 꼬임을 부른다. 역방향 조회는 리포지토리 쿼리로 푼다.
- 애그리거트 내부 연관은 자식→부모 `@ManyToOne` 단방향이 기본이다(자식이 FK 소유).
- 부모→자식 캐스케이드 생명주기가 필요하면 정규형은 `mappedBy` 양방향이다(부모 `@OneToMany(mappedBy=...)`, 자식 `@ManyToOne` FK 소유).
  - 단방향 `@OneToMany` + `@JoinColumn`은 쓰지 않는다 — 자식 INSERT 후 FK UPDATE가 추가로 발생하고 NOT NULL 제약과 충돌한다.
  - 자식이 부모를 읽지 않는 애그리거트에서 FK 소유 필드(`@ManyToOne`)는 매핑·`mappedBy` 대상 전용이라 자바 코드가 읽지 않는다. 미사용 필드 정적 분석(예: Error Prone `UnusedVariable`)이 지적하면 프레임워크가 리플렉션으로 읽는다는 사실을 필드에 마커로 표기하고(예: Error Prone `@Keep`), getter를 더해 상향 탐색을 열지 않는다.
    - 마커(사실 선언)가 `@SuppressWarnings`(검사 억제)보다 낫다 — 억제는 "이 검사를 끈다"는 습관을 퍼뜨려 진짜 미사용 코드까지 덮게 만든다.
- `@OneToMany` 컬렉션 필드는 `Set`으로 선언한다. 순서가 도메인 의미면 `@OrderColumn`으로 명시한다.
  - `List`는 자식 일부 삭제 시 delete-all-reinsert를 유발하고, 두 개 이상 `List` 연관을 동시 fetch join하면 `MultipleBagFetchException`이 난다.
- `@ManyToMany`를 금지한다. 조인 테이블에 컬럼을 못 붙이고 쿼리·캐스케이드가 불투명하다. 연결 테이블을 독립 `@Entity`로 승격한다.
  - 연결 엔티티가 같은 애그리거트 내부의 두 엔티티를 잇는 경우에만 양쪽을 `@ManyToOne` + `@JoinColumn(foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))`으로 매핑한다. 서로 다른 애그리거트를 잇는 경우 양쪽을 `UUID xxxId` 필드로 보관한다(경계 넘는 참조는 객체 연관 금지).
- 앱 레벨 캐스케이드: 신규 자식은 부모 애그리거트의 `@OneToMany(mappedBy = "...", cascade = CascadeType.ALL, orphanRemoval = true)`로 생성·정리한다(자식 단건 `INSERT` 금지의 소유는 → [architecture](architecture.md)).
  - `CascadeType.REMOVE`만으로는 삭제만 전파되고 생성은 전파되지 않는다.

### 버저닝 (`@Version`)

- 낙관락을 기본으로 두지 않는다(last-write-wins). 경합 민감 상태전이(잔액 차감·좌석 예약)만 `@Version`으로 승격한다.
  - `@Version` 필드에는 `version`(`BIGINT`, default 0) 컬럼을 Flyway로 추가한다 — `ddl-auto=validate`라 컬럼이 없으면 기동이 스키마 불일치로 실패한다.
  - 충돌 시 `ObjectOptimisticLockingFailureException`은 409로 응답한다(서버 자동 재시도 없음, 클라이언트 재시도). 재시도 폭주가 실측되면 비관락으로 승격한다.

### 소프트삭제

- 삭제는 논리삭제를 기본으로 한다. 삭제를 지원하는 엔티티는 nullable `deletedAt`(`Instant`) 컬럼을 두고, 물리 DELETE 대신 엔티티의 삭제 의도 메서드(`delete()`)로 `deletedAt`을 세팅한다. `Remover`가 이 메서드를 호출한다(setter 금지라 서비스가 필드를 직접 세팅하지 않는다).
  - 활성-only 조회·finder 직접 호출 금지·삭제 포함 조회 네이밍의 소유는 → [architecture](architecture.md). 소프트삭제는 그 규칙의 동기다.
  - 부모를 소프트삭제하면 같은 트랜잭션에서 자식도 소프트삭제한다 — `deletedAt` 세팅은 dirty checking일 뿐이라 `orphanRemoval`이 발동하지 않고 물리 FK도 없어 DB가 막지 않는다.

### 생성 진입점

- 엔티티 생성·변환 규칙(정적 팩토리 `create()` 하나·불변식 검증·팩토리 네이밍)의 소유는 → [coding-conventions](coding-conventions.md). 아래는 그 규칙을 적용한 엔티티 골격이다.

```java
@Entity
@Table(schema = "users", name = "users",       // 예약어 divergence: User 엔티티 → users 테이블
       indexes = @Index(name = "idx_user_team_id", columnList = "team_id")) // 논리 FK 인덱싱
public class User extends BaseTimeEntity<UUID> {
    @Id private UUID id;                        // @GeneratedValue 금지 — create()에서 확정
    @Convert(converter = EmailConverter.class) private Email email;  // VO 필드 — AttributeConverter로 varchar 매핑
    @Column private String name;                 // 무애노테이션 basic 금지 — @Column으로 제외 집합에 포함
    @Enumerated(EnumType.STRING) private Status status;  // ordinal 금지

    @Column(name = "team_id") @Nullable private UUID teamId;  // 경계 넘는 참조는 객체가 아니라 ID 값만(선택)
    @Nullable private Instant deletedAt;         // 소프트삭제 — delete()가 세팅(nullable)

    @Override public UUID getId() { return this.id; }
    public Email getEmail() { return email; }    // 명시적 getter — Info.from(entity)가 읽는 경로
    public String getName() { return name; }
    public Status getStatus() { return status; }

    protected User() {}                         // JPA 기본 생성자는 protected
    private User(UUID id, Email email, String name) {
        this.id = id; this.email = email; this.name = name; this.status = Status.PENDING;
    }

    public static User create(String email, String name) {   // 유일한 생성 진입점
        return new User(UuidV7Generator.generate(), Email.of(email), name);
    }

    public void activate() {                    // 상태 전이는 의도 동사 메서드 — 가드로 허용 전이 강제
        if (status != Status.PENDING) throw new UserStatusException(UserErrorCode.NOT_ACTIVATABLE);
        this.status = Status.ACTIVE;
    }

    public void delete() { this.deletedAt = Instant.now(); }   // 소프트삭제 의도 메서드 — Remover가 호출
}
```
