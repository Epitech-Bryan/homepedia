module.exports = {
  branches: ["main"],
  tagFormat: "webapp-v${version}",
  plugins: [
    "@semantic-release/commit-analyzer",
    "@semantic-release/release-notes-generator",
    ["@semantic-release/changelog", { changelogFile: "webapp/CHANGELOG.md" }],
    ["@semantic-release/npm", { npmPublish: false, pkgRoot: "webapp" }],
    [
      "@semantic-release/git",
      {
        assets: ["webapp/CHANGELOG.md", "webapp/package.json"],
        message: "chore(release): webapp-v${nextRelease.version} [skip ci]",
      },
    ],
    "@semantic-release/gitlab",
  ],
};
