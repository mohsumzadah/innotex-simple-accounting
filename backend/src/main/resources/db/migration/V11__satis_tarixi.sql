-- Satış tarixi (keçmişə də qoyula bilər) və mərhələ hadisəsinin tarixi
alter table deal add column sale_date date;
update deal set sale_date = coalesce(contract_date, cast(created_at as date));
alter table deal alter column sale_date set not null;

alter table deal_event add column event_date date;
update deal_event set event_date = cast(created_at as date);
alter table deal_event alter column event_date set not null;
