/// <reference types="vitest/config" />
import path from "path";
import { defineConfig, loadEnv, type PluginOption } from "vite";
import react from "@vitejs/plugin-react";
import tailwindcss from "@tailwindcss/vite";
import { visualizer } from "rollup-plugin-visualizer";

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), "");
  const apiTarget = env.VITE_API_TARGET ?? "http://localhost:8080";

  // Bundle analyzer: `pnpm analyze` (mode=analyze) opens a treemap of every
  // chunk so we can see what's actually being shipped. Doesn't affect normal
  // dev or production builds.
  const plugins: PluginOption[] = [react(), tailwindcss()];
  if (mode === "analyze") {
    plugins.push(
      visualizer({
        filename: "dist/bundle-stats.html",
        open: true,
        gzipSize: true,
        brotliSize: true,
        template: "treemap",
      }) as PluginOption,
    );
  }

  return {
    plugins,
    resolve: {
      alias: {
        "@": path.resolve(__dirname, "./src"),
      },
    },
    server: {
      host: "0.0.0.0",
      port: 5173,
      proxy: {
        "/api": {
          target: apiTarget,
          changeOrigin: true,
          secure: false,
        },
      },
    },
    test: {
      globals: true,
      environment: "jsdom",
      setupFiles: "./src/test/setup.ts",
    },
  };
});
