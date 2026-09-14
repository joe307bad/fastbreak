# Fastbreak API

An F# API built with Saturn framework for managing daily fastbreak games and user profiles. Deployed on Fly.io.

## API Endpoints

### Health Check
**GET /** 
- Returns API health status and timestamp
- Used for monitoring and uptime checks
- No authentication required

### Daily Fastbreak *(Deprecated)*
**GET /api/day/{date}** *(Deprecated - use /day/{yyyymmdd}/schedule instead)*
- Retrieves fastbreak card data for a specific date (format: yyyyMMdd)
- Returns leaderboard, game card, user's locked selections, and stat sheet
- Optional `userId` query parameter for personalized data
- **This endpoint is deprecated and will be removed in a future version**

### Daily Schedule
**GET /day/{yyyymmdd}/schedule**
- Retrieves the fastbreak card for a specific date (format: yyyyMMdd)
- Returns that day's game schedule and betting options
- No authentication required

### Daily Stats & Leaderboard
**GET /day/{yyyymmdd}/stats/{userId}**
- Retrieves weekly leaderboard and user statistics for a specific date
- Weekly leaderboards run Sunday to Sunday - the date determines which week's leaderboard to return
- Returns:
  - Weekly leaderboard for the Sunday-to-Sunday period containing the specified date
  - User's stat sheet for the previous day
- Requires valid userId parameter

### Lock Card Management
**POST /api/lock**
- Saves a user's game selections for the current day
- The `userId` in the request body identifies the user; no authentication
- Creates or updates locked card with user's picks and total points

### User Profile
**POST /api/profile**
- Creates or updates a user profile keyed by the `userId` in the request body
- Updates username; no authentication

**POST /api/profile/initialize**
- With an empty body, creates a new profile with a random username and fresh userId
- With `{ "userId": "..." }`, returns that existing profile and its most recent locked card
- Returns `{userId, userName, lockedFastBreakCard}`

### Development Endpoints
**POST /trigger/all-jobs** *(Debug builds only)*
- Triggers the job dependency chain for data processing
- Runs fastbreak card results → stat sheets → leaderboard calculations
- Restricted to localhost requests only

## Data Flow

There is no authentication. Clients identify themselves by the `userId` they receive from `/api/profile/initialize` and send it in request bodies.

### Initial User Registration Flow
1. **POST /api/profile/initialize** with an empty body
   - Creates new user profile with random username
   - Returns: `{userId, userName, lockedFastBreakCard}`
2. Client stores userId for subsequent requests (send it back to `/api/profile/initialize` to restore the profile later)

### Daily Game Flow
1. **GET /api/day/{date}/schedule**
   - Retrieves daily fastbreak card with games
2. User selects game picks in client
3. **POST /api/lock** with `userId` in the body
   - Saves user's game selections and total points

### Profile Management Flow
1. **POST /api/profile** with `userId` and `userName` in the body
   - Updates the profile's username
   - Returns success/failure response

### Data Relationships
- **User ID** → **Locked Cards** (via locked-fastbreak-cards collection)
- **User ID** → **Stat Sheets** (calculated from historical performance)
- **Date** → **Leaderboard** (calculated daily with both daily and weekly totals, stored by Sunday date)

## Security Issues & Recommended Fixes

⚠️ **CRITICAL**: This API has several security vulnerabilities that should be addressed before production use.

### 1. MongoDB Injection Risks  
**Issue**:
- Direct string concatenation in MongoDB connection string (Program.fs:130)
- User input passed to MongoDB filters without proper sanitization

**Fix**:
- Use MongoDB connection string builder instead of string concatenation
- Validate and sanitize all user inputs before database queries
- Use strongly-typed filters consistently throughout controllers

**Code Changes**:
```fsharp
// In Program.fs - use MongoUrlBuilder
let mongoConnectionString =
    let builder = MongoUrlBuilder()
    builder.Username <- mongoUser
    builder.Password <- mongoPass
    builder.Server <- MongoServerAddress(mongoIp, 27017)
    builder.DatabaseName <- mongoDb
    builder.AuthenticationSource <- "admin"
    builder.DirectConnection <- true
    builder.ToMongoUrl().ToString()
```

### 2. Rate Limiting & DoS Protection
**Issue**:
- No rate limiting on any endpoints
- Background job trigger endpoint could be abused
- No request slockCardApiize limits or timeout controls

**Fix**:
- Add rate limiting middleware to Saturn application
- Implement per-user and per-IP rate limits
- Add request timeout and size limits

**Implementation**:
```fsharp
// Add to Program.fs service configuration
let configureRateLimiting (services: IServiceCollection) =
    services.AddMemoryCache() |> ignore
    // Add rate limiting middleware
    services
```

### 3. Additional Security Hardening
**TODO**:
- Add HTTPS enforcement and HSTS headers
- Implement request logging and audit trails
- Add input validation middleware for all endpoints
- Set up monitoring for suspicious activity patterns
- Add CORS configuration for production domains only
- Add database connection encryption and certificate validation

## Deployment Setup

### Prerequisites

1. Install the Fly CLI: `curl -L https://fly.io/install.sh | sh`
2. Create a Fly.io account: `flyctl auth signup`
3. Create a new app: `flyctl apps create fastbreak-api`

### Environment Variables

The following environment variables need to be set in Fly.io:

```bash
flyctl secrets set MONGO_USER="your_mongo_user"
flyctl secrets set MONGO_PASS="your_mongo_password"
flyctl secrets set MONGO_IP="your_mongo_ip"
flyctl secrets set MONGO_DB="your_mongo_database"
flyctl secrets set ENABLE_DAILY_JOB="1"
flyctl secrets set ENABLE_SCHEDULE_PULLER="1"
```

### GitHub Secrets

Add the following secret to your GitHub repository:

- `FLY_API_TOKEN`: Your Fly.io API token (get it with `flyctl auth token`)

### Manual Deployment

To deploy manually:

```bash
cd api
flyctl deploy
```

### Automatic Deployment

The API automatically deploys when:
- Changes are pushed to the `main` branch in the `/api` folder
- A PR with changes to `/api` is merged into `main`

### Health Check

The API includes a health endpoint at `/` that returns:

```json
{
  "status": "healthy",
  "timestamp": "2023-01-01T00:00:00.000Z"
}
```

### Cost Optimization

The `fly.toml` configuration is optimized for minimal costs:
- Uses `shared-cpu-1x` (cheapest VM)
- 256MB RAM
- Auto-stop/start machines when not in use
- Scales to zero when idle
