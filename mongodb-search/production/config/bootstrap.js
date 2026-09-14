const rootAdmin = db.getSiblingDB("admin");
const deadline = Date.now() + 180000;

try {
  rs.status();
} catch (error) {
  if (error.codeName === "NotYetInitialized" || error.code === 94) {
    print("Initializing production single-node replica set rs0");
    rs.initiate({
      _id: "rs0",
      members: [{ _id: 0, host: "mongodb-search:27017" }],
    });
  } else {
    throw error;
  }
}

while (Date.now() < deadline) {
  if (rootAdmin.runCommand({ hello: 1 }).isWritablePrimary) {
    break;
  }
  sleep(1000);
}

if (!rootAdmin.runCommand({ hello: 1 }).isWritablePrimary) {
  throw new Error("MongoDB replica set did not elect a primary within 180 seconds");
}

if (!rootAdmin.getUser("mongotUser")) {
  rootAdmin.createUser({
    user: "mongotUser",
    pwd: process.env.MONGOT_PASSWORD,
    roles: [{ role: "searchCoordinator", db: "admin" }],
  });
  print("Created MongoDB Search sync user mongotUser");
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
  if (!applicationUser.database || !applicationUser.username || !applicationUser.password) {
    throw new Error(`Missing configuration for ${applicationUser.label}`);
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

print("PRODUCTION_SINGLE_VPS_BOOTSTRAP_OK");