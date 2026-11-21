import axios, { AxiosError, InternalAxiosRequestConfig } from 'axios';

export interface ApiEnvelope<T> {
  success: boolean;
  code: string;
  message: string;
  data: T;
  serverTime: string;
}

const httpClient = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api/v1',
  timeout: 20000,
  headers: {
    'Content-Type': 'application/json',
  },
});

httpClient.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  const token = localStorage.getItem('o2ofood_access_token');
  if (token && config.headers) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

httpClient.interceptors.response.use(
  (response) => response,
  async (error: AxiosError) => {
    const originalRequest = error.config as InternalAxiosRequestConfig & { _retry?: boolean };
    if (error.response?.status === 401 && originalRequest && !originalRequest._retry) {
      originalRequest._retry = true;
      const refreshToken = localStorage.getItem('o2ofood_refresh_token');
      if (refreshToken) {
        try {
          const res = await axios.post<ApiEnvelope<{ accessToken: string; refreshToken: string }>>(
            '/api/v1/auth/refresh',
            { refreshToken }
          );
          const { accessToken, refreshToken: newRefresh } = res.data.data;
          localStorage.setItem('o2ofood_access_token', accessToken);
          localStorage.setItem('o2ofood_refresh_token', newRefresh);
          originalRequest.headers.Authorization = `Bearer ${accessToken}`;
          return httpClient(originalRequest);
        } catch {
          localStorage.removeItem('o2ofood_access_token');
          localStorage.removeItem('o2ofood_refresh_token');
          localStorage.removeItem('o2ofood_user');
          window.location.href = '/login';
        }
      }
    }
    return Promise.reject(error);
  }
);

export default httpClient;
