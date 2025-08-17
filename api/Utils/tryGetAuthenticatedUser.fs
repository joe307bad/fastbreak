module api.Utils.tryGetAuthenticatedUser

open Microsoft.AspNetCore.Http
open api.Utils.tokenAuthPipeline

let tryGetAuthenticatedUser (ctx: HttpContext) : AuthenticatedUser option =
    match ctx.Items.TryGetValue("AuthenticatedUser") with
    | true, value when value <> null ->
        match value with
        | :? AuthenticatedUser as user -> Some user
        | _ -> None
    | _ -> None

let tryGetUserId (ctx: HttpContext) : string option =
    match tryGetAuthenticatedUser ctx with
    | Some user -> Some user.UserId
    | None -> None

let tryGetUserEmail (ctx: HttpContext) : string option =
    match tryGetAuthenticatedUser ctx with
    | Some user -> Some user.Email
    | None -> None