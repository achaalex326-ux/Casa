-- Cleaning exchanges require two household participants, independently of staff privileges.
-- No existing turn, rotation, family, bill or payment is changed by this migration.
begin;
create or replace function public.casa_represents_cleaning(p_family_id bigint)
returns boolean language sql stable security definer set search_path='' as $$
 select p_family_id is not null and exists(
  select 1 from public.profiles p where p.id=auth.uid()
   and p.role::text in ('family','payer','sysadmin')
   and ((p.family_id is not null and public.casa_cleaning_household(p.family_id::bigint)=public.casa_cleaning_household(p_family_id))
    or (p.role::text='payer' and p.second_family_id is not null and public.casa_cleaning_household(p.second_family_id::bigint)=public.casa_cleaning_household(p_family_id)))
 );
$$;
revoke all on function public.casa_represents_cleaning(bigint) from public,anon;
grant execute on function public.casa_represents_cleaning(bigint) to authenticated;

create or replace function public.casa_cleaning_swap_targets(p_turn_id uuid)
returns table(id uuid,family_id smallint,family_name text,turn_date date)
language plpgsql stable security definer set search_path='' as $$
declare own public.cleaning_turns%rowtype;v_role text;
begin
 select p.role::text into v_role from public.profiles p where p.id=auth.uid();
 select * into own from public.cleaning_turns t where t.id=p_turn_id;
 if auth.uid() is null or v_role is null or v_role not in ('family','payer','sysadmin') or own.id is null
   or not public.casa_represents_cleaning(own.family_id::bigint) then
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
 if not public.casa_represents_cleaning(a.family_id::bigint) then raise exception 'Solo puedes solicitar cambios de tu hogar.' using errcode='42501';end if;
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
 if r.created_by=auth.uid() or not public.casa_represents_cleaning(r.target_family_id::bigint) then raise exception 'Solo puede responder el hogar receptor.' using errcode='42501';end if;
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
revoke all on function public.request_cleaning_swap(uuid,uuid),public.review_cleaning_swap(uuid,boolean) from public,anon;
grant execute on function public.request_cleaning_swap(uuid,uuid),public.review_cleaning_swap(uuid,boolean) to authenticated;
commit;
