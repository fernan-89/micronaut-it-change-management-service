# micronaut-it-change-management-service

BIAN-aligned Service Domain **it-change-management** (Control Record: `ChangeRequest`), port `8086`.

Scaffolded by `scripts/new-service.ps1`. Add the aggregate, use cases, controller (`/it-change-management/v1/{id}/{behavior-qualifier}`),
persistence adapter, Postman suite and ADRs, keeping `gradlew check` at 100% line and branch coverage.

## Error catalog

| Code | HTTP | Meaning |
|---|---|---|
| `ERR-CHG-00404` | 404 | ChangeRequest not found |
| `ERR-CHG-00409` | 409 | State conflict or duplicate (ADR-019) |
| `ERR-VALIDATION-00400` | 400 | Payload/header/identifier validation failure |
| `ERR-INTERNAL-00500` | 500 | Unexpected technical failure |

## License

Proprietary - all rights reserved. See [LICENSE](LICENSE). This software is not open source.