# Module architecture

The application package is `com.ferozfaiz.uniapi`. Spring Modulith checks package dependencies and named interfaces. Business modules generally separate HTTP APIs, application services, domain behavior, and persistence adapters. Cross-module interfaces are declared in package descriptors.

<!-- implemented-modules:start -->

| Package | Responsibility |
| --- | --- |
| `platform` | Configuration, security, persistence infrastructure, observability, messages, and web errors |
| `shared` | Small generic contracts for errors, text, pagination, and localization |
| `iam` | Identity, users, roles, permissions, and authentication integration |
| `session` | Session and token lifecycle contracts |
| `audit` | Session audit/history |
| `data` | Shared reference data and data-owned category APIs |
| `category` | Generic category registration, authorization, policies, operations, and JDBC infrastructure |
| `catalogue` | Products, catalogue categories, merchants, offers, prices, and category-provider integration |

<!-- implemented-modules:end -->

Liquibase migrations live under `src/main/resources/db/changelog/`, grouped by owning module. User-facing messages live under `src/main/resources/messages/` in default and French bundles. Tests mirror the application modules, with isolated fixtures for provider conformance and architecture checks.

The category module supports selected-system admin operations. Catalogue maintains its own routes and contributes a registered category provider. Module availability describes implemented source boundaries; it does not imply that every product feature is available in the deployed admin demo.
