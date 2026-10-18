-- Las familias del encargado cubren su cuota al pagar las facturas de servicios.
-- No cambia importes de gastos/cuotas ni las deudas de las demás familias.
-- La corrección de históricos genera cobertura automática auditada, no efectivo nuevo.
begin;

CREATE OR REPLACE FUNCTION public.create_monthly_basic_services(p_month date, p_water numeric, p_electricity numeric, p_gas numeric, p_wifi numeric, p_due_date date, p_water_receipt text, p_electricity_receipt text, p_gas_receipt text, p_wifi_receipt text, p_other_water numeric DEFAULT 0, p_other_electricity numeric DEFAULT 0, p_reference_only boolean DEFAULT false)
 RETURNS uuid
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
declare
  v_user uuid:=auth.uid();
  v_month date:=date_trunc('month',p_month)::date;
  v_water numeric(12,2):=round(coalesce(p_water,0),2);
  v_electricity numeric(12,2):=round(coalesce(p_electricity,0),2);
  v_gas numeric(12,2):=round(coalesce(p_gas,0),2);
  v_wifi numeric(12,2):=round(coalesce(p_wifi,0),2);
  v_other_water numeric(12,2):=round(coalesce(p_other_water,0),2);
  v_other_electricity numeric(12,2):=round(coalesce(p_other_electricity,0),2);
  v_reference_count integer:=0;
  v_water_base numeric(12,2);
  v_electricity_base numeric(12,2);
  v_family_water_base numeric(12,2);
  v_family_electricity_base numeric(12,2);
  v_family_water numeric(12,2);
  v_family_electricity numeric(12,2);
  v_restaurant_water numeric(12,2);
  v_restaurant_electricity numeric(12,2);
  v_family_gas numeric(12,2);
  v_family_wifi numeric(12,2);
  v_family_total numeric(12,2);
  v_monthly_id uuid;
  v_expense uuid;
  v_share uuid;
  v_share_amount numeric(12,2);
  v_base numeric(12,2);
  v_acc numeric(12,2):=0;
  v_i integer:=0;
  v_f record;
  v_payer record;
  v_restaurant uuid;
  v_pending_water_credit numeric(12,2);
  v_pending_electricity_credit numeric(12,2);
  v_pending_gas_credit numeric(12,2);
  v_pending_wifi_credit numeric(12,2);
