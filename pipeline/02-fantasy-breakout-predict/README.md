# Fantasy Breakout Prediction Pipeline

This pipeline analyzes NFL data to identify potential fantasy football breakout candidates through defensive matchups and second-year player analysis.

## Scripts Execution Order

### Step 1: Install Required Packages
```bash
Rscript scripts/install_packages.R
```
Installs all required R packages including:
- nflfastR, dplyr, tidyr, readr
- gt, webshot2, scales
- ffanalytics, nflreadr, ffpros

### Step 2: Fetch ADP Data (Optional)
```bash
Rscript scripts/get_adp_data.R [year]
# Example:
Rscript scripts/get_adp_data.R 2024
```
- Fetches Average Draft Position (ADP) data for the specified year
- Attempts multiple data sources including nflreadr and public CSV endpoints
- Outputs: `adp_data_[year].csv`

### Step 3: Defense Power Rankings
```bash
Rscript scripts/defensive_stats.R [year] [output_file]
# Example:
Rscript scripts/defensive_stats.R 2023 defense_rankings_2023.csv
```
Analyzes defensive performance metrics:
- Points allowed per game
- Rushing yards allowed per game
- Passing yards allowed per game
- Turnovers per game
- Total yards allowed per game (rushing + passing)
- Outputs: CSV file and PNG visualization

### Step 4: Second-Year Sleepers Identification
```bash
Rscript scripts/second_year_sleepers.R [year] [output_file]
# Example:
Rscript scripts/second_year_sleepers.R 2024 second_year_sleepers_2024.csv
```
Identifies second-year fantasy sleepers:
- Uses FantasyPros ECR (Expert Consensus Rankings) data
- Filters for players with ECR > 100 (outside top 100)
- Only analyzes players in their second year
- Calculates sleeper scores based on multiple factors:
  - Draft position value
  - Previous year performance
  - Age bonus
  - ECR ranking
- Includes snap count analysis (Year 1 vs Year 2)
- Outputs: CSV file and PNG visualization with top 30 sleepers

#### TODOs for Enhancement:
2. **Add week parameter** - Enhance script to accept an optional week parameter that:
   - Gets each player's next opponent for the specified week
   - Calculates snap share trends up to that week in the current year
   - Provides matchup-specific sleeper recommendations based on opponent's defensive rankings

## Notes
- All scripts include built-in rate limiting delays (1-3 seconds) to avoid API restrictions
- Scripts require internet connection for data fetching
- Year must be between 1999 and current year for defensive stats
- Year must be between 2020 and current year for sleeper analysis