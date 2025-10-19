# NFL Fantasy Breakout Prediction CLI

A machine learning tool to predict second-year NFL player fantasy breakouts, comparing ML model predictions against a composite "Sleeper Score" baseline.

## Overview

This CLI tool trains and evaluates binary classification models to predict when second-year NFL players will have fantasy breakout performances (+5 fantasy points from previous week). It uses **leave-one-out cross-validation** to prevent data leakage and compares ML predictions against a composite scoring system across 40+ features.

## Setup

1. Generate historical data (see `pipeline/02-nfl-fantasy-sleeper-breakout/README.md`)
2. Set `WEEKLY_PLAYER_STATS_DATA_FOLDER` in `.env` file pointing to weekly CSV data containing player statistics

## Usage

All commands use the same base pattern:
```bash
dotnet run 02-nfl-fantasy-breakout <command> [arguments]
```

### Commands

#### verify-data
Verify that data sources are accessible and environment is configured correctly.

```bash
dotnet run 02-nfl-fantasy-breakout verify-data
```

**What it does:**
- Checks that `.env` file exists and is readable
- Verifies `WEEKLY_PLAYER_STATS_DATA_FOLDER` is set
- Confirms data directory exists and contains CSV files
- Validates CSV file structure

---

#### train-and-evaluate-algorithms
Compare different ML algorithms to determine which performs best for this prediction task.

```bash
dotnet run 02-nfl-fantasy-breakout train-and-evaluate-algorithms
```

**What it does:**
- Trains two logistic regression algorithms:
  - **LbfgsLogisticRegression** (Limited-memory BFGS optimizer)
  - **SdcaLogisticRegression** (Stochastic Dual Coordinate Ascent)
- Evaluates each algorithm using cross-validation
- Reports metrics: Accuracy, Precision, Recall, F1 Score, AUC
- Recommends best-performing algorithm

**Sample Output:**
```
================================================
ML Algorithm Comparison Results
================================================

Algorithm: LbfgsLogisticRegression
├─ Accuracy:  0.742
├─ Precision: 0.451
├─ Recall:    0.623
├─ F1 Score:  0.524
└─ AUC:       0.789

Algorithm: SdcaLogisticRegression
├─ Accuracy:  0.728
├─ Precision: 0.429
├─ Recall:    0.598
├─ F1 Score:  0.500
└─ AUC:       0.765

Recommendation: Use LbfgsLogisticRegression
```

---

#### predict-weekly-hits
Train model on historical data and predict hits for each week using leave-one-out cross-validation. Generates JSON output for visualization.

```bash
dotnet run 02-nfl-fantasy-breakout predict-weekly-hits --output path/to/output
```

**Arguments:**
- `--output` (optional): Directory path where `output.json` will be saved

**Example:**
```bash
dotnet run 02-nfl-fantasy-breakout predict-weekly-hits --output pipeline/02-nfl-fantasy-sleeper-breakout/d3-charts
```

**What it does:**
1. Loads all historical weekly data (excludes 2025 data)
2. For each week:
   - Trains model on **all other weeks** (leave-one-out)
   - Makes predictions for the held-out week
   - Compares ML predictions vs Sleeper Score predictions
   - Calculates accuracy, precision, recall, F1 score
3. Generates `output.json` with:
   - Overall statistics (total weeks, players, accuracy metrics)
   - Top 10 ML predictions per week
   - Top 10 Sleeper Score predictions per week
   - Biggest ML successes (high-confidence correct predictions)
   - Weekly performance comparison

**Sample JSON Output Structure:**
```json
{
  "generatedAt": "2024-12-15 14:30:00",
  "overallStats": {
    "totalAnalyzedGames": 16,
    "totalAnalyzedPlayers": 263,
    "mlTotalTop10Hits": 29,
    "mlAccuracyTop10": 0.18125,
    "sleeperTotalTop10Hits": 24,
    "sleeperAccuracyTop10": 0.15
  },
  "weeklyPredictions": [
    {
      "year": 2024,
      "week": 3,
      "mlTop10Predictions": [...],
      "sleeperTop10Predictions": [...],
      "mlTop10Hits": 3,
      "sleeperTop10Hits": 2
    }
  ],
  "caseStudy": {
    "biggestMLSuccesses": [...]
  }
}
```

