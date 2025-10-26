# NBA Elo Rating System - Implementation Plan

## What is Elo?

Elo is a dynamic rating system originally designed for chess that updates team ratings after each game based on:
- Expected outcome (probability of winning)
- Actual outcome (win/loss)
- Margin of victory (optional enhancement)

**Key concept**: When a strong team beats a weak team, ratings change minimally. When an upset occurs, ratings change dramatically.

## Elo Rating Formula

### Basic Elo Update
```
New Rating = Old Rating + K × (Actual - Expected)
```

Where:
- **K** = K-factor (sensitivity to each game, typically 20-40)
- **Actual** = 1 for win, 0 for loss
- **Expected** = Win probability based on rating difference

### Win Probability Formula
```
Win Probability (Team A) = 1 / (1 + 10^((Rating_B - Rating_A) / 400))
```

### Margin of Victory Multiplier (MOV)
FiveThirtyEight enhancement:
```
MOV Multiplier = ln(abs(Point Diff) + 1) × (2.2 / ((ELOW - ELOL) × 0.001 + 2.2))
```

Where:
- ELOW = Winner's Elo rating
- ELOL = Loser's Elo rating
- Point Diff = Final score difference

## Phase 1: Foundation & Data Setup

### 1.1 Install Dependencies
```r
# Install hoopR
install.packages("hoopR")

# Install supporting packages
install.packages(c("tidyverse", "lubridate", "glue"))
```

### 1.2 Load NBA Game Data
```r
library(hoopR)
library(tidyverse)
library(lubridate)

# Load team box scores (contains game results)
team_box <- load_nba_team_box()

# Filter to desired seasons (start with 2023-24)
games_2024 <- team_box %>%
  filter(season == 2024) %>%
  arrange(game_date)
```

### 1.3 Data Preparation Tasks
- [ ] Load game results data
- [ ] Clean and validate data (no duplicates, complete scores)
- [ ] Create game-level dataset (currently have team-level rows)
- [ ] Identify home/away teams
- [ ] Extract final scores
- [ ] Order games chronologically
- [ ] Handle back-to-back games
- [ ] Identify playoff vs regular season games

### 1.4 Data Structure Design
```r
# Target structure: One row per game
game_data <- tibble(
  game_id = character(),
  game_date = date(),
  season = numeric(),
  home_team = character(),
  away_team = character(),
  home_score = numeric(),
  away_score = numeric(),
  home_win = logical(),
  point_diff = numeric(),
  game_type = character()  # regular, playoff, play_in
)
```

## Phase 2: Core Elo Algorithm Implementation

### 2.1 Initialize Team Ratings
```r
# Starting Elo rating (typically 1500 or 1505)
INITIAL_ELO <- 1500

# Create team ratings table
team_elo <- tibble(
  team = unique(c(game_data$home_team, game_data$away_team)),
  elo_rating = INITIAL_ELO,
  games_played = 0
)
```

### 2.2 Define Core Functions

#### Calculate Win Probability
```r
calc_win_prob <- function(elo_a, elo_b) {
  1 / (1 + 10^((elo_b - elo_a) / 400))
}
```

#### Calculate Margin of Victory Multiplier
```r
calc_mov_multiplier <- function(point_diff, elo_winner, elo_loser) {
  log(abs(point_diff) + 1) * (2.2 / ((elo_winner - elo_loser) * 0.001 + 2.2))
}
```

#### Update Elo Ratings
```r
update_elo <- function(elo_winner, elo_loser, point_diff, k_factor = 20) {
  # Calculate expected win probability for winner
  expected_win <- calc_win_prob(elo_winner, elo_loser)

  # Calculate MOV multiplier
  mov_mult <- calc_mov_multiplier(point_diff, elo_winner, elo_loser)

  # Calculate rating changes
  elo_change <- k_factor * mov_mult * (1 - expected_win)

  # Return new ratings
  list(
    winner_new = elo_winner + elo_change,
    loser_new = elo_loser - elo_change,
    elo_change = elo_change,
    expected_win_prob = expected_win
  )
}
```

