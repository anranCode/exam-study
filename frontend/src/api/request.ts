import axios, {
  type AxiosError,
  type AxiosInstance,
  type AxiosRequestConfig,
  type AxiosResponse,
  type InternalAxiosRequestConfig
} from 'axios'
import { ElMessage } from 'element-plus'

/** 接口基地址,开发环境为 /api(经 Vite proxy 转发到后端) */
const API_BASE = import.meta.env.VITE_API_BASE || '/api'
/** 统一超时时间(毫秒) */
const REQUEST_TIMEOUT = 15000

/** 后端统一响应包装 */
export interface ApiResult<T> {
  code: number
  message: string
  data: T
}

/** 携带业务错误码的错误对象,业务层可按 code 分支处理 */
export class ApiError extends Error {
  readonly code: number
  readonly httpStatus?: number

  constructor(code: number, message: string, httpStatus?: number) {
    super(message)
    this.name = 'ApiError'
    this.code = code
    this.httpStatus = httpStatus
  }
}

const instance: AxiosInstance = axios.create({
  baseURL: API_BASE,
  timeout: REQUEST_TIMEOUT,
  headers: {
    'Content-Type': 'application/json'
  }
})

instance.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  // 单用户应用,不做鉴权;超时与 Content-Type 已在 create 时统一设置。
  return config
})

function isApiResult(value: unknown): value is ApiResult<unknown> {
  return (
    !!value &&
    typeof value === 'object' &&
    'code' in value &&
    typeof (value as { code: unknown }).code === 'number'
  )
}

instance.interceptors.response.use(
  (response: AxiosResponse) => {
    const body: unknown = response.data
    // 非统一包装(如静态资源)原样返回
    if (!isApiResult(body)) {
      return body as unknown as AxiosResponse
    }
    if (body.code === 0) {
      // 成功:直接把 data 交给业务层
      return body.data as unknown as AxiosResponse
    }
    const message = body.message || '请求失败'
    ElMessage.error(message)
    throw new ApiError(body.code, message, response.status)
  },
  (error: AxiosError) => {
    const status = error.response?.status
    const body: unknown = error.response?.data

    // 后端返回了统一包装的业务错误
    if (isApiResult(body) && body.code !== 0) {
      const message = body.message || '请求失败'
      ElMessage.error(message)
      throw new ApiError(body.code, message, status)
    }

    // 有响应但非统一包装(4xx / 5xx)
    if (error.response) {
      const message = `请求失败(HTTP ${status})`
      ElMessage.error(message)
      throw new ApiError(status ?? -1, message, status)
    }

    // 网络不可达 / 超时
    const message = `无法连接服务器,请确认后端已启动,地址:${API_BASE}`
    ElMessage.error(message)
    throw new ApiError(-1, message)
  }
)

/**
 * 类型安全的请求封装:拦截器已解包,这里的返回值就是后端的 data。
 */
export const http = {
  get<T>(url: string, config?: AxiosRequestConfig): Promise<T> {
    return instance.get(url, config) as unknown as Promise<T>
  },
  post<T>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T> {
    return instance.post(url, data, config) as unknown as Promise<T>
  },
  put<T>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T> {
    return instance.put(url, data, config) as unknown as Promise<T>
  },
  delete<T>(url: string, config?: AxiosRequestConfig): Promise<T> {
    return instance.delete(url, config) as unknown as Promise<T>
  }
}

export default instance