#!/usr/bin/env python3
"""Provision the "Topspin" usage dashboard.

    cd o11y && python3 grafana/topspin-dashboard.py

Idempotent: it overwrites the dashboard at uid `topspin-usage` every run, so this
file is the definition and Grafana holds a copy rather than the other way round.
Edit here, re-run, and anything changed by hand in the UI is replaced.

The rows come from topspin.blog, which counts its own page views and writes them
through its backend to the `/write` endpoint on this host — see
`app/src/backend/lib/usage-metrics.ts` in that repository for why the browser
does not write here directly. Two tables, created by
`CREATE TABLE IF NOT EXISTS` below so the dashboard reads zero rather than an
error before the first visit arrives:

    topspin_pageview   env, section, value, timestamp
    topspin_login      env, provider, value, timestamp
    topspin_mcp        env, tool, resource, value, timestamp

Nine charts: one per counted page section, one for logins, and two for the MCP
server — mutations broken down by what they changed, and by which tool did it.
"""

from __future__ import annotations

import base64
import json
import os
import sys
import urllib.error
import urllib.request

HOST = os.environ.get("O11Y_HOST", "https://fastbreak-o11y.fly.dev")
DASHBOARD_UID = "topspin-usage"

# The QuestDB datasource this Grafana already has. Looked up by type rather than
# hardcoded, so a rebuilt datasource with a new uid needs no edit here.
DATASOURCE_TYPE = "questdb-questdb-datasource"

# title, table, an extra WHERE clause, the sentence shown on the panel's tooltip,
# and a symbol column to draw one series per value of. Adding a counted section
# is one line, plus the section name in topspin's own
# `shared/src/paths/usage-section.ts`.
PANELS = [
    ("Docs visits", "topspin_pageview", "section = 'docs'", "/docs/", None),
    ("Tool visits", "topspin_pageview", "section = 'tool'", "/tool/ and anything under it", None),
    ("Workspace visits", "topspin_pageview", "section = 'workspace'", "/workspace/", None),
    ("Dashboard visits", "topspin_pageview", "section = 'dashboard'", "/dashboard/", None),
    ("Query visits", "topspin_pageview", "section = 'query'", "/query/", None),
    ("Post visits", "topspin_pageview", "section = 'post'", "/post/", None),
    (
        "Logins",
        "topspin_login",
        None,
        "Completed sign-ins, counted server-side where the session cookie is set",
        None,
    ),
    # The MCP server, twice: once by what was changed and once by what did the
    # changing. Reads are not in this table at all — `run_query` and the `list_`
    # tools write nothing, so counting them would make these a measure of how
    # often a model looked rather than how often it acted.
    (
        "MCP mutations by resource",
        "topspin_mcp",
        None,
        "Tool calls that changed a workspace, query, post or dashboard. Reads are not counted.",
        "resource",
    ),
    (
        "MCP mutations by tool",
        "topspin_mcp",
        None,
        "The same calls, by which tool made them.",
        "tool",
    ),
]

TABLES = [
    "CREATE TABLE IF NOT EXISTS topspin_pageview "
    "(env SYMBOL, section SYMBOL, value LONG, timestamp TIMESTAMP) "
    "TIMESTAMP(timestamp) PARTITION BY DAY",
    "CREATE TABLE IF NOT EXISTS topspin_login "
    "(env SYMBOL, provider SYMBOL, value LONG, timestamp TIMESTAMP) "
    "TIMESTAMP(timestamp) PARTITION BY DAY",
    "CREATE TABLE IF NOT EXISTS topspin_mcp "
    "(env SYMBOL, tool SYMBOL, resource SYMBOL, value LONG, timestamp TIMESTAMP) "
    "TIMESTAMP(timestamp) PARTITION BY DAY",
]


def env_password() -> str:
    """The Grafana admin password, from the environment or from `.env` beside this."""
    if os.environ.get("GF_SECURITY_ADMIN_PASSWORD"):
        return os.environ["GF_SECURITY_ADMIN_PASSWORD"]

    path = os.path.join(os.path.dirname(__file__), "..", ".env")
    try:
        with open(path) as handle:
            for line in handle:
                key, _, value = line.partition("=")
                if key.strip() == "GF_SECURITY_ADMIN_PASSWORD":
                    return value.strip().strip("'\"")
    except FileNotFoundError:
        pass

    sys.exit("No GF_SECURITY_ADMIN_PASSWORD in the environment or in o11y/.env")


def call(path: str, payload=None, method="GET"):
    request = urllib.request.Request(
        f"{HOST}{path}",
        data=None if payload is None else json.dumps(payload).encode(),
        headers={
            "Content-Type": "application/json",
            "Authorization": "Basic " + base64.b64encode(f"admin:{PASSWORD}".encode()).decode(),
        },
        method=method,
    )
    try:
        return json.load(urllib.request.urlopen(request))
    except urllib.error.HTTPError as error:
        sys.exit(f"{method} {path} failed: {error.code} {error.read().decode()[:300]}")


def run_sql(uid: str, statement: str) -> dict:
    """One statement through the datasource, which is the only SQL path we have.

    Caddy's `/exec` is behind a basic-auth password this script does not hold;
    Grafana's own credentials are the ones in `.env`, and its datasource speaks
    to QuestDB over the Postgres wire regardless.
    """
    body = {
        "queries": [
            {
                "refId": "A",
                "datasource": {"type": DATASOURCE_TYPE, "uid": uid},
                "queryType": "sql",
                "rawSql": statement,
                "format": 0,
                "selectedFormat": 2,
            }
        ],
        "from": "now-30d",
        "to": "now",
    }
    return call("/grafana/api/ds/query", body, "POST")["results"]["A"]


