# deploy/compose — Docker Compose Overrides

This directory holds environment-specific Docker Compose override files that extend the root `docker-compose.yml`.

## Usage

Overlay files are merged with the root compose file using `-f`:

```bash
# Example: use the resource-constrained override for CI
docker compose -f docker-compose.yml -f deploy/compose/docker-compose.ci.yml up -d
```

## Planned Overrides

| File | Purpose |
|------|---------|
| `docker-compose.ci.yml` | CI-optimised: no persistent volumes, reduced memory |
| `docker-compose.services.yml` | Add PulseGraph service containers once Dockerfiles exist |
| `docker-compose.tls.yml` | Enable TLS for OpenSearch and Redpanda (staging) |

## Notes

- The root `docker-compose.yml` is the canonical local dev configuration.
- Override files should only contain diffs — do not duplicate service definitions.
- See the [root README](../../README.md) for the full port reference.
