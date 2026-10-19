-- Only the disposable PostgreSQL fixture; prior community checks left 2030 turn IDs.
truncate test.results;
-- An unrelated payer, a recipient payer and another recipient account.
insert into public.profiles(id,role,family_id,second_family_id,display_name) values
('00000000-0000-0000-0000-000000000011','payer',5,6,'Unrelated payer'),
('00000000-0000-0000-0000-000000000012','payer',5,4,'Recipient payer'),
('00000000-0000-0000-0000-000000000013','family',4,null,'Second recipient account');
set role authenticated;
select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000001',false);
select test.check(not public.casa_represents_cleaning(1),'Unassigned admin represents no household');
select test.check(not public.casa_represents_cleaning(null),'Null is never a represented household');
select test.expect_error($q$select * from public.casa_cleaning_swap_targets('20000000-0000-0000-0000-000000000002')$q$,'42501');
select test.expect_error($q$select public.request_cleaning_swap('20000000-0000-0000-0000-000000000002','20000000-0000-0000-0000-000000000003')$q$,'42501');
select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000005',false);
select test.check(public.casa_represents_cleaning(1),'Alias account represents canonical household');
select test.check(not public.casa_represents_cleaning(4),'Alias account does not represent unrelated household');
select public.request_cleaning_swap('20000000-0000-0000-0000-000000000002','20000000-0000-0000-0000-000000000003');
select test.expect_error($q$select public.review_cleaning_swap((select id from public.cleaning_swap_requests where status='pending'),true)$q$,'42501');
reset role;
-- Select the pending UUID into a non-secret fixture setting for adversarial calls.
select set_config('test.swap_id',(select id::text from public.cleaning_swap_requests where status='pending'),false);
set role authenticated;
select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000001',false);
select test.expect_error($q$select public.review_cleaning_swap(current_setting('test.swap_id')::uuid,true)$q$,'42501');
select test.expect_error($q$select public.review_cleaning_swap(current_setting('test.swap_id')::uuid,false)$q$,'42501');
select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000011',false);
select test.expect_error($q$select * from public.casa_cleaning_swap_targets('20000000-0000-0000-0000-000000000002')$q$,'42501');
select test.expect_error($q$select public.request_cleaning_swap('20000000-0000-0000-0000-000000000002','20000000-0000-0000-0000-000000000003')$q$,'42501');
select test.expect_error($q$select public.review_cleaning_swap(current_setting('test.swap_id')::uuid,true)$q$,'42501');
select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000012',false);
select test.check(public.casa_represents_cleaning(4),'Payer can represent secondary household');
select public.review_cleaning_swap(current_setting('test.swap_id')::uuid,false);
reset role;
select test.check((select family_id=1 from public.cleaning_turns where id='20000000-0000-0000-0000-000000000002'),'Rejected exchange preserves source assignment');
select test.check((select family_id=4 from public.cleaning_turns where id='20000000-0000-0000-0000-000000000003'),'Rejected exchange preserves destination assignment');
set role authenticated;
select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000005',false);
select public.request_cleaning_swap('20000000-0000-0000-0000-000000000002','20000000-0000-0000-0000-000000000003');
select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000013',false);
select test.expect_error($q$select public.review_cleaning_swap((select id from public.cleaning_swap_requests where status='pending'),null)$q$,'22023');
select public.review_cleaning_swap((select id from public.cleaning_swap_requests where status='pending'),true);
reset role;
select test.check((select family_id=4 from public.cleaning_turns where id='20000000-0000-0000-0000-000000000002'),'Recipient account accepts and transfers source');
select test.check((select family_id=1 from public.cleaning_turns where id='20000000-0000-0000-0000-000000000003'),'Recipient account accepts and transfers destination');
-- The same user representing two different households must not answer their own request.
update public.profiles set family_id=4,second_family_id=3 where id='00000000-0000-0000-0000-000000000012';
set role authenticated;
select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000012',false);
select public.request_cleaning_swap('20000000-0000-0000-0000-000000000002','20000000-0000-0000-0000-000000000001');
select test.expect_error($q$select public.review_cleaning_swap((select id from public.cleaning_swap_requests where status='pending'),true)$q$,'42501');
select test.expect_error($q$select public.review_cleaning_swap((select id from public.cleaning_swap_requests where status='pending'),false)$q$,'42501');
select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000004',false);
select test.check(not public.casa_represents_cleaning(1),'Restaurant represents no cleaning household');
select test.expect_error($q$select public.review_cleaning_swap(current_setting('test.swap_id')::uuid,true)$q$,'42501');
select set_config('request.jwt.claim.sub','',false);
select test.check(not public.casa_represents_cleaning(1),'No session represents no cleaning household');
reset role;
set role anon;
select test.expect_error($q$select public.casa_represents_cleaning(1)$q$,'42501');
reset role;
select test.check((select count(*)=1 and min(amount)=123.45 from public.payments),'No exchange changes financial records');
select json_build_object('householdSwapChecks',count(*),'result','PASS') from test.results;
