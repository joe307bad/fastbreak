### Step 1: Defense Power Rankings
```bash
Rscript scripts/defensive_stats.R [year] [output_file]
```
- Points allowed per game
- Rushing yards allowed per game
- Passing yards allowed per game
- Turnovers per game
- Total yards allowed per game (rushing + passing)

### Step 2: Second-Year Sleepers Identification
```bash
Rscript scripts/second_year_sleepers.R [year] [output_file]
```
- use library(fantasypros)
- Get players with ADP (Average Draft Position) > 100
- Only get players who are in their second year