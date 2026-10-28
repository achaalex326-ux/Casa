"""Valida permisos y persistencia en un PostgreSQL desechable, sin usar Supabase real.
Requiere Docker. No publica puertos; elimina el contenedor y su volumen al terminar.
"""
from pathlib import Path
import subprocess
import time
import uuid

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
CONTAINER = "casa-invoice-test-" + uuid.uuid4().hex[:10]
IMAGE = "postgres:17-alpine@sha256:b0f9560a2de083e2cc7382e75f808c7381a32852a7ec49117deedb300e552b24"


def run(*args, data=None):
    result = subprocess.run(args, input=data, text=True, capture_output=True)
    if result.returncode:
        raise RuntimeError(result.stderr or result.stdout)
    return result.stdout


def sql(path):
    return run(
        "docker", "exec", "-i", CONTAINER, "psql", "-X", "-v", "ON_ERROR_STOP=1", "-U", "postgres", "-q", data=path.read_text())


try:
    run("docker", "run", "--detach", "--name", CONTAINER, "--network", "none",
        "-e", "POSTGRES_HOST_AUTH_METHOD=trust", IMAGE)
    for attempt in range(60):
        ready = subprocess.run(["docker", "exec", CONTAINER, "pg_isready", "-U", "postgres"], capture_output=True)
        process = subprocess.run(["docker", "exec", CONTAINER, "cat", "/proc/1/comm"], capture_output=True, text=True)
        # El servidor temporal de initdb también responde a pg_isready; esperar al proceso final.
        if ready.returncode == 0 and process.stdout.strip() == "postgres":
            break
        time.sleep(0.5)
    else:
        raise RuntimeError("PostgreSQL no inició: " + run("docker", "logs", CONTAINER))
    sql(HERE / "payer_services_fixture.sql")
    sql(HERE / "payer_services_seed.sql")
    # Changing the rule must not change existing shares, invoices or payments.
    for _ in range(2):
        sql(ROOT / "supabase/migrations/202610280010_invoice_total_services.sql")
    unchanged = run("docker", "exec", "-i", CONTAINER, "psql", "-X", "-v", "ON_ERROR_STOP=1", "-U", "postgres", "-qtA", data="select not exists((select * from test.original_payments except select * from payments) union all (select * from payments except select * from test.original_payments));")
    assert unchanged.strip() == 't'
    output = sql(HERE / "invoice_total_checks.sql")
    print(next(line.strip() for line in output.splitlines() if '\"invoiceTotalChecks\"' in line))
    # Reproduce an already-saved, understated January and keep real payment data.
    run("docker","exec","-i",CONTAINER,"psql","-X","-v","ON_ERROR_STOP=1","-U","postgres","-q",data="""
      create table public.payment_batches(id uuid primary key);
      update monthly_basic_services set family_total_amount=310.28,calculation_method='historical_average',gas_amount=9 where month_start='2026-01-01';
      update expenses set total_amount=310.28 where id=(select expense_id from monthly_basic_services where month_start='2026-01-01');
      update expense_shares set amount_due=case when family_id=6 then 51.73 else 51.71 end where expense_id=(select expense_id from monthly_basic_services where month_start='2026-01-01');
      insert into payments(share_id,family_id,amount,applied_amount,paid_at,status,kind,note,created_by)
      select s.id,3,20,20,'2026-01-20','approved','manual','Real payment fixture, preserve exactly','00000000-0000-0000-0000-000000000001' from expense_shares s join monthly_basic_services m on m.expense_id=s.expense_id where m.month_start='2026-01-01' and s.family_id=3;
      create table test.before_repair_payments as select * from payments;
      select set_config('request.jwt.claim.sub','',false);
    """)
    for _ in range(2):
        sql(ROOT / "supabase/repair_worksheet_2026.sql")
    checks = run("docker","exec","-i",CONTAINER,"psql","-X","-v","ON_ERROR_STOP=1","-U","postgres","-qtA",data="""
      select family_total_amount=396.50 and gas_amount=0 from monthly_basic_services where month_start='2026-01-01';
      select not exists((select * from test.before_repair_payments except select * from payments));
      select count(*)=1 from casa_finance_repairs;
      select s.amount_due-public.share_approved_paid(s.id)=46.08 from expense_shares s join monthly_basic_services m on m.expense_id=s.expense_id where m.month_start='2026-01-01' and s.family_id=3;
      select sum(family_total_amount)=2250.50 from monthly_basic_services where month_start between '2026-01-01' and '2026-06-01';
      select not has_table_privilege('authenticated','casa_finance_repairs','select');
      select not has_table_privilege('anon','casa_finance_repairs','select');
    """)
    assert checks.strip().splitlines()==['t']*7, checks
    print('{"repairChecks":7,"idempotent":true,"existingPaymentsPreserved":true,"status":"PASS"}')
    # A conflicting historical overpayment must roll back every edit and backup.
    statement = """
      delete from casa_finance_repairs;
      insert into payments(share_id,family_id,amount,applied_amount,paid_at,status,kind,created_by)
      select s.id,3,100,100,'2026-01-21','approved','manual','00000000-0000-0000-0000-000000000001' from expense_shares s join monthly_basic_services m on m.expense_id=s.expense_id where m.month_start='2026-01-01' and s.family_id=3;
    """
    run("docker","exec","-i",CONTAINER,"psql","-X","-v","ON_ERROR_STOP=1","-U","postgres","-qtA",data=statement)
    try:
        sql(ROOT / "supabase/repair_worksheet_2026.sql")
        raise AssertionError('Overpayment was silently overwritten')
    except RuntimeError as error:
        assert 'conciliar sin perder el saldo a favor' in str(error), error
    safe = run("docker","exec","-i",CONTAINER,"psql","-X","-v","ON_ERROR_STOP=1","-U","postgres","-qtA",data="select count(*)=0 from casa_finance_repairs; select sum(amount)=120 from payments where family_id=3 and paid_at between '2026-01-20' and '2026-01-21';")
    assert safe.strip().splitlines()==['t','t'], safe
    print('{"repairRollbackChecks":2,"status":"PASS"}')

finally:
    subprocess.run(["docker", "rm", "-fv", CONTAINER], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
