begin;

create table if not exists public.cleaning_rotation_settings (
  id smallint primary key default 1 check (id = 1),
  enabled boolean not null default false,
  family_order smallint[] not null,
  anchor_monday date not null check (extract(isodow from anchor_monday) = 1),
  default_checklist jsonb not null default '[]'::jsonb check (jsonb_typeof(default_checklist) = 'array'),
  created_by uuid not null references public.profiles(id),
  updated_at timestamptz not null default now()
);
alter table public.cleaning_rotation_settings enable row level security;
revoke all on public.cleaning_rotation_settings from public, anon, authenticated;
grant select on public.cleaning_rotation_settings to authenticated;
drop policy if exists casa_rotation_admin_read on public.cleaning_rotation_settings;
create policy casa_rotation_admin_read on public.cleaning_rotation_settings
for select to authenticated using (public.casa_is_sysadmin());

-- Solo la tarea programada y la función de configuración pueden llamar al generador.
create or replace function public.casa_generate_cleaning_weeks()
returns integer language plpgsql security definer set search_path = '' as $$
declare cfg public.cleaning_rotation_settings%rowtype;
  base_week date := date_trunc('week', now() at time zone 'America/La_Paz')::date;
  week_start date; family smallint; tasks jsonb; turn_id uuid;
  slot integer; inserted_count integer := 0;
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
    family := cfg.family_order[slot];
    select t.checklist into tasks from public.cleaning_turns t where t.family_id=family
      and case when jsonb_typeof(t.checklist)='array' then jsonb_array_length(t.checklist)>0 else false end
      order by t.turn_date desc,t.created_at desc,t.id desc limit 1;
    tasks := coalesce(tasks,cfg.default_checklist);
    insert into public.cleaning_turns(family_id,turn_date,checklist,note,created_by)
    values (family,week_start,tasks,'Turno semanal automático',cfg.created_by) returning id into turn_id;
    perform public.notify_family(family,'cleaning','Turno semanal de limpieza',
      'Tu semana de limpieza comienza el '||week_start::text,'cleaning',turn_id,
      'casa-weekly-cleaning-'||week_start::text);
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
      or cardinality(p_family_order) <> (select count(*) from public.families)
      or cardinality(p_family_order) <> (select count(distinct f) from unnest(p_family_order) f)
      or exists (select 1 from unnest(p_family_order) f where not exists (select 1 from public.families p where p.id=f)) then
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
