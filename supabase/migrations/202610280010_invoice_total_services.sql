-- Whole invoices are shared; historical averages no longer create restaurant
-- debts. This migration changes the rule only; existing accounting rows stay.
begin;

alter table public.monthly_basic_services
  add column if not exists calculation_method text not null default 'historical_average',
  add column if not exists family_count smallint;

create or replace function public.create_monthly_basic_services(
  p_month date, p_water numeric, p_electricity numeric, p_gas numeric, p_wifi numeric,
  p_due_date date, p_water_receipt text, p_electricity_receipt text,
  p_gas_receipt text, p_wifi_receipt text, p_other_water numeric default 0,
  p_other_electricity numeric default 0, p_reference_only boolean default false
) returns uuid language plpgsql security definer set search_path = public as $$
declare
  v_actor uuid := auth.uid();
  v_month date := date_trunc('month', p_month)::date;
  v_water numeric(12,2) := coalesce(p_water, 0);
  v_electricity numeric(12,2) := coalesce(p_electricity, 0);
  v_gas numeric(12,2) := coalesce(p_gas, 0);
  v_wifi numeric(12,2) := coalesce(p_wifi, 0);
  v_other_water numeric(12,2) := coalesce(p_other_water, 0);
  v_other_electricity numeric(12,2) := coalesce(p_other_electricity, 0);
  v_water_credit numeric(12,2) := 0;
  v_electricity_credit numeric(12,2) := 0;
  v_gas_credit numeric(12,2) := 0;
  v_wifi_credit numeric(12,2) := 0;
  v_total numeric(12,2);
  v_base numeric(12,2);
  v_share_amount numeric(12,2);
  v_count integer;
  v_index integer := 0;
  v_monthly uuid;
  v_expense uuid;
  v_share uuid;
  v_family record;
