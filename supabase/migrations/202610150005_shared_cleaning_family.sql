begin;

-- La agrupación solo afecta a limpieza. No cambia IDs, perfiles ni movimientos financieros.
create table if not exists public.cleaning_family_aliases (
 family_id smallint primary key references public.families(id),
 household_id smallint not null references public.families(id),
 check (family_id<>household_id)
);
alter table public.cleaning_family_aliases enable row level security;
revoke all on public.cleaning_family_aliases from public,anon,authenticated;
grant select on public.cleaning_family_aliases to authenticated;
drop policy if exists casa_cleaning_alias_read on public.cleaning_family_aliases;
create policy casa_cleaning_alias_read on public.cleaning_family_aliases for select to authenticated using (true);

create or replace function public.casa_cleaning_household(p_family_id bigint)
returns bigint language sql stable security definer set search_path='' as $$
 select coalesce((select a.household_id from public.cleaning_family_aliases a where a.family_id=p_family_id),p_family_id);
$$;
revoke all on function public.casa_cleaning_household(bigint) from public,anon;
grant execute on function public.casa_cleaning_household(bigint) to authenticated;

create or replace function public.casa_can_manage_cleaning(p_family_id bigint)
returns boolean language sql stable security definer set search_path='' as $$
 select auth.uid() is not null and exists (
  select 1 from public.profiles p where p.id=auth.uid() and (
   p.role::text='sysadmin' or
   (p.role::text in ('family','payer') and public.casa_cleaning_household(p.family_id)=public.casa_cleaning_household(p_family_id)) or
   (p.role::text='payer' and public.casa_cleaning_household(p.second_family_id)=public.casa_cleaning_household(p_family_id))
  )
 );
$$;
revoke all on function public.casa_can_manage_cleaning(bigint) from public,anon;
grant execute on function public.casa_can_manage_cleaning(bigint) to authenticated;

alter table public.cleaning_turns enable row level security;
grant select on public.cleaning_turns to authenticated;
drop policy if exists casa_shared_cleaning_read on public.cleaning_turns;
create policy casa_shared_cleaning_read on public.cleaning_turns for select to authenticated
using (public.casa_can_manage_cleaning(family_id));

-- Solo las fotos de un turno compartido se comparten entre sus cuentas.
-- Los comprobantes de pagos conservan sus políticas anteriores.
drop policy if exists casa_shared_cleaning_receipt_read on storage.objects;
create policy casa_shared_cleaning_receipt_read on storage.objects for select to authenticated
using (bucket_id='receipts' and exists (
 select 1 from public.cleaning_turns t where t.completion_receipt_path=storage.objects.name
 and public.casa_can_manage_cleaning(t.family_id)
));

create or replace function public.complete_cleaning_turn(p_turn_id uuid,p_receipt_path text,p_note text)
returns void language plpgsql security definer set search_path='' as $$
declare turn public.cleaning_turns%rowtype;
begin
 select * into turn from public.cleaning_turns where id=p_turn_id for update;
 if not found then raise exception 'Turno no encontrado' using errcode='22023'; end if;
 if not public.casa_can_manage_cleaning(turn.family_id) then
  raise exception 'Solo la familia asignada o el administrador puede completar este turno.' using errcode='42501';
 end if;
 if turn.status::text<>'pending' then raise exception 'El turno ya está completado.' using errcode='22023'; end if;
 if p_receipt_path is null or p_receipt_path not like auth.uid()::text||'/%'
    or not exists (select 1 from storage.objects o where o.bucket_id='receipts' and o.name=p_receipt_path) then
  raise exception 'Sube una foto de la limpieza desde tu propia cuenta.' using errcode='22023';
 end if;
 update public.cleaning_turns set status='done',completion_receipt_path=p_receipt_path,
  note=coalesce(nullif(btrim(coalesce(p_note,'')),''),note),completed_at=now() where id=p_turn_id;
 perform public.notify_all('cleaning','Limpieza realizada',
  'La familia completó su turno de limpieza.','cleaning',p_turn_id,null);
 perform public.audit('complete','cleaning_turn',p_turn_id::text,jsonb_build_object('family',turn.family_id));
end;
$$;
revoke all on function public.complete_cleaning_turn(uuid,text,text) from public,anon;
grant execute on function public.complete_cleaning_turn(uuid,text,text) to authenticated;

create or replace function public.casa_generate_cleaning_weeks()
returns integer language plpgsql security definer set search_path = '' as $$
declare cfg public.cleaning_rotation_settings%rowtype;
  base_week date := date_trunc('week', now() at time zone 'America/La_Paz')::date;
  week_start date; family smallint; tasks jsonb; turn_id uuid;
  slot integer; member smallint; inserted_count integer := 0;
