namespace Fastbreak.Cli.Services

open System
open System.IO
open System.Globalization
open Fastbreak.Cli.Entities

module CsvParser =
    
    type CsvParseResult =
        | Success of GameData
        | ParseError of string * int * string // error message, line number, raw line
    
    type CsvLoadResult =
        | LoadSuccess of GameData list * int // games, error count
        | FileNotFound of string
        | FileError of string
    
    let private parseFloat (str: string) =
        if String.IsNullOrWhiteSpace(str) then None
        else
            match Double.TryParse(str.Trim(), NumberStyles.Float, CultureInfo.InvariantCulture) with
            | (true, value) -> Some value
            | _ -> None
    
    let private parseInt (str: string) =
        if String.IsNullOrWhiteSpace(str) then None
        else
            match Int32.TryParse(str.Trim()) with
            | (true, value) -> Some value
            | _ -> None
    
    let private parseDate (str: string) =
        if String.IsNullOrWhiteSpace(str) then None
        else
            match DateTime.TryParse(str.Trim()) with
            | (true, date) -> Some date
            | _ -> None
    
    let private parseGameFromCsvLine (lineNumber: int) (line: string) : CsvParseResult =
        try
            if String.IsNullOrWhiteSpace(line) then
                ParseError("Empty line", lineNumber, line)
            else
                let fields = line.Split(',') |> Array.map (fun s -> s.Trim())
                
                if fields.Length < 6 then
                    ParseError($"Insufficient columns: expected at least 6, found {fields.Length}", lineNumber, line)
                else
                    // Required fields
                    let gameId = if String.IsNullOrWhiteSpace(fields.[0]) then $"game-{lineNumber}" else fields.[0]
                    let date = parseDate fields.[1]
                    let homeTeam = fields.[2]
                    let awayTeam = fields.[3]
                    let homeScore = parseInt fields.[4]
                    let awayScore = parseInt fields.[5]
                    
                    match date, homeScore, awayScore with
                    | Some d, Some hs, Some as_ when not (String.IsNullOrWhiteSpace(homeTeam)) && not (String.IsNullOrWhiteSpace(awayTeam)) ->
                        // Optional fields with safe parsing
                        let weather = 
                            if fields.Length > 9 then
                                match parseFloat fields.[6], parseFloat fields.[7], parseFloat fields.[9] with
                                | Some temp, Some wind, Some precip when fields.Length > 8 && not (String.IsNullOrWhiteSpace(fields.[8])) ->
                                    Some {
                                        Temperature = temp
                                        WindSpeed = wind
                                        WindDirection = fields.[8]
                                        Precipitation = precip
                                    }
                                | _ -> None
                            else None
                        
                        let homePitcher =
                            if fields.Length > 15 then
                                match parseFloat fields.[11], parseFloat fields.[12], parseInt fields.[13], parseInt fields.[14], parseFloat fields.[15] with
                                | Some era, Some whip, Some k, Some bb, Some ip when not (String.IsNullOrWhiteSpace(fields.[10])) ->
                                    Some {
                                        Name = fields.[10]
                                        ERA = era
                                        WHIP = whip
                                        Strikeouts = k
                                        Walks = bb
                                        InningsPitched = ip
                                    }
                                | _ -> None
                            else None
                        
                        let awayPitcher =
                            if fields.Length > 21 then
                                match parseFloat fields.[17], parseFloat fields.[18], parseInt fields.[19], parseInt fields.[20], parseFloat fields.[21] with
                                | Some era, Some whip, Some k, Some bb, Some ip when not (String.IsNullOrWhiteSpace(fields.[16])) ->
                                    Some {
                                        Name = fields.[16]
                                        ERA = era
                                        WHIP = whip
                                        Strikeouts = k
                                        Walks = bb
                                        InningsPitched = ip
                                    }
                                | _ -> None
                            else None
                        
                        let metrics =
                            if fields.Length > 27 then
                                match parseFloat fields.[22], parseFloat fields.[23], parseFloat fields.[24], parseFloat fields.[25], parseFloat fields.[26], parseFloat fields.[27] with
                                | Some hops, Some aops, Some herp, Some aerp, Some hfip, Some afip ->
                                    Some {
                                        HomeOPS = hops
                                        AwayOPS = aops
                                        HomeERAPlus = herp
                                        AwayERAPlus = aerp
                                        HomeFIP = hfip
                                        AwayFIP = afip
                                    }
                                | _ -> None
                            else None
                        
                        let gameData = {
                            GameId = gameId
                            HomeTeam = homeTeam
                            AwayTeam = awayTeam
                            HomeScore = hs
                            AwayScore = as_
                            Date = d
                            Weather = weather
                            HomePitcher = homePitcher
                            AwayPitcher = awayPitcher
                            Metrics = metrics
                        }
                        
                        Success gameData
                    | _ ->
                        ParseError("Failed to parse required fields (date, homeTeam, awayTeam, scores)", lineNumber, line)
        with
        | ex -> ParseError($"Parse exception: {ex.Message}", lineNumber, line)
    
    let loadGamesFromCsv (filePath: string) : CsvLoadResult =
        try
            if not (File.Exists(filePath)) then
                FileNotFound($"CSV file not found: {filePath}")
            else
                let mutable validGames = []
                let mutable errorCount = 0
                let mutable lineNumber = 0
                
                use reader = new StreamReader(filePath)
                
                // Skip header if present
                if not reader.EndOfStream then
                    let firstLine = reader.ReadLine()
                    lineNumber <- lineNumber + 1
                    
                    // Check if first line looks like a header
                    if not (firstLine.ToLower().Contains("gameid") || firstLine.ToLower().Contains("date")) then
                        // Process first line as data
                        match parseGameFromCsvLine lineNumber firstLine with
                        | Success game -> validGames <- game :: validGames
                        | ParseError (msg, line, raw) -> 
                            printfn "Line %d parse error: %s" line msg
                            errorCount <- errorCount + 1
                
                // Process remaining lines
                while not reader.EndOfStream do
                    let line = reader.ReadLine()
                    lineNumber <- lineNumber + 1
                    
                    if not (String.IsNullOrWhiteSpace(line)) then
                        match parseGameFromCsvLine lineNumber line with
                        | Success game -> validGames <- game :: validGames
                        | ParseError (msg, line, raw) -> 
                            printfn "Line %d parse error: %s" line msg
                            errorCount <- errorCount + 1
                
                LoadSuccess(List.rev validGames, errorCount)
        with
        | :? FileNotFoundException -> FileNotFound($"CSV file not found: {filePath}")
        | :? UnauthorizedAccessException -> FileError($"Access denied to file: {filePath}")
        | :? DirectoryNotFoundException -> FileError($"Directory not found for file: {filePath}")
        | ex -> FileError($"Error reading CSV file: {ex.Message}")
    
    let validateCsvFile (filePath: string) : Result<unit, string> =
        if String.IsNullOrWhiteSpace(filePath) then
            Error "File path cannot be empty"
        elif not (File.Exists(filePath)) then
            Error $"File not found: {filePath}"
        elif not (Path.GetExtension(filePath).ToLower() = ".csv") then
            Error $"File must be a CSV file (found extension: {Path.GetExtension(filePath)})"
        else
            try
                use reader = new StreamReader(filePath)
                let firstLine = reader.ReadLine()
                if String.IsNullOrEmpty(firstLine) then
                    Error "CSV file is empty"
                else
                    Ok ()
            with
            | :? UnauthorizedAccessException -> Error $"Access denied: {filePath}"
            | ex -> Error $"Cannot read file: {ex.Message}"