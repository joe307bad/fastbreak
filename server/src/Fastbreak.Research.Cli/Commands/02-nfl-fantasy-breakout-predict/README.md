# NFL Fantasy Breakout Prediction CLI

A machine learning-powered CLI tool that analyzes NFL fantasy football data to predict player breakouts using both composite scoring and ML methodologies.

## Overview

This command analyzes CSV data from the R-based fantasy breakout prediction pipeline to train and evaluate models that predict when second-year NFL players will have breakout fantasy performances. It compares traditional composite scoring approaches against machine learning models to determine which methodology performs better.

## Usage

```bash
dotnet run 02-nfl-fantasy-breakout --data /path/to/csv/directory --output /path/to/output.json
```

### Arguments

- `--data` (required): Directory path containing CSV files from the fantasy breakout prediction pipeline
- `--output` (required): Path where the weekly metrics JSON file should be saved

### Example

```bash
dotnet run 02-nfl-fantasy-breakout --data /Users/joebad/Source/fastbreak/pipeline/02-fantasy-breakout-predict/sleeper_analysis_2024 --output /Users/joebad/Source/fastbreak/pipeline/02-fantasy-breakout-predict/sleeper_analysis_2024/weekly_metrics.json
```

## Complete Execution Workflow

To generate weekly performance analysis and visualization:

### Step 1: Generate Weekly Metrics JSON
Run the F# command to analyze data and generate weekly hit counts:

```bash
cd server/src/Fastbreak.Research.Cli
dotnet run 02-nfl-fantasy-breakout --data /path/to/sleeper_analysis_2024 --output /path/to/output/weekly_metrics.json
```

This generates:
- Model performance analysis and comparison
- Weekly metrics JSON file at the specified output location with three metrics per week:
  - Top 10 sleeper score hits
  - Top 3 sleeper score hits
  - ML model successful predictions

### Step 2: Generate Visualization
Run the R script to create a comparative line graph:

```bash
cd pipeline/02-fantasy-breakout-predict
Rscript scripts/plot_weekly_metrics.R sleeper_analysis_2024/weekly_metrics.csv output_plot.png
```

This produces:
- Multi-line graph comparing the three performance metrics over time
- Summary statistics and efficiency ratios
- High-resolution PNG plot for analysis

## What It Does

### 1. **Data Analysis**
- Loads and analyzes training data files (`*_raw.csv`) containing 60+ features per player
- Loads sleeper score files (`*.csv`) containing composite rankings and hit status
- Provides comprehensive data overview including file counts, record counts, and feature analysis

### 2. **Breakout Detection**
- Uses a configurable **5.0 fantasy point threshold** to define breakouts
- Identifies players who increased their fantasy points by 5+ from the previous week
- Analyzes breakout patterns across weeks 3-17 of the NFL season

### 3. **Model Training & Comparison**
- **70/30 Data Split**: 70% for training, 30% for testing
- **Sleeper Score Model**: Uses composite score with 100-point threshold
- **ML Model**: Combines sleeper score (60%) + playing time (20%) + momentum (20%)
- Trains both models on the same data for fair comparison

### 4. **Performance Evaluation**
- Calculates accuracy, precision, recall, and F1 scores for both methodologies
- Provides confusion matrix analysis
- Determines statistical winner based on test set performance

### 5. **Prediction Analysis**
- Shows top 10 predicted breakouts with confidence scores
- Identifies missed breakouts (actual breakouts not predicted)
- Provides player-level insights for fantasy decision making

### 6. **Weekly Metrics JSON Generation**
- Generates weekly metrics JSON at the specified output path containing hit counts for visualization
- Tracks three performance metrics across all NFL weeks:
  - Top 10 sleeper score hits per week
  - Top 3 sleeper score hits per week
  - ML model successful predictions per week
- Enables comparative analysis of methodology performance over time

## Sample Output

### Model Comparison Results
```
=== MODEL COMPARISON ===
                    Sleeper Score    ML Model    Difference
                    -------------    --------    ----------
Train Accuracy:     66.5%          71.8%       +5.3%
Test Accuracy:      61.6%          71.2%       +9.6%
Precision:          33.3%          51.6%       +18.3%
Recall:             27.3%          72.7%       +45.5%
F1 Score:           0.300           0.604        +0.304

=== METHODOLOGY WINNER ===
🏆 ML MODEL WINS with 71.2% test accuracy (vs 61.6% sleeper score)
   Improvement: +9.6% accuracy, +18.3% precision, +45.5% recall
```

### Top Predictions
```
Top 10 Predicted Breakouts:
Player               Pos Team Actual Pred   Conf     FP Δ   Sleeper
--------------------------------------------------------------------------------
Emanuel Wilson       RB  GB   ✓      ✓      75.4     11.7   118.0
Chase Brown          RB  CIN  ✓      ✓      74.5     14.3   115.0
Tyjae Spears         RB  TEN  ✓      ✓      66.7     23.9   89.0
```

## Input Data Requirements

The tool expects CSV files in the specified directory with the following structure:

### Training Data Files (`*_raw.csv`)
- **60+ features** including snap counts, physical attributes, draft capital, efficiency metrics
- Key columns: `player`, `position`, `team`, `fp_delta`, `sleeper_score`, `prev_week_fp`
- Generated by the R-based fantasy breakout prediction pipeline

### Sleeper Score Files (`*.csv`)
- **21 columns** including sleeper rankings and hit status
- Key columns: `sleeper_rank`, `player`, `position`, `hit_status`, `sleeper_score`
- Contains composite scoring results from R analysis

## Key Metrics

- **Breakout Threshold**: 5.0 fantasy points increase from previous week
- **Data Split**: 70% training, 30% testing (with random seed for reproducibility)
- **Success Criteria**: Accuracy, precision, recall, and F1 score comparisons
- **Confidence Scoring**: Normalized probability scores for prediction ranking

## Machine Learning Approach

### Sleeper Score Model
- **Method**: Single threshold (100+ sleeper score)
- **Pros**: Simple, interpretable
- **Cons**: Conservative, misses many breakouts

### ML Model
- **Method**: Weighted combination of multiple factors
- **Features**:
  - Sleeper score (60% weight)
  - Playing time indicator (20% weight)
  - Performance momentum (20% weight)
- **Threshold**: 0.5 combined score

## Use Cases

1. **Fantasy Football Analysis**: Identify undervalued players likely to break out
2. **Methodology Research**: Compare traditional vs ML approaches in sports analytics
3. **Model Validation**: Evaluate prediction accuracy using historical data
4. **Feature Importance**: Understand which factors best predict breakouts

## Dependencies

- **ML.NET**: For potential machine learning extensions
- **F# Core Libraries**: System.IO for file operations
- **Input Data**: CSV files from R-based breakout prediction pipeline

## Performance Benchmarks

Based on 2024 NFL season data (243 total records):
- **ML Model**: 71.2% accuracy, 51.6% precision, 72.7% recall
- **Sleeper Score**: 61.6% accuracy, 33.3% precision, 27.3% recall
- **Improvement**: +9.6% accuracy, +45.5% better breakout detection

## Future Enhancements

- Integration with full ML.NET pipeline for advanced algorithms
- Real-time data ingestion from live NFL feeds
- Position-specific model tuning (RB vs WR vs TE)
- Confidence interval analysis for prediction reliability
- Export predictions to fantasy platform APIs