create schema if not exists skeleton;

create table skeleton.skeleton_probe (
    id         uuid         primary key,
    label      varchar(200) not null,
    created_at timestamptz  not null,
    updated_at timestamptz  not null
);

create index idx_skeleton_probe_label on skeleton.skeleton_probe (label);
