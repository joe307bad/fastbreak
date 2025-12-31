# TEAM STATS (weekly)
- cum_epa (cumulative week to week) # nflreadr
- def_epa # nflreadr
- off_epa # nflreadr
- passing_epa # nflreadr
- rushing_epa # nflreadr
- passing_cpoe # nflreadr
- receiving_epa # nflreadr
- pacr # nflreadr
- passing_first_downs # nflreadr
- sacks_suffered # nflreadr
- def_sacks # nflreadr
- turnover_margin (def_interceptions + fumble_recovery_opp - passing_interceptions - rushing_fumbles_lost - receiving_fumbles_lost) # nflreadr (calculated)

# PLAYER STATS (season aggregates)
QB (top 1)
- dakota # nflreadr
- passing_epa # nflreadr
- passing_cpoe # nflreadr
- pacr # nflreadr
- passing_air_yards # nflreadr

RB (top 2)
- rushing_epa # nflreadr
- rushing_first_downs # nflreadr
- carries # nflreadr
- receiving_epa # nflreadr
- target_share # nflreadr

WR/TE (top 3)
- wopr # nflreadr
- receiving_epa # nflreadr
- racr # nflreadr
- target_share # nflreadr
- air_yards_share # nflreadr

# MATCHUP META
- odds (spread, ml, over_under) # ESPN API: https://site.api.espn.com/apis/site/v2/sports/football/nfl/scoreboard (events > competitions > odds)
- h2h_record # nflreadr load_schedules()
- common_opponents (opponent, week, result, score for each team) # nflreadr load_schedules()

# STEPS
1. load nflreadr weekly team stats (2025 season) for all teams
2. calc season totals for all teams, rank each stat across nfl
3. calc cum_epa, turnover_margin per team per week
4. load player stats (2025), filter to season totals
5. filter players by min snaps/game (QB: 20, RB: 10, WR/TE: 15)
6. rank all filtered players by position across nfl for each stat
7. for each team: take top N players by position
8. load matchup data (2025 teams)
9. build nested json per matchup with ranks
10. write output json

# OUTPUT JSON
```json
{
  "hou-lac": {
    "odds": { "spread": -3, "ml": -150, "over_under": 47.5 },
    "h2h_record": "1-1",
    "common_opponents": [
      {
        "opponent": "KC",
        "hou": { "week": 16, "result": "W", "score": "27-19" },
        "lac": { "week": 14, "result": "L", "score": "17-19" }
      }
    ],
    "hou": {
      "team_stats": {
        "cum_epa_by_week": {
          "week-1": 0.5,
          "week-2": 1.2,
          "week-18": 15.3
        },
        "current": {
          "def_epa": { "value": -0.2, "rank": 5 },
          "off_epa": { "value": 0.7, "rank": 12 },
          "passing_epa": { "value": 45.2, "rank": 8 },
          "rushing_epa": { "value": 12.1, "rank": 15 },
          "passing_cpoe": { "value": 0.03, "rank": 10 },
          "receiving_epa": { "value": 22.5, "rank": 9 },
          "pacr": { "value": 0.85, "rank": 14 },
          "passing_first_downs": { "value": 185, "rank": 11 },
          "sacks_suffered": { "value": 32, "rank": 6 },
          "def_sacks": { "value": 48, "rank": 3 },
          "turnover_margin": { "value": 5, "rank": 7 }
        }
      },
      "players": {
        "qb": {
          "name": "CJ Stroud",
          "dakota": { "value": 0.15, "rank": 8 },
          "passing_epa": { "value": 45.2, "rank": 6 },
          "passing_cpoe": { "value": 0.03, "rank": 12 },
          "pacr": { "value": 0.85, "rank": 15 },
          "passing_air_yards": { "value": 2847, "rank": 10 }
        },
        "rbs": [
          {
            "name": "Joe Mixon",
            "rushing_epa": { "value": 12.3, "rank": 5 },
            "rushing_first_downs": { "value": 48, "rank": 8 },
            "carries": { "value": 245, "rank": 4 },
            "receiving_epa": { "value": 5.2, "rank": 12 },
            "target_share": { "value": 0.12, "rank": 18 }
          }
        ],
        "receivers": [
          {
            "name": "Nico Collins",
            "wopr": { "value": 0.72, "rank": 11 },
            "receiving_epa": { "value": 22.5, "rank": 7 },
            "racr": { "value": 0.88, "rank": 15 },
            "target_share": { "value": 0.24, "rank": 9 },
            "air_yards_share": { "value": 0.32, "rank": 6 }
          }
        ]
      }
    },
    "lac": { "..." }
  }
}
```