begin
  if v_actor is null or coalesce(public.current_app_role()::text, '') not in ('sysadmin','payer') then
    raise exception 'No tienes permiso para registrar servicios básicos';
  end if;
  if p_month is null or p_due_date is null then raise exception 'Faltan el mes o la fecha límite'; end if;
  if public.is_period_closed(v_month) then raise exception 'Ese mes está cerrado'; end if;
  if exists (select 1 from unnest(array[p_water,p_electricity,p_gas,p_wifi,p_other_water,p_other_electricity]) amount
             where amount < 0 or amount::text in ('NaN','Infinity','-Infinity')) then
    raise exception 'Los montos deben ser números iguales o mayores a cero';
  end if;
  perform pg_advisory_xact_lock(hashtextextended('casa-monthly-services', 0));
  if exists (select 1 from public.monthly_basic_services where month_start = v_month) then
    raise exception 'Ese mes ya tiene servicios básicos registrados';
  end if;
  lock table public.families in share mode;
  select count(*) into v_count from public.families;
  if v_count < 1 then raise exception 'No hay familias para repartir el gasto'; end if;

  if not coalesce(p_reference_only, false) then
    -- Discounts are explicit records, never inferred from historical averages.
    perform id from public.utility_credits where applied_month_start is null order by id for update;
    select coalesce(sum(amount) filter (where service = 'water'), 0),
           coalesce(sum(amount) filter (where service = 'electricity'), 0),
           coalesce(sum(amount) filter (where service = 'gas'), 0),
           coalesce(sum(amount) filter (where service = 'wifi'), 0)
      into v_water_credit, v_electricity_credit, v_gas_credit, v_wifi_credit
      from public.utility_credits where applied_month_start is null;
    v_other_water := v_other_water + v_water_credit;
    v_other_electricity := v_other_electricity + v_electricity_credit;
    if v_other_water > v_water or v_other_electricity > v_electricity or v_gas_credit > v_gas or v_wifi_credit > v_wifi then
      raise exception 'Los descuentos no pueden superar la factura de su servicio';
    end if;
  else
    v_other_water := 0;
    v_other_electricity := 0;
  end if;
  v_total := v_water + v_electricity + v_gas + v_wifi - v_other_water - v_other_electricity - v_gas_credit - v_wifi_credit;

  insert into public.monthly_basic_services(
    month_start, due_date, water_amount, electricity_amount, gas_amount, wifi_amount,
    water_receipt_path, electricity_receipt_path, gas_receipt_path, wifi_receipt_path,
    family_water_base_amount, family_electricity_base_amount, other_water_contribution,
    other_electricity_contribution, family_water_amount, family_electricity_amount,
    restaurant_water_amount, restaurant_electricity_amount, family_total_amount,
    is_reference_only, calculation_method, family_count, created_by
  ) values (
    v_month, p_due_date, v_water, v_electricity, v_gas, v_wifi, p_water_receipt, p_electricity_receipt,
    p_gas_receipt, p_wifi_receipt, v_water, v_electricity, v_other_water, v_other_electricity,
    v_water - v_other_water, v_electricity - v_other_electricity, 0, 0,
    case when coalesce(p_reference_only, false) then 0 else v_total end,
    coalesce(p_reference_only, false), 'bill_total', v_count, v_actor
  ) returning id into v_monthly;

  if not coalesce(p_reference_only, false) then
    if v_total > 0 then
      insert into public.expenses(concept, category, total_amount, expense_date, due_date,
        justification, receipt_path, paid_by_family_id, fund_amount_used, created_by)
      values ('Servicios básicos ' || to_char(v_month, 'YYYY-MM'), 'basic', v_total, v_month, p_due_date,
        'Suma completa de las facturas menos descuentos explícitos. Sin recorte por promedio histórico.',
        coalesce(p_water_receipt,p_electricity_receipt,p_gas_receipt,p_wifi_receipt), null, 0, v_actor)
      returning id into v_expense;
      update public.monthly_basic_services set expense_id = v_expense, monthly_expense_id = v_expense where id = v_monthly;
      v_base := round(v_total / v_count, 2);
      if v_base * (v_count - 1) > v_total then v_base := trunc(v_total / v_count, 2); end if;
      for v_family in select id from public.families order by id loop
        v_index := v_index + 1;
        v_share_amount := case when v_index = v_count then v_total - v_base * (v_count - 1) else v_base end;
        insert into public.expense_shares(expense_id, family_id, amount_due)
          values (v_expense, v_family.id, v_share_amount) returning id into v_share;
        if v_share_amount > 0 and exists (select 1 from public.profiles p where p.role::text = 'payer'
          and v_family.id in (p.family_id, p.second_family_id)) then
          insert into public.payments(share_id, family_id, amount, applied_amount, credit_amount,
            paid_at, kind, status, note, created_by, reviewed_by, reviewed_at)
          values (v_share, v_family.id, v_share_amount, v_share_amount, 0, v_month, 'auto', 'approved',
            'Importe cubierto por el encargado al pagar las facturas; no es un depósito recibido.', v_actor, v_actor, now());
        elsif v_share_amount > 0 then
          perform public.apply_credit_to_share(v_share, v_actor);
        end if;
      end loop;
    end if;
    update public.utility_credits set applied_month_start = v_month, applied_at = now()
      where applied_month_start is null and service in ('water','electricity','gas','wifi');
    insert into public.notifications(user_id, kind, title, body, ref_entity, ref_id)
      select id, 'expense', 'Servicios básicos registrados',
        'Servicios de ' || to_char(v_month,'YYYY-MM') || '. Total repartido: ' || v_total || ' Bs.',
        'monthly_basic_services', v_monthly from public.profiles where role::text <> 'restaurant';
  end if;
  perform public.audit('create','monthly_basic_services',v_monthly::text,
    jsonb_build_object('calculation_method','bill_total','month',v_month,'family_count',v_count,
      'invoices',v_water+v_electricity+v_gas+v_wifi,'discounts',v_other_water+v_other_electricity+v_gas_credit+v_wifi_credit,
      'family_total',v_total,'reference_only',coalesce(p_reference_only,false),'restaurant_auto_charge',0));
  return v_monthly;
end;
$$;

-- Fail clearly on an unmigrated backend, rather than silently using averages.
create or replace function public.casa_create_monthly_services(
  p_month date, p_water numeric, p_electricity numeric, p_gas numeric, p_wifi numeric,
  p_due_date date, p_water_receipt text, p_electricity_receipt text,
  p_gas_receipt text, p_wifi_receipt text, p_other_water numeric default 0,
  p_other_electricity numeric default 0, p_reference_only boolean default false,
  p_expected_total numeric default null, p_expected_count integer default null
) returns uuid language plpgsql security definer set search_path = public as $$
declare v_id uuid; v_total numeric; v_count integer;
begin
  v_id := public.create_monthly_basic_services(p_month,p_water,p_electricity,p_gas,p_wifi,
    p_due_date,p_water_receipt,p_electricity_receipt,p_gas_receipt,p_wifi_receipt,
    p_other_water,p_other_electricity,p_reference_only);
  select family_total_amount,family_count into v_total,v_count from public.monthly_basic_services where id=v_id;
  if (p_expected_total is not null and p_expected_total<>v_total)
    or (p_expected_count is not null and p_expected_count<>v_count) then
    raise exception 'El reparto cambió desde la vista previa. Actualiza los datos y revisa el resumen antes de guardar.';
  end if;
  return v_id;
end;
$$;
revoke all on function public.casa_create_monthly_services(date,numeric,numeric,numeric,numeric,date,text,text,text,text,numeric,numeric,boolean,numeric,integer) from public, anon;
grant execute on function public.casa_create_monthly_services(date,numeric,numeric,numeric,numeric,date,text,text,text,text,numeric,numeric,boolean,numeric,integer) to authenticated;
notify pgrst, 'reload schema';
commit;