begin
  select * into cfg from public.cleaning_rotation_settings where id=1 for update;
  if not found or not cfg.enabled then return 0; end if;
  if not exists (select 1 from public.profiles p where p.id=cfg.created_by and p.role::text='sysadmin') then
    return 0;
  end if;
  -- Serializa generadores y asignaciones manuales concurrentes durante esta operación breve.
  lock table public.cleaning_turns in share row exclusive mode;
  for i in 0..11 loop
    week_start := base_week + i*7;
    if week_start < cfg.anchor_monday then continue; end if;
    if exists (select 1 from public.cleaning_turns t where t.turn_date>=week_start and t.turn_date<week_start+7) then
      continue;
    end if;
    slot := mod((week_start-cfg.anchor_monday)/7,cardinality(cfg.family_order)) + 1;
    family := public.casa_cleaning_household(cfg.family_order[slot]);
    select t.checklist into tasks from public.cleaning_turns t where public.casa_cleaning_household(t.family_id)=family
      and case when jsonb_typeof(t.checklist)='array' then jsonb_array_length(t.checklist)>0 else false end
      order by t.turn_date desc,t.created_at desc,t.id desc limit 1;
    tasks := coalesce(tasks,cfg.default_checklist);
    insert into public.cleaning_turns(family_id,turn_date,checklist,note,created_by)
    values (family,week_start,tasks,'Turno semanal automático',cfg.created_by) returning id into turn_id;
    for member in select f.id from public.families f where public.casa_cleaning_household(f.id)=family loop
      perform public.notify_family(member,'cleaning','Turno semanal de limpieza',
        'Tu semana de limpieza comienza el '||week_start::text,'cleaning',turn_id,
        'casa-weekly-cleaning-'||week_start::text);
    end loop;
    inserted_count := inserted_count+1;
  end loop;
  return inserted_count;
end;
$$;
revoke all on function public.casa_generate_cleaning_weeks() from public, anon, authenticated;

create or replace function public.casa_configure_cleaning_rotation(
  p_enabled boolean, p_family_order smallint[], p_anchor_monday date, p_default_checklist jsonb
)
returns jsonb language plpgsql security definer set search_path = '' as $$
declare created_count integer := 0;
begin
  if not public.casa_is_sysadmin() then
    raise exception 'Solo el administrador puede configurar la repetición de limpieza.' using errcode='42501';
  end if;
  if p_enabled is null then raise exception 'Selecciona si la repetición estará activa.' using errcode='22023'; end if;
  if p_family_order is null or array_ndims(p_family_order) is distinct from 1
      or array_lower(p_family_order,1) is distinct from 1
      or cardinality(p_family_order) <> (select count(*) from public.families f where not exists (select 1 from public.cleaning_family_aliases a where a.family_id=f.id))
      or cardinality(p_family_order) <> (select count(distinct f) from unnest(p_family_order) f)
      or exists (select 1 from unnest(p_family_order) f where not exists (select 1 from public.families p where p.id=f))
      or exists (select 1 from unnest(p_family_order) f join public.cleaning_family_aliases a on a.family_id=f) then
    raise exception 'El orden debe incluir a todas las familias una sola vez.' using errcode='22023';
  end if;
  if p_anchor_monday is null or extract(isodow from p_anchor_monday)<>1 then
    raise exception 'La fecha inicial debe ser un lunes.' using errcode='22023';
  end if;
  if p_default_checklist is null or jsonb_typeof(p_default_checklist)<>'array' then
    raise exception 'Las tareas deben ser una lista.' using errcode='22023';
  end if;
  if jsonb_array_length(p_default_checklist)>40 or exists (
    select 1 from jsonb_array_elements(p_default_checklist) t
    where jsonb_typeof(t)<>'string' or char_length(btrim(t#>>'{}')) not between 1 and 160
  ) then raise exception 'Usa hasta 40 tareas de 1 a 160 caracteres.' using errcode='22023'; end if;
  insert into public.cleaning_rotation_settings(id,enabled,family_order,anchor_monday,default_checklist,created_by,updated_at)
  values (1,p_enabled,p_family_order,p_anchor_monday,p_default_checklist,auth.uid(),now())
  on conflict(id) do update set enabled=excluded.enabled,family_order=excluded.family_order,
    anchor_monday=excluded.anchor_monday,default_checklist=excluded.default_checklist,
    created_by=excluded.created_by,updated_at=excluded.updated_at;
  if p_enabled then created_count:=public.casa_generate_cleaning_weeks(); end if;
  perform public.audit('update','cleaning_rotation_settings','1',
    jsonb_build_object('enabled',p_enabled,'family_order',p_family_order,'created_weeks',created_count));
  return jsonb_build_object('enabled',p_enabled,'created_weeks',created_count);
end;
$$;
revoke all on function public.casa_configure_cleaning_rotation(boolean,smallint[],date,jsonb) from public, anon;
grant execute on function public.casa_configure_cleaning_rotation(boolean,smallint[],date,jsonb) to authenticated;

notify pgrst,'reload schema';
commit;
