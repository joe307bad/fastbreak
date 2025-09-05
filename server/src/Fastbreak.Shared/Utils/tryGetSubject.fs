namespace Fastbreak.Shared.Utils

open Microsoft.AspNetCore.Http
open System.Reflection

module TryGetSubject =

    let tryGetSubject (ctx: HttpContext) : string option =
        match ctx.Items.TryGetValue("GoogleUser") with
        | true, value when value <> null ->
            let prop =
                value.GetType().GetProperty("Subject", BindingFlags.Public ||| BindingFlags.Instance)

            match prop with
            | null -> None
            | _ ->
                match prop.GetValue(value) with
                | :? string as subjectStr -> Some subjectStr
                | _ -> None
        | _ -> None
