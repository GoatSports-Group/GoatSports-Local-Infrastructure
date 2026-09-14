const rootAdmin = db.getSiblingDB("admin");
const deadline = Date.now() + 120000;

try {
  rs.status();
} catch (error) {
  if (error.codeName === "NotYetInitialized" || error.code === 94) {
    print("Initializing local replica set rs0");
    rs.initiate({
      _id: "rs0",
      members: [{ _id: 0, host: "mongodb-search:27017" }],
    });
  } else {
    throw error;
  }
}

while (Date.now() < deadline) {
  const hello = rootAdmin.runCommand({ hello: 1 });
  if (hello.isWritablePrimary) {
    break;
  }
  sleep(1000);
}

if (!rootAdmin.runCommand({ hello: 1 }).isWritablePrimary) {
  throw new Error("MongoDB replica set did not elect a primary within 120 seconds");
}

const mongotUsername = "mongotUser";
if (!rootAdmin.getUser(mongotUsername)) {
  rootAdmin.createUser({
    user: mongotUsername,
    pwd: process.env.MONGOT_PASSWORD,
    roles: [{ role: "searchCoordinator", db: "admin" }],
  });
  print(`Created MongoDB Search sync user ${mongotUsername}`);
}

const applicationUsers = [
  {
    label: "AI service",
    database: process.env.AI_MONGODB_DATABASE,
    username: process.env.AI_MONGODB_USERNAME,
    password: process.env.AI_MONGODB_PASSWORD,
  },
  {
    label: "audit service",
    database: process.env.AUDIT_MONGODB_DATABASE,
    username: process.env.AUDIT_MONGODB_USERNAME,
    password: process.env.AUDIT_MONGODB_PASSWORD,
  },
  {
    label: "social service",
    database: process.env.SOCIAL_MONGODB_DATABASE,
    username: process.env.SOCIAL_MONGODB_USERNAME,
    password: process.env.SOCIAL_MONGODB_PASSWORD,
  },
];

for (const applicationUser of applicationUsers) {
  if (!applicationUser.password) {
    throw new Error(`Missing password for ${applicationUser.label}`);
  }

  const applicationDatabase = db.getSiblingDB(applicationUser.database);
  if (!applicationDatabase.getUser(applicationUser.username)) {
    applicationDatabase.createUser({
      user: applicationUser.username,
      pwd: applicationUser.password,
      roles: [{ role: "readWrite", db: applicationUser.database }],
    });
    print(`Created ${applicationUser.label} user ${applicationUser.username}`);
  }
}

print("MongoDB Search bootstrap completed");

