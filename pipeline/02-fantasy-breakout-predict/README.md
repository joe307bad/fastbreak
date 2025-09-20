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
- Points allowed per game (primary ranking metric)
- **Rush defense rank** - Ranking based on rushing yards allowed per game (1=best, 32=worst)
- **Pass defense rank** - Ranking based on passing yards allowed per game (1=best, 32=worst)
- Rushing yards allowed per game
- Passing yards allowed per game
- Turnovers per game
- Total yards allowed per game (rushing + passing)
- Outputs: CSV file and PNG visualization with color-coded rankings

### Step 4: Second-Year Sleepers Identification
```bash
Rscript scripts/second_year_sleepers.R [year] [week] [output_file] [--exclude-current-week-fp]
# Examples:
Rscript scripts/second_year_sleepers.R 2024 5 second_year_sleepers_2024.csv
Rscript scripts/second_year_sleepers.R 2025 3 --exclude-current-week-fp second_year_sleepers_2025_w3.csv
```

### Step 5: Batch Analysis (Multiple Weeks)
```bash
Rscript scripts/run_sleeper_analysis_batch.R [year] [start_week] [end_week] [--combine]
# Examples:
Rscript scripts/run_sleeper_analysis_batch.R 2024           # Run weeks 3-17 for 2024
Rscript scripts/run_sleeper_analysis_batch.R 2024 5 10      # Run weeks 5-10 for 2024
Rscript scripts/run_sleeper_analysis_batch.R 2024 3 17 --combine  # Run and combine raw data
```
Batch processes multiple weeks of sleeper analysis:
- Automatically runs `second_year_sleepers.R` for each week in range
- Default range: weeks 3-17 (full fantasy season)
- Creates organized output directory: `sleeper_analysis_[year]/`
- Progress tracking and error handling
- 5-second delay between weeks for API rate limiting
- `--combine` flag merges all raw CSV files for ML training
- Outputs: Individual week files plus optional combined raw data file

#### **Arguments:**
- `[year]` - NFL season year
- `[week]` - Current NFL week for analysis
- `[output_file]` - CSV output filename
- `[--exclude-current-week-fp]` - **Optional flag** to exclude current week fantasy points and show historical data

#### **Features:**
- Uses FantasyPros ECR (Expert Consensus Rankings) data
- Filters for players with ECR > 100 (outside top 100)
- Only analyzes RB, WR, and TE positions (excludes QBs)
- Only analyzes players in their second year
- **Excludes players with >10 fantasy points in the previous week** (maintains true "sleeper" status)
- **Excludes injured players** using nflfastR injury reports for the specified week (Out, Doubtful, IR, PUP)
- **Excludes practice squad players** using nflfastR roster data (only active roster players included)
- **Requires meaningful playing time** (≥10% snap share in recent weeks or ≥5 snaps in most recent week)
- **Enhanced snap count analysis:**
  - Y1 and Y2 snap percentages
  - Snap percentage change from Y1 to Y2
  - Y2 average weekly snap share change
  - **2-week sliding window snap trend analysis** (rewards players with increasing snap counts)
- **Defensive matchup integration:**
  - Automatically loads defense rankings if `defense_rankings_[year].csv` exists
  - Maps each player's next opponent
  - Calculates position-specific matchup scores (RB vs rush defense, WR/TE vs pass defense)
  - Integrates matchup scoring into overall sleeper score
- **Weekly fantasy points tracking:**
  - Current week and previous week fantasy points (when flag not used)
  - Historical weekly fantasy points (when `--exclude-current-week-fp` flag is used)
  - Fantasy points delta and "HIT" status for breakout detection
- Outputs: CSV file and PNG visualization with top 30 sleepers

#### **Flag Usage:**
**Default mode (no flag):**
- Shows current week and previous week fantasy points
- Includes defensive matchup data and scores
- Displays fantasy points delta and HIT status

**With `--exclude-current-week-fp` flag:**
- Shows historical fantasy points for all weeks leading up to (but not including) the specified week
- Excludes defensive matchup columns
- Includes all players, even those with 0.0 fantasy points (UDFAs)
- Displays columns: w1, w2, w3, etc. (depending on current week)

#### Sleeper Score Calculation (Max: 175 points)

The sleeper score is calculated by combining six weighted components:

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

