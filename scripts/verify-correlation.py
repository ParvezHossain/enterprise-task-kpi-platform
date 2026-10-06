#!/usr/bin/env python3
"""Check the last real create request appears in Task and eventual KPI JSON logs."""
import json
from pathlib import Path
import subprocess

identifier = json.loads(Path('.local/last-workflow.json').read_text())['requestId']
compose = ['docker', 'compose', '--env-file', '.local/stack.env', '-f', 'enterprise-platform/docker-compose.yml']
for service in ('task-management', 'kpi-service'):
    logs = subprocess.check_output(compose + ['logs', '--no-log-prefix', service], text=True)
    matches = []
    for line in logs.splitlines():
        try:
            event = json.loads(line)
            if event.get('requestId') == identifier:
                matches.append(event)
        except ValueError:
            pass
    if not matches:
        raise SystemExit('Correlation missing from ' + service)
    print(service + ': ' + str(len(matches)) + ' matching JSON events; requestId=' + identifier)
