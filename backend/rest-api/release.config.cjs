module.exports = {
  branches: ["main"],
  tagFormat: "rest-api-v${version}",
  plugins: [
    "@semantic-release/commit-analyzer",
    "@semantic-release/release-notes-generator",
    [
      "@semantic-release/changelog",
      { changelogFile: "backend/rest-api/CHANGELOG.md" },
    ],
    [
      "@semantic-release/exec",
      {
        prepareCmd:
          "mvn -f backend/pom.xml versions:set -DnewVersion=${nextRelease.version} -DgenerateBackupPoms=false -DprocessAllModules",
      },
    ],
    [
      "@semantic-release/git",
      {
        assets: [
          "backend/rest-api/CHANGELOG.md",
          "backend/pom.xml",
          "backend/common/pom.xml",
          "backend/rest-api/pom.xml",
        ],
        message: "chore(release): rest-api-v${nextRelease.version} [skip ci]",
      },
    ],
    "@semantic-release/gitlab",
  ],
};
