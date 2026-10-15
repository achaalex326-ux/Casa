-- Solo para el PostgreSQL desechable del validador.
truncate test.results;
insert into public.cleaning_turns(family_id,turn_date,status,checklist,created_by) values
 (1,date_trunc('week',now() at time zone 'America/La_Paz')::date-7,'done','["Escalera"]','00000000-0000-0000-0000-000000000001'),
 (3,date_trunc('week',now() at time zone 'America/La_Paz')::date,'done','["Histórica protegida"]','00000000-0000-0000-0000-000000000001');
set role authenticated;
select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000001',false);
select test.expect_error($q$select public.casa_generate_cleaning_weeks()$q$,'42501');
select test.expect_error($q$select public.casa_configure_cleaning_rotation(null,array[1,2,3]::smallint[],current_date,'[]')$q$,'22023');
select test.expect_error($q$select public.casa_configure_cleaning_rotation(true,array[1,1,3]::smallint[],current_date,'[]')$q$,'22023');
select test.expect_error($q$select public.casa_configure_cleaning_rotation(true,array[1,2]::smallint[],current_date,'[]')$q$,'22023');
select test.expect_error($q$select public.casa_configure_cleaning_rotation(true,array[1,2,999]::smallint[],current_date,'[]')$q$,'22023');
select test.expect_error($q$select public.casa_configure_cleaning_rotation(true,array[1,2,null]::smallint[],current_date,'[]')$q$,'22023');
select test.expect_error($q$select public.casa_configure_cleaning_rotation(true,array[]::smallint[],current_date,'[]')$q$,'22023');
select test.expect_error($q$select public.casa_configure_cleaning_rotation(true,array[[1,2,3]]::smallint[],current_date,'[]')$q$,'22023');
select test.expect_error($q$select public.casa_configure_cleaning_rotation(true,'[0:2]={1,2,3}'::smallint[],current_date,'[]')$q$,'22023');
select test.expect_error($q$select public.casa_configure_cleaning_rotation(true,array[1,2,3]::smallint[],date_trunc('week',now())::date+1,'[]')$q$,'22023');
select test.expect_error($q$select public.casa_configure_cleaning_rotation(true,array[1,2,3]::smallint[],null,'[]')$q$,'22023');
select test.expect_error($q$select public.casa_configure_cleaning_rotation(true,array[1,2,3]::smallint[],date_trunc('week',now())::date,'{}')$q$,'22023');
select test.expect_error($q$select public.casa_configure_cleaning_rotation(true,array[1,2,3]::smallint[],date_trunc('week',now())::date,'[1]')$q$,'22023');
select test.expect_error($q$select public.casa_configure_cleaning_rotation(true,array[1,2,3]::smallint[],date_trunc('week',now())::date,'[" "]')$q$,'22023');
select test.check((public.casa_configure_cleaning_rotation(true,array[2,1,3]::smallint[],
  date_trunc('week',now() at time zone 'America/La_Paz')::date,'["Baño"]')->>'created_weeks')::integer=11,'Only unassigned future weeks generated');
select test.check((select enabled and family_order=array[2,1,3]::smallint[] from public.cleaning_rotation_settings where id=1),'Order persisted');
reset role;
select test.check((select count(*)=13 from public.cleaning_turns),'Existing two rows plus eleven new rows');
select test.check((select family_id=3 and status='done' and checklist='["Histórica protegida"]'::jsonb
 from public.cleaning_turns where turn_date=date_trunc('week',now() at time zone 'America/La_Paz')::date),'Manual current week unchanged');
select test.check((select checklist='["Escalera"]'::jsonb and family_id=1
 from public.cleaning_turns where turn_date=date_trunc('week',now() at time zone 'America/La_Paz')::date+7),'Latest family tasks retained');
select test.check((select checklist='["Baño"]'::jsonb and family_id=2
 from public.cleaning_turns where turn_date=date_trunc('week',now() at time zone 'America/La_Paz')::date+21),'Default tasks for family without history');
