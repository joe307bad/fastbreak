# MLB FanGraphs Utilities - Resilient wrappers around baseballr's fg_* leaderboards

# baseballr's fg_* functions build their result inside a tryCatch that only
# message()es on failure, so the frame is never assigned and the call dies at
# `return(leaders)` with the opaque "object 'leaders' not found". A FanGraphs
# blip therefore reads like a code bug and takes the whole script down: on
# 2026-08-21 every fg_* call failed across the 6am run, so every FanGraphs-backed
# MLB chart served day-old data while mlb__matchup_stats (ESPN) updated fine.
#
# fg_retry() re-issues the call with backoff and reports a message that names
# FanGraphs, so a real outage is distinguishable from a schema break.

# Seconds to wait before each retry. length() + 1 == total attempts.
fg_retry_delays <- c(30, 120, 300)

fg_retry <- function(fn, ..., label = deparse(substitute(fn)), delays = fg_retry_delays) {
  attempts <- length(delays) + 1

  for (attempt in seq_len(attempts)) {
    result <- tryCatch(
      suppressWarnings(suppressMessages(fn(...))),
      error = function(e) e
    )

    if (!inherits(result, "error") && !is.null(result) && NROW(result) > 0) {
      if (attempt > 1) {
        cat("FanGraphs:", label, "succeeded on attempt", attempt, "\n")
      }
      return(result)
    }

    reason <- if (inherits(result, "error")) {
      conditionMessage(result)
    } else {
      "no rows returned"
    }

    if (attempt == attempts) {
      stop(sprintf(
        "FanGraphs unavailable: %s failed after %d attempts (last: %s)",
        label, attempts, reason
      ), call. = FALSE)
    }

    delay <- delays[attempt]
    cat(sprintf("FanGraphs: %s attempt %d failed (%s) - retrying in %gs\n",
                label, attempt, reason, delay))
    Sys.sleep(delay)
  }
}
