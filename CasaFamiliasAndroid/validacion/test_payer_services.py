"""Valida permisos y persistencia en un PostgreSQL desechable, sin usar Supabase real.
Requiere Docker. No publica puertos; elimina el contenedor y su volumen al terminar.
"""
from pathlib import Path
import subprocess
import time
import uuid

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
CONTAINER = "casa-payer-test-" + uuid.uuid4().hex[:10]
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
    sql(HERE / "payer_services_original_rpc.sql")
    sql(HERE / "payer_services_before.sql")
    migration = ROOT / "supabase/migrations/202610180007_payer_services.sql"
    for _ in range(2):
        sql(migration)
    output = sql(HERE / "payer_services_checks.sql")
    print(next(line.strip() for line in output.splitlines() if '"payerServicesChecks"' in line))

finally:
    subprocess.run(["docker", "rm", "-fv", CONTAINER], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
