-- One deposit/receipt, allocated atomically across the family's oldest outstanding debts.
-- Existing payments and balances are left untouched by installation.
begin;
create table if not exists public.payment_batches (
 id uuid primary key,
 family_id smallint not null references public.families(id),
 amount numeric(12,2) not null check(amount>0),
 applied_amount numeric(12,2) not null default 0 check(applied_amount>=0),
 credit_amount numeric(12,2) not null default 0 check(credit_amount>=0),
 paid_at date not null,
 method text not null check(method in ('qr','cash')),
 target_share_id uuid references public.expense_shares(id),
 receipt_path text,
 note text,
 ocr_text text,
 planned_allocation jsonb not null check(jsonb_typeof(planned_allocation)='array'),
 planned_credit numeric(12,2) not null default 0 check(planned_credit>=0),
 status public.payment_status not null default 'pending',
 created_by uuid not null references public.profiles(id),
 reviewed_by uuid references public.profiles(id),
 reviewed_at timestamptz,
 created_at timestamptz not null default now(),
 check(applied_amount+credit_amount<=amount),
 check(status<>'approved' or applied_amount+credit_amount=amount)
);
alter table public.payments add column if not exists batch_id uuid references public.payment_batches(id);
create index if not exists casa_payments_batch on public.payments(batch_id) where batch_id is not null;
create index if not exists casa_payment_batches_family on public.payment_batches(family_id,created_at desc);
create unique index if not exists casa_payment_batches_receipt on public.payment_batches(receipt_path) where receipt_path is not null;
alter table public.payment_batches enable row level security;
revoke all on public.payment_batches from public,anon,authenticated;
grant select on public.payment_batches to authenticated;
drop policy if exists casa_payment_batches_read on public.payment_batches;
create policy casa_payment_batches_read on public.payment_batches for select to authenticated using(
 exists(select 1 from public.profiles p where p.id=auth.uid() and
  (p.role::text in ('sysadmin','payer') or (p.role::text='family' and p.family_id=payment_batches.family_id)))
);

-- Internal planner; never exposes another family's finances through an authenticated RPC.
create or replace function public.casa_payment_plan(p_family smallint,p_amount numeric,p_share uuid default null)
returns table(share_id uuid,concept text,month_start date,expense_date date,due_date date,balance_before numeric,amount numeric)
language plpgsql stable security definer set search_path='' as $$
declare r record;remaining numeric:=p_amount;
begin
 for r in
  select s.id,e.concept,m.month_start,e.expense_date,e.due_date,
   greatest(s.amount_due-public.share_approved_paid(s.id),0) as balance
  from public.expense_shares s join public.expenses e on e.id=s.expense_id
  left join lateral(select min(x.month_start) as month_start from public.monthly_basic_services x where x.expense_id=e.id or x.monthly_expense_id=e.id) m on true
  where s.family_id=p_family and (p_share is null or s.id=p_share)
  order by coalesce(m.month_start,e.expense_date),e.due_date,s.id
 loop
  exit when remaining<=0;
  if r.balance>0 then
   share_id:=r.id;concept:=r.concept;month_start:=r.month_start;expense_date:=r.expense_date;due_date:=r.due_date;
   balance_before:=r.balance;amount:=least(remaining,r.balance);remaining:=remaining-amount;return next;
  end if;
 end loop;
end;$$;
revoke all on function public.casa_payment_plan(smallint,numeric,uuid) from public,anon,authenticated;

create or replace function public.casa_review_payment_batch(p_batch_id uuid,p_approve boolean)
returns void language plpgsql security definer set search_path='' as $$
declare b public.payment_batches%rowtype;r record;v_family smallint;v_role text;v_first boolean:=true;
 v_applied numeric(12,2):=0;v_credit numeric(12,2);v_last uuid;v_actor uuid:=auth.uid();
