-- Word (.docx) şablonları və sənədləri: format + fayl; DOCX üçün html boş qalır
alter table template alter column html drop not null;
alter table template add column format varchar(10) not null default 'HTML';
alter table template add column file_id bigint;
alter table deal_document alter column html drop not null;
alter table deal_document add column format varchar(10) not null default 'HTML';
alter table deal_document add column file_id bigint;
alter table deal_document add column previous_file_id bigint;
