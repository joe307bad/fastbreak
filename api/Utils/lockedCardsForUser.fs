module api.Utils.lockedCardsForUser

open MongoDB.Bson
open MongoDB.Bson.Serialization
open MongoDB.Driver

let lockedCardForUserAsync (database: IMongoDatabase) (id: string) (yesterday: string) = async {
    let filter =
        Builders<BsonDocument>.Filter.Eq("userId", BsonValue.Create(id))
        |> fun f ->
            Builders<BsonDocument>.Filter
                .And(f, Builders<BsonDocument>.Filter.Eq("date", BsonValue.Create(yesterday)))

    let! result =
        database
            .GetCollection("locked-fastbreak-cards")
            .Find(filter)
            .FirstOrDefaultAsync()
        |> Async.AwaitTask
        
    return if (isNull result) then None else Some (BsonSerializer.Deserialize result)
}