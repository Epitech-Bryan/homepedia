#!/usr/bin/env node
/**
 * generate-changesets.js
 * ----------------------
 * Pipeline CI script — gestion du versioning via @changesets/cli
 *
 * Structure du monorepo :
 *   app/   — frontend React + Vite (Docker)
 *   api/   — backend Spring Boot (Docker)
 *
 * Workflow :
 *  1. Détecte les projets versionnables touchés par le dernier commit :
 *       - Projet app  : dossier app/ contenant un vite.config.ts ou vite.config.js
 *       - Projet api  : dossier api/ contenant un pom.xml
 *  2. Parse le message de commit (Conventional Commits) pour déterminer
 *     le niveau de bump : major / minor / patch
 *  3. S'assure que chaque projet touché a un package.json minimal
 *     (requis par Changesets pour gérer la version)
 *  4. Génère un fichier .changeset/<timestamp>-auto.md
 *  5. Lance `changeset version` → bumpe les package.json + CHANGELOG.md
 *  6. Produit ci/bump-report.json (consommé par ci/parse-versions.py)
 *     Chaque entrée inclut `type`: "app" | "api" pour orienter le build
 *  7. Commit global "chore: bump versions [skip ci]" + tags + push
 *
 * Variables d'environnement (GitLab CI) :
 *   GITLAB_CI_TOKEN  — Project Access Token (write_repository, Developer)
 *   CI_PROJECT_URL   — ex: https://gitlab.com/group/repo
 *   CI_COMMIT_BRANCH — branche courante
 *   CI_COMMIT_MESSAGE — message du dernier commit
 *   GIT_USER_EMAIL   — email pour le commit de bump (défaut: ci@homepedia.local)
 *   GIT_USER_NAME    — nom pour le commit de bump   (défaut: CI Bot)
 */

import { execSync } from "node:child_process";
import { existsSync, readFileSync, writeFileSync } from "node:fs";
import { join, resolve } from "node:path";
import { randomUUID } from "node:crypto";

// ---------------------------------------------------------------------------
// Chemins
// ---------------------------------------------------------------------------

const REPO_ROOT        = resolve(import.meta.dirname, "..");
const CHANGESET_DIR    = join(REPO_ROOT, ".changeset");
const BUMP_REPORT_PATH = join(REPO_ROOT, "ci", "bump-report.json");

// ---------------------------------------------------------------------------
// Conventional Commits → niveau de bump
// ---------------------------------------------------------------------------

const BUMP_PATTERNS = {
  major: /^(feat|fix|refactor|perf|build|chore|docs|style|test|ci)(\(.+\))?!:|^BREAKING CHANGE/m,
  minor: /^feat(\(.+\))?:/m,
  patch: /^(fix|perf|refactor)(\(.+\))?:/m,
};

/**
 * @param {string} message
 * @returns {"major"|"minor"|"patch"|null}
 */
function determineBump(message) {
  if (BUMP_PATTERNS.major.test(message)) return "major";
  if (BUMP_PATTERNS.minor.test(message)) return "minor";
  if (BUMP_PATTERNS.patch.test(message)) return "patch";
  return null;
}

// ---------------------------------------------------------------------------
// Git helpers
// ---------------------------------------------------------------------------

/**
 * @param {string} cmd
 * @param {{ check?: boolean }} [opts]
 * @returns {string}
 */
function run(cmd, { check = true } = {}) {
  try {
    return execSync(cmd, { cwd: REPO_ROOT, encoding: "utf8" }).trim();
  } catch (err) {
    if (check) {
      console.error(`[ERROR] Command failed: ${cmd}`);
      console.error(err.stderr ?? err.message);
      process.exit(1);
    }
    return (err.stdout ?? "").trim();
  }
}

function configureGit() {
  const email = process.env.GIT_USER_EMAIL ?? "ci@homepedia.local";
  const name  = process.env.GIT_USER_NAME  ?? "CI Bot";
  run(`git config user.email "${email}"`);
  run(`git config user.name "${name}"`);
}

function pushChanges(branch) {
  const token      = process.env.GITLAB_CI_TOKEN ?? "";
  const projectUrl = process.env.CI_PROJECT_URL  ?? "";

  if (token && projectUrl) {
    const authedUrl = projectUrl.replace("https://", `https://oauth2:${token}@`);
    run(`git remote set-url origin "${authedUrl}"`);
  }

  run(`git push origin HEAD:${branch} --follow-tags`);
}

// ---------------------------------------------------------------------------
// Détection des projets
// ---------------------------------------------------------------------------

/**
 * @typedef {{ name: string, dir: string, type: "app" | "api" }} Project
 */

/**
 * Retourne tous les projets versionnables du monorepo :
 *  - Projet app  : dossier app/ contenant un vite.config.ts ou vite.config.js
 *  - Projet api  : dossier api/ contenant un pom.xml
 * @returns {Project[]}
 */
