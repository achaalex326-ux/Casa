-- Extends payer_services_fixture.sql, solely in a disposable PostgreSQL container.
alter table public.families add primary key(id);
alter table public.profiles add primary key(id);
alter table public.expenses add primary key(id);
alter table public.expense_shares add primary key(id);
alter table public.payments add primary key(id);
alter table public.payments add check(amount>0),add check(applied_amount>=0),add check(credit_amount>=0),add check(applied_amount+credit_amount<=amount);
create schema storage;
create table storage.objects(bucket_id text,name text);
create table test.alerts(family_id smallint,kind text,title text,body text,ref_id uuid);
create function public.notify_family(smallint,text,text,text,text,uuid,text) returns void language sql as $$insert into test.alerts values($1,$2,$3,$4,$6)$$;
create function public.notify_all(text,text,text,text,uuid,text) returns void language sql as $$insert into test.alerts values(null,$1,$2,$3,$5)$$;
create or replace function public.current_app_role() returns public.app_role language sql stable security definer set search_path='' as $$select role from public.profiles where id=auth.uid()$$;
create function test.expect_error(statement text,expected text) returns void language plpgsql as $$begin begin execute statement;exception when others then if sqlstate<>expected then raise exception 'Wrong SQLSTATE %, expected %: %',sqlstate,expected,sqlerrm;end if;insert into test.results values('Denied: '||statement);return;end;raise exception 'Unexpectedly allowed: %',statement;end;$$;
create or replace function public.audit(text,text,text,jsonb) returns void language plpgsql as $$begin if current_setting('test.fail_audit',true)='on' then raise exception 'Simulated audit failure';end if;insert into test.audit_log values($1,$2,$3,$4);end;$$;
grant usage on schema test,auth to authenticated,anon;
grant execute on function auth.uid() to authenticated,anon;
grant select,insert on test.results to authenticated,anon;
grant select on public.profiles,public.families,public.payments to authenticated;
alter table public.profiles enable row level security;
create policy own_profile on public.profiles for select to authenticated using(id=auth.uid());
alter table public.payments enable row level security;
create policy payment_read on public.payments for select to authenticated using(exists(select 1 from public.profiles p where p.id=auth.uid() and (p.role::text in ('sysadmin','payer') or p.family_id=payments.family_id)));
insert into public.families(id,name) values(1,'Hogar Uno'),(2,'Hogar Dos'),(3,'Hogar Tres');
insert into public.profiles(id,display_name,username,contact_email,role,family_id) values
('00000000-0000-0000-0000-000000000001','Admin','admin','admin@example.test','sysadmin',null),
('00000000-0000-0000-0000-000000000002','Familia','family','family@example.test','family',1),
('00000000-0000-0000-0000-000000000003','Encargado','payer','payer@example.test','payer',2),
('00000000-0000-0000-0000-000000000004','Otra familia','other','other@example.test','family',2),
('00000000-0000-0000-0000-000000000005','Restaurante','restaurant','restaurant@example.test','restaurant',3);
-- Deliberately insert months out of order to test chronological allocation.
insert into public.expenses(id,concept,category,total_amount,expense_date,due_date,created_by) values
('10000000-0000-0000-0000-000000000003','Marzo','basic',240,'2030-03-01','2030-03-20','00000000-0000-0000-0000-000000000001'),
('10000000-0000-0000-0000-000000000001','Enero','basic',240,'2030-01-01','2030-01-20','00000000-0000-0000-0000-000000000001'),
('10000000-0000-0000-0000-000000000002','Febrero','basic',240,'2030-02-01','2030-02-20','00000000-0000-0000-0000-000000000001');
insert into public.expense_shares(id,expense_id,family_id,amount_due) values
('20000000-0000-0000-0000-000000000003','10000000-0000-0000-0000-000000000003',1,80),
('20000000-0000-0000-0000-000000000001','10000000-0000-0000-0000-000000000001',1,80),
('20000000-0000-0000-0000-000000000002','10000000-0000-0000-0000-000000000002',1,80),
('20000000-0000-0000-0000-000000000004','10000000-0000-0000-0000-000000000001',2,80);
insert into storage.objects values('receipts','00000000-0000-0000-0000-000000000002/deposit.png'),('receipts','00000000-0000-0000-0000-000000000002/other.png');

create function public.current_family_id() returns smallint language sql stable security definer set search_path='' as $$select family_id from public.profiles where id=auth.uid()$$;
insert into public.monthly_basic_services(month_start,due_date,expense_id,monthly_expense_id,created_by)
select expense_date,due_date,id,id,created_by from public.expenses;
