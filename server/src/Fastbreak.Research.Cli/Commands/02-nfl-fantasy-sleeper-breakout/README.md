# NFL Fantasy Sleeper Breakout Prediction

ML model to predict second-year player fantasy breakouts using leave-one-out cross-validation.

## Setup

1. Generate historical data (see `pipeline/02-nfl-fantasy-sleeper-breakout/README.md`)
2. Set `WEEKLY_PLAYER_STATS_DATA_FOLDER` in `.env` file pointing to weekly CSV data

## Commands

### verify-data
Check data sources accessible:
```bash
dotnet run 02-nfl-fantasy-breakout verify-data
```

### train-and-evaluate-algorithms
Compare ML algorithms (LbfgsLogisticRegression vs SdcaLogisticRegression):
```bash
dotnet run 02-nfl-fantasy-breakout train-and-evaluate-algorithms
```

### predict-weekly-hits
Train model, predict hits for each week (leave-one-out cross-validation), generate output.json:
```bash
dotnet run 02-nfl-fantasy-breakout predict-weekly-hits --output path/to/output
```

Example to generate d3-charts/output.json:
```bash
dotnet run 02-nfl-fantasy-breakout predict-weekly-hits --output pipeline/02-nfl-fantasy-sleeper-breakout/d3-charts
```

### predict-new-week
Predict hits for new week using CSV:
```bash
dotnet run 02-nfl-fantasy-breakout predict-new-week --file week-data.csv --output output-dir
```

## How It Works

- **Leave-one-out cross-validation**: Each week predicted using model trained on all OTHER weeks (no data leakage)
- **Top 3/10 metrics**: Count actual hits among top 3/10 ML recommendations vs sleeper score rankings
- **Features**: 40+ including snap share, fantasy points, size scores, matchup data, sleeper score