function getProjects() {
  const projects = [];

  // Projet app (app/vite.config.ts ou app/vite.config.js)
  const appDir = join(REPO_ROOT, "app");
  if (
    existsSync(appDir) &&
    (existsSync(join(appDir, "vite.config.ts")) ||
     existsSync(join(appDir, "vite.config.js")))
  ) {
    projects.push({ name: "app", dir: "app", type: "app" });
  }

  // Projet api (api/pom.xml)
  if (existsSync(join(REPO_ROOT, "api", "pom.xml"))) {
    projects.push({ name: "api", dir: "api", type: "api" });
  }

  return projects;
}

/**
 * Retourne les projets touchés par le dernier commit.
 * Sur le premier commit (pas de HEAD~1), retourne tous les projets.
 * @param {Project[]} allProjects
 * @returns {Project[]}
 */
function getTouchedProjects(allProjects) {
  const hasParent = run("git rev-list --count HEAD", { check: false });
  const diffBase  = parseInt(hasParent, 10) > 1 ? "HEAD~1" : null;

  let changedFiles;
  if (diffBase) {
    changedFiles = run(`git diff ${diffBase} HEAD --name-only`, { check: false })
      .split("\n")
      .filter(Boolean);
  } else {
    // Premier commit : tous les fichiers trackés
    changedFiles = run("git ls-files", { check: false })
      .split("\n")
      .filter(Boolean);
  }

  const projectPrefixes = allProjects.map((p) => `${p.dir}/`);
  const hasOutsideFiles = changedFiles.some(
    (file) => !projectPrefixes.some((prefix) => file.startsWith(prefix)),
  );
  if (hasOutsideFiles) {
    console.log("[INFO] Files outside project dirs detected — rebuilding all projects.");
    return allProjects;
  }

  return allProjects.filter((project) =>
    changedFiles.some((file) => file.startsWith(`${project.dir}/`)),
  );
}

// ---------------------------------------------------------------------------
// package.json minimal par projet (requis par Changesets)
// ---------------------------------------------------------------------------

/**
 * S'assure qu'un package.json minimal existe dans le dossier projet.
 * Ne touche pas les package.json existants (ex: app/ a le sien).
 * @param {Project} project
 */
function ensureProjectPackageJson(project) {
  const pkgPath = join(REPO_ROOT, project.dir, "package.json");
  if (existsSync(pkgPath)) return;

  const pkg = {
    name:    project.name,
    version: "0.1.0",
    private: true,
  };
  writeFileSync(pkgPath, JSON.stringify(pkg, null, 2) + "\n");
  console.log(`[INIT] Created ${project.dir}/package.json (v0.1.0)`);
}

// ---------------------------------------------------------------------------
// Version helpers
// ---------------------------------------------------------------------------

/**
 * Lit la version depuis package.json d'un projet.
 * @param {Project} project
 * @returns {string}
 */
function readVersionFromPackageJson(project) {
  const pkgPath = join(REPO_ROOT, project.dir, "package.json");
  const pkg     = JSON.parse(readFileSync(pkgPath, "utf8"));
  return pkg.version;
}

// ---------------------------------------------------------------------------
// Génération du fichier .changeset
// ---------------------------------------------------------------------------

/**
 * @param {Project[]} projects
 * @param {"major"|"minor"|"patch"} bumpType
 * @param {string} commitMessage
 * @returns {string} chemin du fichier généré
 */
function generateChangesetFile(projects, bumpType, commitMessage) {
  const subject = commitMessage.split("\n")[0].trim();

  const frontmatter = projects
    .map((p) => `  "${p.name}": ${bumpType}`)
    .join("\n");

  const content = `---\n${frontmatter}\n---\n\n${subject}\n`;

  const id       = randomUUID().slice(0, 8);
  const filename = `${Date.now()}-${id}-auto.md`;
  const filepath = join(CHANGESET_DIR, filename);

  writeFileSync(filepath, content);
  console.log(`[CHANGESET] Generated ${filepath}`);
  return filepath;
}

// ---------------------------------------------------------------------------
// Main
// ---------------------------------------------------------------------------

