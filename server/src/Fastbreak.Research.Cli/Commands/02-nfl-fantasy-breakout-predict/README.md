# NFL Fantasy Breakout Prediction - Data Collection Steps

This document outlines the process for collecting data to predict breakout fantasy football performances.

## Data Collection Pipeline

### Step 1: Defense Power Rankings
```bash
dotnet run -- --defense-power-rankings --year <YEAR>
```
Generates a CSV with defensive metrics for each team:
- Points allowed per game
- Rushing yards allowed per game
- Passing yards allowed per game
- Turnovers per game
- Total yards allowed per game (rushing + passing)

### Step 2: Second-Year Sleepers Identification
```bash
dotnet run -- --second-year-sleepers --year <YEAR>
```
Identifies fantasy players with:
- ADP (Average Draft Position) > 100
- Focus on second-year players with breakout potential

### Step 3: Early Season Analysis
```bash
dotnet run -- --early-season-analysis --year <YEAR> --defense-pr-csv <PATH_TO_DEFENSE_CSV> --sleeper-csv <PATH_TO_SLEEPER_CSV>
```
Analyzes previous year's performance data:
- Reviews stats for all identified sleepers from Step 2
- Extracts the performances where player <5 fantasy points, had a steady increase in snap share game over game, and played against a bad defense. When a player fit these criteria, we label it 1-4 (1 being a low breakout with 5+ points, 2 being a medium breakout 5-10 points, 3 being a breakout performance 10-15 points, and 4 being an extreme breakout with 15+ breakouts). This is the label the model will learn from
- Model features:
  - Snap share % and weekly trend
  - Target share % (for receivers/TEs)
  - Red zone touches/targets
  - Days of rest between games
  - Home/away status
  - Vegas implied team total
  - Defense rankings (points, yards, turnovers allowed)
  - Player efficiency metrics (YPC, YPT, catch rate)
  - Previous 3-game fantasy point average
- Trains a multiclass classification model using ML.NET AutoML:
  - Tests multiple algorithms: LightGBM, FastTree, FastForest, and logistic regression variants
  - Automatically selects the best performer based on accuracy and F1 score
  - Uses 80/20 train-test split with temporal ordering preserved
  - Outputs model performance metrics and feature importance
- This steps saves the model to disk for use in the mid-season analysis in Step 4


### Step 4: Mid-Season Analysis
```bash
dotnet run -- --mid-season-analysis --year <YEAR> --defense-pr-csv <PATH_TO_DEFENSE_CSV> --sleeper-csv <PATH_TO_SLEEPER_CSV>
```
Refines the sleeper list based on current season trends:
- Analyzes snap percentage increases for all sleepers
- Excludes players who have already broken out (5+ fantasy points in a game)
- Use Step 1 to build the defense power rankings for the current season
- Uses the model for Step 3 to analyze the sleeper list, analyzes who has been experiencing an increase in snap count, and the current defense power rankings to produce a confidence score of who will have a breakout performance for the next week
