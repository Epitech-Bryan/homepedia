#!/usr/bin/env python3
"""
parse-versions.py
-----------------
Lit ci/bump-report.json produit par generate-changesets.js et écrit
ci/pipeline.env (dotenv) avec les variables de build par projet.

Variables émises (toujours les 4) :
  BUILD_APP=0|1
  APP_VERSION=x.y.z
  BUILD_API=0|1
  API_VERSION=x.y.z
"""

import json
import sys
from pathlib import Path

REPO_ROOT        = Path(__file__).resolve().parent.parent
BUMP_REPORT_PATH = REPO_ROOT / "ci" / "bump-report.json"
PIPELINE_ENV     = REPO_ROOT / "ci" / "pipeline.env"

PROJECTS = {
    "app": {"build_var": "BUILD_APP", "version_var": "APP_VERSION"},
    "api": {"build_var": "BUILD_API", "version_var": "API_VERSION"},
}

DEFAULT_VERSION = "0.0.0"


def main() -> None:
    if not BUMP_REPORT_PATH.exists():
        print(f"[ERROR] {BUMP_REPORT_PATH} not found. Did generate-changesets.js run?", file=sys.stderr)
        sys.exit(1)

    report: list[dict] = json.loads(BUMP_REPORT_PATH.read_text())

    # Index report by project name
    bumped = {entry["project"]: entry["new_version"] for entry in report}

    lines = []
    for project, meta in PROJECTS.items():
        if project in bumped:
            build = "1"
            version = bumped[project]
            print(f"[BUILD] {project} → v{version}")
        else:
            build = "0"
            version = DEFAULT_VERSION

        lines.append(f"{meta['build_var']}={build}")
        lines.append(f"{meta['version_var']}={version}")

    env_content = "\n".join(lines) + "\n"
    PIPELINE_ENV.write_text(env_content)
    print(f"[INFO] Written: {PIPELINE_ENV}")
    print(env_content)


if __name__ == "__main__":
    main()
