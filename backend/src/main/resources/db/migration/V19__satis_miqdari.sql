-- Satışın miqdarı (nomenklatura vahidi ilə, məs. 2 ədəd lisenziya). Qiymət = vahid qiymət × miqdar (UI hesablayır, redaktə olunur).
alter table deal add column quantity int not null default 1;
