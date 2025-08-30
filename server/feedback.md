# Elo+ Blog Post Feedback Discussion

## Original Feedback Points

### 1. More Math Details on ML Model Adjustments
**Feedback**: "I would be interested in more of the math in terms of exactly how the ML model adjustments are made. There are a number of ways I could imagine such an adjustment being made and in your formulation it's not obvious based on what you wrote."

**Response Plan**: Expand on the math and how the adjustments are made.

### 2. Train/Validation/Test Split
**Feedback**: "Also id assume this set up would require an additional validation set of data- so train/validation/test. Where you are using the train data to get initial elo estimates and train the ml model and then presumably the validation data to tune the ML model adjustment factor."

**Your Question**: "My Elo+ setup requires an additional validation set of data. Can you elaborate on this? The only set of data I used was all MLB games from 2024 + the point in time sabermetrics for pitching/batting for each team. I trained it on 80% of the games and then tested it on the last 20% to get the 8% over baseline."

**Detailed Explanation from Commenter**:
- Assumes your model is: `P_final = f(p_elo, ML)` or `P_final = p_elo + α*g(ML)`
- This is a "tilting model" where you start with baseline Elo and tilt towards ML predictions
- The issue: using same data to train both Elo and ML models AND determine tilting parameter α could lead to overfitting
- Suggested split: 65% train, 15% validation, 20% test
    1. Use training data to fit both Elo and ML models
    2. Use validation data to estimate hyperparameters (α tilting parameter)
    3. Evaluate final predictions on test data

### 3. Better Baseline Comparison
**Feedback**: "Finally it's great to have a simple baseline but if you are pitching this as an extension of elo I also want a fair comparison with a more vanilla elo system - otherwise it's not clear if the added complexity is worth the squeeze."

**Response Plan**: "Alter the baseline to compare against using pure Elo instead of picking the home team every time"

## Referenced Paper
https://www.sciencedirect.com/science/article/abs/pii/S0169207020300157
- About extending traditional Elo to margin of victory
- Could replace margin of victory with function of subsequent model evaluation
- Shows there are many ways to make Elo extensions

## Action Items

### 1. [ ] Add more detailed math on ML model adjustments

**Research Findings on Mathematical Formulations:**

**Standard Elo Formula Foundation:**
- Core Elo update: `New Rating = Old Rating + K × (Actual Result – Expected Result)`
- Expected probability: `P1 = 1.0 / (1.0 + 10^((rating2 - rating1) / 400))`
- Statistical interpretation: Elo ratings are weights in logistic regression learned through SGD-like updates

**Hybrid Model Mathematical Approaches:**

**Tilting Parameter α Formulations:**
Your Elo+ system appears to use: `P_final = f(p_elo, ML)` or `P_final = p_elo + α*g(ML)`

**Related Mathematical Extensions from Literature:**
1. **Linear Model**: Direct MOV prediction with `E[MOV] = β(R_A - R_B)`
2. **Joint Additive**: `E[Y] = logistic(R_A - R_B)` and `E[MOV] = γ(R_A - R_B)`
3. **Multiplicative**: Uses MOV to moderate learning rate: `K_new = K * f(MOV)`
4. **Logistic**: MOV-dependent K-factor through logistic transformation

**Specific Mathematical Detail Needs:**
- Derivation of optimal α (tilting parameter)
- Gradient computation for hybrid loss function
- Convergence properties of the combined system
- Stability analysis of the rating updates

### 2. [ ] Clarify the train/validation/test split approach

**Research Findings on Data Splitting:**