def sql_for(table: str, where: str | None, group: str | None) -> str:
    clauses = ["$__timeFilter(timestamp)", "env = 'prod'"]
    if where:
        clauses.append(where)
    # A symbol in the select beside the timestamp becomes a series per value:
    # QuestDB groups a SAMPLE BY on it, and the plugin reads the string column as
    # the series name. Nothing else about the query changes.
    selected = "  timestamp AS time,\n" + (f"  {group},\n" if group else "")
    measure = "mutations" if table == "topspin_mcp" else "visits"
    return (
        f"SELECT\n{selected}  COUNT(*) AS {measure}\n"
        f"FROM {table}\nWHERE " + "\n  AND ".join(clauses) +
        # FILL before ALIGN TO. The other order parses in most SQL dialects and
        # is rejected by QuestDB with "unexpected token [FILL]", which reaches a
        # reader as a broken panel rather than a syntax error.
        "\nSAMPLE BY $__sampleByInterval FILL(0) ALIGN TO CALENDAR"
    )


def build(uid: str) -> dict:
    datasource = {"type": DATASOURCE_TYPE, "uid": uid}
    panels = []

    for index, (title, table, where, description, group) in enumerate(PANELS):
        panels.append(
            {
                "id": index + 1,
                "type": "timeseries",
                "title": title,
                "description": description,
                "datasource": datasource,
                # Two across. Seven panels, so logins sit alone on the last row,
                # which suits the one number here that is not a page view.
                "gridPos": {"h": 8, "w": 12, "x": (index % 2) * 12, "y": (index // 2) * 8},
                "targets": [
                    {
                        "refId": "A",
                        "datasource": datasource,
                        "queryType": "sql",
                        "rawSql": sql_for(table, where, group),
                        "format": 0,
                        "selectedFormat": 2,
                    }
                ],
                "fieldConfig": {
                    "defaults": {
                        "custom": {
                            "drawStyle": "bars",
                            "fillOpacity": 70,
                            "lineWidth": 0,
                            "barAlignment": 0,
                            "axisSoftMin": 0,
                            **({"stacking": {"mode": "normal", "group": "A"}} if group else {}),
                        },
                        "unit": "short",
                        "decimals": 0,
                        "min": 0,
                        # A grouped panel draws several series and has to colour
                        # them apart; a single-series one is one colour so the
                        # six page charts read as one family.
                        "color": (
                            {"mode": "palette-classic"}
                            if group
                            else {
                                "mode": "fixed",
                                "fixedColor": "blue" if table == "topspin_pageview" else "green",
                            }
                        ),
                    },
                    "overrides": [],
                },
                "options": {
                    # The total is the number being asked for; the bars are how
                    # it arrived. Both on screen at once.
                    "legend": {
                        # A grouped panel's legend is a table, so each series can
                        # carry its own total beside its name.
                        "displayMode": "table" if group else "list",
                        "placement": "bottom",
                        "showLegend": True,
                        "calcs": ["sum"],
                    },
                    "tooltip": {"mode": "multi" if group else "single", "sort": "desc" if group else "none"},
                },
            }
        )

    return {
        "uid": DASHBOARD_UID,
        "title": "Topspin",
        "tags": ["topspin"],
        "timezone": "browser",
        "schemaVersion": 39,
        "refresh": "5m",
        "time": {"from": "now-30d", "to": "now"},
        "editable": True,
        "description": (
            "Usage counters for topspin.blog. Page views are counted by the client "
            "on every route change and written through that app's own backend; "
            "logins are counted server-side where the session is created. Totals, "
            "not unique users. Defined by o11y/grafana/topspin-dashboard.py."
        ),
        "panels": panels,
    }


PASSWORD = env_password()

sources = [d for d in call("/grafana/api/datasources") if d["type"] == DATASOURCE_TYPE]
if not sources:
    sys.exit(f"No {DATASOURCE_TYPE} datasource on {HOST}")
uid = sources[0]["uid"]
print(f"datasource {uid}")

for statement in TABLES:
    result = run_sql(uid, statement)
    print(f"  {statement.split('(')[0].strip()}: {result['status']}")

result = call(
    "/grafana/api/dashboards/db",
    {"dashboard": build(uid), "overwrite": True, "message": "Topspin usage counters"},
    "POST",
)
print(f"dashboard {result['status']} v{result['version']} -> {HOST}{result['url']}")

# Every panel run against the real database, because a dashboard that saves is
# not a dashboard that draws: a query Grafana accepts can still be one QuestDB
# refuses, and the only place that shows up is the panel.
print("checking each panel's query")
failures = 0
for panel in build(uid)["panels"]:
    raw = (
        panel["targets"][0]["rawSql"]
        .replace("$__timeFilter(timestamp)", "timestamp > dateadd('d', -30, now())")
        .replace("$__sampleByInterval", "1h")
    )
    outcome = run_sql(uid, raw)
    ok = outcome["status"] == 200
    failures += 0 if ok else 1
    print(f"  {panel['title']:<20} {outcome['status']} {'' if ok else outcome.get('error', '')}")

sys.exit(1 if failures else 0)
