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
- Only analyzes RB, WR, and TE positions (excludes QBs)
- Only analyzes players in their second year
- Includes snap count analysis (Year 1 vs Year 2)
- Outputs: CSV file and PNG visualization with top 30 sleepers

#### Sleeper Score Calculation (Max: 130 points)

The sleeper score is calculated by combining four weighted components:

**1. Draft Value (0-50 points)**
- Undrafted Free Agent (UDFA): 50 points
- Draft pick > 200: 40 points
- Draft pick > 150: 30 points
- Draft pick > 100: 20 points
- Draft pick > 50: 10 points
- Draft pick ≤ 50: 0 points

**2. Performance Score (0-40 points)**
- Didn't play Year 1 (0 games): 40 points
- Low production but played (PPG < threshold): 30 points
- Played but minimal fantasy points: 20 points
- Moderate production (PPG between threshold and 1.5x threshold): 20 points
- Higher production: 0 points
- Position PPG thresholds:
  - RB/WR: 6 PPG
  - TE: 4 PPG

**3. Age Score (0-20 points)**
- Age ≤ 22: 20 points
- Age ≤ 23: 15 points
- Age ≤ 24: 10 points
- Age > 24: 0 points

**4. ECR Score (0-20 points)**
- Scaled based on ECR ranking within the dataset
- Better ECR ranking (lower number) = higher score
- Formula: `20 - ((ecr - min_ecr) / (max_ecr - min_ecr)) * 20`

**Total Score = Draft Value + Performance Score + Age Score + ECR Score**

Higher scores indicate better sleeper candidates, typically late-round/undrafted young players with limited Year 1 production but favorable expert rankings.

## Notes
- All scripts include built-in rate limiting delays (1-3 seconds) to avoid API restrictions
- Scripts require internet connection for data fetching
- Year must be between 1999 and current year for defensive stats
- Year must be between 2020 and current year for sleeper analysis