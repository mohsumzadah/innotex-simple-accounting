-- Ayarlar: mənfəət vergisi 20%, rekvizitlər boş (Ayarlardan daxil edilir)
insert into settings (id, profit_tax_rate) values (1, 20);

-- Başlanğıc maaş dərəcələri (2026-01), istifadəçinin verdiyi dəyərlər; faizlər %-lə
insert into payroll_rate (valid_from, dsmf_limit, dsmf_emp_low, dsmf_emp_high, dsmf_er_low, dsmf_er_high, unemp_emp, unemp_er,
  med_limit, med_low, med_high, income_limit, income_exempt, income_low, income_high)
values ('2026-01', 200, 3, 10, 22, 15, 0.5, 0.5, 8000, 2, 0.5, 8000, 200, 3, 14);

-- 2026 iş günü cədvəli (5 günlük rejim)
insert into work_calendar (period, days) values
 ('2026-01', 20), ('2026-02', 20), ('2026-03', 16), ('2026-04', 22), ('2026-05', 18), ('2026-06', 19),
 ('2026-07', 23), ('2026-08', 21), ('2026-09', 22), ('2026-10', 22), ('2026-11', 19), ('2026-12', 22);
