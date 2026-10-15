-- Enable the family interactions requested for the native app without exposing financial data.
begin;
create or replace function public.casa_is_family_member()
returns boolean language sql stable security definer set search_path='' as $$
 select exists(select 1 from public.profiles p where p.id=auth.uid() and p.role::text='family' and p.family_id is not null);
$$;
revoke all on function public.casa_is_family_member() from public,anon;
grant execute on function public.casa_is_family_member() to authenticated;

drop policy if exists casa_family_proposals_read on public.proposals;
create policy casa_family_proposals_read on public.proposals for select to authenticated using(public.casa_is_family_member());
drop policy if exists casa_family_own_vote_read on public.proposal_votes;
create policy casa_family_own_vote_read on public.proposal_votes for select to authenticated using(
 public.casa_is_family_member() and family_id=(select p.family_id from public.profiles p where p.id=auth.uid()));
drop policy if exists casa_family_swap_read on public.cleaning_swap_requests;
create policy casa_family_swap_read on public.cleaning_swap_requests for select to authenticated using(
 public.casa_can_manage_cleaning(requester_family_id::bigint) or public.casa_can_manage_cleaning(target_family_id::bigint));
drop policy if exists casa_proposal_receipt_read on storage.objects;
create policy casa_proposal_receipt_read on storage.objects for select to authenticated using(
 bucket_id='receipts' and public.casa_is_family_member() and exists(
 select 1 from public.proposals p where p.receipt_path=storage.objects.name and storage.objects.name like p.created_by::text||'/%'));

create or replace function public.casa_proposal_totals()
returns table(proposal_id uuid,yes_count bigint,no_count bigint)
language plpgsql stable security definer set search_path='' as $$
begin
 if not exists(select 1 from public.profiles p where p.id=auth.uid() and p.role::text in ('family','payer','sysadmin')) then
  raise exception 'No autorizado.' using errcode='42501';
 end if;
 return query select p.id,count(v.id) filter(where v.vote),count(v.id) filter(where not v.vote)
 from public.proposals p left join public.proposal_votes v on v.proposal_id=p.id group by p.id;
end; $$;
revoke all on function public.casa_proposal_totals() from public,anon;
grant execute on function public.casa_proposal_totals() to authenticated;

create or replace function public.create_proposal(p_concept text,p_amount numeric,p_justification text,p_receipt_path text)
returns uuid language plpgsql security definer set search_path='' as $$
declare v_id uuid;v_role text;
begin
 select p.role::text into v_role from public.profiles p where p.id=auth.uid();
 if auth.uid() is null or v_role is null or v_role not in ('family','payer','sysadmin')
    or (v_role='family' and not public.casa_is_family_member()) then
  raise exception 'No autorizado.' using errcode='42501';
 end if;
 if p_concept is null or length(trim(p_concept))<1 or p_amount is null or p_amount<=0 or p_amount::text in ('NaN','Infinity','-Infinity')
   or p_justification is null or length(trim(p_justification))<3 then
  raise exception 'Completa el concepto, el monto y la justificación.' using errcode='22023';
 end if;
 if p_receipt_path is not null and (p_receipt_path not like auth.uid()::text||'/%'
   or not exists(select 1 from storage.objects o where o.bucket_id='receipts' and o.name=p_receipt_path)) then
  raise exception 'Selecciona un comprobante propio válido.' using errcode='22023';
 end if;
 insert into public.proposals(concept,estimated_amount,justification,receipt_path,created_by)
 values(trim(p_concept),round(p_amount,2),trim(p_justification),p_receipt_path,auth.uid()) returning id into v_id;
 perform public.notify_all('proposal','Nueva propuesta: '||trim(p_concept),'Presupuesto estimado: '||round(p_amount,2)||' Bs','proposal',v_id,null);
 perform public.audit('create','proposal',v_id::text,jsonb_build_object('amount',round(p_amount,2)));
 return v_id;
end; $$;

