-- Esta parte requiere Supabase con pg_cron disponible.
-- No genera turnos hasta que el administrador active el plan desde la aplicación.
begin;
create extension if not exists pg_cron with schema pg_catalog;
select cron.schedule('casa-cleaning-weekly','5 4 * * 1',
  'select public.casa_generate_cleaning_weeks();');
commit;
