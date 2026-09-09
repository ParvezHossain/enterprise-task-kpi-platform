#!/usr/bin/env python3
"""Verify a locally built auth image with disposable PostgreSQL and signing keys."""
import argparse
import json
from pathlib import Path
import secrets
import subprocess
import tempfile
import time
import urllib.request
import uuid


def run(*args, input=None, check=True):
    result = subprocess.run(args, input=input, text=True, capture_output=True, timeout=180)
    if check and result.returncode:
        # Do not print commands, input or container logs: they may contain credentials.
        raise RuntimeError(f"{args[0]} operation failed (exit {result.returncode})")
    return result


def wait_for(description, check, seconds=180):
    deadline = time.monotonic() + seconds
    while time.monotonic() < deadline:
        if check():
            return
        time.sleep(2)
    raise RuntimeError(f"Timed out waiting for {description}")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--image", default="auth-server:local")
    args = parser.parse_args()
    suffix = uuid.uuid4().hex[:12]
    network, database, application = [f"auth-smoke-{kind}-{suffix}" for kind in ("net", "db", "app")]
    with tempfile.TemporaryDirectory(prefix="auth-container-smoke-") as temporary:
        root = Path(temporary)  # mode 0700 protects all temporary credentials on the host.
        keys = root / "keys"
        keys.mkdir(mode=0o755)
        private, public = keys / "private.pem", keys / "public.pem"
        run("openssl", "genpkey", "-algorithm", "RSA", "-pkeyopt", "rsa_keygen_bits:3072", "-out", str(private))
        run("openssl", "pkey", "-in", str(private), "-pubout", "-out", str(public))
        # Only this child directory is bind-mounted. UID 10001 must be able to read it.
        private.chmod(0o444)
        public.chmod(0o444)
        admin, migrator, runtime = [secrets.token_hex(24) for _ in range(3)]
        pg_env = root / "postgres.env"
        pg_env.write_text(f"POSTGRES_PASSWORD={admin}\n")
        pg_env.chmod(0o600)
        app_env = root / "auth.env"
        app_env.write_text(f"""SPRING_PROFILES_ACTIVE=docker,prod
AUTH_DB_URL=jdbc:postgresql://postgres:5432/auth_db
AUTH_DB_USERNAME=auth_app
AUTH_DB_PASSWORD={runtime}
AUTH_DB_MIGRATION_USERNAME=auth_migrator
AUTH_DB_MIGRATION_PASSWORD={migrator}
AUTH_ISSUER=http://localhost:9000
AUTH_RSA_PRIVATE_KEY=file:/run/auth-keys/private.pem
AUTH_RSA_PUBLIC_KEY=file:/run/auth-keys/public.pem
AUTH_RSA_KEY_ID=container-smoke
""")
        app_env.chmod(0o600)
        try:
            run("docker", "network", "create", network)
            run("docker", "run", "-d", "--rm", "--name", database, "--network", network,
                "--network-alias", "postgres", "--env-file", str(pg_env), "postgres:18.6-alpine")
            wait_for("PostgreSQL", lambda: run("docker", "exec", database, "pg_isready",
                                              "-h", "127.0.0.1", "-U", "postgres", check=False).returncode == 0)
            run("docker", "exec", "-i", database, "psql", "-U", "postgres", "-v", "ON_ERROR_STOP=1",
                input=f"""CREATE ROLE auth_migrator LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE PASSWORD '{migrator}';
CREATE ROLE auth_app LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE PASSWORD '{runtime}';
CREATE DATABASE auth_db OWNER auth_migrator;
REVOKE CONNECT ON DATABASE auth_db FROM PUBLIC;
GRANT CONNECT ON DATABASE auth_db TO auth_migrator, auth_app;
\\connect auth_db
REVOKE ALL ON SCHEMA public FROM PUBLIC;
GRANT USAGE ON SCHEMA public TO auth_app;
""")
            run("docker", "run", "-d", "--rm", "--name", application, "--network", network,
                "--env-file", str(app_env), "--mount", f"type=bind,src={keys},dst=/run/auth-keys,readonly",
                "--read-only", "--tmpfs", "/tmp:rw,noexec,nosuid,size=64m", "--cap-drop=ALL",
                "--security-opt=no-new-privileges", "-p", "127.0.0.1::9000", args.image)
            wait_for("Docker health check", lambda: run(
                "docker", "inspect", "--format", "{{.State.Health.Status}}", application).stdout.strip() == "healthy")
            assert run("docker", "exec", application, "id", "-u").stdout.strip() == "10001"
            run("docker", "exec", application, "sh", "-c", "! command -v javac && ! command -v mvn")
            port = json.loads(run("docker", "inspect", "--format",
                                 "{{json .NetworkSettings.Ports}}", application).stdout)["9000/tcp"][0]["HostPort"]
            with urllib.request.urlopen(f"http://127.0.0.1:{port}/actuator/health", timeout=10) as response:
                assert response.status == 200
                assert json.load(response)["status"] == "UP"
            owner = run("docker", "exec", database, "psql", "-U", "postgres", "-d", "auth_db", "-Atc",
                        "SELECT tableowner FROM pg_tables WHERE schemaname='public' "
                        "AND tablename='flyway_schema_history' LIMIT 1").stdout.strip()
            assert owner == "auth_migrator"
            print("PASS: Docker healthy; HTTP 200/UP; UID 10001; JRE-only; read-only root; Flyway owned by auth_migrator.")
        finally:
            for name in (application, database):
                run("docker", "rm", "-f", name, check=False)
            run("docker", "network", "rm", network, check=False)


if __name__ == "__main__":
    main()