begin
  if v_user is null or coalesce(public.current_app_role()::text,'') not in ('sysadmin','payer') then
    raise exception 'No tienes permiso para registrar servicios básicos';
  end if;
  if v_water<0 or v_electricity<0 or v_gas<0 or v_wifi<0
     or v_other_water<0 or v_other_electricity<0 then
    raise exception 'Los montos no pueden ser negativos';
  end if;
  if p_due_date is null then raise exception 'Falta la fecha límite'; end if;
  if exists(select 1 from public.monthly_basic_services where month_start=v_month) then
    raise exception 'Ese mes ya tiene servicios básicos registrados';
  end if;

  select
    coalesce(round(avg(water_amount),2),0),
    coalesce(round(avg(electricity_amount),2),0),
    count(*)
  into v_water_base,v_electricity_base,v_reference_count
  from (
    select water_amount,electricity_amount
    from public.monthly_basic_services
    where month_start<v_month
      and (
        coalesce(is_reference_only,false)
        or (coalesce(restaurant_water_amount,0)=0 and coalesce(restaurant_electricity_amount,0)=0)
      )
    order by month_start desc
    limit 6
  ) hist;

  if v_reference_count=0 then
    select base_amount into v_water_base from public.utility_baselines where service='water';
    select base_amount into v_electricity_base from public.utility_baselines where service='electricity';
  end if;
  if coalesce(v_water_base,0)<=0 then v_water_base:=v_water; end if;
  if coalesce(v_electricity_base,0)<=0 then v_electricity_base:=v_electricity; end if;

  insert into public.utility_baselines(service,base_amount,source_months,updated_by,updated_at)
  values('water',round(v_water_base,2),greatest(least(v_reference_count,6),1),v_user,now())
  on conflict(service) do update
  set base_amount=excluded.base_amount,source_months=excluded.source_months,
      updated_by=excluded.updated_by,updated_at=excluded.updated_at;
  insert into public.utility_baselines(service,base_amount,source_months,updated_by,updated_at)
  values('electricity',round(v_electricity_base,2),greatest(least(v_reference_count,6),1),v_user,now())
  on conflict(service) do update
  set base_amount=excluded.base_amount,source_months=excluded.source_months,
      updated_by=excluded.updated_by,updated_at=excluded.updated_at;

  -- Créditos pendientes registrados desde "Gastos externos → Descontar de un
  -- servicio". Se suman al aporte manual que ya existía en este formulario.
  select coalesce(sum(amount),0) into v_pending_water_credit from public.utility_credits where service='water' and applied_month_start is null;
  select coalesce(sum(amount),0) into v_pending_electricity_credit from public.utility_credits where service='electricity' and applied_month_start is null;
  select coalesce(sum(amount),0) into v_pending_gas_credit from public.utility_credits where service='gas' and applied_month_start is null;
  select coalesce(sum(amount),0) into v_pending_wifi_credit from public.utility_credits where service='wifi' and applied_month_start is null;

  v_other_water:=v_other_water+v_pending_water_credit;
  v_other_electricity:=v_other_electricity+v_pending_electricity_credit;

  v_family_water_base:=least(v_water,round(v_water_base,2));
  v_family_electricity_base:=least(v_electricity,round(v_electricity_base,2));

  v_other_water:=least(v_other_water,v_family_water_base);
  v_other_electricity:=least(v_other_electricity,v_family_electricity_base);

  v_family_water:=round(v_family_water_base-v_other_water,2);
  v_family_electricity:=round(v_family_electricity_base-v_other_electricity,2);
  v_restaurant_water:=greatest(v_water-v_family_water_base,0);
  v_restaurant_electricity:=greatest(v_electricity-v_family_electricity_base,0);

  -- Gas y WiFi no tienen componente de restaurante: el crédito pendiente se
  -- resta directo de lo que pagan las 6 familias.
  v_family_gas:=greatest(round(v_gas-least(v_pending_gas_credit,v_gas),2),0);
  v_family_wifi:=greatest(round(v_wifi-least(v_pending_wifi_credit,v_wifi),2),0);

  v_family_total:=round(v_family_water+v_family_electricity+v_family_gas+v_family_wifi,2);

  insert into public.monthly_basic_services(
    month_start,due_date,water_amount,electricity_amount,gas_amount,wifi_amount,
    water_receipt_path,electricity_receipt_path,gas_receipt_path,wifi_receipt_path,
    family_water_base_amount,family_electricity_base_amount,
    other_water_contribution,other_electricity_contribution,
    family_water_amount,family_electricity_amount,restaurant_water_amount,
    restaurant_electricity_amount,family_total_amount,is_reference_only,created_by
  ) values(
    v_month,p_due_date,v_water,v_electricity,v_gas,v_wifi,
    p_water_receipt,p_electricity_receipt,p_gas_receipt,p_wifi_receipt,
    v_family_water_base,v_family_electricity_base,
    v_other_water,v_other_electricity,
    v_family_water,v_family_electricity,v_restaurant_water,
    v_restaurant_electricity,case when p_reference_only then 0 else v_family_total end,
    p_reference_only,v_user
  ) returning id into v_monthly_id;

  if not p_reference_only and v_family_total>0 then
    insert into public.expenses(
      concept,category,total_amount,expense_date,due_date,justification,
      receipt_path,paid_by_family_id,fund_amount_used,created_by
    ) values(
      'Servicios básicos '||to_char(v_month,'YYYY-MM'),
      'basic',v_family_total,v_month,p_due_date,
      'Monto familiar después de descontar aportes de actividades y el consumo adicional del restaurante.',
      coalesce(p_water_receipt,p_electricity_receipt,p_gas_receipt,p_wifi_receipt),
      null,0,v_user
    ) returning id into v_expense;

    update public.monthly_basic_services set monthly_expense_id=v_expense, expense_id=v_expense where id=v_monthly_id;

    select family_id,second_family_id into v_payer
    from public.profiles where role='payer' limit 1;

    v_base:=round(v_family_total/6.0,2);
    for v_f in select id from public.families order by id loop
      v_i:=v_i+1;
      if v_i<6 then
        v_share_amount:=v_base;
        v_acc:=v_acc+v_share_amount;
      else
        v_share_amount:=round(v_family_total-v_acc,2);
      end if;

      insert into public.expense_shares(expense_id,family_id,amount_due)
      values(v_expense,v_f.id,v_share_amount)
      returning id into v_share;

      if v_share_amount>0 and (
        v_f.id=coalesce(v_payer.family_id,-1)
        or v_f.id=coalesce(v_payer.second_family_id,-1)
      ) then
        insert into public.payments(
          share_id,family_id,amount,applied_amount,credit_amount,paid_at,
          kind,status,note,created_by,reviewed_by,reviewed_at
        ) values(
          v_share,v_f.id,v_share_amount,v_share_amount,0,v_month,
          'auto','approved','Cuota cubierta automáticamente por el encargado de pagos.',
          v_user,v_user,now()
        );
      elsif v_share_amount>0 then
        perform public.apply_credit_to_share(v_share,v_user);
      end if;
    end loop;
  end if;

  if not p_reference_only then
  select id into v_restaurant
  from public.profiles where role='restaurant' order by created_at limit 1;

  if v_restaurant is not null then
    if v_restaurant_water>0 then
      insert into public.external_utility_debts(
        profile_id,payer_name,service,month_start,bill_amount,family_base_amount,
        amount_due,due_date,created_by
      )
      select v_restaurant,p.display_name,'water',v_month,v_water,v_family_water_base,
             v_restaurant_water,p_due_date,v_user
      from public.profiles p where p.id=v_restaurant
      on conflict(profile_id,service,month_start) do update
      set bill_amount=excluded.bill_amount,
          family_base_amount=excluded.family_base_amount,
          amount_due=excluded.amount_due,
          due_date=excluded.due_date;
    end if;
    if v_restaurant_electricity>0 then
      insert into public.external_utility_debts(
        profile_id,payer_name,service,month_start,bill_amount,family_base_amount,
        amount_due,due_date,created_by
      )
      select v_restaurant,p.display_name,'electricity',v_month,v_electricity,v_family_electricity_base,
             v_restaurant_electricity,p_due_date,v_user
      from public.profiles p where p.id=v_restaurant
      on conflict(profile_id,service,month_start) do update
      set bill_amount=excluded.bill_amount,
          family_base_amount=excluded.family_base_amount,
          amount_due=excluded.amount_due,
          due_date=excluded.due_date;
    end if;
  end if;

  end if;

  -- Se marcan como usados todos los créditos pendientes que se acaban de aplicar.
  if not p_reference_only then
    update public.utility_credits
    set applied_month_start=v_month, applied_at=now()
    where applied_month_start is null
      and (
        (service='water' and v_pending_water_credit>0)
        or (service='electricity' and v_pending_electricity_credit>0)
        or (service='gas' and v_pending_gas_credit>0)
        or (service='wifi' and v_pending_wifi_credit>0)
      );
  end if;

  if not p_reference_only then
    insert into public.notifications(user_id,kind,title,body,ref_entity,ref_id)
    select id,'expense','Servicios básicos registrados',
         'Se registró el paquete de servicios de '||to_char(v_month,'YYYY-MM')||
         '. Monto familiar: '||v_family_total||' Bs.',
         'monthly_basic_services',v_monthly_id
    from public.profiles where role<>'restaurant';
  end if;

  perform public.audit(
    'create','monthly_basic_services',v_monthly_id::text,
    jsonb_build_object(
      'month',v_month,'family_total',v_family_total,
      'reference_only',p_reference_only,'source_months',v_reference_count,
      'other_water',v_other_water,'other_electricity',v_other_electricity,
      'gas_credit_applied',v_pending_gas_credit,'wifi_credit_applied',v_pending_wifi_credit,
      'restaurant_water',v_restaurant_water,
      'restaurant_electricity',v_restaurant_electricity
    )
  );

  return v_monthly_id;
