-- Requiere 202610150001_qr_familias.sql. Conserva el guardado y su auditoría.
begin;

create or replace function public.save_payment_qr_settings(
  p_qr_image_path text, p_qr_title text, p_qr_note text
)
returns void language plpgsql security definer set search_path = '' as $$
begin
  -- Sin perfil o sin sesión también se rechaza: un rol NULL no es administrador.
  if not public.casa_is_sysadmin() then
    raise exception 'Solo el administrador puede configurar el QR de pago' using errcode = '42501';
  end if;
  if p_qr_image_path is null or length(trim(p_qr_image_path)) = 0 then
    raise exception 'Falta la imagen del QR' using errcode = '22023';
  end if;
  insert into public.payment_qr_settings(id, qr_image_path, qr_title, qr_note, updated_by, updated_at)
  values (1, p_qr_image_path, coalesce(nullif(trim(p_qr_title), ''), 'Pago por QR'),
          nullif(trim(coalesce(p_qr_note, '')), ''), auth.uid(), now())
  on conflict (id) do update
  set qr_image_path = excluded.qr_image_path,
      qr_title = excluded.qr_title,
      qr_note = excluded.qr_note,
      updated_by = excluded.updated_by,
      updated_at = excluded.updated_at;
  perform public.audit('update', 'payment_qr_settings', '1',
    jsonb_build_object('qr_title', p_qr_title));
end;
$$;
revoke all on function public.save_payment_qr_settings(text, text, text) from public, anon;
grant execute on function public.save_payment_qr_settings(text, text, text) to authenticated;

notify pgrst, 'reload schema';
commit;
