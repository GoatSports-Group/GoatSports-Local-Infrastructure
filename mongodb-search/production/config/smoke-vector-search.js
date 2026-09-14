const databaseName = process.env.AI_MONGODB_DATABASE;
const indexName = process.env.MONGODB_VECTOR_INDEX;
const dimensions = Number(process.env.MONGODB_VECTOR_DIMENSIONS);
const smokeId = `production-vector-smoke-${Date.now()}`;
const collection = db.getSiblingDB(databaseName).getCollection("embedding_records");
const vector = Array(dimensions).fill(0);
vector[0] = 1;
let found = false;

collection.insertOne({
  smokeTestId: smokeId,
  embeddingId: smokeId,
  sourceType: "KNOWLEDGE_CHUNK",
  sourceId: smokeId,
  modelVersionId: smokeId,
  dimensions,
  vector,
  embeddedAt: new Date(),
});

try {
  for (let attempt = 0; attempt < 30; attempt += 1) {
    const rows = collection.aggregate([
      {
        $vectorSearch: {
          index: indexName,
          path: "vector",
          queryVector: vector,
          numCandidates: 10,
          limit: 1,
          filter: { sourceType: "KNOWLEDGE_CHUNK" },
        },
      },
    ]).toArray();

    if (rows.some((row) => row.smokeTestId === smokeId)) {
      found = true;
      break;
    }
    sleep(1000);
  }
} finally {
  collection.deleteMany({ smokeTestId: smokeId });
}

if (!found) {
  throw new Error("Vector smoke-test document was not returned within 30 seconds");
}

print("VECTOR_SEARCH_SMOKE_OK");