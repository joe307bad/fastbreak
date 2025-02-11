module PickController

open Saturn.Endpoint

let scheduleController database =
    router { post "/" (getTomorrowsSchedulesHandler database) }


