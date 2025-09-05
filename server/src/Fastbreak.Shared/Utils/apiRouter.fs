namespace Fastbreak.Shared.Utils

open Giraffe
open Saturn

module ApiRouter =

    let apiRouter = pipeline { plug (routeStartsWith "/api") }