### 2.3 Game-by-Game Processing Loop
```r
# Initialize results storage
game_results <- tibble()

# Process each game chronologically
for (i in 1:nrow(game_data)) {
  game <- game_data[i, ]

  # Get current ratings
  home_elo <- team_elo$elo_rating[team_elo$team == game$home_team]
  away_elo <- team_elo$elo_rating[team_elo$team == game$away_team]

  # Adjust for home court advantage (typically +100 Elo points)
  home_elo_adj <- home_elo + HOME_COURT_ADVANTAGE

  # Calculate win probability
  home_win_prob <- calc_win_prob(home_elo_adj, away_elo)

  # Determine winner/loser
  if (game$home_win) {
    update <- update_elo(home_elo, away_elo, game$point_diff)
    team_elo$elo_rating[team_elo$team == game$home_team] <- update$winner_new
    team_elo$elo_rating[team_elo$team == game$away_team] <- update$loser_new
  } else {
    update <- update_elo(away_elo, home_elo, game$point_diff)
    team_elo$elo_rating[team_elo$team == game$away_team] <- update$winner_new
    team_elo$elo_rating[team_elo$team == game$home_team] <- update$loser_new
  }

  # Store results
  game_results <- bind_rows(game_results, tibble(
    game_id = game$game_id,
    game_date = game$game_date,
    home_team = game$home_team,
    away_team = game$away_team,
    home_elo_pre = home_elo,
    away_elo_pre = away_elo,
    home_win_prob = home_win_prob,
    home_win_actual = game$home_win,
    elo_change = update$elo_change
  ))

  # Increment games played
  team_elo$games_played[team_elo$team == game$home_team] <-
    team_elo$games_played[team_elo$team == game$home_team] + 1
  team_elo$games_played[team_elo$team == game$away_team] <-
    team_elo$games_played[team_elo$team == game$away_team] + 1
}
```

### 2.4 Phase 2 Deliverables
- [ ] Core Elo calculation functions
- [ ] Game-by-game processing loop
- [ ] Elo rating updates after each game
- [ ] Historical tracking of all ratings
- [ ] Basic validation (all teams start at 1500, sum is preserved)

## Phase 3: Calibration & Parameter Tuning

### 3.1 Key Parameters to Tune

#### K-Factor
- **Low K (10-20)**: Slower, more stable changes
- **High K (30-40)**: Faster adaptation to team changes
- **FiveThirtyEight uses**: 20 for NBA

Test range: `k_values <- seq(10, 40, by = 5)`

#### Home Court Advantage
- **Typical range**: 75-125 Elo points
- **FiveThirtyEight uses**: ~100 points

Test range: `hca_values <- seq(50, 150, by = 10)`

#### Initial Rating
- **Standard**: 1500
- **Alternative**: 1505 (for symmetry)

#### Season Carryover (Regression to Mean)
Between seasons, adjust ratings:
```r
# Regress 1/3 toward mean of 1505
new_elo <- 1505 + (2/3) * (old_elo - 1505)
```

Test regression: `regression_values <- c(0.25, 0.33, 0.5, 0.67, 0.75)`

### 3.2 Optimization Approach

#### Define Evaluation Metrics
```r
# 1. Prediction Accuracy
accuracy <- mean(
  (game_results$home_win_prob > 0.5) == game_results$home_win_actual
)

# 2. Brier Score (measures probability calibration)
brier_score <- mean(
  (game_results$home_win_prob - as.numeric(game_results$home_win_actual))^2
)

# 3. Log Loss
log_loss <- -mean(
  game_results$home_win_actual * log(game_results$home_win_prob) +
  (1 - game_results$home_win_actual) * log(1 - game_results$home_win_prob)
)
```

#### Grid Search
```r
# Create parameter grid
param_grid <- expand.grid(
  k_factor = seq(10, 40, by = 5),
  home_advantage = seq(75, 125, by = 10),
  season_regression = c(0.25, 0.33, 0.5)
)

# Test each combination
results <- map_dfr(1:nrow(param_grid), function(i) {
  params <- param_grid[i, ]

  # Run Elo simulation with these parameters
  elo_results <- run_elo_simulation(
    games = game_data,
    k = params$k_factor,
    hca = params$home_advantage,
    regression = params$season_regression
  )

  # Calculate metrics
  tibble(
    k_factor = params$k_factor,
    home_advantage = params$home_advantage,
    season_regression = params$season_regression,
    accuracy = calculate_accuracy(elo_results),
    brier_score = calculate_brier_score(elo_results),
    log_loss = calculate_log_loss(elo_results)
  )
})

# Find optimal parameters
best_params <- results %>%
  arrange(log_loss) %>%
  slice(1)
```

