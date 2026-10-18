"""Valida permisos y persistencia en un PostgreSQL desechable, sin usar Supabase real.
Requiere Docker. No publica puertos; elimina el contenedor y su volumen al terminar.
"""
from pathlib import Path
import subprocess
import time
import uuid

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
CONTAINER = "casa-backend-test-" + uuid.uuid4().hex[:10]
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
    sql(HERE / "backend_fixture.sql")
    for _ in range(2):
        # pg_cron se comprueba en Supabase; el conjunto financiero se ejecuta
        # con su propio esquema más abajo, en test_payer_services.py.
        for migration in sorted((ROOT / "supabase/migrations").glob("*.sql")):
            if migration.name.endswith(("_cron.sql", "_payer_services.sql")):
                continue
            sql(migration)
    output = sql(HERE / "backend_checks.sql")
    print(next(line.strip() for line in output.splitlines() if '"backendChecks"' in line))
    output = sql(HERE / 'rotation_checks.sql')
    print(next(line.strip() for line in output.splitlines() if '"rotationChecks"' in line))
    output = sql(HERE / 'shared_cleaning_checks.sql')
    print(next(line.strip() for line in output.splitlines() if '"sharedCleaningChecks"' in line))
    output = sql(HERE / 'native_community_checks.sql')
    print(next(line.strip() for line in output.splitlines() if '"nativeCommunityChecks"' in line))
finally:
    subprocess.run(["docker", "rm", "-fv", CONTAINER], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)

# Las cuotas de servicios necesitan su propio esquema contractual financiero.
# Este conjunto se ejecuta aparte en otro contenedor y no se omite.
import sys
subprocess.run([sys.executable, str(HERE / "test_payer_services.py")], check=True)
