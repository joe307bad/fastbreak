module api.Utils.asyncMap

let asyncMap f asyncVal =
    async {
        let! result = asyncVal
        return f result
    }
