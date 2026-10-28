-- One-off operation explicitly requested from the January-June 2026 worksheet.
-- Run only after migration 010 and a fresh read of the project's accounts.
-- Yellow/gray are NOT payment evidence. Real payments and prior adjustments stay.
-- Missing months use their calendar month end as the due date. Existing dates stay.
begin;
set local lock_timeout = '5s';
set local statement_timeout = '30s';
lock table public.monthly_basic_services, public.expenses, public.expense_shares,
  public.payments, public.payment_batches, public.utility_credits, public.external_utility_debts,
  public.family_credits in share row exclusive mode;

create table if not exists public.casa_finance_repairs(
  id text primary key, before_data jsonb not null, applied_by uuid not null,
  applied_at timestamptz not null default now()
);
alter table public.casa_finance_repairs enable row level security;
revoke all on public.casa_finance_repairs from anon, authenticated;

create temporary table casa_worksheet(month date,water numeric,electricity numeric,wifi numeric,total numeric) on commit drop;
insert into casa_worksheet values
 ('2026-01-01',36,211.50,149,396.50),
 ('2026-02-01',24,165.60,149,338.60),
 ('2026-03-01',32,145.20,149,326.20),
 ('2026-04-01',36,243.60,149,428.60),
 ('2026-05-01',36.50,198,149,383.50),
 ('2026-06-01',36.50,191.60,149,377.10);

do $repair$
declare
  v_actor uuid; v_w record; v_m public.monthly_basic_services%rowtype;
  v_f record; v_s public.expense_shares%rowtype;
  v_expense uuid; v_base numeric(12,2); v_amount numeric(12,2); v_paid numeric(12,2);
  v_index integer; v_payer boolean; v_before jsonb;
  v_tag constant text := 'worksheet-2026-jan-jun-full-invoices-v1';
