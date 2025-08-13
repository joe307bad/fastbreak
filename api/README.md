# Fastbreak API

An F# API built with Saturn framework for managing daily fastbreak games and user profiles. Deployed on Fly.io.

## API Endpoints

### Health Check
**GET /** 
- Returns API health status and timestamp
- Used for monitoring and uptime checks
- No authentication required

### Daily Fastbreak
**GET /api/day/{date}**
- Retrieves fastbreak card data for a specific date (format: yyyyMMdd)
- Returns leaderboard, game card, user's locked selections, and stat sheet
- Optional `userId` query parameter for personalized data

### Lock Card Management
**POST /api/lock**
- Saves user's game selections for the current day
- Requires Google authentication via Authorization header
- Creates or updates locked card with user's picks and total points

### User Profile
**POST /api/profile**
- Creates or updates user profile information
- Requires Google authentication and validates user ownership
- Updates username and maintains Google ID association

**POST /api/profile/initialize/{userId}**
- Initializes new user profile with random username
- Creates unique user ID and links to Google account
- Returns user profile data and any existing locked cards

### Development Endpoints
**POST /trigger/all-jobs** *(Debug builds only)*
- Triggers the job dependency chain for data processing
- Runs fastbreak card results → stat sheets → leaderboard calculations
- Restricted to localhost requests only

## Authentication & Data Flow

### Google Authentication Flow
The API uses Google JWT tokens for authentication. Protected endpoints require an `Authorization: Bearer <google_jwt_token>` header.

### Initial User Registration Flow
1. **Client authenticates with Google** and obtains JWT token
2. **POST /api/profile/initialize/{googleId}** with Authorization header
   - Creates new user profile with random username
   - Links Google ID to internal userId
   - Returns: `{userId, userName, lockedFastBreakCard}`
3. Client stores userId for subsequent requests

### Daily Game Flow
1. **GET /api/day/{date}** (optional userId query param)
   - Retrieves daily fastbreak card with games
   - Returns leaderboard, game options, user's locked selections, stat sheet
   - No authentication required for basic data
2. User selects game picks in client
3. **POST /api/lock** with Authorization header
   - Saves user's game selections and total points
   - Requires authentication to link selections to user

### Profile Management Flow  
1. **POST /api/profile** with Authorization header
   - Updates user profile (username, etc.)
   - Validates user ownership via Google ID matching
   - Returns success/failure response

### Data Relationships
- **Google ID** → **Internal User ID** (via profiles collection)
- **User ID** → **Locked Cards** (via locked-fastbreak-cards collection)  
- **User ID** → **Stat Sheets** (calculated from historical performance)
- **Date** → **Leaderboard** (calculated daily with both daily and weekly totals, stored by Sunday date)

## Security Issues & Recommended Fixes

⚠️ **CRITICAL**: This API has several security vulnerabilities that should be addressed before production use.

### 1. User ID Exposure & Authorization Bypass
**Issue**: 
- `POST /api/profile/initialize/{userId}` expects userId in URL path
- No validation that authenticated user owns the requested userId

**Fix**:
- Remove userId from URL paths - derive from authenticated JWT token instead
- Change `POST /api/profile/initialize/{userId}` to `POST /api/profile/initialize`
- Add ownership validation: check that `googleId` from JWT matches profile's `googleId`

**Code Changes**:
```fsharp

// In ProfileController.fs - remove userId parameter  
post "/profile/initialize" (initializeMyProfileHandler database)
```

### 2. Google ID Privacy Leak
**Issue**:
- Google IDs exposed in URL paths (`/api/profile/initialize/{googleId}`)
- Google IDs logged in server logs, browser history, referrer headers
- Violates user privacy and Google's best practices

**Fix**:
- Extract Google ID from validated JWT token only (already available in `ctx.Items["GoogleUser"]`)
- Never pass Google IDs in URLs or request bodies
- Update ProfileController.fs:95 to remove userId parameter matching

### 3. JWT Token Validation Issues
**Issue**:
- No audience validation on Google JWT tokens in `googleAuthPipeline.fs:12`
- Missing issuer verification beyond Google's signature
- No token expiration enforcement in application code

**Fix**:
```fsharp
// In googleAuthPipeline.fs - add proper validation
let validateGoogleToken (idToken: string) =
    task {
        try
            let validationSettings = GoogleJsonWebSignature.ValidationSettings()
            validationSettings.Audience <- [| "YOUR_GOOGLE_CLIENT_ID" |]
            validationSettings.IssuedAtClockTolerance <- TimeSpan.FromMinutes(5.0)
            let! payload = GoogleJsonWebSignature.ValidateAsync(idToken, validationSettings)
            return Some payload
        with ex ->
            return None
    }
```

### 4. MongoDB Injection Risks  
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

### 5. Information Disclosure
**Issue**:
- Different error responses reveal system information (404 vs 401 in googleAuthPipeline.fs:43)
- Debug endpoints exposed that could leak internal state
- Inconsistent error handling across controllers

**Fix**:
- Standardize all authentication failures to return 401 with generic message
- Remove or secure debug endpoints in production builds
- Implement consistent error response format across all endpoints
- Add request/response logging for security monitoring

**Code Changes**:
```fsharp
// In googleAuthPipeline.fs - standardize error responses
| _ ->
    ctx.SetStatusCode 401
    ctx.WriteJsonAsync({| error = "Authentication required" |}) |> ignore
    return Some(ctx)
```

### 6. Rate Limiting & DoS Protection
**Issue**:
- No rate limiting on any endpoints
- Background job trigger endpoint could be abused
- No request slockCardApiize limits or timeout controls

**Fix**:
- Add rate limiting middleware to Saturn application
- Implement per-user and per-IP rate limits
- Add request timeout and size limits
- Secure job trigger endpoints with additional authentication

**Implementation**:
```fsharp
// Add to Program.fs service configuration
let configureRateLimiting (services: IServiceCollection) =
    services.AddMemoryCache() |> ignore
    // Add rate limiting middleware
    services
```

### 7. Additional Security Hardening
**TODO**:
- Add HTTPS enforcement and HSTS headers
- Implement request logging and audit trails
- Add input validation middleware for all endpoints
- Set up monitoring for suspicious activity patterns
- Add CORS configuration for production domains only
- Implement proper session management and token refresh
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
