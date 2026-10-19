-- Production function definitions only, no real records or secrets. Disposable test fixture.
CREATE OR REPLACE FUNCTION public.record_payment_v7(p_share_id uuid, p_amount numeric, p_paid_at date, p_method text, p_receipt_path text, p_note text, p_ocr_text text)
 RETURNS uuid
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
declare
  v_user uuid:=auth.uid();
  v_role public.app_role;
  v_family smallint;
  v_share public.expense_shares%rowtype;
  v_payment uuid;
  v_method text:=lower(trim(coalesce(p_method,'')));
  v_note text;
  v_expense public.expenses%rowtype;
  v_family_name text;
begin
  if v_user is null then raise exception 'No autenticado'; end if;

  select role,family_id into v_role,v_family
  from public.profiles where id=v_user;

  select * into v_share
  from public.expense_shares where id=p_share_id;

  if not found then raise exception 'Deuda no encontrada'; end if;
  if v_role='family' and v_share.family_id<>v_family then
    raise exception 'Solo puedes registrar pagos de tu propia familia';
  end if;
  if v_role not in ('family','payer','sysadmin') then raise exception 'No autorizado'; end if;
  if p_amount is null or p_amount<=0 then raise exception 'Monto inválido'; end if;
  if v_method not in ('qr','cash') then raise exception 'Método de pago inválido'; end if;

  if v_method='qr' and nullif(trim(coalesce(p_receipt_path,'')),'') is null then
    raise exception 'El pago por QR requiere foto del comprobante';
  end if;

  v_note:=case when v_method='qr' then 'Método: QR' else 'Método: Efectivo' end;
  if nullif(trim(coalesce(p_note,'')),'') is not null then
    v_note:=v_note||' · '||trim(p_note);
  end if;

  select * into v_expense from public.expenses where id=v_share.expense_id;
  select name into v_family_name from public.families where id=v_share.family_id;

  insert into public.payments(
    share_id,family_id,amount,paid_at,kind,status,
    receipt_path,note,ocr_text,created_by
  )
  values(
    p_share_id,v_share.family_id,round(p_amount,2),p_paid_at,
    'manual','pending',p_receipt_path,v_note,p_ocr_text,v_user
  )
  returning id into v_payment;

  if v_role in ('sysadmin','payer') then
    perform public.review_payment(v_payment,true);
  end if;

  -- Notificación interna para TODOS los usuarios de Casa Familias.
  perform public.notify_all(
    'payment',
    'Nuevo pago registrado',
    coalesce(v_family_name,'Familia '||v_share.family_id::text)||
      ' registró '||round(p_amount,2)||' Bs por '||coalesce(v_expense.concept,'un pago')||
      ' mediante '||case when v_method='qr' then 'QR' else 'efectivo' end||'.',
    'payment',
    v_payment,
    'payment-upload:'||v_payment::text
  );

  perform public.audit(
    'submit','payment',v_payment::text,
    jsonb_build_object(
      'amount',round(p_amount,2),
      'method',v_method,
      'has_receipt',p_receipt_path is not null,
      'family_id',v_share.family_id,
      'expense_id',v_share.expense_id
    )
  );

  return v_payment;
end $function$;

CREATE OR REPLACE FUNCTION public.review_payment(p_payment_id uuid, p_approve boolean)
 RETURNS void
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
declare
  v_role public.app_role:=public.current_app_role();
  v_p public.payments%rowtype;
  v_share public.expense_shares%rowtype;
  v_paid numeric(12,2);
  v_balance numeric(12,2);
  v_apply numeric(12,2);
  v_credit numeric(12,2);
