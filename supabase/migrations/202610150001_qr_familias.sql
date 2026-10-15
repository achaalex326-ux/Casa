-- Ejecutar una vez en SQL Editor del proyecto Supabase de Casa Familias.
-- Repetible: no borra familias, usuarios, comprobantes ni movimientos.
begin;

create or replace function public.casa_is_sysadmin()
returns boolean language sql stable security definer set search_path = '' as $$
  select auth.uid() is not null and exists (
    select 1 from public.profiles p
    where p.id = auth.uid() and p.role::text = 'sysadmin'
  );
$$;
revoke all on function public.casa_is_sysadmin() from public, anon;
grant execute on function public.casa_is_sysadmin() to authenticated;

insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('payment-qr', 'payment-qr', false, 5242880,
        array['image/png', 'image/jpeg', 'image/webp'])
on conflict (id) do update set public = false,
  file_size_limit = excluded.file_size_limit,
  allowed_mime_types = excluded.allowed_mime_types;

-- Un QR es compartido con las cuentas autenticadas; solo el administrador escribe.
-- Estas políticas no modifican el bucket de comprobantes (receipts).
drop policy if exists casa_payment_qr_read on storage.objects;
create policy casa_payment_qr_read on storage.objects for select to authenticated
using (bucket_id = 'payment-qr');
drop policy if exists casa_payment_qr_insert on storage.objects;
create policy casa_payment_qr_insert on storage.objects for insert to authenticated
with check (bucket_id = 'payment-qr' and name like 'qr/%' and public.casa_is_sysadmin());
drop policy if exists casa_payment_qr_update on storage.objects;
create policy casa_payment_qr_update on storage.objects for update to authenticated
using (bucket_id = 'payment-qr' and public.casa_is_sysadmin())
with check (bucket_id = 'payment-qr' and name like 'qr/%' and public.casa_is_sysadmin());
drop policy if exists casa_payment_qr_delete on storage.objects;
create policy casa_payment_qr_delete on storage.objects for delete to authenticated
using (bucket_id = 'payment-qr' and public.casa_is_sysadmin());

create or replace function public.casa_admin_rename_family(p_family_id bigint, p_name text)
returns jsonb language plpgsql security definer set search_path = '' as $$
declare v_name text := btrim(p_name); v_result jsonb;
begin
  if not public.casa_is_sysadmin() then
    raise exception 'Solo el administrador puede cambiar nombres de familias.' using errcode = '42501';
  end if;
  if v_name is null or char_length(v_name) < 1 or char_length(v_name) > 80 then
    raise exception 'Escribe un nombre de familia de 1 a 80 caracteres.' using errcode = '22023';
  end if;
  -- Evita dos renombrados simultáneos al mismo nombre.
  perform pg_catalog.pg_advisory_xact_lock(639481001);
  if exists (select 1 from public.families f
             where lower(btrim(f.name)) = lower(v_name) and f.id <> p_family_id) then
    raise exception 'Ya existe una familia con ese nombre.' using errcode = '22023';
  end if;
  update public.families set name = v_name where id = p_family_id
  returning jsonb_build_object('id', id, 'name', name) into v_result;
  if not found then raise exception 'La familia ya no existe.' using errcode = '22023'; end if;
  return v_result;
end;
$$;
revoke all on function public.casa_admin_rename_family(bigint, text) from public, anon;
grant execute on function public.casa_admin_rename_family(bigint, text) to authenticated;

create or replace function public.casa_admin_assign_user_families(
  p_user_id uuid, p_family_id bigint, p_second_family_id bigint default null
)
returns jsonb language plpgsql security definer set search_path = '' as $$
declare v_role text; v_result jsonb;
begin
  if not public.casa_is_sysadmin() then
    raise exception 'Solo el administrador puede cambiar familias de usuarios.' using errcode = '42501';
  end if;
  select p.role::text into v_role from public.profiles p where p.id = p_user_id for update;
  if not found then raise exception 'El usuario ya no existe.' using errcode = '22023'; end if;
  if v_role is null or v_role not in ('family', 'payer') then
    raise exception 'Este rol no utiliza familias asignadas.' using errcode = '22023';
  end if;
  if p_family_id is null or not exists (select 1 from public.families f where f.id = p_family_id) then
    raise exception 'Selecciona una familia principal válida.' using errcode = '22023';
  end if;
  if v_role = 'family' and p_second_family_id is not null then
    raise exception 'Una cuenta de familia solo puede tener una familia.' using errcode = '22023';
  end if;
  if v_role = 'payer' and (p_second_family_id is null or p_second_family_id = p_family_id
     or not exists (select 1 from public.families f where f.id = p_second_family_id)) then
    raise exception 'El encargado debe tener dos familias válidas y diferentes.' using errcode = '22023';
  end if;
  update public.profiles set family_id = p_family_id, second_family_id = p_second_family_id
  where id = p_user_id
  returning jsonb_build_object('id', id, 'family_id', family_id,
                              'second_family_id', second_family_id) into v_result;
  -- No se trasladan saldos ni pagos: siguen perteneciendo a su familia original.
  return v_result;
end;
$$;
revoke all on function public.casa_admin_assign_user_families(uuid, bigint, bigint) from public, anon;
grant execute on function public.casa_admin_assign_user_families(uuid, bigint, bigint) to authenticated;

notify pgrst, 'reload schema';
commit;
