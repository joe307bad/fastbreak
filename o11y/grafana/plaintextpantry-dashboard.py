#!/usr/bin/env python3
"""Provision the "Plaintext Pantry" usage dashboard.

    cd o11y && python3 grafana/plaintextpantry-dashboard.py

Idempotent, like its sibling `topspin-dashboard.py`: it overwrites the
dashboard at uid `plaintextpantry-usage` every run, so this file is the
definition and Grafana holds a copy rather than the other way round. Edit
here, re-run, and anything changed by hand in the UI is replaced.

The rows come from plaintextpantry.com, whose F# server writes them to the
`/write` endpoint on this host in line protocol - see
`app/src/Server/Usage.fs` in that repository, which also says why the browser
does not write here directly. Three tables, created by `CREATE TABLE IF NOT
EXISTS` below so the panels read zero rather than an error before the first
row arrives:

    ptp_pageview   env, section, value, timestamp
    ptp_login      env, provider, value, timestamp
    ptp_write      env, source, resource, action, value, timestamp

Every row carries a count in `value` rather than standing for one event -
saving a recipe writes one row for its dozen shopping items - so the panels
sum that column instead of counting rows.

Seven charts: the login page and the pages behind it, sign-ins, recipes made,
pantries shared, what an assistant changed through the MCP server, and what
the app itself changed. They are drawn like the other dashboards on this
Grafana rather than to their own taste - see `build` below.
"""

from __future__ import annotations

import base64
import json
import os
import sys
import urllib.error
import urllib.request

HOST = os.environ.get("O11Y_HOST", "https://fastbreak-o11y.fly.dev")
DASHBOARD_UID = "plaintextpantry-usage"

# The QuestDB datasource this Grafana already has. Looked up by type rather than
# hardcoded, so a rebuilt datasource with a new uid needs no edit here.
DATASOURCE_TYPE = "questdb-questdb-datasource"

# title, table, an extra WHERE clause, the sentence on the panel's tooltip, and
# a symbol column to draw one series per value of.
PANELS = [
    (
        "Login page",
        "ptp_pageview",
        "section = 'login'",
        "Times the signed-out screen was shown - every visit by someone with no session, not only the ones that went on to sign in.",
        None,
    ),
    (
        "Page visits",
        "ptp_pageview",
        None,
        "Every page the app opened, counted where the page changed. `recipe` is any one recipe's page.",
        "section",
    ),
    (
        "Logins",
        "ptp_login",
        None,
        "Sessions begun, counted server-side in the OIDC callback. A session that renews itself daily for a year is one login.",
        None,
    ),
    (
        "Recipes created",
        "ptp_write",
        "resource = 'recipes' AND action = 'create'",
        "New recipes, whether typed into the app or written by an assistant through the MCP server.",
        None,
    ),
    (
        "Pantries shared",
        "ptp_write",
        "resource = 'pantry_members'",
        "`create` is somebody scanning a pantry's code and asking to join; `approve` is the owner letting them in, which is the share actually happening.",
        "action",
    ),
    (
        "MCP mutations",
        "ptp_write",
        "source = 'mcp'",
        "Tool calls that changed something, by the area they changed. Reads are not counted: a `list_` or `get_` tool writes nothing, so counting them would measure how often a model looked rather than how often it acted.",
        "resource",
    ),
    (
        "Changes from the app",
        "ptp_write",
        "source = 'app'",
        "Everything the app itself wrote, by what it changed - the other half of the MCP panel above. Counted as the database took them, so a refused write is not in here.",
        "resource",
    ),
]

TABLES = [
    "CREATE TABLE IF NOT EXISTS ptp_pageview "
    "(env SYMBOL, section SYMBOL, value LONG, timestamp TIMESTAMP) "
    "TIMESTAMP(timestamp) PARTITION BY DAY",
    "CREATE TABLE IF NOT EXISTS ptp_login "
    "(env SYMBOL, provider SYMBOL, value LONG, timestamp TIMESTAMP) "
    "TIMESTAMP(timestamp) PARTITION BY DAY",
    "CREATE TABLE IF NOT EXISTS ptp_write "
    "(env SYMBOL, source SYMBOL, resource SYMBOL, action SYMBOL, value LONG, timestamp TIMESTAMP) "
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
    measure = "changes" if table == "ptp_write" else "visits" if table == "ptp_pageview" else "logins"
    return (
        # SUM, not COUNT: one row can stand for several changes (see the
        # module docstring), and for the tables where it never does, the
        # value is 1 and the two agree.
        f"SELECT\n{selected}  SUM(value) AS {measure}\n"
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
                # Two across; seven panels, so the app's own writes sit alone
                # on the last row under the MCP panel they are the other half of.
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
                        # Drawn the way the Fastbreak and Topspin dashboards
                        # next door draw theirs: a thin unfilled line, linear
                        # between points, classic palette. Three dashboards on
                        # one Grafana that look like three products is a worse
                        # result than any of the styles on its own.
                        "custom": {
                            "drawStyle": "line",
                            "lineWidth": 1,
                            "lineInterpolation": "linear",
                            "fillOpacity": 0,
                            "gradientMode": "none",
                            "showPoints": "auto",
                            "pointSize": 5,
                            "spanNulls": False,
                            "barAlignment": 0,
                            "axisSoftMin": 0,
                            "stacking": {"group": "A", "mode": "none"},
                        },
                        "unit": "short",
                        "decimals": 0,
                        "min": 0,
                        "color": {"mode": "palette-classic"},
                    },
                    "overrides": [],
                },
                "options": {
                    # The same deliberate difference from Fastbreak's panels
                    # that Topspin's make: the question here is "how many", so
                    # each series carries its own total for the window. The
                    # line is how it arrived; the total is the answer.
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
        "title": "Plaintext Pantry",
        "tags": ["plaintextpantry"],
        "timezone": "browser",
        "schemaVersion": 39,
        "refresh": "5m",
        "time": {"from": "now-30d", "to": "now"},
        "editable": True,
        "description": (
            "Usage counters for plaintextpantry.com. Pages are counted by the app "
            "where the page changes and written through its own server; sign-ins, "
            "recipes, shares and MCP calls are counted server-side where they "
            "happen. Totals, not unique users - nothing here says who did any of "
            "it. Defined by o11y/grafana/plaintextpantry-dashboard.py."
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
    {"dashboard": build(uid), "overwrite": True, "message": "Plaintext Pantry usage counters"},
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
    print(f"  {panel['title']:<22} {outcome['status']} {'' if ok else outcome.get('error', '')}")

sys.exit(1 if failures else 0)
