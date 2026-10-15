-- Solo para PostgreSQL desechable de test_backend.py; nunca ejecutar en Supabase.
create role anon;
create role authenticated;
create schema auth;
create schema storage;
create schema test;
create function auth.uid() returns uuid language sql stable as $$
  select nullif(current_setting('request.jwt.claim.sub', true), '')::uuid;
$$;
grant usage on schema auth, storage, test to anon, authenticated;
grant execute on function auth.uid() to anon, authenticated;
create table public.families (id bigint primary key, name text not null);
create table public.profiles (id uuid primary key, role text, family_id bigint references public.families,
  second_family_id bigint references public.families, display_name text, contact_email text);
create table public.payments (id bigint primary key, family_id bigint references public.families, amount numeric);
create table public.payment_qr_settings (id smallint primary key, qr_image_path text,
  qr_title text, qr_note text, updated_by uuid references public.profiles, updated_at timestamptz);
create table test.qr_audit (action text, entity text, entity_id text, details jsonb);
create function public.audit(p_action text, p_entity text, p_entity_id text, p_details jsonb)
returns void language sql as $$
  insert into test.qr_audit values (p_action, p_entity, p_entity_id, p_details);
$$;
create table storage.buckets (id text primary key, name text not null, public boolean,
  file_size_limit bigint, allowed_mime_types text[]);
create table storage.objects (id bigint generated always as identity primary key,
  bucket_id text references storage.buckets, name text);
alter table public.profiles enable row level security;
create policy own_profile on public.profiles for select to authenticated using (id = auth.uid());
alter table storage.objects enable row level security;
grant select on public.profiles, public.families to authenticated;
grant select, insert, update, delete on storage.objects to anon, authenticated;
grant usage on all sequences in schema storage to anon, authenticated;
insert into public.families values (1, 'Familia Uno'), (2, 'Familia Dos'), (3, 'Familia Tres');
insert into public.profiles values
 ('00000000-0000-0000-0000-000000000001', 'sysadmin', null, null, 'Admin', 'admin@example.test'),
 ('00000000-0000-0000-0000-000000000002', 'family', 1, null, 'Familia', 'familia@example.test'),
 ('00000000-0000-0000-0000-000000000003', 'payer', 1, 2, 'Encargado', 'encargado@example.test'),
 ('00000000-0000-0000-0000-000000000004', 'restaurant', null, null, 'Restaurante', 'restaurante@example.test');
insert into public.payments values (1, 1, 123.45);
insert into storage.buckets values ('receipts', 'receipts', false, null, null);
insert into storage.objects (bucket_id, name) values ('receipts', 'private.png');
create table test.results (label text);
grant insert, select on test.results to anon, authenticated;
create function test.check(ok boolean, label text) returns void language plpgsql as $$
begin
  if ok is distinct from true then raise exception 'FAIL: %', label; end if;
  insert into test.results values (label);
end;
$$;
create function test.expect_error(statement text, expected text) returns void language plpgsql as $$
begin
  begin
    execute statement;
  exception when others then
    if sqlstate <> expected then raise exception 'Wrong SQLSTATE: %, expected %: %', sqlstate, expected, sqlerrm; end if;
    insert into test.results values ('Denied: ' || statement); return;
  end;
  raise exception 'Unexpectedly allowed: %', statement;
end;
$$;

-- Fixtures de la rotación semanal, sin credenciales ni servicios reales.
create table public.cleaning_turns (
  id uuid primary key default gen_random_uuid(), family_id smallint references public.families,
  turn_date date not null, status text not null default 'pending', checklist jsonb not null default '[]',
  note text, completion_receipt_path text, completed_at timestamptz, created_by uuid not null references public.profiles, created_at timestamptz not null default now()
);
create table test.cleaning_notifications (family_id smallint, turn_id uuid, dedupe_key text);
create function public.notify_family(p_family smallint,p_kind text,p_title text,p_body text,
  p_ref_entity text default null,p_ref_id uuid default null,p_dedupe text default null)
returns void language sql as $$
  insert into test.cleaning_notifications values(p_family,p_ref_id,p_dedupe);
$$;

create table test.cleaning_completions (turn_id uuid);
create function public.notify_all(p_kind text,p_title text,p_body text,p_ref_entity text default null,p_ref_id uuid default null,p_dedupe text default null)
returns void language sql as $$ insert into test.cleaning_completions values(p_ref_id); $$;

-- Native family community interactions; all data stays in the disposable container.
create table public.proposals(id uuid primary key default gen_random_uuid(),concept text,estimated_amount numeric,justification text,receipt_path text,status text not null default 'open',created_by uuid,created_at timestamptz default now());
create table public.proposal_votes(id uuid primary key default gen_random_uuid(),proposal_id uuid references public.proposals,family_id smallint references public.families,vote boolean,created_by uuid,created_at timestamptz default now(),unique(proposal_id,family_id));
create table public.cleaning_swap_requests(id uuid primary key default gen_random_uuid(),requester_turn_id uuid references public.cleaning_turns,target_turn_id uuid references public.cleaning_turns,requester_family_id smallint,target_family_id smallint,status text not null default 'pending',created_by uuid,reviewed_by uuid,reviewed_at timestamptz,created_at timestamptz default now());
alter table public.proposals enable row level security;
alter table public.proposal_votes enable row level security;
alter table public.cleaning_swap_requests enable row level security;
grant select on public.proposals,public.proposal_votes,public.cleaning_swap_requests to authenticated;
