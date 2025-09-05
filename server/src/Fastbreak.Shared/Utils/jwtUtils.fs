namespace Fastbreak.Shared.Utils
open System
open System.Text
open System.IdentityModel.Tokens.Jwt
open System.Security.Claims
open Microsoft.IdentityModel.Tokens

module JwtUtils =

    let private getJwtSecret () =
        Environment.GetEnvironmentVariable("JWT_SECRET") 
        |> Option.ofObj 
        |> Option.defaultValue "your-super-secret-jwt-key-change-this-in-production"

    let private getJwtExpiry () =
        Environment.GetEnvironmentVariable("JWT_EXPIRY_MINUTES")
        |> Option.ofObj
        |> Option.bind (fun x -> 
            match Int32.TryParse(x) with
            | true, value -> Some value
            | false, _ -> None)
        |> Option.defaultValue 60

    let generateJwtToken (userId: string) (email: string) =
        let secret = getJwtSecret()
        let key = SymmetricSecurityKey(Encoding.UTF8.GetBytes(secret))
        let creds = SigningCredentials(key, SecurityAlgorithms.HmacSha256)
        
        let claims = [|
            Claim(ClaimTypes.NameIdentifier, userId)
            Claim(ClaimTypes.Email, email)
        |]
        
        let expiryMinutes = getJwtExpiry()
        let token = JwtSecurityToken(
            issuer = "fastbreak-api",
            audience = "fastbreak-client",
            claims = claims,
            expires = DateTime.UtcNow.AddMinutes(float expiryMinutes),
            signingCredentials = creds
        )
        
        JwtSecurityTokenHandler().WriteToken(token)

    let validateJwtToken (token: string) =
        try
            let secret = getJwtSecret()
            let key = SymmetricSecurityKey(Encoding.UTF8.GetBytes(secret))
            
            let validationParameters = TokenValidationParameters(
                ValidateIssuerSigningKey = true,
                IssuerSigningKey = key,
                ValidateIssuer = true,
                ValidIssuer = "fastbreak-api",
                ValidateAudience = true,
                ValidAudience = "fastbreak-client",
                ValidateLifetime = true,
                ClockSkew = TimeSpan.Zero
            )
            
            let handler = JwtSecurityTokenHandler()
            let principal = handler.ValidateToken(token, validationParameters, ref null)
            
            let userIdClaim = principal.FindFirst(ClaimTypes.NameIdentifier)
            let emailClaim = principal.FindFirst(ClaimTypes.Email)
            
            match userIdClaim, emailClaim with
            | null, _ | _, null -> None
            | userId, email -> Some (userId.Value, email.Value)
        with
        | _ -> None