begin
 select p.role::text into v_role from public.profiles p where p.id=v_actor;
 if v_actor is null or v_role is null or v_role not in ('sysadmin','payer') then raise exception 'No puedes revisar pagos.' using errcode='42501';end if;
 if p_approve is null then raise exception 'Selecciona aprobar o rechazar.' using errcode='22023';end if;
 select family_id into v_family from public.payment_batches where id=p_batch_id;
 if v_family is null then raise exception 'Depósito no disponible.' using errcode='22023';end if;
 -- All legacy and batch approvals use the same family -> payment -> share lock order.
 perform 1 from public.families where id=v_family for update;
 select * into b from public.payment_batches where id=p_batch_id for update;
 if b.status::text<>'pending' then raise exception 'El depósito ya fue revisado.' using errcode='22023';end if;
 perform 1 from public.payments where batch_id=b.id order by id for update;
 if (select count(*) from public.payments where batch_id=b.id)<>1
  or not exists(select 1 from public.payments where id=b.id and batch_id=b.id and status::text='pending' and amount=b.amount) then
  raise exception 'El depósito requiere revisión de integridad.' using errcode='22023';end if;
 if not p_approve then
  update public.payments set status='rejected',reviewed_by=v_actor,reviewed_at=now() where batch_id=b.id;
  update public.payment_batches set status='rejected',reviewed_by=v_actor,reviewed_at=now() where id=b.id;
  perform public.notify_family(b.family_id,'payment','Depósito rechazado','El depósito de '||b.amount||' Bs fue rechazado. Consulta el detalle en Pagos.','payment',b.id,'batch-reject:'||b.id::text);
  perform public.audit('reject','payment_batch',b.id::text,jsonb_build_object('amount',b.amount));return;
 end if;
 perform 1 from public.expense_shares where family_id=b.family_id order by id for update;
 -- Recalculate against approved payments now, rather than trusting the earlier preview.
 for r in select * from public.casa_payment_plan(b.family_id,b.amount,b.target_share_id) loop
  if v_first then
   update public.payments set share_id=r.share_id,amount=r.amount,applied_amount=r.amount,credit_amount=0,
    status='approved',reviewed_by=v_actor,reviewed_at=now() where id=b.id;
   v_last:=b.id;v_first:=false;
  else
   insert into public.payments(share_id,family_id,amount,applied_amount,credit_amount,paid_at,kind,status,receipt_path,note,ocr_text,created_by,reviewed_by,reviewed_at,batch_id)
   values(r.share_id,b.family_id,r.amount,r.amount,0,b.paid_at,'manual','approved',b.receipt_path,b.note,b.ocr_text,b.created_by,v_actor,now(),b.id) returning id into v_last;
  end if;
  v_applied:=v_applied+r.amount;
 end loop;
 v_credit:=b.amount-v_applied;
 if v_first then
  -- Other reviewed payments may already have cleared every debt while this was pending.
  update public.payments set status='approved',applied_amount=0,credit_amount=b.amount,reviewed_by=v_actor,reviewed_at=now() where id=b.id;
 else
  update public.payments set amount=amount+v_credit,credit_amount=v_credit where id=v_last;
 end if;
 if v_credit>0 then
  insert into public.family_credits(family_id,balance) values(b.family_id,v_credit)
  on conflict(family_id) do update set balance=public.family_credits.balance+excluded.balance,updated_at=now();
 end if;
 if (select sum(amount) from public.payments where batch_id=b.id)<>b.amount
  or (select sum(applied_amount+credit_amount) from public.payments where batch_id=b.id)<>b.amount then
  raise exception 'El reparto no coincide con el depósito.' using errcode='22023';end if;
 update public.payment_batches set status='approved',applied_amount=v_applied,credit_amount=v_credit,reviewed_by=v_actor,reviewed_at=now() where id=b.id;
 perform public.notify_family(b.family_id,'payment','Depósito aprobado','Se aprobaron '||b.amount||' Bs: '||v_applied||' Bs aplicados a tus deudas y '||v_credit||' Bs de saldo a favor. Revisa el reparto por mes en Pagos.','payment',b.id,'batch-approve:'||b.id::text);
 perform public.audit('approve','payment_batch',b.id::text,jsonb_build_object('amount',b.amount,'applied',v_applied,'credit',v_credit,
  'allocation',(select jsonb_agg(jsonb_build_object('payment_id',p.id,'share_id',p.share_id,'applied',p.applied_amount,'credit',p.credit_amount) order by p.id) from public.payments p where p.batch_id=b.id)));
end;$$;
revoke all on function public.casa_review_payment_batch(uuid,boolean) from public,anon;
grant execute on function public.casa_review_payment_batch(uuid,boolean) to authenticated;

create or replace function public.casa_record_payment_batch(p_request_id uuid,p_family_id smallint,p_amount numeric,p_paid_at date,p_method text,p_receipt_path text,p_note text,p_ocr_text text,p_share_id uuid default null)
returns uuid language plpgsql security definer set search_path='' as $$
declare v_role text;v_family smallint;v_user uuid:=auth.uid();v_amount numeric(12,2);v_method text:=lower(trim(coalesce(p_method,'')));
 v_note text;v_receipt text:=nullif(trim(coalesce(p_receipt_path,'')),'');v_plan jsonb;v_applied numeric;v_anchor uuid;b public.payment_batches%rowtype;
