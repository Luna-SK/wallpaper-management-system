# wzut-wallpaper-manager frontend

图片管理系统前端工作台，基于 Vue 3、TypeScript、Vite、Pinia、Vue Router 和 Element Plus。

## Scripts

```bash
npm install
npm run dev
npm run typecheck
npm run build
npm run test
npm run test:e2e
```

默认开发地址为 `http://localhost:5173` 或 `http://127.0.0.1:5173`，前端业务请求保持 `/api`。

开发代理端口来自前端自己的环境文件：

```bash
cp .env.example .env
```

默认 `VITE_BACKEND_PORT=18090`，因此 `/api` 会代理到 `http://localhost:18090`。前端 `.env` 不读取后端或 Docker Compose 的配置文件。
