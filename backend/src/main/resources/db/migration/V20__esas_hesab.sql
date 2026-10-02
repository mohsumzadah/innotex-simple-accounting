-- Əsas (default) hesab: yeni mədaxil/xərc formalarında avtomatik seçilir. Ən çox bir hesab əsas olur.
alter table account add column is_default boolean not null default false;
update account set is_default = true where id = (select min(id) from account where type = 'BANK');
