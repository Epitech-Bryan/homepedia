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
      // Manual chunks isolate the slow-moving libs (react, leaflet) from app
      // code. A deploy that only touches src/ then doesn't bust the browser
      // cache for ~600 KB of vendor JS.
      //
      // recharts is intentionally NOT in manualChunks: it's only consumed by
      // three lazy()-loaded chart wrappers (PriceChart, PriceHistoryChart,
      // SentimentChart). Listing it here forced Vite to emit a
      // <link rel="modulepreload"> in index.html, pulling 391 KB eagerly on
      // every visit even when the user never opens a chart page. Letting
      // Vite handle the split automatically keeps recharts in a shared
      // chunk that is only preloaded transitively by the lazy chart pages.
      rollupOptions: {
        output: {
          manualChunks: {
            "vendor-react": ["react", "react-dom", "react-router-dom"],
            "vendor-query": ["@tanstack/react-query"],
            "vendor-leaflet": ["leaflet", "leaflet.heat", "leaflet.vectorgrid", "react-leaflet"],
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
          // Backend serves controllers at the root path; in prod Traefik
          // strips `/api` via its stripprefix middleware. The dev proxy
          // needs the same rewrite or every call comes back 404.
          rewrite: (path) => path.replace(/^\/api/, ""),
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
