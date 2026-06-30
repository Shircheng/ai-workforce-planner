import {defineConfig} from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

const backendProxyTarget =
    process.env.VITE_BACKEND_PROXY_TARGET ?? 'http://127.0.0.1:8080'
const analyticsProxyTarget =
    process.env.VITE_ANALYTICS_PROXY_TARGET ?? 'http://127.0.0.1:8000'

// https://vite.dev/config/
export default defineConfig({
    plugins: [react(), tailwindcss()],
    server: {
        proxy: {
            '/api': {
                target: backendProxyTarget,
                changeOrigin: true,
            },
            '/python-analysis': {
                target: analyticsProxyTarget,
                changeOrigin: true,
                rewrite: (path) => path.replace(/^\/python-analysis/, ''),
            },
        },
    },
})
