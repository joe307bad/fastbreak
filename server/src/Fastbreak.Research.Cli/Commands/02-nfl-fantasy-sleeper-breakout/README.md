# NFL Fantasy Sleeper Breakout Prediction

This command provides data verification utilities for NFL fantasy football sleeper breakout analysis.

## Prerequisites

Configure the following environment variables (can be set in `.env` file):

- `WEEKLY_PLAYER_STATS_DATA_FOLDER` - Directory containing weekly player stats CSV files

To generate the data for the prerequisites look at `pipeline/02-nfl-fantasy-sleeper-breakout/README.md`

## Commands

### Verify Data Sources

Verify that all required data sources are accessible and properly configured:

```bash
dotnet run 02-nfl-fantasy-breakout verify-data
```
