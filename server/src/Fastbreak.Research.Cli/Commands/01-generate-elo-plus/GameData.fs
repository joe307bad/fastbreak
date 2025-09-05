namespace Fastbreak.Research.Cli.Commands.GenerateEloPlus

open System


type PitcherData = {
    Name: string
    ERA: float
    WHIP: float
    Strikeouts: int
    Walks: int
    InningsPitched: float
}

type SabrMetrics = {
    HomeOPS: float
    AwayOPS: float
    HomeERAPlus: float
    AwayERAPlus: float
    HomeFIP: float
    AwayFIP: float
}

type GameData = {
    GameId: string
    HomeTeam: string
    AwayTeam: string
    HomeScore: int
    AwayScore: int
    Date: DateTime
    HomePitcher: PitcherData option
    AwayPitcher: PitcherData option
    Metrics: SabrMetrics option
}

type EloRating = {
    Team: string
    StandardElo: decimal
    EloPlus: decimal option
    LastUpdated: DateTime
}

module SampleData =
    
    
    let samplePitcher1 = {
        Name = "Ace Starter"
        ERA = 2.85
        WHIP = 1.12
        Strikeouts = 185
        Walks = 45
        InningsPitched = 210.1
    }
    
    let samplePitcher2 = {
        Name = "Control Lefty"
        ERA = 3.42
        WHIP = 1.25
        Strikeouts = 142
        Walks = 38
        InningsPitched = 178.2
    }
    
    let sampleMetrics1 = {
        HomeOPS = 0.785
        AwayOPS = 0.732
        HomeERAPlus = 112
        AwayERAPlus = 98
        HomeFIP = 3.45
        AwayFIP = 4.12
    }
    
    let sampleMetrics2 = {
        HomeOPS = 0.698
        AwayOPS = 0.801
        HomeERAPlus = 95
        AwayERAPlus = 118
        HomeFIP = 4.23
        AwayFIP = 3.67
    }
    
    let sampleGames = [
        {
            GameId = "2024-04-15-NYY-BOS"
            HomeTeam = "Boston Red Sox"
            AwayTeam = "New York Yankees"
            HomeScore = 7
            AwayScore = 4
            Date = DateTime(2024, 4, 15)
            HomePitcher = Some samplePitcher1
            AwayPitcher = Some samplePitcher2
            Metrics = Some sampleMetrics1
        }
        {
            GameId = "2024-04-16-NYY-BOS"
            HomeTeam = "Boston Red Sox"
            AwayTeam = "New York Yankees"
            HomeScore = 3
            AwayScore = 8
            Date = DateTime(2024, 4, 16)
            HomePitcher = Some samplePitcher2
            AwayPitcher = Some samplePitcher1
            Metrics = Some sampleMetrics2
        }
        {
            GameId = "2024-04-20-LAD-SF"
            HomeTeam = "San Francisco Giants"
            AwayTeam = "Los Angeles Dodgers"
            HomeScore = 5
            AwayScore = 2
            Date = DateTime(2024, 4, 20)
            HomePitcher = Some { Name = "West Coast Ace"; ERA = 2.95; WHIP = 1.08; Strikeouts = 198; Walks = 42; InningsPitched = 195.1 }
            AwayPitcher = Some { Name = "Power Righty"; ERA = 3.15; WHIP = 1.18; Strikeouts = 215; Walks = 55; InningsPitched = 201.2 }
            Metrics = Some { HomeOPS = 0.756; AwayOPS = 0.823; HomeERAPlus = 108; AwayERAPlus = 105; HomeFIP = 3.25; AwayFIP = 3.38 }
        }
        {
            GameId = "2024-04-21-LAD-SF"
            HomeTeam = "San Francisco Giants"
            AwayTeam = "Los Angeles Dodgers"
            HomeScore = 1
            AwayScore = 9
            Date = DateTime(2024, 4, 21)
            HomePitcher = Some { Name = "Struggling Starter"; ERA = 4.85; WHIP = 1.45; Strikeouts = 95; Walks = 65; InningsPitched = 125.0 }
            AwayPitcher = Some { Name = "Cy Young Candidate"; ERA = 2.12; WHIP = 0.95; Strikeouts = 245; Walks = 35; InningsPitched = 215.1 }
            Metrics = Some { HomeOPS = 0.645; AwayOPS = 0.887; HomeERAPlus = 85; AwayERAPlus = 135; HomeFIP = 4.95; AwayFIP = 2.85 }
        }
        {
            GameId = "2024-05-01-HOU-TEX"
            HomeTeam = "Texas Rangers"
            AwayTeam = "Houston Astros"
            HomeScore = 6
            AwayScore = 5
            Date = DateTime(2024, 5, 1)
            HomePitcher = Some { Name = "Texas Heat"; ERA = 3.65; WHIP = 1.28; Strikeouts = 155; Walks = 48; InningsPitched = 167.1 }
            AwayPitcher = Some { Name = "Houston Rocket"; ERA = 3.21; WHIP = 1.15; Strikeouts = 178; Walks = 41; InningsPitched = 189.2 }
            Metrics = Some { HomeOPS = 0.778; AwayOPS = 0.745; HomeERAPlus = 102; AwayERAPlus = 108; HomeFIP = 3.78; AwayFIP = 3.55 }
        }
    ]