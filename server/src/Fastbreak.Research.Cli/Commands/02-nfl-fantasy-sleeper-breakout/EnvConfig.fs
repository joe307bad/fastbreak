namespace Fastbreak.Research.Cli.Commands.NflFantasyBreakoutPredict

open System
open System.IO

module EnvConfig =

    let loadEnvFile () =
        let envPath = Path.Combine(__SOURCE_DIRECTORY__, ".env")
        if File.Exists(envPath) then
            File.ReadAllLines(envPath)
            |> Array.filter (fun line ->
                not (String.IsNullOrWhiteSpace(line)) &&
                not (line.TrimStart().StartsWith("#")) &&
                line.Contains("="))
            |> Array.iter (fun line ->
                let parts = line.Split('=', 2)
                if parts.Length = 2 then
                    Environment.SetEnvironmentVariable(parts.[0].Trim(), parts.[1].Trim()))
