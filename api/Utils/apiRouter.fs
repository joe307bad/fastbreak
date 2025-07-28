module api.Utils.apiRouter

open Giraffe
open Saturn

let apiRouter =
    pipeline {
        plug (routeStartsWith "/api")
    }