### 3.3 Phase 3 Deliverables
- [ ] Parameter tuning framework
- [ ] Evaluation metrics implementation
- [ ] Grid search across parameter space
- [ ] Optimal parameter identification
- [ ] Sensitivity analysis documentation

## Phase 4: Advanced Features & Enhancements

### 4.1 Playoff Multiplier
Increase K-factor for playoff games:
```r
k_factor_playoff <- k_factor * 1.5  # Playoff games weighted 50% more
```

### 4.2 Travel & Rest Adjustments
```r
# Calculate rest days
game_data <- game_data %>%
  group_by(home_team) %>%
  mutate(home_rest_days = as.numeric(game_date - lag(game_date))) %>%
  ungroup()

# Adjust Elo for back-to-back (rest = 1 day)
rest_adjustment <- function(rest_days) {
  case_when(
    rest_days == 1 ~ -25,  # Back-to-back penalty
    rest_days >= 7 ~ 15,   # Well-rested bonus
    TRUE ~ 0
  )
}
```

### 4.3 Autocorrelation Features
Track recent form/momentum:
```r
# Calculate moving average of recent performance vs expectation
team_data <- team_data %>%
  group_by(team) %>%
  mutate(
    recent_performance = zoo::rollmean(
      actual_result - expected_result,
      k = 10,
      fill = 0,
      align = "right"
    )
  )
```

### 4.4 Injury Adjustments
If player data available:
```r
# Approximate team strength loss
calc_injury_impact <- function(injured_players, team_roster) {
  sum(injured_players$minutes_per_game) / sum(team_roster$minutes_per_game) * -50
}
```

### 4.5 Phase 4 Deliverables
- [ ] Playoff K-factor multiplier
- [ ] Rest/schedule adjustments
- [ ] Momentum/form indicators
- [ ] Injury impact (if data available)
- [ ] Documentation of enhancements

## Phase 5: Validation & Backtesting

### 5.1 Historical Validation
Test on multiple seasons:
```r
seasons_to_test <- 2015:2024

validation_results <- map_dfr(seasons_to_test, function(season) {
  # Run Elo for this season
  season_results <- run_elo_season(season, optimal_params)

  # Calculate metrics
  tibble(
    season = season,
    accuracy = calc_accuracy(season_results),
    brier_score = calc_brier_score(season_results),
    log_loss = calc_log_loss(season_results)
  )
})
```

### 5.2 Playoff Prediction Accuracy
```r
# At end of regular season, use Elo to predict playoff outcomes
playoff_predictions <- function(regular_season_elo, playoff_games) {
  # Compare predicted vs actual playoff winners
  # Compare predicted series outcomes
}
```

### 5.3 Compare to Vegas Lines
If betting odds available:
```r
# Convert Elo probability to spread
elo_to_spread <- function(win_prob) {
  # Approximate conversion
  -400 * log10((1 - win_prob) / win_prob) / 25
}

# Compare to actual Vegas spreads
correlation(elo_spread, vegas_spread)
```

### 5.4 Compare to Other Power Rankings
```r
# Load FiveThirtyEight, ESPN BPI, etc.
# Calculate rank correlation (Spearman's rho)
cor(our_elo_rank, fivethirtyeight_rank, method = "spearman")
```

### 5.5 Calibration Curves
```r
# Bin predictions by probability
game_results %>%
  mutate(prob_bin = cut(home_win_prob, breaks = seq(0, 1, 0.1))) %>%
  group_by(prob_bin) %>%
  summarize(
    predicted = mean(home_win_prob),
    actual = mean(home_win_actual),
    n_games = n()
  ) %>%
  ggplot(aes(x = predicted, y = actual)) +
  geom_point(aes(size = n_games)) +
  geom_abline(slope = 1, intercept = 0, linetype = "dashed") +
  labs(title = "Elo Calibration Curve")
```

