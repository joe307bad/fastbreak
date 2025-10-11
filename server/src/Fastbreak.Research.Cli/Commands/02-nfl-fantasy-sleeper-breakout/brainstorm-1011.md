# NFL Fantasy Sleeper Breakout - Brainstorm Session (10/11)

## ACTUAL Current State (Cleared Preconceptions)

### What We Have
- **19 CSV files** with comprehensive weekly player stats (2024 weeks 3-18, 2025 weeks 3-5)
- **Target variable already labeled**: `hit` column (binary 1/0)
- **Rich feature set** with ~60+ columns per player per week
- **Already calculated scores**: draft_value_score, performance_score, age_score, ecr_score, matchup_score, snap_trend_score
- **Data is ready to use** - no data cleaning or transformation needed

### The Problem (Supervised Binary Classification)
**Goal**: Train an ML model to predict which players will be "hits" in upcoming weeks based on their historical performance features

**Input**: All feature columns from weekly stats CSVs
**Output**: Probability that player will be a "hit" (or binary prediction)

**This is NOT**: A rules-based scoring system
**This IS**: Machine learning on labeled training data to predict hits

---

## Data Schema (Confirmed from CSV Files)

### Target Variable
- `hit` - Binary (1 = hit, 0 = not a hit) ← **THIS IS WHAT WE PREDICT**

### Performance Metrics
- `prev_week_fp` - Previous week fantasy points
- `current_week_fp` - Current week fantasy points
- `sleeper_score` - Pre-calculated composite score
- `fp_week_delta` - Change in fantasy points
- `total_fp_y2`, `avg_fp_y2`, `max_fp_y2`, `min_fp_y2` - Year 2 stats
- `fp_per_snap_y2` - Efficiency metric
- `fp_consistency_y2` - Performance variance
- `total_fantasy_points_y1`, `ppg_y1` - Year 1 production
- `fp_per_snap_y1` - Year 1 efficiency

### Snap Count Features (Critical Predictors)
- `w1_snap_share` - Week 1 snap percentage
- `y2_snap_share_change` - Year-over-year change
- `sliding_window_avg_delta` - Trend indicator
- `max_snap_pct_y2`, `min_snap_pct_y2` - Range
- `avg_snap_pct_y2` - Average usage
- `snap_pct_change` - Recent change
- `snap_pct_variance` - Usage consistency
- `snap_consistency_y2` - How stable is usage
- `total_off_snaps_y2` - Total snaps
- `total_off_snaps_y1` - Year 1 snaps
- `avg_snap_pct_y1` - Year 1 average snap %
- Boolean flags:
  - `crossed_10pct_snaps`
  - `crossed_20pct_snaps`
  - `crossed_30pct_snaps`
  - `has_positive_trend`
  - `significant_snap_jump`

### Player Identifiers & Info
- `player_id`, `player`
- `position` - QB/RB/WR/TE
- `team`, `opponent`

### Player Physical Attributes
- `height`, `weight`, `age`
- Position-specific scores:
  - `rb_size_score` - RB build evaluation
  - `wr_height_score` - WR height advantage
  - `te_size_score` - TE size evaluation

### Draft/Prospect Information
- `draft_number` - Draft position
- `entry_year` - Year entered NFL
- `years_exp` - Experience level
- `college`
- Boolean flags:
  - `is_udfa` - Undrafted free agent
  - `is_day3_pick` - Late round pick
  - `is_early_pick` - High draft capital
  - `is_young_breakout` - Age-based breakout window

### Rookie Year Context
- `games_y1` - Games played year 1
- `rookie_year_usage` - Y1 usage score

### Matchup/Opponent Data
- `opponent_rush_def_rank` - Opponent run defense ranking
- `opponent_pass_def_rank` - Opponent pass defense ranking

### Expert Consensus
- `ecr` - Expert Consensus Ranking (lower = better)
- `player_available` - Roster availability

### Pre-Calculated Scores (Could be features OR could be what we're trying to improve)
- `draft_value_score`
- `performance_score`
- `age_score`
- `ecr_score`
- `matchup_score`
- `snap_trend_score`

### Metadata
- `season`, `analysis_week`

---

## ML Algorithms to Test

We'll test multiple ML.NET binary classification algorithms to find the best performer:

1. **FastTree** (Boosted Decision Tree)
   - Fast training
   - Good feature importance
   - Default for binary classification

