-- Only the disposable PostgreSQL fixture. No real user records are touched.
truncate test.results;
insert into public.families values(4,'Cuatro'),(5,'Cinco'),(6,'Seis');
insert into public.profiles(id,role,family_id,display_name) values
('00000000-0000-0000-0000-000000000007','family',4,'Cuatro'),
('00000000-0000-0000-0000-000000000008','family',5,'Cinco'),
('00000000-0000-0000-0000-000000000009','family',6,'Seis'),
('00000000-0000-0000-0000-000000000010','family',1,'Otra cuenta del mismo voto');
insert into storage.objects(bucket_id,name) values('receipts','00000000-0000-0000-0000-000000000005/proposal.png');
insert into public.cleaning_turns(id,family_id,turn_date,checklist,created_by) values
('20000000-0000-0000-0000-000000000001',1,'2030-01-07','["Baño"]','00000000-0000-0000-0000-000000000001'),
('20000000-0000-0000-0000-000000000002',3,'2030-01-14','["Baño"]','00000000-0000-0000-0000-000000000001'),
('20000000-0000-0000-0000-000000000003',4,'2030-01-21','["Baño"]','00000000-0000-0000-0000-000000000001'),
('20000000-0000-0000-0000-000000000004',2,'2030-01-28','["Baño"]','00000000-0000-0000-0000-000000000001');
set role authenticated;
select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000005',false);
select test.check(public.casa_is_family_member(),'Family membership is verified from profile');
select public.create_proposal('Propuesta familiar',100,'Arreglo de prueba','00000000-0000-0000-0000-000000000005/proposal.png');
select test.check((select count(*)=1 from public.proposals),'Family can create and read proposal');
select test.expect_error($q$select public.create_proposal('',100,'Justificación',null)$q$,'22023');
select test.expect_error($q$select public.create_proposal('Prueba',-1,'Justificación',null)$q$,'22023');
select test.expect_error($q$select public.create_proposal('Prueba','NaN'::numeric,'Justificación',null)$q$,'22023');
select test.expect_error($q$select public.create_proposal('Prueba',100,'x',null)$q$,'22023');
select test.expect_error($q$select public.create_proposal('Prueba',100,'Justificación','private.png')$q$,'22023');
select test.expect_error($q$select public.create_proposal('Prueba',100,'Justificación','00000000-0000-0000-0000-000000000005/missing.png')$q$,'22023');
select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000002',false);
select test.check((select count(*)=1 from public.proposals),'Another family can read shared proposal');
select test.check((select count(*)=1 from storage.objects where name='00000000-0000-0000-0000-000000000005/proposal.png'),'Proposal photo is shared with voter');
select test.check((select count(*)=0 from storage.objects where name='private.png'),'Payment and unrelated photos stay private');
select public.cast_proposal_vote((select id from public.proposals limit 1),true);
select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000010',false);
select public.cast_proposal_vote((select id from public.proposals limit 1),false);
select test.check((select count(*)=1 and not bool_or(vote) from public.proposal_votes),'Two accounts for same accounting family share one vote');
select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000002',false);
select public.cast_proposal_vote((select id from public.proposals limit 1),true);
select test.expect_error($q$select public.cast_proposal_vote((select id from public.proposals limit 1),null)$q$,'22023');
select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000005',false);
select public.cast_proposal_vote((select id from public.proposals limit 1),true);
select test.check((select count(*)=1 and min(family_id)=2 from public.proposal_votes),'Family can read only its own accounting vote');
select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000006',false);
select public.cast_proposal_vote((select id from public.proposals limit 1),true);
select test.check((select bool_and(status='open') from public.proposals),'Three yes votes do not close proposal');
select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000007',false);
select public.cast_proposal_vote((select id from public.proposals limit 1),true);
select test.check((select bool_and(status='approved') from public.proposals),'Four yes votes approve with existing threshold');
select test.check((select yes_count=4 and no_count=0 from public.casa_proposal_totals() limit 1),'Aggregate totals are correct without voter identities');
select test.expect_error($q$select public.cast_proposal_vote((select id from public.proposals limit 1),false)$q$,'22023');
select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000001',false);
select test.expect_error($q$select public.cast_proposal_vote((select id from public.proposals limit 1),true)$q$,'42501');
select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000004',false);
select test.expect_error($q$select public.create_proposal('Fuera',100,'Justificación',null)$q$,'42501');
select test.expect_error($q$select * from public.casa_proposal_totals()$q$,'42501');
select test.check((select count(*)=0 from public.proposals),'Restaurant cannot read proposals');
select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000005',false);
select test.check((select count(*)>0 from public.casa_cleaning_swap_targets('20000000-0000-0000-0000-000000000001')),'Shared-household account can request targets');
select test.check((select count(*)=0 from public.casa_cleaning_swap_targets('20000000-0000-0000-0000-000000000001') t where family_id in (1,2)),'Targets exclude same canonical household');
select test.check((select bool_and(not (to_jsonb(t)?'note') and not (to_jsonb(t)?'completion_receipt_path')) from public.casa_cleaning_swap_targets('20000000-0000-0000-0000-000000000001') t),'Target RPC excludes private notes and photos');
select test.expect_error($q$select public.request_cleaning_swap('20000000-0000-0000-0000-000000000001','20000000-0000-0000-0000-000000000004')$q$,'22023');
select public.request_cleaning_swap('20000000-0000-0000-0000-000000000001','20000000-0000-0000-0000-000000000002');
select test.check((select count(*)=1 from public.cleaning_swap_requests),'Requester sees its request');
select test.expect_error($q$select public.request_cleaning_swap('20000000-0000-0000-0000-000000000001','20000000-0000-0000-0000-000000000002')$q$,'22023');
select test.expect_error($q$select public.review_cleaning_swap((select id from public.cleaning_swap_requests limit 1),true)$q$,'42501');
select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000007',false);
select test.check((select count(*)=0 from public.cleaning_swap_requests),'Unrelated family cannot see requests');
select test.expect_error($q$select public.request_cleaning_swap('20000000-0000-0000-0000-000000000001','20000000-0000-0000-0000-000000000003')$q$,'42501');
select test.expect_error($q$select * from public.casa_cleaning_swap_targets('20000000-0000-0000-0000-000000000001')$q$,'42501');
select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000006',false);
select test.check((select count(*)=1 from public.cleaning_swap_requests),'Recipient can see request');
select public.review_cleaning_swap((select id from public.cleaning_swap_requests limit 1),true);
select test.check((select status='accepted' from public.cleaning_swap_requests limit 1),'Recipient accepts request');
select test.check((select family_id=3 from public.cleaning_turns where id='20000000-0000-0000-0000-000000000001'),'Turn one transfers to recipient household');
select test.expect_error($q$select public.review_cleaning_swap((select id from public.cleaning_swap_requests limit 1),true)$q$,'22023');
select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000005',false);
select test.check((select family_id=1 from public.cleaning_turns where id='20000000-0000-0000-0000-000000000002'),'Shared account sees the exchanged own turn');
select public.request_cleaning_swap('20000000-0000-0000-0000-000000000002','20000000-0000-0000-0000-000000000003');
select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000007',false);
select public.review_cleaning_swap((select id from public.cleaning_swap_requests where status='pending' limit 1),false);
select test.check((select count(*)=1 from public.cleaning_swap_requests where status='rejected'),'Recipient can reject without exchanging');
reset role;
select test.check((select family_id=1 from public.cleaning_turns where id='20000000-0000-0000-0000-000000000002'),'Rejected request preserves assignment');
set role anon;
select test.expect_error($q$select * from public.casa_proposal_totals()$q$,'42501');
select test.expect_error($q$select * from public.casa_cleaning_swap_targets(null)$q$,'42501');
select test.expect_error($q$select public.create_proposal('No',1,'Nada',null)$q$,'42501');
reset role;
select test.check((select count(*)=1 and min(amount)=123.45 and min(family_id)=1 from public.payments),'Community migration preserves financial data');
select json_build_object('nativeCommunityChecks',count(*),'result','PASS') from test.results;
