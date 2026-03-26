# Homepedia Backend

Monorepo Maven multi-modules:
- `common`
- `rest-api`
- `data-pipeline`

## Toolchain recommandee

Le projet est aligne sur:
- Java 21
- Maven 3.9.x

Un fichier `/.sdkmanrc` est fourni pour charger la bonne toolchain automatiquement.

### Avec SDKMAN

```bash
sdk env install
sdk env
java -version
mvn -version
```

## Premier build (commande la plus importante)

Depuis la racine `backend`, lance:

```bash
mvn clean install
```

Ce que fait cette commande:
- `clean`: supprime les anciens artefacts (`target/`)
- `install`: compile, teste, package, puis installe les artefacts en local (`~/.m2`)

Pourquoi c'est utile au debut:
- construit tous les modules dans le bon ordre
- installe `common` pour que `rest-api` et `data-pipeline` resolvent leurs dependances locales

## Version courte (via Makefile)

Si tu veux un point d'entree simple:

```bash
make setup
make clean-install
```

Pour voir toutes les commandes dispo:

```bash
make help
```

## Builds cibles par module

Build uniquement l'API (et ses dependances de modules):

```bash
mvn clean install -pl rest-api --also-make
```

Build uniquement la data pipeline (et ses dependances de modules):

```bash
mvn clean install -pl data-pipeline --also-make
```

`--also-make` (ou `--am`) demande a Maven de construire aussi les modules dont le module cible depend.

## Lancer les applications

```bash
mvn -pl rest-api spring-boot:run
mvn -pl data-pipeline spring-boot:run
```
