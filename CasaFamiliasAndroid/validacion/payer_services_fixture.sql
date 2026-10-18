-- Esquema mínimo derivado de metadatos, con datos completamente ficticios.
-- Solo se carga en PostgreSQL desechable de test_payer_services.py.
create role anon; create role authenticated;
create schema auth; create schema test;
create function auth.uid() returns uuid language sql stable as $$ select nullif(current_setting('request.jwt.claim.sub',true),'')::uuid $$;
create type public.app_role as enum ('sysadmin','payer','family','restaurant');
create type public.cleaning_status as enum ('pending','done');
create type public.expense_category as enum ('basic','external');
create type public.fund_tx_type as enum ('contribution','income','expense');
create type public.payment_kind as enum ('auto','manual','credit');
create type public.payment_status as enum ('pending','approved','rejected');
create type public.period_status as enum ('open','closed');
create type public.proposal_status as enum ('open','approved','rejected');
create type public.swap_status as enum ('pending','accepted','rejected','cancelled');
create table public.families (id int2 not null,
name text not null,
created_at timestamptz not null default now());
create table public.profiles (id uuid not null,
display_name text not null,
username text not null,
contact_email text not null,
role public.app_role not null default 'family'::app_role,
family_id int2,
created_at timestamptz not null default now(),
second_family_id int2);
create table public.expenses (id uuid not null default gen_random_uuid(),
concept text not null,
category public.expense_category not null,
total_amount numeric not null,
expense_date date not null,
due_date date not null,
justification text,
receipt_path text,
ocr_text text,
paid_by_family_id int2,
fund_amount_used numeric not null default 0,
created_by uuid not null,
created_at timestamptz not null default now());
create table public.expense_shares (id uuid not null default gen_random_uuid(),
expense_id uuid not null,
family_id int2 not null,
amount_due numeric not null,
created_at timestamptz not null default now());
create table public.payments (id uuid not null default gen_random_uuid(),
share_id uuid not null,
family_id int2 not null,
amount numeric not null,
applied_amount numeric not null default 0,
credit_amount numeric not null default 0,
paid_at date not null,
kind public.payment_kind not null default 'manual'::payment_kind,
status public.payment_status not null default 'pending'::payment_status,
receipt_path text,
note text,
ocr_text text,
created_by uuid not null,
reviewed_by uuid,
reviewed_at timestamptz,
created_at timestamptz not null default now());
create table public.monthly_basic_services (id uuid not null default gen_random_uuid(),
month_start date not null,
water_amount numeric not null default 0,
electricity_amount numeric not null default 0,
gas_amount numeric not null default 0,
wifi_amount numeric not null default 0,
due_date date not null,
water_receipt_path text,
electricity_receipt_path text,
gas_receipt_path text,
wifi_receipt_path text,
expense_id uuid,
created_by uuid not null,
created_at timestamptz not null default now(),
is_reference_only bool not null default false,
family_water_base_amount numeric not null default 0,
family_electricity_base_amount numeric not null default 0,
other_water_contribution numeric not null default 0,
other_electricity_contribution numeric not null default 0,
family_water_amount numeric not null default 0,
family_electricity_amount numeric not null default 0,
restaurant_water_amount numeric not null default 0,
restaurant_electricity_amount numeric not null default 0,
family_total_amount numeric not null default 0,
monthly_expense_id uuid);
create table public.utility_baselines (service text not null,
base_amount numeric not null default 0,
source_months int2 not null default 6,
updated_by uuid,
updated_at timestamptz not null default now());
create table public.utility_credits (id uuid not null default gen_random_uuid(),
service text not null,
amount numeric not null,
note text,
receipt_path text,
created_by uuid,
created_at timestamptz not null default now(),
applied_month_start date,
applied_at timestamptz);
create table public.external_utility_debts (id uuid not null default gen_random_uuid(),
profile_id uuid not null,
payer_name text not null,
service text not null,
month_start date not null,
bill_amount numeric not null default 0,
family_base_amount numeric not null default 0,
amount_due numeric not null default 0,
amount_paid numeric not null default 0,
due_date date not null,
status text not null default 'pending'::text,
received_by text,
paid_at timestamptz,
receipt_path text,
note text,
created_by uuid not null,
created_at timestamptz not null default now());
create table public.notifications (id uuid not null default gen_random_uuid(),
user_id uuid not null,
kind text not null,
title text not null,
body text not null,
ref_entity text,
ref_id uuid,
dedupe_key text,
read_at timestamptz,
created_at timestamptz not null default now());
create table public.family_credits (family_id int2 not null,
balance numeric not null default 0,
updated_at timestamptz not null default now());
alter table public.utility_baselines add primary key(service);
alter table public.family_credits add primary key(family_id);
alter table public.external_utility_debts add unique(profile_id,service,month_start);
alter table public.monthly_basic_services add unique(month_start);
create function public.current_app_role() returns public.app_role language sql stable as $$ select role from public.profiles where id=auth.uid() $$;
create function public.is_period_closed(date) returns boolean language sql stable as $$ select false $$;
create table test.audit_log(action text,entity text,entity_id text,details jsonb);
create function public.audit(text,text,text,jsonb) returns void language sql as $$ insert into test.audit_log values($1,$2,$3,$4) $$;
create table test.results(label text);
create function test.check(ok boolean,label text) returns void language plpgsql as $$ begin if ok is distinct from true then raise exception 'FAIL: %',label;end if;insert into test.results values(label);end $$;
CREATE OR REPLACE FUNCTION public.share_approved_paid(p_share uuid)
 RETURNS numeric
 LANGUAGE sql
 STABLE SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
  select coalesce(sum(applied_amount),0) from public.payments
  where share_id=p_share and status='approved'
$function$
;
CREATE OR REPLACE FUNCTION public.apply_credit_to_share(p_share uuid, p_actor uuid)
 RETURNS void
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
declare
  v_share public.expense_shares%rowtype;
  v_credit numeric(12,2);
  v_paid numeric(12,2);
  v_use numeric(12,2);
  v_expense public.expenses%rowtype;
begin
  select * into v_share from public.expense_shares where id=p_share;
  if not found or v_share.amount_due<=0 then return; end if;

  insert into public.family_credits(family_id,balance)
  values(v_share.family_id,0)
  on conflict(family_id) do nothing;

  select balance into v_credit
  from public.family_credits
  where family_id=v_share.family_id
  for update;

  v_paid:=public.share_approved_paid(v_share.id);
  v_use:=least(v_credit,greatest(v_share.amount_due-v_paid,0));
  if v_use<=0 then return; end if;

  select * into v_expense from public.expenses where id=v_share.expense_id;
  insert into public.payments(
    share_id,family_id,amount,applied_amount,credit_amount,paid_at,
    kind,status,note,created_by,reviewed_by,reviewed_at
  ) values(
    v_share.id,v_share.family_id,v_use,v_use,0,v_expense.expense_date,
    'credit','approved','Aplicado automáticamente desde saldo a favor',
    p_actor,p_actor,now()
  );

  update public.family_credits
  set balance=balance-v_use,updated_at=now()
  where family_id=v_share.family_id;
end $function$
;