**5. Defensive Matchup Score (0-30 points)** *(when defense rankings available)*
- **Position-specific matchup analysis:**
  - **RBs:** Evaluated against opponent's rush defense rank
  - **WRs/TEs:** Evaluated against opponent's pass defense rank
- **Scoring scale:**
  - Opponent ranked 29-32 (worst defenses): 30 points
  - Opponent ranked 25-28: 25 points
  - Opponent ranked 21-24: 20 points
  - Opponent ranked 17-20: 15 points
  - Opponent ranked 13-16: 10 points
  - Opponent ranked 9-12: 5 points
  - Opponent ranked 1-8 (best defenses): 0 points

**6. Sliding Window Snap Trend Score (0-15 points)** *(for week 3+)*
- **2-week sliding window analysis** of snap count percentage changes
- **Example for Week 8:** Analyzes snap % deltas from Week 6→7 and Week 7→8, then averages them
- **Scoring scale:**
  - Average delta ≥10%: 15 points (major snap count increase)
  - Average delta ≥5%: 12 points (significant increase)
  - Average delta ≥2%: 8 points (moderate increase)
  - Average delta >0%: 5 points (small increase)
  - Average delta ≥-2%: 2 points (stable/slight decrease)
  - Average delta <-2%: 0 points (declining snap count)

**Total Score = Draft Value + Performance Score + Age Score + ECR Score + Matchup Score + Sliding Window Score**

Higher scores indicate better sleeper candidates, typically late-round/undrafted young players with limited Year 1 production, favorable expert rankings, and advantageous defensive matchups.

#### **Output Columns**

**Core Player Info:**
- `Rank` - Sleeper ranking (1 = best sleeper candidate)
- `Player` - Player name
- `Pos` - Position (RB/WR/TE)
- `Team` - Current NFL team

**Snap Count Analysis:**
- `Y1 Snap%` - Average snap percentage in Year 1
- `Y2 Snap%` - Average snap percentage in Year 2
- `Snap Δ` - Change in snap percentage from Y1 to Y2
- `Y2 Avg Δ` - Average weekly snap share change in Year 2
- `2W Trend` - 2-week sliding window average snap percentage delta (recent trend)

**Performance Data:**
- `Games Y1` - Games played in Year 1
- `PPG Y1` - Fantasy points per game in Year 1
- `Games Y2` - Games played in Year 2 (current season)

**Weekly Fantasy Points** *(when using --exclude-current-week-fp flag)*:
- `W1`, `W2`, `W3`, etc. - Fantasy points for each week
- Includes UDFAs who show 0.0 for weeks they didn't play

**Current Week Analysis** *(default mode)*:
- `Opp` - Next opponent
- `Def Rank` - Opponent's relevant defense rank (rush for RB, pass for WR/TE)
- `Match Pts` - Defensive matchup score (0-30 points)
- `W[X] FP` - Previous week fantasy points
- `W[Y] FP` - Current week fantasy points
- `FP Δ` - Fantasy points change from previous to current week
- `HIT` - Indicates significant fantasy point increase (>5 points)

**Scoring:**
- `Score` - Total sleeper score (higher = better sleeper candidate)
- `ECR` - FantasyPros Expert Consensus Ranking

## **Complete Workflow Example**

To get the most comprehensive sleeper analysis with defensive matchups:

```bash
# Step 1: Generate defense rankings for the current season
Rscript scripts/defensive_stats.R 2025 defense_rankings_2025.csv

# Step 2: Run sleeper analysis (will automatically use defense rankings)
Rscript scripts/second_year_sleepers.R 2025 3 second_year_sleepers_2025_w3.csv

# Step 3: For historical analysis without current week bias
Rscript scripts/second_year_sleepers.R 2025 3 --exclude-current-week-fp second_year_sleepers_2025_historical.csv
```

The sleeper script will automatically detect and integrate the defense rankings file if it exists in the same directory, enhancing the sleeper scores with position-specific defensive matchup analysis.

## Notes
- All scripts include built-in rate limiting delays (1-3 seconds) to avoid API restrictions
- Scripts require internet connection for data fetching
- Year must be between 1999 and current year for defensive stats
- Year must be between 2020 and current year for sleeper analysis
- Defense rankings file (`defense_rankings_[year].csv`) will be automatically loaded if available
- Missing values in visualizations display as "-" rather than "UDFA"