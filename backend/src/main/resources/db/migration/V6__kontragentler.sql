-- Müştərilər -> Kontragentlər: növ, bank VÖEN, müxbir hesab, qeyd; şirkət ayarlarına bank VÖEN və müxbir hesab
alter table customer add column kind varchar(10) not null default 'CUSTOMER';
alter table customer add column bank_voen varchar(50);
alter table customer add column correspondent_account varchar(50);
alter table customer add column note varchar(1000);
alter table settings add column bank_voen varchar(50) not null default '';
alter table settings add column correspondent_account varchar(50) not null default '';

-- şablonların rekvizit bloklarına yeni sahələr (yalnız SWIFT sətri olan yerlərə)
update template set html = replace(html, 'SWIFT: {{company.swift}}', 'SWIFT: {{company.swift}}<br>Bank VÖEN: {{company.bankVoen}}<br>Müxbir hesab: {{company.correspondentAccount}}');
update template set html = replace(html, 'SWIFT: {{customer.swift}}', 'SWIFT: {{customer.swift}}<br>Bank VÖEN: {{customer.bankVoen}}<br>Müxbir hesab: {{customer.correspondentAccount}}');