begin
  if exists(select 1 from public.casa_finance_repairs where id=v_tag) then
    if exists(select 1 from casa_worksheet sheet left join monthly_basic_services b on b.month_start=sheet.month
      where b.id is null or b.water_amount<>sheet.water or b.electricity_amount<>sheet.electricity
        or b.gas_amount<>0 or b.wifi_amount<>sheet.wifi or b.family_total_amount<>sheet.total) then
      raise exception 'La reparación ya se aplicó, pero los datos cambiaron después. Revisar antes de repetir.';
    end if;
    return;
  end if;
  if (select count(*) from public.families)<>6 then raise exception 'La hoja corresponde a seis cuentas; revisar las familias actuales'; end if;
  select id into v_actor from public.profiles where role::text='sysadmin' order by id limit 1;
  if v_actor is null then raise exception 'Falta un administrador para auditar la corrección'; end if;
  if auth.uid() is not null and coalesce(public.current_app_role()::text,'')<>'sysadmin' then
    raise exception 'Solo el administrador puede corregir los históricos';
  end if;
  -- An applied utility discount cannot be silently removed and then reused.
  if exists(select 1 from public.utility_credits where applied_month_start in(select month from casa_worksheet)) then
    raise exception 'Hay descuentos aplicados a estos meses: conciliar antes de reemplazar el reparto';
  end if;
  if exists(select 1 from public.external_utility_debts where month_start in(select month from casa_worksheet) and amount_paid>0) then
    raise exception 'Hay aportes reales del restaurante: conciliar antes de reasignar esos importes';
  end if;
  select jsonb_build_object(
    'monthly_basic_services',(select jsonb_agg(to_jsonb(t)) from monthly_basic_services t where month_start in(select month from casa_worksheet)),
    'expenses',(select jsonb_agg(to_jsonb(e)) from expenses e where id in(select expense_id from monthly_basic_services where month_start in(select month from casa_worksheet))),
    'shares',(select jsonb_agg(to_jsonb(saved_share)) from expense_shares saved_share where expense_id in(select expense_id from monthly_basic_services where month_start in(select month from casa_worksheet))),
    'payments',(select jsonb_agg(to_jsonb(p)) from payments p where share_id in(select id from expense_shares where expense_id in(select expense_id from monthly_basic_services where month_start in(select month from casa_worksheet)))),
    'restaurant',(select jsonb_agg(to_jsonb(d)) from external_utility_debts d where month_start in(select month from casa_worksheet)),
    'family_credits',(select jsonb_agg(to_jsonb(c)) from family_credits c)
  ) into v_before;
  insert into public.casa_finance_repairs(id,before_data,applied_by) values(v_tag,v_before,v_actor);
  perform set_config('request.jwt.claim.sub',v_actor::text,true);
  for v_w in select * from casa_worksheet order by month loop
    select * into v_m from monthly_basic_services where month_start=v_w.month;
    if not found then
      -- Do not consume unrelated, pending utility discounts in this exact import.
      if exists(select 1 from public.utility_credits where applied_month_start is null) then
        raise exception 'Hay descuentos pendientes: conciliar antes de importar meses faltantes';
      end if;
      perform public.casa_create_monthly_services(v_w.month,v_w.water,v_w.electricity,0,v_w.wifi,
        (v_w.month+interval '1 month - 1 day')::date,null,null,null,null);
      continue;
    end if;
    v_expense:=coalesce(v_m.expense_id,v_m.monthly_expense_id);
    if v_expense is null then
      insert into expenses(concept,category,total_amount,expense_date,due_date,justification,created_by)
        values('Servicios básicos '||to_char(v_w.month,'YYYY-MM'),'basic',v_w.total,v_w.month,v_m.due_date,
          'Importado de la hoja de servicios 2026; gas cero, total dividido entre seis cuentas.',v_actor)
        returning id into v_expense;
    end if;
    if exists(select 1 from monthly_basic_services where id<>v_m.id and (expense_id=v_expense or monthly_expense_id=v_expense)) then
      raise exception 'Dos meses comparten un gasto; revisar antes de corregir';
    end if;
    update expenses set total_amount=v_w.total where id=v_expense;
    update monthly_basic_services set water_amount=v_w.water,electricity_amount=v_w.electricity,gas_amount=0,wifi_amount=v_w.wifi,
      family_water_base_amount=v_w.water,family_electricity_base_amount=v_w.electricity,
      other_water_contribution=0,other_electricity_contribution=0,
      family_water_amount=v_w.water,family_electricity_amount=v_w.electricity,
      restaurant_water_amount=0,restaurant_electricity_amount=0,family_total_amount=v_w.total,
      expense_id=v_expense,monthly_expense_id=v_expense,is_reference_only=false,
      calculation_method='bill_total',family_count=6 where id=v_m.id;
    if exists(select family_id from expense_shares where expense_id=v_expense group by family_id having count(*)<>1) then
      raise exception 'Hay deudas duplicadas en un mes; revisar antes de corregir';
    end if;
    v_base:=round(v_w.total/6,2);v_index:=0;
    for v_f in select id from families order by id loop
      v_index:=v_index+1;
      v_amount:=case when v_index=6 then v_w.total-v_base*5 else v_base end;
      select * into v_s from expense_shares where expense_id=v_expense and family_id=v_f.id;
      if not found then
        insert into expense_shares(expense_id,family_id,amount_due) values(v_expense,v_f.id,v_amount) returning * into v_s;
      else
        v_paid:=public.share_approved_paid(v_s.id);
        if v_paid>v_amount then raise exception 'Un pago o ajuste previo supera el nuevo importe; conciliar sin perder el saldo a favor'; end if;
        update expense_shares set amount_due=v_amount where id=v_s.id;
      end if;
      v_payer:=exists(select 1 from profiles where role::text='payer' and v_f.id in(family_id,second_family_id));
      v_paid:=public.share_approved_paid(v_s.id);
      if v_payer and v_paid<v_amount then
        insert into payments(share_id,family_id,amount,applied_amount,credit_amount,paid_at,kind,status,note,created_by,reviewed_by,reviewed_at)
          values(v_s.id,v_f.id,v_amount-v_paid,v_amount-v_paid,0,v_w.month,'auto','approved',
            'Corrección de la hoja 2026: importe cubierto por el encargado; no es un depósito recibido.',v_actor,v_actor,now());
      elsif not v_payer then
        perform public.apply_credit_to_share(v_s.id,v_actor);
      end if;
    end loop;
    -- Retain the mistaken automatic charges, with zero due, as an audit trail.
    update external_utility_debts set amount_due=0,status='paid',
      note=concat_ws(' · ',note,'Reparto corregido según hoja 2026; sin cobro automático al restaurante.')
      where month_start=v_w.month and amount_paid=0;
    perform public.audit('repair_worksheet','monthly_basic_services',v_m.id::text,
      jsonb_build_object('repair',v_tag,'month',v_w.month,'total',v_w.total,'gas',0,'families',6));
  end loop;
  if exists(select 1 from casa_worksheet sheet join monthly_basic_services b on b.month_start=sheet.month
    where (select sum(amount_due) from expense_shares where expense_id=b.expense_id)<>sheet.total) then
    raise exception 'El reparto no cierra: se revierte toda la corrección';
  end if;
  perform public.audit('repair_worksheet','casa_finance_repairs',v_tag,
    jsonb_build_object('months',6,'semester_invoices',2250.50,'payments_preserved',true,'colors_are_not_payments',true));
end $repair$;
select to_char(b.month_start,'YYYY-MM') as month,b.water_amount as water,b.electricity_amount as electricity,
  b.gas_amount as gas,b.wifi_amount as wifi,b.family_total_amount as total,
  (select sum(s.amount_due) from expense_shares s where s.expense_id=b.expense_id) as shares_total
from monthly_basic_services b join casa_worksheet w on w.month=b.month_start order by b.month_start;
commit;
