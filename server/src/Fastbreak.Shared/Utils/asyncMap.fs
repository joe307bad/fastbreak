namespace Fastbreak.Shared.Utils

module AsyncMap =
    let asyncMap f asyncVal =
        async {
            let! result = asyncVal
            return f result
        }