2. **LightGBM** (Gradient Boosted Trees)
   - State-of-the-art for tabular data
   - Handles missing values well
   - Fast and accurate

3. **FastForest** (Random Forest)
   - Ensemble method
   - Robust to outliers
   - Good baseline

4. **SDCA (Stochastic Dual Coordinate Ascent)**
   - Linear classifier
   - Very fast training
   - Good for large datasets

5. **LbfgsLogisticRegression**
   - Logistic regression
   - Interpretable coefficients
   - Good baseline

### Evaluation Metrics
For each algorithm we'll report:
- **AUC** (Area Under ROC Curve) - Primary metric for ranking algorithms
- **Accuracy** - Overall correctness
- **Precision** - When we predict "hit", how often correct?
- **Recall** - Of all actual hits, how many did we catch?
- **F1 Score** - Harmonic mean of precision/recall
- **Confusion Matrix** - True/false positives/negatives

---

## Implementation Plan (F# + ML.NET)

### Phase 1: Multi-Algorithm Training & Evaluation

**Command**: `train-and-evaluate-algorithms`

**Goal**: Run ALL algorithms against the same dataset and compare results to find the best performer

#### Phase 1 Implementation Steps:

1. **Data Loading**
   - Load all 19 CSV files
   - Combine into single dataset
   - Convert to ML.NET IDataView

2. **Data Split (Simple 80/20)**
   - **Training Set**: First 80% of rows (after shuffling)
   - **Test Set**: Last 20% of rows
   - Use consistent random seed for reproducibility

3. **Feature Selection**
   - Start with ALL features except:
     - Target: `hit`
     - Identifiers: `player_id`, `player`
     - Metadata: `season`, `analysis_week`
   - Convert categorical features (position, team, opponent) to one-hot encoding
   - Handle missing values (mean imputation for numeric, mode for categorical)

4. **Train Each Algorithm**
   For each of the 5 algorithms:
   - Create ML.NET pipeline with feature engineering
   - Train on training set (80%)
   - Evaluate on test set (20%)
   - Collect all metrics

5. **Console Output Format**
   ```
   ==================================================
   NFL Fantasy Sleeper Hit Prediction
   Multi-Algorithm Evaluation Results
   ==================================================

   Dataset:
   - Total Samples: 1,234
   - Training Samples: 987 (80%)
   - Test Samples: 247 (20%)
   - Hit Rate: 23.4% (class distribution)

   --------------------------------------------------
   Algorithm Rankings (by AUC):
   --------------------------------------------------

   1. LightGBM               AUC: 0.823
   2. FastTree               AUC: 0.811
   3. FastForest             AUC: 0.795
   4. LbfgsLogisticReg       AUC: 0.742
   5. SDCA                   AUC: 0.731

   --------------------------------------------------
   Detailed Results:
   --------------------------------------------------

   [1] LightGBM
   ├─ AUC:           0.823
   ├─ Accuracy:      0.786
   ├─ Precision:     0.654
   ├─ Recall:        0.712
   ├─ F1 Score:      0.682
   ├─ Training Time: 2.3s
   └─ Confusion Matrix:
      ├─ True Positives:  41
      ├─ False Positives: 22
      ├─ True Negatives:  153
      └─ False Negatives: 31

   [2] FastTree
   ├─ AUC:           0.811
   ├─ Accuracy:      0.774
   ├─ Precision:     0.637
   ├─ Recall:        0.698
   ├─ F1 Score:      0.666
   ├─ Training Time: 1.8s
   └─ Confusion Matrix:
      ├─ True Positives:  39
      ├─ False Positives: 24
      ├─ True Negatives:  151
      └─ False Negatives: 33

   ... (similar for remaining algorithms)

   --------------------------------------------------
   Recommendation: Use LightGBM for production
   --------------------------------------------------
   ```

6. **Code Files to Create**
   - `DataTypes.fs` - Record types for CSV data and predictions
   - `DataLoader.fs` - Load and combine all CSV files
   - `AlgorithmTrainer.fs` - Train individual algorithms
   - `AlgorithmComparison.fs` - Run all algorithms and compare
   - Update `NflFantasyBreakout.fs` - Add train-and-evaluate-algorithms command

---

## Success Criteria for Phase 1

### Minimum Viable Phase 1
- [ ] Command runs without errors
- [ ] All 5 algorithms train successfully
- [ ] Results display in console with metrics
- [ ] Can identify which algorithm performs best

