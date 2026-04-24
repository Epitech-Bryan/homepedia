# [1.1.0](https://gitlab.com/t-dat-902/homepedia/compare/rest-api-v1.0.2...rest-api-v1.1.0) (2026-04-24)


### Bug Fixes

* **ci:** replace @semantic-release/npm with exec for webapp ([fd512d8](https://gitlab.com/t-dat-902/homepedia/commit/fd512d85d88a0863bf6c8f5a261e7ebf87057919))


### Features

* **batch:** add auto-download support for DVF, DPE, and Health datasets ([8020100](https://gitlab.com/t-dat-902/homepedia/commit/80201005cb99084e07bf742ed133c0cb094d8bca))

## [1.0.2](https://gitlab.com/t-dat-902/homepedia/compare/rest-api-v1.0.1...rest-api-v1.0.2) (2026-04-24)


### Bug Fixes

* **ci:** configure DOCKER_HOST for DinD on Kubernetes executor ([728423d](https://gitlab.com/t-dat-902/homepedia/commit/728423d47f39a75c18c06264a9aca3c7adf6c0f3))
* **ci:** use Buildah instead of Docker for K8s builds ([1f958c7](https://gitlab.com/t-dat-902/homepedia/commit/1f958c7d96a2a55cf5afd5bc4c8dae6441288af1))
* update Dockerfiles for current project structure ([ebab65b](https://gitlab.com/t-dat-902/homepedia/commit/ebab65b0c614b8b5d29f4e5b05b1e69ddf11e7ee))

## [1.0.1](https://gitlab.com/t-dat-902/homepedia/compare/rest-api-v1.0.0...rest-api-v1.0.1) (2026-04-24)


### Bug Fixes

* sync child POM parent versions to 1.0.0 ([1673532](https://gitlab.com/t-dat-902/homepedia/commit/16735323af25a80ab4d3b4a21b859a64ec77ba04))

# 1.0.0 (2026-04-24)


### Bug Fixes

* **ci:** allow manual pipeline triggers on [skip ci] commits ([a73f2d4](https://gitlab.com/t-dat-902/homepedia/commit/a73f2d47b39e54489d4d5f3c8e1b19d75c9caaf8))
* **ci:** convert releaserc YAML to CJS for semantic-release ([94c454a](https://gitlab.com/t-dat-902/homepedia/commit/94c454a7c4f75ce8edd68fa92a3f5c8c063b8730))
* **ci:** fix webapp lint cd issue and remove data-pipeline jobs ([2e21ad2](https://gitlab.com/t-dat-902/homepedia/commit/2e21ad2cd4ff72c290c279ffd64c31398666359d))
* **ci:** target parent pom for versions:set in release ([08bb71b](https://gitlab.com/t-dat-902/homepedia/commit/08bb71b9d504be3b9c3c701d7344aa1788820f4a))
* **ci:** use fully qualified image names for buildah compatibility ([974fe14](https://gitlab.com/t-dat-902/homepedia/commit/974fe14a44d28e09af512f5704342a16fa80ae51))
* remove final for jpa entity ([5394f35](https://gitlab.com/t-dat-902/homepedia/commit/5394f35717748a6d9c9b706d565b3cc7402a9640))
* remove package-lock.json reference from app Dockerfiles ([a516ec5](https://gitlab.com/t-dat-902/homepedia/commit/a516ec5ab63c9cebb98ff7fe00d6173616081024))
* resolve @types/node conflict between workspaces for npm ci ([e00c427](https://gitlab.com/t-dat-902/homepedia/commit/e00c427c6fb675a52bdf66da0855bfb7267bb6cc))
* trigger jobs main-branch only, optional needs, remove automergeType ([dde4bfa](https://gitlab.com/t-dat-902/homepedia/commit/dde4bfa22d094b31c2e6beb3c481ecf540ef4324))
* update dependency org.springframework.boot:spring-boot-starter-parent to v3.5.13 ([daac8db](https://gitlab.com/t-dat-902/homepedia/commit/daac8dbdb54547c40386c831c9245c474440e02b))
* update dependency org.springframework.boot:spring-boot-starter-parent to v3.5.14 ([a57bde9](https://gitlab.com/t-dat-902/homepedia/commit/a57bde9aeb7a38dfe50546bbcb7af1c0c60b9b4d))
* **webapp:** resolve ESLint errors in pages ([8208016](https://gitlab.com/t-dat-902/homepedia/commit/8208016cdf78df650ba61debb68b114df5a49d2c))


### Features

* add city review scraper and sentiment analysis module ([91e04d4](https://gitlab.com/t-dat-902/homepedia/commit/91e04d45a4c326eb8ed76e23d561750cc8ef2b7d))
* initialize monorepo with CI/CD, versioning, and Traefik reverse proxy ([66be0c7](https://gitlab.com/t-dat-902/homepedia/commit/66be0c75bb30c829992790349d753de70a9f1c48))
* java multi modules ([256e2de](https://gitlab.com/t-dat-902/homepedia/commit/256e2de76120a08d0b21796cd55b9ccc9f0ce7f5))
* **webapp:** add reviews page with word cloud and sentiment analysis ([ce8b4a2](https://gitlab.com/t-dat-902/homepedia/commit/ce8b4a298fffd52f0c206c3c1c0b9efb5e49836b))
* **webapp:** build complete frontend with pages, components, and API hooks ([6a7c936](https://gitlab.com/t-dat-902/homepedia/commit/6a7c93652e2bf7877a8ea65ec65d885feddfbb6b))

# Changelog
