-- Hesablama metodu: xərc sənəd (invoys) tarixinə aid olur, ödəniş tarixi (entry_date) hesab hərəkətini müəyyən edir
alter table expense add column doc_date date;
update expense set doc_date = entry_date;
update expense set doc_date = '2026-09-04' where description like '%083001135193%';
alter table expense alter column doc_date set not null;

alter table settings add column tax_method varchar(20) not null default 'ACCRUAL';
