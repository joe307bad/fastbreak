module PickController

open Saturn.Endpoint
open ScheduleController

let scheduleController database =
    router { post "/" (getTomorrowsSchedulesHandler database) }


