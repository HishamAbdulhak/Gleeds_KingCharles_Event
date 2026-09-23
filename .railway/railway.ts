// Railway project for the backend (README → Deploy). `railway config plan` diffs it, `railway config apply` applies it.
// Secrets are set once with `railway variable set` and kept by preserve(); they never live here.
import { database, defineRailway, github, project, preserve, service } from "railway/iac";

export default defineRailway(() => {
  // postgres() pins 18; the schema is tested on 16 (Testcontainers). DB_URL below names this service in ${{postgres.…}}.
  const db = database("postgres", "postgres", {
    image: "ghcr.io/railwayapp-templates/postgres-ssl:16",
    defaultMountPath: "/var/lib/postgresql/data",
  });

  const api = service("api", {
    source: github("HishamAbdulhak/Gleeds_KingCharles_Event", { branch: "main", rootDirectory: "/backend" }),
    // a frontend-only push must not restart the backend: that drops every live Game
    build: { builder: "DOCKERFILE", watchPatterns: ["/backend/**"] },
    healthcheck: "/api/leaderboard",
    replicas: 1, // GameEngine and the simple broker keep live state in memory
    env: {
      PORT: "8080", // where Railway routes the domain and the healthcheck; Spring's own default
      DB_URL: "jdbc:postgresql://${{postgres.PGHOST}}:${{postgres.PGPORT}}/${{postgres.PGDATABASE}}",
      DB_USER: db.env.PGUSER,
      DB_PASSWORD: db.env.PGPASSWORD,
      JWT_SECRET: preserve(),
      ADMIN_EMAIL: preserve(),
      ADMIN_PASSWORD: preserve(),
      CORS_ORIGIN: preserve(),
    },
  });

  return project("king-charles-quiz", { resources: [db, api] });
});
