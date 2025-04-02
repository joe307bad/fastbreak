module Utils

open Giraffe
open Saturn

let apiRouter =
    pipeline {
        plug (routeStartsWith "/api")
    }