select test.check((select bool_and(t.family_id=(array[2,1,3]::smallint[])[mod((t.turn_date-date_trunc('week',now() at time zone 'America/La_Paz')::date)/7,3)+1])
 from public.cleaning_turns t where t.turn_date>date_trunc('week',now() at time zone 'America/La_Paz')::date),'Cycle repeats exactly every three weeks');
select test.check((select count(*)=count(distinct turn_date) from public.cleaning_turns),'No duplicate dates');
select test.check((select bool_and(extract(isodow from turn_date)=1) from public.cleaning_turns),'Monday dates');
select test.check((select count(*)=11 and count(distinct dedupe_key)=11 from test.cleaning_notifications),'One notification per new week');
select test.check(public.casa_generate_cleaning_weeks()=0,'Generator rerun is idempotent');
create table test.generated_snapshot as select md5(jsonb_agg(to_jsonb(t) order by t.id)::text) as checksum from public.cleaning_turns t;
set role authenticated;
select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000001',false);
select test.check((public.casa_configure_cleaning_rotation(true,array[1,3,2]::smallint[],date_trunc('week',now() at time zone 'America/La_Paz')::date,'["Nueva tarea"]')->>'created_weeks')::integer=0,'Changing order does not overwrite assigned weeks');
select test.check((public.casa_configure_cleaning_rotation(false,array[1,3,2]::smallint[],date_trunc('week',now() at time zone 'America/La_Paz')::date,'["Nueva tarea"]')->>'enabled')::boolean=false,'Admin can pause');
reset role;
select test.check((select md5(jsonb_agg(to_jsonb(t) order by t.id)::text)=(select checksum from test.generated_snapshot) from public.cleaning_turns t),'Pausing and order edits preserve all turn details');
select test.check(public.casa_generate_cleaning_weeks()=0,'Paused generator does not add weeks');
-- Una activación con inicio futuro no adelanta semanas anteriores al ancla.
update public.cleaning_rotation_settings set enabled=true,anchor_monday=date_trunc('week',now() at time zone 'America/La_Paz')::date+140;
select test.check(public.casa_generate_cleaning_weeks()=0,'Future anchor is respected');
update public.cleaning_rotation_settings set anchor_monday=date_trunc('week',now() at time zone 'America/La_Paz')::date;
delete from public.cleaning_turns where turn_date=date_trunc('week',now() at time zone 'America/La_Paz')::date+77;
update public.profiles set role='family' where id='00000000-0000-0000-0000-000000000001';
select test.check(public.casa_generate_cleaning_weeks()=0,'Automatic job stops if its administrator loses the role');
update public.profiles set role='sysadmin' where id='00000000-0000-0000-0000-000000000001';
select test.check(public.casa_generate_cleaning_weeks()=1,'Job replenishes missing future week');
set role authenticated;
select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000002',false);
select test.check((select count(*)=0 from public.cleaning_rotation_settings),'Family cannot read admin configuration');
select test.expect_error($q$select public.casa_configure_cleaning_rotation(true,array[1,2,3]::smallint[],current_date,'[]')$q$,'42501');
select test.expect_error($q$select public.casa_generate_cleaning_weeks()$q$,'42501');
select test.expect_error($q$update public.cleaning_rotation_settings set enabled=false$q$,'42501');
select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000003',false);
select test.expect_error($q$select public.casa_configure_cleaning_rotation(true,array[1,2,3]::smallint[],current_date,'[]')$q$,'42501');
select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000004',false);
select test.expect_error($q$select public.casa_configure_cleaning_rotation(true,array[1,2,3]::smallint[],current_date,'[]')$q$,'42501');
select set_config('request.jwt.claim.sub','',false);
select test.expect_error($q$select public.casa_configure_cleaning_rotation(true,array[1,2,3]::smallint[],current_date,'[]')$q$,'42501');
reset role;
set role anon;
select test.expect_error($q$select public.casa_configure_cleaning_rotation(true,array[1,2,3]::smallint[],current_date,'[]')$q$,'42501');
select test.expect_error($q$select public.casa_generate_cleaning_weeks()$q$,'42501');
reset role;
select json_build_object('rotationChecks',count(*),'result','PASS','cron','not run in local PostgreSQL') from test.results;