---

#### predict-new-week
Predict breakout hits for a new week using a trained model and generate interactive HTML visualization.

```bash
dotnet run 02-nfl-fantasy-breakout predict-new-week --file week-data.csv --output output-dir
```

**Arguments:**
- `--file` (required): Path to CSV file with new week's player data
- `--output` (required): Directory where HTML visualization will be saved

**Example:**
```bash
dotnet run 02-nfl-fantasy-breakout predict-new-week \
  --file data/second_year_2025_week1.csv \
  --output predictions/2025-week1
```

**What it does:**
1. Trains model on **all historical data**
2. Loads new week's player data from CSV
3. Makes predictions for each player
4. Ranks players by ML confidence and Sleeper Score
5. Generates interactive HTML chart using Plotly.NET showing:
   - Top predictions ranked by ML confidence
   - Sleeper Score comparison
   - Player details (position, team, stats)

---

## Complete Execution Workflow

Here's a typical workflow for using this tool:

### Step 1: Verify Environment Setup
```bash
dotnet run 02-nfl-fantasy-breakout verify-data
```

### Step 2: Compare ML Algorithms (Optional)
```bash
dotnet run 02-nfl-fantasy-breakout train-and-evaluate-algorithms
```

### Step 3: Generate Historical Performance Summary
```bash
dotnet run 02-nfl-fantasy-breakout predict-weekly-hits \
  --output pipeline/02-nfl-fantasy-sleeper-breakout/d3-charts
```
This creates `output.json` that can be visualized in web dashboards.

### Step 4: Predict Future Week Breakouts
```bash
dotnet run 02-nfl-fantasy-breakout predict-new-week \
  --file data/second_year_2025_week1.csv \
  --output predictions/2025-week1
```
This generates an interactive HTML visualization of predictions.

---

## What It Does

### 1. Data Analysis
- Loads weekly player statistics from CSV files
- Parses 40+ features per player including:
  - Fantasy performance (Y1/Y2 stats, week-over-week deltas)
  - Snap count trends (snap share, consistency, variance)
  - Physical attributes (height, weight, age, position-specific scores)
  - Draft pedigree (draft position, UDFA status)
  - Matchup data (opponent defensive rankings)
  - Expert consensus rankings (ECR, availability)

### 2. Breakout Detection
- **Definition of "Hit"**: Player's fantasy points increase by ≥5 from previous week
- Tracks actual breakout performances across historical data
- Compares prediction accuracy against actual outcomes

### 3. Model Training & Comparison
- **ML Model**: LbfgsLogisticRegression (binary classification)
  - Uses ML.NET framework
  - Features: OneHotEncoded categorical + normalized numeric features
  - Training: Logistic regression with L-BFGS optimizer
- **Sleeper Score Model**: Composite score of 6 weighted components:
  - Draft Value Score
  - Performance Score
  - Age Score
  - ECR Score
  - Matchup Score
  - Snap Trend Score

### 4. Performance Evaluation
Uses **leave-one-out cross-validation**:
- For each week in dataset:
  - Train model on all OTHER weeks
  - Predict breakouts for held-out week
  - Calculate metrics without data leakage

**Metrics Tracked:**
- **Accuracy**: Overall correct predictions
- **Precision**: % of predicted hits that were actual hits
- **Recall**: % of actual hits that were predicted
- **F1 Score**: Harmonic mean of precision and recall
- **Top N Hits**: How many actual breakouts occurred in top N predictions

### 5. Prediction Analysis
- Ranks players by prediction confidence (0-1 probability)
- Compares ML vs Sleeper Score rankings
- Identifies high-confidence predictions
- Tracks biggest prediction successes (high confidence + large breakout)

### 6. Weekly Metrics JSON Generation
Produces structured JSON output containing:
- **Overall Statistics**: Aggregated performance across all weeks
- **Weekly Predictions**: Per-week top 10 predictions from both models
- **Case Studies**: Notable prediction successes and failures
- **Comparison Metrics**: ML accuracy vs Sleeper Score accuracy

---

## Input Data Requirements