begin
  if v_role not in ('sysadmin','payer') then
    raise exception 'No tienes permiso para revisar pagos';
  end if;

  select * into v_p from public.payments where id=p_payment_id for update;
  if not found then raise exception 'Pago no encontrado'; end if;
  if v_p.status<>'pending' then raise exception 'El pago ya fue revisado'; end if;

  if not p_approve then
    update public.payments
    set status='rejected',reviewed_by=auth.uid(),reviewed_at=now()
    where id=p_payment_id;
    perform public.notify_family(v_p.family_id,'payment','Pago rechazado',
      'El pago de '||v_p.amount||' Bs fue rechazado.','payment',v_p.id,null);
    perform public.audit('reject','payment',v_p.id::text,
      jsonb_build_object('amount',v_p.amount));
    return;
  end if;

  select * into v_share from public.expense_shares where id=v_p.share_id;
  if not found then raise exception 'Deuda no encontrada'; end if;
  v_paid:=public.share_approved_paid(v_share.id);
  v_balance:=greatest(v_share.amount_due-v_paid,0);
  v_apply:=least(v_p.amount,v_balance);
  v_credit:=round(v_p.amount-v_apply,2);

  insert into public.family_credits(family_id,balance)
  values(v_p.family_id,0)
  on conflict(family_id) do nothing;

  update public.payments
  set status='approved',applied_amount=v_apply,credit_amount=v_credit,
      reviewed_by=auth.uid(),reviewed_at=now()
  where id=p_payment_id;

  if v_credit>0 then
    update public.family_credits
    set balance=balance+v_credit,updated_at=now()
    where family_id=v_p.family_id;
  end if;

  perform public.notify_family(v_p.family_id,'payment','Pago aprobado',
    'Pago aprobado: '||v_p.amount||' Bs. Aplicado: '||v_apply||
    ' Bs. Saldo a favor generado: '||v_credit||' Bs.',
    'payment',v_p.id,null);
  perform public.audit('approve','payment',v_p.id::text,
    jsonb_build_object('amount',v_p.amount,'applied',v_apply,'credit',v_credit));
end $function$;

CREATE OR REPLACE FUNCTION public.family_portal_debts()
 RETURNS TABLE(share_id uuid, expense_id uuid, concept text, expense_date date, due_date date, amount_due numeric, amount_paid numeric, pending_submitted numeric, balance numeric, debt_status text, month_start date, water_amount numeric, electricity_amount numeric, gas_amount numeric, wifi_amount numeric)
 LANGUAGE sql
 STABLE SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
  select
    s.id,
    e.id,
    e.concept,
    e.expense_date,
    e.due_date,
    s.amount_due,
    coalesce((
      select sum(p.applied_amount)
      from public.payments p
      where p.share_id=s.id and p.status='approved'
    ),0)::numeric as amount_paid,
    coalesce((
      select sum(p.amount)
      from public.payments p
      where p.share_id=s.id and p.status='pending'
    ),0)::numeric as pending_submitted,
    greatest(
      s.amount_due-coalesce((
        select sum(p.applied_amount)
        from public.payments p
        where p.share_id=s.id and p.status='approved'
      ),0),0
    )::numeric as balance,
    case
      when greatest(
        s.amount_due-coalesce((
          select sum(p.applied_amount)
          from public.payments p
          where p.share_id=s.id and p.status='approved'
        ),0),0
      )<=0.005 then 'paid'
      when coalesce((
        select sum(p.amount)
        from public.payments p
        where p.share_id=s.id and p.status='pending'
      ),0)>0 then 'review'
      when e.due_date<current_date then 'overdue'
      when coalesce((
        select sum(p.applied_amount)
        from public.payments p
        where p.share_id=s.id and p.status='approved'
      ),0)>0 then 'partial'
      else 'pending'
    end,
    m.month_start,
    m.water_amount,
    m.electricity_amount,
    m.gas_amount,
    m.wifi_amount
  from public.expense_shares s
  join public.expenses e on e.id=s.expense_id
  left join public.monthly_basic_services m on m.expense_id=e.id
  where s.family_id=public.current_family_id()
  order by
    case when e.due_date<current_date then 0 else 1 end,
    e.due_date,
    e.expense_date desc
$function$;
