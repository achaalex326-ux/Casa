insert into public.families(id,name) select i,'Familia de prueba '||i from generate_series(1,6) i;
insert into public.profiles(id,display_name,username,contact_email,role,family_id,second_family_id) values
 ('00000000-0000-0000-0000-000000000001','Admin','admin','admin@example.test','sysadmin',null,null),
 ('00000000-0000-0000-0000-000000000002','Encargado','payer','payer@example.test','payer',1,2),
 ('00000000-0000-0000-0000-000000000003','Familia','family','family@example.test','family',3,null);
insert into public.expenses(id,concept,category,total_amount,expense_date,due_date,created_by) values
 ('10000000-0000-0000-0000-000000000001','Servicios de prueba','basic',600,'2026-09-01','2026-09-20','00000000-0000-0000-0000-000000000001'),
 ('10000000-0000-0000-0000-000000000002','Extraordinario de prueba','external',60,'2026-09-01','2026-09-20','00000000-0000-0000-0000-000000000001');
insert into public.expense_shares(id,expense_id,family_id,amount_due)
 select ('20000000-0000-0000-0000-'||lpad(i::text,12,'0'))::uuid,'10000000-0000-0000-0000-000000000001',i,100 from generate_series(1,6) i;
insert into public.expense_shares(id,expense_id,family_id,amount_due) values
 ('20000000-0000-0000-0000-000000000007','10000000-0000-0000-0000-000000000002',1,10);
insert into public.payments(id,share_id,family_id,amount,applied_amount,credit_amount,paid_at,kind,status,created_by) values
 ('30000000-0000-0000-0000-000000000001','20000000-0000-0000-0000-000000000001',1,40,40,0,'2026-09-10','manual','approved','00000000-0000-0000-0000-000000000001'),
 ('30000000-0000-0000-0000-000000000002','20000000-0000-0000-0000-000000000002',2,10,0,0,'2026-09-10','manual','pending','00000000-0000-0000-0000-000000000001');
insert into public.family_credits(family_id,balance) values(1,15),(3,20);
create table test.original_payments as select * from public.payments;
create table test.original_shares as select * from public.expense_shares;
create table test.original_expenses as select * from public.expenses;
create table test.original_credits as select * from public.family_credits;
