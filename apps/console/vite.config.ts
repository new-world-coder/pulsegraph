import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

export default defineConfig({
  plugins: [react()],
  server: {
    port: 3000,
    proxy: {
      "/api": {
        target: "http://localhost:8090",
        changeOrigin: true,
      },
      "/freeze": {
        target: "http://localhost:8092",
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/freeze/, "/v1/freeze"),
      },
      "/graph": {
        target: "http://localhost:8091",
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/graph/, "/v1/graph"),
      },
    },
  },
  build: {
    outDir: "dist",
    sourcemap: true,
  },
});
