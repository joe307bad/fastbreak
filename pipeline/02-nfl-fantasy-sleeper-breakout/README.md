# NFL Fantasy Sleeper Breakout Scripts

This directory contains R scripts for analyzing NFL fantasy football breakout candidates, specifically focusing on second-year players.

## Scripts

### `collect_second_year_data.R`

Collects and processes comprehensive data for second-year NFL players (RB, WR, TE) to identify potential fantasy breakout candidates.

**Usage:**
```bash
# Process all weeks (3-18) for a season
Rscript collect_second_year_data.R [year] [output_directory]

# Process a specific week only
Rscript collect_second_year_data.R [year] [output_directory] [week]
```

**Examples:**
```bash
# Process entire 2024 season
Rscript collect_second_year_data.R 2024 ./data/

# Process only week 5 of 2024 season
Rscript collect_second_year_data.R 2024 ./data/ w5
```

---

### `defensive_stats.R`

Generates defensive power rankings for NFL teams based on points allowed per game, with additional metrics for rush and pass defense.

**Usage:**
```bash
Rscript defensive_stats.R [year] [output_file]
```

**Example:**
```bash
Rscript defensive_stats.R 2023 defense_rankings_2023.csv
```

---

## Order of Execution and Usage

### Building Historical Data for Model Training

If you are building the data from scratch to calculate sleeper scores enhanced with ML, you need to first pull as much historical data as possible.

**Step 1: Collect historical player data**

Pull all the data needed for 2024 by running:

```bash
Rscript scripts/collect_second_year_data.R 2024 ./output/weekly_player_stats/
```

This generates historical data for the entire 2024 season (weeks 3-18).

**Step 2: Generate defensive power rankings**

The strength of the opposing defense is an important factor in determining if a player will have a breakout performance. To generate defensive power rankings, run:

```bash
Rscript scripts/defensive_stats.R 2024 ./output/defensive_stats/defense_rankings_2024.csv
```

**Step 3: Train the model**

With this data, you have the necessary information to train a model to predict sleeper breakouts. The model training code is located at `server/src/Fastbreak.Research.Cli/Commands/02-nfl-fantasy-sleeper-breakout`.

### Making Future Predictions

Once the model is trained, you'll need to generate fresh data for upcoming weeks to make actual predictions.

**Pull data for a specific upcoming week:**

```bash
Rscript scripts/collect_second_year_data.R 2024 ./output/weekly_player_stats/ w3
```

This command pulls Week 3 of 2024. If the week hasn't occurred yet, the output will **not** include two columns:
- `current_week_fp`: Current week fantasy points earned
- `fp_week_delta`: Fantasy points delta between the current week and previous week

If the week has already occurred, these columns will be included in the output.

Using this data, you can then use the model to add a confidence score to each player that gauges the likelihood of a breakout performance (+5 points or greater).