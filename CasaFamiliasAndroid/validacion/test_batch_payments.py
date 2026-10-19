"""Valida permisos y persistencia en un PostgreSQL desechable, sin usar Supabase real.
Requiere Docker. No publica puertos; elimina el contenedor y su volumen al terminar.
"""
from pathlib import Path
import subprocess
import time
import uuid

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
CONTAINER = "casa-batch-test-" + uuid.uuid4().hex[:10]
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
    sql(HERE / "batch_payments_fixture.sql")
    sql(HERE / "batch_payments_legacy.sql")
    for _ in range(2):
        sql(ROOT / "supabase/migrations/202610190009_multi_month_payments.sql")
    output=sql(HERE / "batch_payments_checks.sql")
    print(next(line.strip() for line in output.splitlines() if '"batchPaymentChecks"' in line))

    # Race real SQL transactions. No mock locks, network or production data.
    import concurrent.futures
    def statement(text):
        return run("docker","exec","-i",CONTAINER,"psql","-X","-v","ON_ERROR_STOP=1","-U","postgres","-qtA",data=text)
    family="00000000-0000-0000-0000-000000000002"
    staff="00000000-0000-0000-0000-000000000001"
    def auth(uid,body):
        return "begin;set local statement_timeout='10s';set local lock_timeout='8s';set local role authenticated;select set_config('request.jwt.claim.sub','"+uid+"',true);"+body+";commit;"
    def race(a,b):
        with concurrent.futures.ThreadPoolExecutor(max_workers=2) as ex:
            futures=[ex.submit(statement,x) for x in [a,b]]
            return [f.result() for f in futures]
    def reset_payments():
        statement("truncate public.payments,public.payment_batches;truncate public.family_credits;")
    def submit(batch,amount):
        return "select public.casa_record_payment_batch('"+batch+"',1::smallint,"+str(amount)+",'2030-03-20','cash',null,null,null,null)"
    a="50000000-0000-0000-0000-000000000001";b="50000000-0000-0000-0000-000000000002"
    reset_payments();statement(auth(family,submit(a,200)+";"+submit(b,100)))
    race(auth(staff,"select public.casa_review_payment_batch('"+a+"',true);select pg_sleep(0.15)"),auth(staff,"select public.casa_review_payment_batch('"+b+"',true)"))
    assert statement("select sum(amount)=300 and sum(applied_amount)=240 and sum(credit_amount)=60 from payments;").strip()=='t'
    assert statement("select balance=60 from family_credits where family_id=1;").strip()=='t'
    reset_payments();statement(auth(family,submit(a,200)))
    statement("insert into payments(id,share_id,family_id,amount,paid_at,kind,status,created_by) values('"+b+"','20000000-0000-0000-0000-000000000003',1,40,'2030-03-20','manual','pending','"+family+"');")
    race(auth(staff,"select public.review_payment('"+a+"',true);select pg_sleep(0.15)"),auth(staff,"select public.review_payment('"+b+"',true)"))
    assert statement("select sum(amount)=240 and sum(applied_amount)=240 and sum(credit_amount)=0 from payments;").strip()=='t'
    reset_payments();race(auth(family,submit(a,200)),auth(family,submit(a,200)))
    assert statement("select count(*)=1 and sum(amount)=200 from payment_batches;").strip()=='t'
    assert statement("select count(*)=1 and sum(amount)=200 from payments;").strip()=='t'
    print('{"batchConcurrencyScenarios":3,"result":"PASS"}')

finally:
    subprocess.run(["docker", "rm", "-fv", CONTAINER], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
