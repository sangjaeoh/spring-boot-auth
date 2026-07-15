-- 약관: 문서(유형)와 버전은 생명주기가 달라 별 애그리거트(DOMAIN_MODEL §1.4). 버전은 발행 후 불변·append 전용.
create table usr.terms_document (
    id         uuid        primary key,
    type       varchar(30) not null,
    required   boolean     not null,
    created_at timestamptz not null,
    updated_at timestamptz not null
);

create unique index uq_terms_document_type on usr.terms_document (type);

create table usr.terms_version (
    id             uuid        primary key,
    type           varchar(30) not null,
    version        int         not null,
    content        text        not null,
    effective_from timestamptz not null,
    created_at     timestamptz not null,
    updated_at     timestamptz not null
);

create unique index uq_terms_version_type_version on usr.terms_version (type, version);
