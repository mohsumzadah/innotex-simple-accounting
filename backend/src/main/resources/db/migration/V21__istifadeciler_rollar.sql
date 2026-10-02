-- Bir neçə istifadəçi və rol: ADMIN (sahib, istifadəçiləri idarə edir) | ACCOUNTANT (mühasib).
-- Mövcud istifadəçi ADMIN olur. Köhnə sessiyalar istifadəçiyə bağlı olmadığı üçün silinir (yenidən giriş lazımdır).
alter table app_user add column name varchar(200) not null default '';
alter table app_user add column role varchar(20) not null default 'ADMIN';
alter table app_user add column active boolean not null default true;
alter table app_user add column created_at timestamp;
alter table app_user add constraint app_user_role_chk check (role in ('ADMIN', 'ACCOUNTANT'));

delete from session_token;
alter table session_token add column user_id bigint not null references app_user(id) on delete cascade;
create index session_token_user_idx on session_token(user_id);
