-- Kontragent: hüquqi/fiziki şəxs ayrıca ölçüdür (entity_type), kind yalnız rol (CUSTOMER|SUPPLIER|BOTH). Kart nömrəsi fiziki şəxsin geri ödəniş/ödəniş üçün rekvizitidir.
alter table customer add column entity_type varchar(12) not null default 'LEGAL';
alter table customer add column card_number varchar(30);
update customer set entity_type = 'INDIVIDUAL', kind = 'CUSTOMER' where kind = 'INDIVIDUAL';
