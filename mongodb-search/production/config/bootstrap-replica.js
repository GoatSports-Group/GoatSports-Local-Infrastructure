const adminDatabase = db.getSiblingDB("admin");
const applicationDatabaseName = process.env.AI_MONGODB_DATABASE;
const deadline = Date.now() + 180000;
const rootUsername = process.env.MONGO_ROOT_USERNAME;

try {
  adminDatabase.auth(rootUsername, process.env.MONGO_ROOT_PASSWORD);
} catch (error) {
  print(`Root authentication is not available yet: ${error.message}`);
}

try {
  rs.status();
} catch (error) {
  if (error.codeName === "NotYetInitialized" || error.code === 94) {
    rs.initiate({
      _id: "rs0",
      members: [
        { _id: 0, host: "mongo-01.internal:27017", priority: 2 },
        { _id: 1, host: "mongo-02.internal:27017", priority: 1 },
        { _id: 2, host: "mongo-03.internal:27017", priority: 1 },
      ],
    });
  } else {
    throw error;
  }
}

while (Date.now() < deadline) {
  if (adminDatabase.runCommand({ hello: 1 }).isWritablePrimary) {
    break;
  }
  sleep(1000);
}

if (!adminDatabase.runCommand({ hello: 1 }).isWritablePrimary) {
  throw new Error("Replica set did not elect a primary within 180 seconds");
}

if (!adminDatabase.getUser(rootUsername)) {
  adminDatabase.createUser({
    user: rootUsername,
    pwd: process.env.MONGO_ROOT_PASSWORD,
    roles: [{ role: "root", db: "admin" }],
  });
}
adminDatabase.auth(rootUsername, process.env.MONGO_ROOT_PASSWORD);

if (!adminDatabase.getUser("mongotUser")) {
  adminDatabase.createUser({
    user: "mongotUser",
    pwd: process.env.MONGOT_PASSWORD,
    roles: [{ role: "searchCoordinator", db: "admin" }],
  });
}

const applicationUsers = [
  {
    database: applicationDatabaseName,
    username: process.env.AI_MONGODB_USERNAME,
    password: process.env.AI_MONGODB_PASSWORD,
  },
  {
    database: process.env.AUDIT_MONGODB_DATABASE,
    username: process.env.AUDIT_MONGODB_USERNAME,
    password: process.env.AUDIT_MONGODB_PASSWORD,
  },
  {
    database: process.env.SOCIAL_MONGODB_DATABASE,
    username: process.env.SOCIAL_MONGODB_USERNAME,
    password: process.env.SOCIAL_MONGODB_PASSWORD,
  },
];

for (const applicationUser of applicationUsers) {
  if (!applicationUser.password) {
    throw new Error(`Missing password for ${applicationUser.username}`);
  }
  const applicationDatabase = db.getSiblingDB(applicationUser.database);
  if (!applicationDatabase.getUser(applicationUser.username)) {
    applicationDatabase.createUser({
      user: applicationUser.username,
      pwd: applicationUser.password,
      roles: [{ role: "readWrite", db: applicationUser.database }],
    });
  }
}

print("PRODUCTION_REPLICA_BOOTSTRAP_OK");