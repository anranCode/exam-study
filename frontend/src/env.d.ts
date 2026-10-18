/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** 后端接口基地址,开发环境为 /api(经 Vite proxy 转发) */
  readonly VITE_API_BASE: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}