### CSV File Schema
Each CSV file should contain these columns:

#### Player Identifiers
- `player_id`, `player`, `position`, `team`, `opponent`

#### Target Variable
- `hit` (boolean): Whether player had +5 FP breakout

#### Performance Metrics
- `prev_week_fp`, `current_week_fp`, `fp_week_delta`, `sleeper_score`

#### Year 2 Fantasy Stats
- `total_fp_y2`, `avg_fp_y2`, `max_fp_y2`, `min_fp_y2`, `fp_per_snap_y2`, `fp_consistency_y2`
- `total_games_y2`, `games_played_y2`

#### Year 1 Fantasy Stats
- `total_fantasy_points_y1`, `ppg_y1`, `fp_per_snap_y1`, `games_y1`

#### Snap Count Features
- `w1_snap_share`, `y2_snap_share_change`, `sliding_window_avg_delta`
- `max_snap_pct_y2`, `min_snap_pct_y2`, `avg_snap_pct_y2`
- `snap_pct_change`, `snap_pct_variance`, `snap_consistency_y2`
- `total_off_snaps_y2`, `total_off_snaps_y1`, `avg_snap_pct_y1`
- Boolean flags: `crossed_10pct_snaps`, `crossed_20pct_snaps`, `crossed_30pct_snaps`, `has_positive_trend`, `significant_snap_jump`

#### Physical Attributes
- `height`, `weight`, `age`
- Position scores: `rb_size_score`, `wr_height_score`, `te_size_score`

#### Draft/Prospect Info
- `draft_number`, `entry_year`, `years_exp`, `college`
- Boolean flags: `is_udfa`, `is_day3_pick`, `is_early_pick`, `is_young_breakout`

#### Matchup Data
- `opponent_rush_def_rank`, `opponent_pass_def_rank`

#### Expert Rankings
- `ecr`, `player_available`

#### Composite Scores
- `draft_value_score`, `performance_score`, `age_score`, `ecr_score`, `matchup_score`, `snap_trend_score`

#### Context
- `season`, `analysis_week`

---

## Key Metrics

### Model Performance Metrics
- **Accuracy**: (TP + TN) / Total
- **Precision**: TP / (TP + FP) - "Of predicted breakouts, how many actually happened?"
- **Recall**: TP / (TP + FN) - "Of actual breakouts, how many did we predict?"
- **F1 Score**: 2 × (Precision × Recall) / (Precision + Recall)

### Top N Analysis
- **ML Top 10 Hits**: # of actual breakouts in top 10 ML predictions
- **Sleeper Top 10 Hits**: # of actual breakouts in top 10 Sleeper Score rankings
- **ML Top 10 Accuracy**: ML Top 10 Hits / 10
- **Sleeper Top 10 Accuracy**: Sleeper Top 10 Hits / 10

---

## Machine Learning Approach

### Sleeper Score Model
A **composite baseline** (not machine learning) that combines 6 weighted factors:
- Draft pedigree (higher draft picks score better)
- Historical performance (Y1/Y2 stats)
- Age (younger breakouts valued)
- Expert consensus rankings (ECR)
- Defensive matchup favorability
- Snap count trend momentum

**Threshold**: Players with Sleeper Score ≥ 100 are predicted as "hits"

### ML Model (LbfgsLogisticRegression)
A **binary classification model** trained on historical data:

**Feature Engineering:**
- OneHot encoding for categorical variables (Position, Team, Opponent)
- Min-Max normalization for numeric features
- 40+ input features concatenated into feature vector
- **IMPORTANT**: The Sleeper Score itself is included as one of the input features

**Algorithm:**
- Logistic regression with L-BFGS optimizer
- Outputs probability (0-1) of breakout occurring
- Threshold: Probability > 0.5 → predicted hit

**Training Strategy:**
- Leave-one-out cross-validation prevents overfitting
- Each week predicted using model that never saw that week's data

---

## What This Actually Compares

This tool compares **two different approaches** to predicting breakouts:

1. **Sleeper Score Alone** (Simple threshold rule)
   - Uses the composite Sleeper Score value directly
   - Predicts "hit" if Sleeper Score ≥ 100
   - Simple, interpretable, no training required
   - Baseline to beat

