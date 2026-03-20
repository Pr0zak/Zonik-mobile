---
description: Fetch app logs from the Zonik server
allowed-tools: Bash, WebFetch
---

# Fetch App Logs

Retrieve debug logs uploaded from the Zonik mobile app to the server.

**Usage:** `/logs [id]` — list all logs, or fetch a specific log by ID

**Argument:** $ARGUMENTS

## Server

- Base URL: `http://zonik:3000`
- `GET /api/logs/app` — list all uploaded logs (returns `{"items": [...], "total": N}`)
- `GET /api/logs/app/{id}` — fetch a specific log entry with full log content
- These endpoints are unauthenticated.

## Steps

1. **Parse argument**: If an ID is provided, fetch that specific log. Otherwise, list all logs.

2. **List logs** (no argument):
   ```bash
   curl -s "http://zonik:3000/api/logs/app" -H "Accept: application/json"
   ```
   Display a table with: ID, device, app version, timestamp, log size.

3. **Fetch specific log** (ID provided):
   ```bash
   curl -s "http://zonik:3000/api/logs/app/{id}" -H "Accept: application/json"
   ```
   Display the log metadata (device, version, timestamp) and the log content.
   If the log content is very large, show only the last 100 lines by default and mention the total line count.

4. **Error handling**: If the server is unreachable or returns an error, report it clearly.
