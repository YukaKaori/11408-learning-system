# Docker

Containerization is a candidate for a future production-readiness phase (not
yet scheduled — see `docs/architecture.md`'s roadmap and
`docs/phase7-final-report.md`'s "Recommended Phase 8 scope"):

- `Dockerfile` for `server` (layered Spring Boot image)
- `Dockerfile` for `web` (static build served by nginx)
- `docker-compose.yml` for local full-stack development (MySQL, Redis, server, web)

Kept empty until then — no speculative configuration.