2. **ML Model** (Hybrid ML + Sleeper Score approach)
   - **Includes Sleeper Score as one of many input features**
   - Also uses the 6 individual component scores (draft, performance, age, ECR, matchup, snap trend)
   - Learns optimal weighting between Sleeper Score and all other features
   - Discovers non-linear interactions and patterns
   - More complex, requires training

**Key Question**: Can machine learning learn to combine the Sleeper Score with raw features to outperform using the Sleeper Score threshold alone?

**Important Note**: This is NOT a pure "ML vs heuristic" comparison. The ML model has an advantage because it uses the Sleeper Score PLUS additional features and learns optimal weights. Think of it as:
- **Baseline**: Use sleeper score with fixed threshold (≥100 = hit)
- **ML Approach**: Learn how to weight sleeper score + 40+ other features to predict hits

---

## Use Cases

### Fantasy Football Analysis
- **Pre-draft research**: Identify second-year breakout candidates
- **Waiver wire**: Find undervalued players with breakout potential
- **Weekly lineups**: Predict which players might exceed expectations

### Sports Analytics Research
- **Model comparison**: Evaluate ML vs heuristic approaches
- **Feature importance**: Understand which factors drive breakouts
- **Trend analysis**: Track how snap share trends predict fantasy success

### Machine Learning Education
- **Binary classification example**: Real-world imbalanced dataset
- **Cross-validation practice**: Learn proper evaluation techniques
- **Feature engineering**: See domain knowledge applied to ML

---

## Dependencies

- **.NET SDK**: 6.0 or higher
- **ML.NET**: Machine learning framework
- **Plotly.NET**: Interactive chart generation
- **Argu**: Command-line argument parsing
- **DotEnv.Net**: Environment variable management

---

## Performance Benchmarks

Based on 2024 season analysis (16 weeks, 263 player-weeks):

| Metric | ML Model | Sleeper Score |
|--------|----------|---------------|
| **Top 10 Accuracy** | ~18.1% | ~15.0% |
| **Top 3 Accuracy** | ~22-25% | ~18-20% |
| **Overall Precision** | ~34.1% | ~30-35% |
| **Overall Recall** | ~53.2% | ~40-45% |

**Note**: Predicting fantasy breakouts is inherently difficult (high variance in NFL). Even modest improvements over baseline are meaningful.

---

## Future Enhancements

### Potential Improvements
- **Algorithm expansion**: Test gradient boosting (LightGBM, XGBoost)
- **Time-series features**: Incorporate momentum/trend indicators
- **Ensemble methods**: Combine multiple models
- **Hyperparameter tuning**: Optimize model parameters
- **Feature selection**: Identify most predictive features
- **Position-specific models**: Train separate models per position
- **Weekly recalibration**: Update model as season progresses

### Visualization Enhancements
- Interactive web dashboard
- Player drill-down views
- Confidence interval visualization
- Feature importance charts
- Historical trend overlays

---

## How It Works (Technical Deep Dive)

### Leave-One-Out Cross-Validation
Traditional train/test splits can leak data when weeks are correlated. This tool uses **leave-one-out**:

```
Week 3:  [TRAIN] [TRAIN] [TEST]  [TRAIN] [TRAIN] ...
Week 4:  [TRAIN] [TRAIN] [TRAIN] [TEST]  [TRAIN] ...
Week 5:  [TRAIN] [TRAIN] [TRAIN] [TRAIN] [TEST]  ...
```

Each week is predicted using a model that **never saw that week's data**, preventing overfitting.

### Pipeline Architecture
```
CSV Data → Parse/Validate → Feature Engineering → ML.NET Pipeline
                                                    ↓
                                           [OneHot Encoding]
                                                    ↓
                                           [Normalization]
                                                    ↓
                                           [LbfgsLogisticRegression]
                                                    ↓
                                           Predictions (0-1 probability)
```

### Output Generation
The `predict-weekly-hits` command generates JSON optimized for D3.js visualization:
- Sorted by week for contiguous line charts
- Top 10 predictions per week for both models
- Metadata for tooltips and annotations
- Performance metrics for summary statistics
