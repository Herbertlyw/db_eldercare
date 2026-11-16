import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
const backend = process.env.FRONTEND_API_TARGET || "http://127.0.0.1:18080";
export default defineConfig({
  plugins: [react()],
  publicDir: false,
  server: {
    host: "127.0.0.1",
    port: 18089,
    strictPort: true,
    proxy: Object.fromEntries(
      [
        "/api",
        "/images",
        "/demo-people.json",
        "/brand-mark.svg",
        "/neighborhood.svg",
      ].map((path) => [path, { target: backend }]),
    ),
  },
  build: { outDir: "dist", emptyOutDir: true },
});
