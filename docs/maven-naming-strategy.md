# Maven Module Naming Convention

This document outlines the uniform naming strategy for Maven modules in the Catalyst project. 

## Strategy Rules

1. **Root POM**:
   - The primary root POM must have the artifact ID `catalyst-parent`.
   - Packaging is `pom`.

2. **Aggregator and Parent Modules**:
   - Any module that has `packaging=pom` (which either serves as a parent POM to submodules, aggregates submodules, or both) **must** be suffixed with `-parent`.
   - Examples:
     - `catalyst-parent` (Root POM)
     - `catalyst-client-parent` (Client aggregator and parent)
     - `catalyst-common-parent` (Common utilities aggregator and parent)
     - `catalyst-server-parent` (Server applications aggregator)

3. **Artifact (JAR) Modules**:
   - Any module that compiles to a concrete jar artifact (`packaging=jar`) must **not** have the `-parent` suffix.
   - They should use descriptive names indicating their package and responsibility.
   - Examples:
     - `catalyst-common-network`
     - `catalyst-common-dto`
     - `catalyst-login-service`
     - `catalyst-gateway`