### 5.6 Phase 5 Deliverables
- [ ] Multi-season backtesting
- [ ] Playoff prediction accuracy
- [ ] Comparison to betting markets
- [ ] Comparison to other rankings systems
- [ ] Calibration analysis
- [ ] Performance report & visualizations

## Phase 6: Production & Automation

### 6.1 Daily Update Pipeline
```r
# Function to update Elo with new games
update_elo_ratings <- function(current_elo, new_games) {
  # Load current ratings
  # Process new games
  # Update ratings
  # Save new ratings
}

# Scheduled execution (cron job or GitHub Actions)
```

### 6.2 Output Formats

#### Power Rankings Table
```r
current_rankings <- team_elo %>%
  arrange(desc(elo_rating)) %>%
  mutate(
    rank = row_number(),
    rating_change_7d = elo_rating - elo_7d_ago
  ) %>%
  select(rank, team, elo_rating, rating_change_7d, games_played)
```

#### Game Predictions
```r
upcoming_games <- load_nba_schedule(future = TRUE)

predictions <- upcoming_games %>%
  mutate(
    home_elo = get_elo(home_team),
    away_elo = get_elo(away_team),
    home_win_prob = calc_win_prob(home_elo + HCA, away_elo),
    predicted_spread = elo_to_spread(home_win_prob)
  )
```

### 6.3 Visualizations

#### Elo History Over Time
```r
ggplot(elo_history, aes(x = date, y = elo_rating, color = team)) +
  geom_line() +
  labs(title = "NBA Elo Ratings: 2023-24 Season")
```

#### Current Rankings Distribution
```r
ggplot(team_elo, aes(x = reorder(team, elo_rating), y = elo_rating)) +
  geom_col() +
  coord_flip() +
  labs(title = "Current NBA Elo Power Rankings")
```

#### Win Probability Matrix
```r
# Create matrix of win probabilities for all matchups
teams <- team_elo$team
win_prob_matrix <- outer(
  team_elo$elo_rating + HCA,
  team_elo$elo_rating,
  Vectorize(calc_win_prob)
)

# Heatmap visualization
```

### 6.4 Reporting & Insights
- Weekly power rankings report
- Notable rating changes
- Biggest surprises (upset probability)
- Playoff probability projections
- Title odds based on Elo

### 6.5 Phase 6 Deliverables
- [ ] Automated daily update script
- [ ] Power rankings output
- [ ] Game prediction output
- [ ] Visualization dashboard
- [ ] Weekly insights report
- [ ] API or web interface (optional)

## Success Metrics

### Minimum Viable Product (MVP)
- ✓ Elo ratings update after each game
- ✓ Prediction accuracy > 65%
- ✓ Brier score < 0.22
- ✓ Rankings correlate with other systems (ρ > 0.85)

### Stretch Goals
- Prediction accuracy > 70%
- Beat Vegas spread accuracy
- Accurate playoff bracket predictions
- Real-time updates during games
- Public dashboard/API

## Timeline Estimate

- **Phase 1**: 2-3 days (data setup)
- **Phase 2**: 3-4 days (core implementation)
- **Phase 3**: 4-5 days (tuning & optimization)
- **Phase 4**: 3-4 days (enhancements)
- **Phase 5**: 4-5 days (validation)
- **Phase 6**: 5-7 days (production)

**Total**: 3-4 weeks for full implementation

## Key References

- [FiveThirtyEight NBA Elo Methodology](https://fivethirtyeight.com/features/how-we-calculate-nba-elo-ratings/)
- [Elo Rating System (Wikipedia)](https://en.wikipedia.org/wiki/Elo_rating_system)
- [The FiveThirtyEight Complete History of the NBA](https://github.com/fivethirtyeight/data/tree/master/nba-elo)
- [Basketball-Reference SRS](https://www.basketball-reference.com/about/ratings.html)

## Next Steps

1. **Start with Phase 1**: Load and explore hoopR data
2. **Implement basic Elo**: Get something working end-to-end
3. **Validate on single season**: Make sure logic is correct
4. **Tune parameters**: Optimize for predictive accuracy
5. **Expand and enhance**: Add advanced features
6. **Backtest extensively**: Validate on historical data
7. **Deploy to production**: Automate and publish

---

**Ready to begin implementation?** Start with Phase 1: Data Setup