**Three-Way Split Methodology (per commenter's suggestion):**
- **Training Set (65%)**: Fit both Elo ratings AND ML model parameters
- **Validation Set (15%)**: Tune hyperparameters, specifically the tilting parameter α
- **Test Set (20%)**: Final unbiased evaluation

**Why This Prevents Overfitting:**
- Using same data for both Elo training and α tuning creates **data leakage**
- The α parameter learns patterns specific to the training set
- Validation set provides independent data for hyperparameter optimization

**Implementation Strategy:**
1. Split 2024 MLB games: 65%/15%/20% chronologically or randomly
2. Train Elo ratings on training set only
3. Train ML model on training set with same game outcomes
4. Use validation set to optimize α via grid search or gradient descent
5. Final evaluation on test set for unbiased performance estimate

**Cross-Validation Enhancement:**
- For small datasets, use nested cross-validation
- Outer CV for model evaluation, inner CV for hyperparameter tuning
- Prevents overfitting while maintaining robust performance estimates

### 3. [ ] Compare against vanilla Elo system instead of just home team baseline

**Research Findings on Vanilla Elo Baselines:**

**Standard MLB Elo Implementation:**
- **FiveThirtyEight Standard**: K-factor of 4 for MLB (Nate Silver's recommendation)
- **Starting Rating**: Typically 1500 (arbitrary baseline)
- **Update Formula**: Standard logistic probability with 400-point scale
- **Regression to Mean**: Often includes seasonal reset mechanisms

**Implementation Details:**
- **Home Field Advantage**: Usually 25-30 Elo points for home team
- **Pitching Adjustments**: Some systems adjust for starting pitcher quality
- **Season Boundaries**: Ratings often regress toward mean between seasons

**Comparison Metrics:**
- **Log-loss**: Standard probabilistic scoring metric
- **Brier Score**: Measures calibration and discrimination
- **ROC-AUC**: Area under receiver operating curve
- **Accuracy**: Simple correct prediction percentage

**Expected Performance Baseline:**
- Vanilla Elo in MLB typically achieves 52-56% accuracy
- Your 8% improvement over "home team every time" baseline needs context
- Home team baseline in MLB is approximately 54% (home field advantage)
- So your current performance is likely ~58.3%, which is strong but needs comparison to vanilla Elo

### 4. [ ] Review the referenced paper for additional context

**Paper Summary: "Extension of the Elo rating system to margin of victory" (Kovalchik, 2020)**

**Key Findings:**
- **Four MOV Extension Methods**: Linear, Joint Additive, Multiplicative, Logistic
- **Performance Results**: All MOV approaches improved prediction vs standard Elo
- **Best Performer**: Joint additive model - only one with unbiased ratings and stable variance
- **Tennis Application**: Achieved ~70% accuracy (compared to ~65% standard Elo)

**Mathematical Framework Relevance:**
- **Joint Additive Model**: Most similar to your Elo+ approach
    - Maintains standard Elo estimation step
    - Adds separate MOV prediction component
    - Could replace MOV with ML model predictions

**Parameter Optimization Insights:**
- **Multi-parameter Tuning**: Used weighted RMSE objective function
- **Inverse Relationships**: Learning rates inversely related to MOV variance
- **Stability**: Only joint additive maintained rating stability over time

**Applicability to Elo+:**
- Your ML predictions could replace "margin of victory" in their framework
- Joint additive approach suggests maintaining separate Elo and ML components
- Paper's stability analysis provides validation methodology for your α parameter

**Key Differences:**
- Their MOV is post-game information; your ML uses pre-game features
- Their focus was tennis; your application is MLB with different dynamics
- Their margin measures performance magnitude; your ML predicts win probability

## Research-Based Questions and Clarifications

### Mathematical Formulation Questions

1. **Tilting Parameter α Optimization:**
    - What is the mathematical derivation for the optimal α value?
    - Is α learned through gradient descent on the validation set loss?
    - Should α be constant or adaptive (e.g., game-dependent or time-varying)?

2. **Loss Function Design:**
    - What loss function combines Elo and ML predictions? Cross-entropy? Weighted combination?
    - How do you handle the different scales/ranges of Elo probabilities vs ML probabilities?
    - Is there regularization to prevent α from becoming too extreme?

3. **Convergence and Stability:**
    - How do you ensure the combined system converges to stable ratings?
    - What happens to Elo ratings when ML model predictions are consistently better/worse?
    - Does the system maintain Elo's zero-sum property (total rating points conserved)?

### Implementation and Validation Questions

4. **Data Temporal Structure:**
    - Should the 65%/15%/20% split be chronological (respecting time) or random?
    - How do you handle season boundaries and player/team changes?
    - What about mid-season roster changes affecting team ML features?

5. **Feature Engineering:**
    - Which sabermetrics features are most predictive in your ML model?
    - How do you handle missing or outdated statistics early in the season?
    - Do you use rolling windows or season-to-date statistics?

6. **Baseline Comparison:**
    - What exact vanilla Elo implementation should be the comparison baseline?
    - Should it include home field advantage? Pitcher adjustments? Seasonal regression?
    - How do you ensure fair comparison (same data, same evaluation metrics)?

### Methodological Questions

7. **Overfitting Detection:**
    - How do you detect if the α parameter is overfitting to the validation set?
    - What early stopping criteria prevent overtuning?
    - How do you validate that the 8% improvement isn't just data leakage?

8. **Cross-Validation Strategy:**
    - Given the temporal nature of baseball, should you use time series CV instead of random CV?
    - How many folds and what validation strategy prevents look-ahead bias?
    - What's the minimum dataset size needed for reliable α estimation?

9. **Model Interpretability:**
    - How do you explain when/why the system trusts ML vs Elo more?
    - Can you identify which types of games benefit most from the hybrid approach?
    - What team/game characteristics make the ML model most valuable?

### Performance and Practical Questions

10. **Evaluation Metrics:**
    - Beyond accuracy, what other metrics validate the system (calibration, log-loss, etc.)?
    - How do you measure prediction confidence and uncertainty?
    - Should performance be evaluated differently for different game situations?

11. **Real-time Application:**
    - How frequently are Elo ratings updated? After each game? In batches?
    - How do you handle the computational cost of retraining α frequently?
    - What happens when ML model features become stale or unavailable?

12. **Generalization:**
    - Does the approach work for other sports beyond MLB?
    - How sensitive is performance to different ML model choices?
    - Could this extend beyond sports to other competitive domains?

### Future Research Directions

13. **Advanced Extensions:**
    - Could you use multiple α parameters for different game contexts (playoff vs regular season)?
    - What about ensemble approaches combining multiple ML models?
    - How might this integrate with betting market predictions or crowd wisdom?

14. **Theoretical Foundation:**
    - Is there a theoretical framework that justifies the optimal mixing of Elo and ML?
    - Under what conditions should you expect improvement over vanilla Elo?
    - What are the theoretical limits of such hybrid approaches?

## Next Steps Based on Research

### Immediate Actions:
1. **Implement vanilla Elo baseline** with standard MLB parameters (K=4, HFA=30)
2. **Restructure data split** to proper 65%/15%/20% methodology
3. **Add mathematical derivation** of α optimization in blog post
4. **Compare multiple evaluation metrics** beyond simple accuracy

### Blog Post Improvements:
1. **Mathematical Section**: Detail the hybrid loss function and α derivation
2. **Methodology Section**: Explain the three-way data split and why it prevents overfitting
3. **Results Section**: Compare against vanilla Elo, not just home team baseline
4. **Discussion Section**: Address stability, convergence, and when the approach works best

### Validation Experiments:
1. **Ablation Study**: Performance with different α values and ML model combinations
2. **Temporal Analysis**: How performance varies across different parts of the season
3. **Feature Importance**: Which sabermetrics contribute most to the ML component's value