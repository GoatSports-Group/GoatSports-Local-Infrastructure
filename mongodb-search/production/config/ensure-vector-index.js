const databaseName = process.env.AI_MONGODB_DATABASE;
const indexName = process.env.MONGODB_VECTOR_INDEX;
const dimensions = Number(process.env.MONGODB_VECTOR_DIMENSIONS);
const waitSeconds = Number(process.env.MONGODB_SEARCH_INDEX_WAIT_SECONDS);
const targetDatabase = db.getSiblingDB(databaseName);

if (!targetDatabase.getCollectionNames().includes("embedding_records")) {
  targetDatabase.createCollection("embedding_records");
}

const collection = targetDatabase.getCollection("embedding_records");
const deadline = Date.now() + waitSeconds * 1000;
let creationRequested = false;

while (Date.now() < deadline) {
  try {
    const indexes = collection.aggregate([{ $listSearchIndexes: {} }]).toArray();
    const current = indexes.find((candidate) => candidate.name === indexName);

    if (!current && !creationRequested) {
      collection.createSearchIndex(indexName, "vectorSearch", {
        fields: [
          {
            type: "vector",
            path: "vector",
            numDimensions: dimensions,
            similarity: "cosine",
          },
          { type: "filter", path: "sourceType" },
        ],
      });
      creationRequested = true;
      print(`Vector index ${indexName} creation requested`);
    } else if (current && current.status === "READY" && current.queryable === true) {
      print(`Vector index ${indexName} is READY and queryable`);
      quit(0);
    }
  } catch (error) {
    print(`Waiting for MongoDB Search: ${error.message}`);
  }

  sleep(2000);
}

throw new Error(`Vector index ${indexName} was not ready within ${waitSeconds} seconds`);