create or replace function public.cast_proposal_vote(p_proposal uuid,p_vote boolean)
returns void language plpgsql security definer set search_path='' as $$
declare v_family smallint;v_role text;v_yes int;v_no int;v_status text;
begin
 select p.role::text,p.family_id into v_role,v_family from public.profiles p where p.id=auth.uid();
 if auth.uid() is null or v_role is null or v_role not in ('family','payer','sysadmin') then raise exception 'No autorizado.' using errcode='42501';end if;
 if v_family is null then raise exception 'Esta cuenta no representa a una familia.' using errcode='42501';end if;
 if p_vote is null then raise exception 'Selecciona un voto.' using errcode='22023';end if;
 -- Serialize votes on each proposal so simultaneous votes cannot miss the majority.
 perform 1 from public.proposals where id=p_proposal and status::text='open' for update;
 if not found then raise exception 'La propuesta no está abierta.' using errcode='22023';end if;
 insert into public.proposal_votes(proposal_id,family_id,vote,created_by)
 values(p_proposal,v_family,p_vote,auth.uid()) on conflict(proposal_id,family_id) do update set vote=excluded.vote,created_by=excluded.created_by,created_at=now();
 select count(*) filter(where vote),count(*) filter(where not vote) into v_yes,v_no from public.proposal_votes where proposal_id=p_proposal;
 if v_yes>=4 then update public.proposals set status='approved' where id=p_proposal;
 elsif v_no>=4 then update public.proposals set status='rejected' where id=p_proposal;end if;
 perform public.audit('vote','proposal',p_proposal::text,jsonb_build_object('family',v_family,'vote',p_vote));
end; $$;

create or replace function public.casa_cleaning_swap_targets(p_turn_id uuid)
returns table(id uuid,family_id smallint,family_name text,turn_date date)
language plpgsql stable security definer set search_path='' as $$
declare own public.cleaning_turns%rowtype;v_role text;
begin
 select p.role::text into v_role from public.profiles p where p.id=auth.uid();
 select * into own from public.cleaning_turns t where t.id=p_turn_id;
 if auth.uid() is null or v_role is null or v_role not in ('family','payer','sysadmin') or own.id is null
   or (v_role='family' and not public.casa_can_manage_cleaning(own.family_id::bigint)) then
  raise exception 'No puedes solicitar cambios para ese turno.' using errcode='42501';end if;
 if own.status::text<>'pending' then raise exception 'El turno ya no está pendiente.' using errcode='22023';end if;
 return query select t.id,t.family_id,f.name,t.turn_date from public.cleaning_turns t join public.families f on f.id=t.family_id
 where t.status::text='pending' and t.id<>own.id and public.casa_cleaning_household(t.family_id::bigint)<>public.casa_cleaning_household(own.family_id::bigint) order by t.turn_date,t.id;
end; $$;
revoke all on function public.casa_cleaning_swap_targets(uuid) from public,anon;
grant execute on function public.casa_cleaning_swap_targets(uuid) to authenticated;