async function main() {
  const branch         = process.env.CI_COMMIT_BRANCH    ?? run("git rev-parse --abbrev-ref HEAD");
  const commitMessage  = process.env.CI_COMMIT_MESSAGE   ?? run("git log -1 --pretty=%B");
  const pipelineSource = process.env.CI_PIPELINE_SOURCE  ?? "push";

  console.log(`[INFO] Branch: ${branch}`);
  console.log(`[INFO] Pipeline source: ${pipelineSource}`);
  console.log(`[INFO] Commit: ${commitMessage.split("\n")[0]}`);

  // 1. Découverte des projets
  const allProjects = getProjects();
  if (allProjects.length === 0) {
    console.log("[INFO] No versionable projects found (app/vite.config.* or api/pom.xml).");
    writeFileSync(BUMP_REPORT_PATH, "[]\n");
    return;
  }
  console.log(`[INFO] Projects found: ${allProjects.map((p) => `${p.name} (${p.type})`).join(", ")}`);

  // Déclenchement manuel (web) : build tous les projets sans bump de version
  if (pipelineSource === "web") {
    console.log("[INFO] Manual trigger: building all projects without version bump.");
    for (const project of allProjects) {
      ensureProjectPackageJson(project);
    }
    const report = allProjects.map((p) => ({
      project:     p.name,
      dir:         p.dir,
      type:        p.type,
      old_version: readVersionFromPackageJson(p),
      new_version: readVersionFromPackageJson(p),
      bump_type:   null,
    }));
    writeFileSync(BUMP_REPORT_PATH, JSON.stringify(report, null, 2) + "\n");
    console.log(`[INFO] Bump report written with ${report.length} project(s).`);
    return;
  }

  // 2. Niveau de bump
  const bumpType = determineBump(commitMessage);
  if (!bumpType) {
    console.log("[INFO] No bumpable commit type detected (chore/docs/style/ci/...). Skipping.");
    writeFileSync(BUMP_REPORT_PATH, "[]\n");
    return;
  }
  console.log(`[INFO] Bump type: ${bumpType}`);

  // 3. Projets touchés
  const touchedProjects = getTouchedProjects(allProjects);
  if (touchedProjects.length === 0) {
    console.log("[INFO] No projects touched by this commit. Skipping.");
    writeFileSync(BUMP_REPORT_PATH, "[]\n");
    return;
  }
  console.log(`[INFO] Touched projects: ${touchedProjects.map((p) => p.dir).join(", ")}`);

  // 4. S'assure que chaque projet a un package.json (requis par Changesets)
  for (const project of touchedProjects) {
    ensureProjectPackageJson(project);
  }

  configureGit();

  // 5. Snapshot des versions actuelles (avant bump)
  const oldVersions = Object.fromEntries(
    touchedProjects.map((p) => [p.name, readVersionFromPackageJson(p)]),
  );

  // 6. Génère le fichier .changeset
  generateChangesetFile(touchedProjects, bumpType, commitMessage);

  // 7. Lance `changeset version` — bumpe les package.json + génère les CHANGELOG.md
  console.log("[INFO] Running: changeset version");
  run("npx changeset version");

  // 7b. Re-synchronise le package-lock.json après les bumps de version
  console.log("[INFO] Syncing package-lock.json");
  run("npm install --package-lock-only");

  // 8. Collecte le rapport
  const report = [];
  const bumpedFiles = [];

  for (const project of touchedProjects) {
    const newVersion = readVersionFromPackageJson(project);
    const oldVersion = oldVersions[project.name];

    if (newVersion === oldVersion) {
      console.log(`[SKIP] ${project.dir}: version unchanged (${oldVersion})`);
      continue;
    }

    bumpedFiles.push(join(REPO_ROOT, project.dir, "package.json"));
    bumpedFiles.push(join(REPO_ROOT, project.dir, "CHANGELOG.md"));

    report.push({
      project:     project.name,
      dir:         project.dir,
      type:        project.type,
      old_version: oldVersion,
      new_version: newVersion,
      bump_type:   bumpType,
    });

    console.log(`[BUMP] ${project.dir}: ${oldVersion} → ${newVersion} (${bumpType})`);
  }

  // 9. Écrit le rapport (toujours, même vide)
  writeFileSync(BUMP_REPORT_PATH, JSON.stringify(report, null, 2) + "\n");
  console.log(`[INFO] Bump report written: ${BUMP_REPORT_PATH}`);

  if (report.length === 0) {
    console.log("[INFO] Nothing bumped.");
    return;
  }

  // 10. Commit global
  const filesToStage = [
    ...bumpedFiles,
    BUMP_REPORT_PATH,
    CHANGESET_DIR,
  ];

  run(`git add -f ${filesToStage.map((f) => `"${f}"`).join(" ")}`);
  run(`git commit -m "chore: bump versions [skip ci]"`);

  // 11. Tags par projet (format: <project-name>@v<version>)
  for (const entry of report) {
    const tag = `${entry.project}@v${entry.new_version}`;
    run(`git tag -a "${tag}" -m "Release ${tag}"`);
    console.log(`[TAG] ${tag}`);
  }

  // 12. Push
  pushChanges(branch);
  console.log("[INFO] Bump commit and tags pushed successfully.");
}

main().catch((err) => {
  console.error("[FATAL]", err);
  process.exit(1);
});
