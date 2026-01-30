# Fastbreak.Daily

A minimal F# console application that uses Google Gemini AI to fetch daily sports news talking points with grounded search.

## Features

- Connects to Google Gemini API (gemini-1.5-flash model) via REST
- Uses Google Search grounding for real-time sports news
- Returns structured JSON output with 3 talking points
- Covers all major sports (NFL, NBA, NHL, MLB, Soccer, etc.)
- **No external SDKs required** - uses built-in .NET HttpClient

## Prerequisites

- .NET 9.0 SDK
- Google AI Studio API key (from https://aistudio.google.com/apikey)

## Setup

### 1. Get Your Gemini API Key

1. Go to [Google AI Studio](https://aistudio.google.com/apikey)
2. Click "Create API Key"
3. Copy your API key

### 2. Configure API Key

**Using .env file** (Recommended):
```bash
# Copy the example file
cp .env.example .env

# Edit .env and add your Gemini API key
GEMINI_API_KEY=your_actual_api_key_here
```

**Or set as environment variable:**
```bash
export GEMINI_API_KEY=your_actual_api_key_here
```

## Build

```bash
dotnet build
```

## Run

```bash
# If using .env file, source it first
set -a && source .env && set +a

# Run the application
dotnet run --project server/src/Fastbreak.Daily/Fastbreak.Daily.fsproj
```

## Output Format

The application returns JSON in the following format:

```json
{
  "date": "2026-01-26",
  "talkingPoints": [
    {
      "title": "Brief headline here",
      "description": "2-3 sentence explanation of the news with details from web search",
      "source": "Source of the information"
    },
    {
      "title": "Another headline",
      "description": "More details about this story from current news",
      "source": "Another source"
    },
    {
      "title": "Third headline",
      "description": "Final story details from today's sports news",
      "source": "Third source"
    }
  ]
}
```

## How It Works

1. **Google Search Grounding**: The application uses Gemini's built-in Google Search grounding feature
2. **Real-time Data**: Gemini searches the web for current sports news
3. **Structured Output**: AI formats the results into clean JSON with talking points
4. **Multi-sport Coverage**: Automatically finds the most important stories across all major sports

## Troubleshooting

### Error: "GEMINI_API_KEY environment variable not set"

Make sure you've set the API key in your .env file or environment variables.

### Error: "API request failed with status 400"

- Check that your API key is valid
- Ensure you copied the entire key without spaces
- Try regenerating your API key in AI Studio

### Error: "API request failed with status 429"

You've hit the rate limit. Wait a few moments and try again, or check your quota in AI Studio.

## Notes

- Uses **Gemini 1.5 Flash** model (gemini-1.5-flash) - stable version with better free tier quota
- **Google Search grounding** enabled for real-time news retrieval
- **No SDK needed** - Google doesn't have a stable official .NET SDK for Gemini yet, so this uses the REST API directly via HttpClient
- The REST API approach is simpler, has no dependencies, and gives you full control
- Free tier available in Google AI Studio
- Rate limits apply based on your API key tier
- Search grounding provides up-to-date sports news from across the web
