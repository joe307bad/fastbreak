namespace Fastbreak.Shared.Utils

open System.IO
open System.Text
open System.Text.Json
open Microsoft.AspNetCore.Http

module DeserializeBody =

    let getRawBody (ctx: HttpContext) =
        task {
            ctx.Request.EnableBuffering()
            use reader = new StreamReader(ctx.Request.Body, Encoding.UTF8, true, 1024, true)
            let! body = reader.ReadToEndAsync()
            ctx.Request.Body.Position <- 0L
            return body
        }

    let deserializeBody<'T> (ctx: HttpContext) =
        task {
            let! body = getRawBody ctx
            let options = JsonSerializerOptions(PropertyNameCaseInsensitive = true)
            let result = JsonSerializer.Deserialize<'T>(body, options)
            return result
        }