begin
 select p.role::text,p.family_id into v_role,v_family from public.profiles p where p.id=v_user;
 if v_user is null or v_role is null or v_role not in ('sysadmin','payer','family') then raise exception 'No autorizado.' using errcode='42501';end if;
 if v_role='family' and p_family_id is distinct from v_family then raise exception 'Solo puedes registrar depósitos de tu familia.' using errcode='42501';end if;
 if p_request_id is null or p_family_id is null or p_paid_at is null or p_amount is null
  or p_amount::text in ('NaN','Infinity','-Infinity') or p_amount<0.005 or p_amount>9999999999.99
  or v_method not in ('qr','cash') or length(coalesce(p_note,''))>1000 then
  raise exception 'Revisa la familia, el monto, la fecha y la forma de pago.' using errcode='22023';end if;
 v_amount:=round(p_amount,2);
 v_note:=case when v_method='qr' then 'Método: QR' else 'Método: Efectivo' end;
 if nullif(trim(coalesce(p_note,'')),'') is not null then v_note:=v_note||' · '||trim(p_note);end if;
 -- Serialize retries independently of family so a repeated request cannot create another deposit.
 perform pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtextextended('casa:deposit:'||p_request_id::text,0));
 select * into b from public.payment_batches where id=p_request_id;
 if found then
  if b.created_by<>v_user or b.family_id<>p_family_id then raise exception 'Solicitud de otra cuenta.' using errcode='42501';end if;
  if b.amount<>v_amount or b.paid_at<>p_paid_at or b.method<>v_method or b.target_share_id is distinct from p_share_id or b.note is distinct from v_note then
   raise exception 'La solicitud ya existe con otros datos. Abre un pago nuevo.' using errcode='22023';end if;
  return b.id;
 end if;
 if v_method='qr' and v_receipt is null then raise exception 'Adjunta el comprobante del depósito.' using errcode='22023';end if;
 if v_receipt is not null and (v_receipt not like v_user::text||'/%' or not exists(select 1 from storage.objects o where o.bucket_id='receipts' and o.name=v_receipt)) then
  raise exception 'Adjunta un comprobante propio válido.' using errcode='22023';end if;
 if v_receipt is not null and exists(select 1 from public.payment_batches where receipt_path=v_receipt) then
  raise exception 'Este comprobante ya pertenece a un depósito. Revisa tu historial.' using errcode='22023';end if;
 perform 1 from public.families where id=p_family_id for update;
 if not found then raise exception 'Familia no disponible.' using errcode='22023';end if;
 if p_share_id is not null and not exists(select 1 from public.expense_shares s where s.id=p_share_id and s.family_id=p_family_id) then
  raise exception 'Selecciona una deuda de esta familia.' using errcode='42501';end if;
 select coalesce(jsonb_agg(to_jsonb(x) order by coalesce(x.month_start,x.expense_date),x.due_date,x.share_id),'[]'::jsonb),coalesce(sum(x.amount),0) into v_plan,v_applied from public.casa_payment_plan(p_family_id,v_amount,p_share_id) x;
 if jsonb_array_length(v_plan)=0 then raise exception 'No hay deudas pendientes para este pago. Actualiza tus datos.' using errcode='22023';end if;
 v_anchor:=(v_plan->0->>'share_id')::uuid;
 insert into public.payment_batches(id,family_id,amount,paid_at,method,target_share_id,receipt_path,note,ocr_text,planned_allocation,planned_credit,created_by)
 values(p_request_id,p_family_id,v_amount,p_paid_at,v_method,p_share_id,v_receipt,v_note,p_ocr_text,v_plan,v_amount-v_applied,v_user);
 -- One pending anchor keeps old APK approval and receipt links working. Approval splits it.
 insert into public.payments(id,share_id,family_id,amount,paid_at,kind,status,receipt_path,note,ocr_text,created_by,batch_id)
 values(p_request_id,v_anchor,p_family_id,v_amount,p_paid_at,'manual','pending',v_receipt,v_note,p_ocr_text,v_user,p_request_id);
 perform public.audit('submit','payment_batch',p_request_id::text,jsonb_build_object('amount',v_amount,'family_id',p_family_id,'planned_allocation',v_plan,'planned_credit',v_amount-v_applied));
 if v_role in ('sysadmin','payer') then
  perform public.casa_review_payment_batch(p_request_id,true);
 else
  insert into public.notifications(user_id,kind,title,body,ref_entity,ref_id,dedupe_key)
  select p.id,'payment','Depósito por revisar',f.name||' envió '||v_amount||' Bs con un solo comprobante. Revisa el reparto entre sus deudas.','payment',p_request_id,'batch-submit:'||p_request_id::text
  from public.profiles p cross join public.families f where p.role::text in ('sysadmin','payer') and f.id=p_family_id;
 end if;
 return p_request_id;
end;$$;
revoke all on function public.casa_record_payment_batch(uuid,smallint,numeric,date,text,text,text,text,uuid) from public,anon;
grant execute on function public.casa_record_payment_batch(uuid,smallint,numeric,date,text,text,text,text,uuid) to authenticated;

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
  v_family smallint;
  v_batch uuid;
  v_paid numeric(12,2);
  v_balance numeric(12,2);
  v_apply numeric(12,2);
  v_credit numeric(12,2);
begin
  if auth.uid() is null or v_role is null or v_role not in ('sysadmin','payer') then
    raise exception 'No tienes permiso para revisar pagos';
  end if;

  if p_approve is null then raise exception 'Selecciona aprobar o rechazar.' using errcode='22023';end if;
  select family_id,batch_id into v_family,v_batch from public.payments where id=p_payment_id;
  if v_family is null then raise exception 'Pago no encontrado';end if;
  if v_batch is not null then perform public.casa_review_payment_batch(v_batch,p_approve);return;end if;
  perform 1 from public.families where id=v_family for update;
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

  select * into v_share from public.expense_shares where id=v_p.share_id for update;
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

revoke all on function public.review_payment(uuid,boolean) from public,anon;
grant execute on function public.review_payment(uuid,boolean) to authenticated;
notify pgrst, 'reload schema';
commit;