### Target Performance (At least one algorithm should hit these)
- **AUC ≥ 0.75** - Good discriminative ability
- **Recall ≥ 0.60** - Catch at least 60% of actual hits
- **Precision ≥ 0.40** - Reasonable false positive rate

### Output Quality
- Clear, readable console output
- Algorithms ranked by AUC
- All metrics displayed for each algorithm
- Training time tracked
- Confusion matrix for interpretability

---

## Technical Details

### Feature Engineering Pipeline (ML.NET)
```fsharp
// Pseudo-code for pipeline
let pipeline =
    mlContext.Transforms
        // Handle missing values
        .ReplaceMissingValues("prev_week_fp", replacementMode = MissingValueReplacingEstimator.ReplacementMode.Mean)
        .ReplaceMissingValues("ecr", replacementMode = MissingValueReplacingEstimator.ReplacementMode.Mean)
        // ... repeat for all numeric columns

        // One-hot encode categorical
        .Categorical.OneHotEncoding("position")
        .Categorical.OneHotEncoding("team")
        .Categorical.OneHotEncoding("opponent")

        // Concatenate all features
        .Concatenate("Features", [| all feature column names |])

        // Normalize features
        .NormalizeMinMax("Features")

        // Append trainer (varies by algorithm)
        .Append(trainer)
```

### Data Split Strategy
- Simple random 80/20 split
- Use fixed random seed (seed = 42) for reproducibility
- Later can switch to time-based split for more realistic evaluation

### Handling Class Imbalance
- Report class distribution in output
- If hit rate < 30%, consider:
  - Class weights in algorithms that support it (FastTree, LightGBM)
  - Adjusting decision threshold
  - SMOTE oversampling (future enhancement)

---

## Future Phases (After Phase 1)

### Phase 2: Feature Importance Analysis
- Extract feature importance from best algorithm
- Identify top predictive features
- Analyze which features matter most

### Phase 3: Hyperparameter Tuning
- Take best algorithm from Phase 1
- Grid search or random search on hyperparameters
- Optimize for AUC

### Phase 4: Time-Based Validation
- Switch from random split to time-based
- Train on earlier weeks, test on later weeks
- More realistic production scenario

### Phase 5: Production Prediction
- Create `predict` command
- Load trained model
- Generate predictions for new week
- Output top N sleeper candidates

---

## Code Organization

```
Commands/02-nfl-fantasy-sleeper-breakout/
  ├── DataVerification.fs (✓ working)
  ├── EnvConfig.fs (✓ working)
  ├── DataTypes.fs (NEW - record types)
  ├── DataLoader.fs (NEW - load CSVs)
  ├── AlgorithmTrainer.fs (NEW - train single algorithm)
  ├── AlgorithmComparison.fs (NEW - compare all algorithms)
  ├── ConsoleOutputFormatter.fs (needs update)
  ├── HtmlOutputGenerator.fs (future)
  └── NflFantasyBreakout.fs (✓ working - add new command)
```

---

## Next Immediate Actions (In Order)

1. **Define DataTypes.fs**
   - Create F# record type matching CSV schema
   - Define prediction output types
   - Define evaluation metrics types

2. **Implement DataLoader.fs**
   - Read all CSV files from folder
   - Parse into F# records
   - Convert to ML.NET IDataView
   - Report dataset statistics

3. **Implement AlgorithmTrainer.fs**
   - Create reusable function to train any algorithm
   - Return trained model + evaluation metrics
   - Handle errors gracefully

4. **Implement AlgorithmComparison.fs**
   - Call AlgorithmTrainer for each algorithm
   - Collect all results
   - Sort by AUC
   - Format output

5. **Update NflFantasyBreakout.fs**
   - Add "train-and-evaluate-algorithms" command
   - Wire up to AlgorithmComparison
   - Test end-to-end

6. **Test & Iterate**
   - Run command
   - Review results
   - Fix any issues
   - Celebrate when we get good AUC scores!

---

## Key Decisions Made

### Data Split: 80/20 Random (for now)
- **Why**: Simple, fast to implement
- **Later**: Switch to time-based for production realism

### Use ALL Features (for now)
- **Why**: Let algorithms figure out what matters
- **Later**: Feature selection based on importance analysis

### Target Metric: AUC
- **Why**: Handles class imbalance well, standard for binary classification
- **Secondary**: F1 score for balanced precision/recall view

### Start Simple, Iterate
- Phase 1 is just about finding best algorithm
- Don't over-engineer
- Get results fast, then improve
