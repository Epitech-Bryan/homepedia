/// <reference types="vitest/config" />
import path from "path";
import { defineConfig, loadEnv, type PluginOption } from "vite";
import react from "@vitejs/plugin-react";
import tailwindcss from "@tailwindcss/vite";
import { visualizer } from "rollup-plugin-visualizer";
import viteCompression from "vite-plugin-compression";

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), "");
  const apiTarget = env.VITE_API_TARGET ?? "http://localhost:8080";

  // Bundle analyzer: `pnpm analyze` (mode=analyze) opens a treemap of every
  // chunk so we can see what's actually being shipped. Doesn't affect normal
  // dev or production builds.
  // viteCompression emits *.gz next to every build artifact ≥1 KB so
  // nginx gzip_static can serve them directly — zero per-request CPU.
  const plugins: PluginOption[] = [
    react(),
    tailwindcss(),
    viteCompression({
      algorithm: "gzip",
      ext: ".gz",
      threshold: 1024,
      deleteOriginFile: false,
    }),
  ];
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
    build: {
      // Manual chunks isolate the slow-moving libs (react, leaflet, recharts)
      // from app code. A deploy that only touches src/ then doesn't bust the
      // browser cache for ~1 MB of vendor JS.
      rollupOptions: {
        output: {
          manualChunks: {
            "vendor-react": ["react", "react-dom", "react-router-dom"],
            "vendor-query": ["@tanstack/react-query"],
            "vendor-leaflet": ["leaflet", "leaflet.heat", "react-leaflet"],
            "vendor-charts": ["recharts"],
          },
        },
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
