### Sleeper Score Calculation (Max: 175 points)

The sleeper score is calculated by combining six weighted components:

**1. Draft Value (0-50 points)**
- Undrafted Free Agent (UDFA): 50 points
- Draft pick > 200: 40 points
- Draft pick > 150: 30 points
- Draft pick > 100: 20 points
- Draft pick > 50: 10 points
- Draft pick ≤ 50: 0 points

**2. Performance Score (0-30 points)**
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