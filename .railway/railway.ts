// Railway project for the backend (README → Deploy). `railway config plan` diffs it, `railway config apply` applies it.
// Secrets are set once with `railway variable set` and kept by preserve(); they never live here.
import { defineRailway, github, postgres, project, preserve, service } from "railway/iac";

export default defineRailway(() => {
  // 18, as Testcontainers: Railway created this database on 18 whatever image the file named, and 18 can't go back
  // to 16 in place. DB_URL below names this service in ${{postgres.…}}.
  const db = postgres("postgres");

  const api = service("api", {
    source: github("HishamAbdulhak/Gleeds_KingCharles_Event", { branch: "main", rootDirectory: "/backend" }),
    // a frontend-only push must not restart the backend: that drops every live Game
    build: { watchPatterns: ["/backend/**"] },   // Railway finds backend/Dockerfile itself
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