end $function$
;

-- Regularizar las cuotas antiguas de servicios que quedaron sin cobertura.
-- Bloquear pagos mientras se calcula el faltante evita contar un abono simultáneo dos veces.
lock table public.payments in share row exclusive mode;
do $repair$
declare
 v_share record;
 v_remaining numeric(12,2);
 v_actor uuid;
 v_payment uuid;
begin
 select id into v_actor from public.profiles where role::text='sysadmin' order by id limit 1;
 if v_actor is null then
   raise exception 'Se necesita un administrador para auditar la regularización.';
 end if;
 for v_share in
   select s.id,s.family_id,s.amount_due,e.id expense_id
   from public.expense_shares s join public.expenses e on e.id=s.expense_id
   where e.category::text='basic' and exists (
     select 1 from public.profiles p where p.role::text='payer'
       and s.family_id in (p.family_id,p.second_family_id)
   )
   order by s.id for update of s
 loop
   select greatest(v_share.amount_due-coalesce(sum(p.applied_amount),0),0)
     into v_remaining from public.payments p
     where p.share_id=v_share.id and p.status::text='approved';
   if v_remaining>0 then
     insert into public.payments(
       share_id,family_id,amount,applied_amount,credit_amount,paid_at,
       kind,status,note,created_by,reviewed_by,reviewed_at
     ) values (
       v_share.id,v_share.family_id,v_remaining,v_remaining,0,
       (now() at time zone 'America/La_Paz')::date,
       'auto','approved',
       'Cuota de servicios cubierta por la familia encargada que paga las facturas. Regularización de cobertura automática; no es un cobro adicional.',
       v_actor,v_actor,now()
     ) returning id into v_payment;
     perform public.audit('cover_payer_services','payment',v_payment::text,
       jsonb_build_object('reason','payer_bills_already_covered','family_id',v_share.family_id,
         'share_id',v_share.id,'expense_id',v_share.expense_id,'applied_amount',v_remaining));
   end if;
 end loop;
end $repair$;
notify pgrst, 'reload schema';
commit;