create or replace function public.request_cleaning_swap(p_requester_turn uuid,p_target_turn uuid)
returns uuid language plpgsql security definer set search_path='' as $$
declare a public.cleaning_turns%rowtype;b public.cleaning_turns%rowtype;v_id uuid;v_role text;
begin
 select p.role::text into v_role from public.profiles p where p.id=auth.uid();
 if auth.uid() is null or v_role is null or v_role not in ('family','payer','sysadmin') then raise exception 'No autorizado.' using errcode='42501';end if;
 perform 1 from public.cleaning_turns where id in (p_requester_turn,p_target_turn) order by id for update;
 select * into a from public.cleaning_turns where id=p_requester_turn;select * into b from public.cleaning_turns where id=p_target_turn;
 if a.id is null or b.id is null then raise exception 'Turno inválido.' using errcode='22023';end if;
 if v_role='family' and not public.casa_can_manage_cleaning(a.family_id::bigint) then raise exception 'Solo puedes solicitar cambios de tu hogar.' using errcode='42501';end if;
 if a.status::text<>'pending' or b.status::text<>'pending' or a.id=b.id or public.casa_cleaning_household(a.family_id::bigint)=public.casa_cleaning_household(b.family_id::bigint) then
  raise exception 'Selecciona dos turnos pendientes de hogares diferentes.' using errcode='22023';end if;
 if exists(select 1 from public.cleaning_swap_requests r where r.status::text='pending' and ((r.requester_turn_id=a.id and r.target_turn_id=b.id) or (r.requester_turn_id=b.id and r.target_turn_id=a.id))) then
  raise exception 'Ya existe una solicitud pendiente para esos turnos.' using errcode='22023';end if;
 insert into public.cleaning_swap_requests(requester_turn_id,target_turn_id,requester_family_id,target_family_id,created_by)
 values(a.id,b.id,a.family_id,b.family_id,auth.uid()) returning id into v_id;
 perform public.audit('request','cleaning_swap',v_id::text,jsonb_build_object('from_family',a.family_id,'to_family',b.family_id));
 return v_id;
end; $$;

create or replace function public.review_cleaning_swap(p_request uuid,p_accept boolean)
returns void language plpgsql security definer set search_path='' as $$
declare r public.cleaning_swap_requests%rowtype;a public.cleaning_turns%rowtype;b public.cleaning_turns%rowtype;v_role text;
begin
 select p.role::text into v_role from public.profiles p where p.id=auth.uid();
 if auth.uid() is null or v_role is null or v_role not in ('family','payer','sysadmin') then raise exception 'No autorizado.' using errcode='42501';end if;
 select * into r from public.cleaning_swap_requests where id=p_request for update;
 if r.id is null or r.status::text<>'pending' then raise exception 'Solicitud no disponible.' using errcode='22023';end if;
 if v_role='family' and not public.casa_can_manage_cleaning(r.target_family_id::bigint) then raise exception 'Solo puede responder el hogar receptor.' using errcode='42501';end if;
 if p_accept is null then raise exception 'Selecciona una respuesta.' using errcode='22023';end if;
 if not p_accept then
  update public.cleaning_swap_requests set status='rejected',reviewed_by=auth.uid(),reviewed_at=now() where id=r.id;
  perform public.audit('reject','cleaning_swap',r.id::text,'{}'::jsonb);return;end if;
 perform 1 from public.cleaning_turns where id in (r.requester_turn_id,r.target_turn_id) order by id for update;
 select * into a from public.cleaning_turns where id=r.requester_turn_id;select * into b from public.cleaning_turns where id=r.target_turn_id;
 if a.id is null or b.id is null or a.status::text<>'pending' or b.status::text<>'pending'
  or public.casa_cleaning_household(a.family_id::bigint)<>public.casa_cleaning_household(r.requester_family_id::bigint)
  or public.casa_cleaning_household(b.family_id::bigint)<>public.casa_cleaning_household(r.target_family_id::bigint) then
  raise exception 'Los turnos cambiaron; revisa el calendario.' using errcode='22023';end if;
 update public.cleaning_turns set family_id=b.family_id where id=a.id;update public.cleaning_turns set family_id=a.family_id where id=b.id;
 update public.cleaning_swap_requests set status='accepted',reviewed_by=auth.uid(),reviewed_at=now() where id=r.id;
 perform public.audit('accept','cleaning_swap',r.id::text,'{}'::jsonb);
end; $$;
revoke all on function public.create_proposal(text,numeric,text,text),public.cast_proposal_vote(uuid,boolean),public.request_cleaning_swap(uuid,uuid),public.review_cleaning_swap(uuid,boolean) from public,anon;
grant execute on function public.create_proposal(text,numeric,text,text),public.cast_proposal_vote(uuid,boolean),public.request_cleaning_swap(uuid,uuid),public.review_cleaning_swap(uuid,boolean) to authenticated;
commit;
