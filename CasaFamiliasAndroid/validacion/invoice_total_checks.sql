-- Only executed against the disposable fixture database.
select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000001',false);
create temporary table worksheet(month date,water numeric,electricity numeric,wifi numeric,total numeric,share numeric);
insert into worksheet values
 ('2026-01-01',36,211.5,149,396.5,66.08),
 ('2026-02-01',24,165.6,149,338.6,56.43),
 ('2026-03-01',32,145.2,149,326.2,54.37),
 ('2026-04-01',36,243.6,149,428.6,71.43),
 ('2026-05-01',36.5,198,149,383.5,63.92),
 ('2026-06-01',36.5,191.6,149,377.1,62.85);
-- Baselines intentionally much lower than the invoices: must have no effect.
insert into utility_baselines(service,base_amount) values('water',1),('electricity',1);
update family_credits set balance=0;

do $$ declare w record; m uuid; begin
 for w in select * from worksheet order by month loop
  m:=public.casa_create_monthly_services(w.month,w.water,w.electricity,0,w.wifi,
    (w.month+interval '1 month - 1 day')::date,null,null,null,null);
  perform test.check((select family_total_amount=w.total and gas_amount=0 and family_count=6 and calculation_method='bill_total'
    and restaurant_water_amount=0 and restaurant_electricity_amount=0 from monthly_basic_services where id=m),'Full invoice sum '||w.month);
  perform test.check((select sum(s.amount_due)=w.total and count(*)=6 from expense_shares s join monthly_basic_services b on b.expense_id=s.expense_id where b.id=m),'Conserved cents '||w.month);
  perform test.check((select bool_and(s.amount_due=w.share) from expense_shares s join monthly_basic_services b on b.expense_id=s.expense_id where b.id=m and s.family_id<6),'Worksheet amounts '||w.month);
  perform test.check(not exists(select 1 from expense_shares s join monthly_basic_services b on b.expense_id=s.expense_id where b.id=m and s.family_id in(1,2) and s.amount_due<>public.share_approved_paid(s.id)),'Payer covered '||w.month);
  perform test.check((select bool_and(public.share_approved_paid(s.id)=0) from expense_shares s join monthly_basic_services b on b.expense_id=s.expense_id where b.id=m and s.family_id not in(1,2)),'No invented payments '||w.month);
 end loop;
end $$;
select test.check(not exists(select 1 from external_utility_debts),'No automatic restaurant debts');
select test.check((select count(*)=6 from monthly_basic_services),'Six months only');
select test.check((select sum(family_total_amount)=2250.50 from monthly_basic_services),'Semester total 2250.50');

create or replace function test.expect_rejection(statement text,expected text) returns void language plpgsql as $$
begin
 begin execute statement; exception when others then
  if position(expected in sqlerrm)=0 then raise;end if;
  perform test.check(true,'Rejected: '||expected);return;
 end;raise exception 'Unexpectedly permitted: %',statement;
end $$;
select test.expect_rejection($q$select public.casa_create_monthly_services('2026-01-01',36,211.5,0,149,'2026-01-31',null,null,null,null)$q$,'Ese mes ya tiene');
select test.expect_rejection($q$select public.casa_create_monthly_services('2026-07-01',-0.001,1,0,1,'2026-07-31',null,null,null,null)$q$,'iguales o mayores');
select test.expect_rejection($q$select public.casa_create_monthly_services('2026-07-01','NaN',1,0,1,'2026-07-31',null,null,null,null)$q$,'iguales o mayores');
select test.expect_rejection($q$select public.casa_create_monthly_services('2026-07-01',1,1,0,1,null,null,null,null,null)$q$,'Faltan');
select test.expect_rejection($q$select public.casa_create_monthly_services('2026-07-01',1,1,0,1,'2026-07-31',null,null,null,null,2,0)$q$,'superar');
select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000003',false);
select test.expect_rejection($q$select public.casa_create_monthly_services('2026-07-01',1,1,0,1,'2026-07-31',null,null,null,null)$q$,'No tienes permiso');
select set_config('request.jwt.claim.sub','',false);
select test.expect_rejection($q$select public.casa_create_monthly_services('2026-07-01',1,1,0,1,'2026-07-31',null,null,null,null)$q$,'No tienes permiso');
select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000002',false);
insert into utility_credits(service,amount) values('water',10),('wifi',5);
select public.casa_create_monthly_services('2026-07-01',36,211.5,0,149,'2026-07-31',null,null,null,null,2,0);
select test.check((select family_total_amount=379.5 and other_water_contribution=12 from monthly_basic_services where month_start='2026-07-01'),'Only explicit discounts reduce total');
select test.check((select count(*)=2 from utility_credits where applied_month_start='2026-07-01'),'Explicit credits consumed once');
select public.casa_create_monthly_services('2026-08-01',36,211.5,0,149,'2026-08-31',null,null,null,null);
select test.check((select family_total_amount=396.5 from monthly_basic_services where month_start='2026-08-01'),'Credits not reused');
insert into utility_credits(service,amount) values('gas',1);
select test.expect_rejection($q$select public.casa_create_monthly_services('2026-09-01',0,0,0,0,'2026-09-30',null,null,null,null)$q$,'superar');
select test.check((select count(*)=1 from utility_credits where applied_month_start is null),'Excess credit retained on rejection');
select public.casa_create_monthly_services('2026-09-01',1,1,0,1,'2026-09-30',null,null,null,null,0,0,true);
select test.check((select expense_id is null and family_total_amount=0 and is_reference_only from monthly_basic_services where month_start='2026-09-01'),'Reference does not create debt');
select test.check((select count(*)=1 from utility_credits where applied_month_start is null),'Reference preserves credits');
delete from utility_credits where applied_month_start is null;
select public.casa_create_monthly_services('2026-10-01',0,0,0,0,'2026-10-31',null,null,null,null);
select test.check((select expense_id is null and family_total_amount=0 and not is_reference_only from monthly_basic_services where month_start='2026-10-01'),'Zero month registered without fictitious debt');
select public.casa_create_monthly_services('2026-11-01',0.03,0,0,0,'2026-11-30',null,null,null,null);
select test.check((select sum(s.amount_due)=0.03 and min(s.amount_due)>=0 from expense_shares s join monthly_basic_services b on b.expense_id=s.expense_id where b.month_start='2026-11-01'),'Small totals never make negative shares');
select test.check((select count(*)=11 from test.audit_log where entity='monthly_basic_services' and action='create'),'One audit entry per created month');
select test.check(not has_function_privilege('anon','public.casa_create_monthly_services(date,numeric,numeric,numeric,numeric,date,text,text,text,text,numeric,numeric,boolean,numeric,integer)','execute'),'Anon cannot call new entry point');
select test.check(has_function_privilege('authenticated','public.casa_create_monthly_services(date,numeric,numeric,numeric,numeric,date,text,text,text,text,numeric,numeric,boolean,numeric,integer)','execute'),'Authenticated entry point available');
select test.expect_rejection($q$select public.casa_create_monthly_services('2027-01-01',36,211.5,0,149,'2027-01-31',null,null,null,null,0,0,false,310.28,6)$q$,'El reparto cambió');
select test.expect_rejection($q$select public.casa_create_monthly_services('2027-01-01',36,211.5,0,149,'2027-01-31',null,null,null,null,0,0,false,396.50,5)$q$,'El reparto cambió');
select test.check(not exists(select 1 from monthly_basic_services where month_start='2027-01-01'),'Changed preview rolls back the entire month');
select json_build_object('invoiceTotalChecks',count(*),'status','PASS') from test.results;
