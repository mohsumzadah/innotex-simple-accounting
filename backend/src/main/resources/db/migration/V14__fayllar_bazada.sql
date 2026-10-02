-- Fayl məzmunu PostgreSQL-də saxlanılır (diskdən asılılıq aradan qalxır).
-- content köçürmə zamanı boş ola bilər: mövcud fayllar proqram başlayanda (FileMigrator) bazaya köçürülür.
alter table stored_file add column content bytea;
alter table stored_file add column content_type varchar(100);
alter table stored_file add column sha256 varchar(64);
alter table stored_file add column created_at timestamp not null default now();
