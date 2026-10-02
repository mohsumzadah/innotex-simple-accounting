-- Şirkət hesablarının və işçilərin bank rekvizitləri (hamısı ixtiyari; Excel-dən köçürmə üçün)
alter table account add column bank_name varchar(200);
alter table account add column iban varchar(50);
alter table account add column bank_code varchar(30);
alter table account add column bank_voen varchar(20);
alter table account add column swift varchar(20);
alter table account add column correspondent_account varchar(50);
alter table account add column card_number varchar(30);
alter table account add column note varchar(500);
alter table employee add column bank_name varchar(200);
alter table employee add column iban varchar(50);
alter table employee add column card_number varchar(30);
alter table employee add column fin varchar(20);
alter table employee add column note varchar(500);
