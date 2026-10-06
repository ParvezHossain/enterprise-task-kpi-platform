#!/usr/bin/env python3
"""Provision generated development data in a running local Compose stack."""
import argparse
import json
from pathlib import Path
import subprocess

parser = argparse.ArgumentParser()
parser.add_argument("--env-file", default=".local/stack.env")
parser.add_argument("--project-name", default="enterprise-platform")
parser.add_argument("--seed-directory")
args = parser.parse_args()
compose = ["docker", "compose", "--env-file", args.env_file, "-p", args.project_name, "-f",
           "enterprise-platform/docker-compose.yml"]
config = json.loads(subprocess.check_output(compose + ["config", "--format", "json"], text=True))
for name in ("auth-server", "task-management", "kpi-service"):
    profiles = config["services"][name]["environment"]["SPRING_PROFILES_ACTIVE"].split(",")
    if "dev" not in profiles or any(p in profiles for p in ("prod", "production")):
        raise SystemExit("Seeding requires explicit dev profiles and refuses production.")
states = [json.loads(line) for line in
          subprocess.check_output(compose + ["ps", "--format", "json"], text=True).splitlines() if line.strip()]
healthy = {state["Service"] for state in states if state.get("Health") == "healthy"}
if not {"auth-server", "task-management", "kpi-service"}.issubset(healthy):
    raise SystemExit("Wait for all application services to be healthy before seeding: docker compose ... up --wait.")
for database, filename in (("auth_db", "auth-seed.sql"), ("task_db", "task-seed.sql")):
    sql = Path(args.seed_directory or Path(args.env_file).parent).joinpath(filename).read_text()
    subprocess.run(
        compose + ["exec", "-T", "postgres", "psql", "-v", "ON_ERROR_STOP=1", "-U", "platform_operator", "-d",
                   database], input=sql, text=True, stdout=subprocess.DEVNULL, check=True)
print(
    "Development users, teams, projects and historical tasks provisioned. Credentials remain in the private users.json file.")
