import type { AxiosInstance } from "axios";
import type {
  AdminOrderDetail,
  AdminOrderSearchRequest,
  AdminOrderSummary,
  AdminStatusUpdateRequest,
  PageResponse,
} from "~/types/admin-order";

export const useAdminOrders = () => {
  const { $axios } = useNuxtApp();
  const axios = $axios as AxiosInstance;

  const search = async (
    searchRequest: AdminOrderSearchRequest,
    page: number,
    size: number,
  ) => {
    const res = await axios.get<PageResponse<AdminOrderSummary>>(
      "/api/v1/orders/admin",
      {
        params: {
          ...searchRequest,
          page,
          size,
          sort: "createdAt,desc",
        },
      },
    );
    return res.data;
  };

  const getDetail = async (orderId: number) => {
    const res = await axios.get<AdminOrderDetail>(
      `/api/v1/orders/admin/${orderId}`,
    );
    return res.data;
  };

  const updateStatus = async (
    orderId: number,
    request: AdminStatusUpdateRequest,
  ) => {
    const res = await axios.patch<AdminOrderDetail>(
      `/api/v1/orders/admin/${orderId}/status`,
      request,
    );
    return res.data;
  };

  const getStats = async () => {
    const res = await axios.get<Record<string, number>>(
      "/api/v1/orders/admin/stats",
    );
    return res.data;
  };

  return {
    search,
    getDetail,
    updateStatus,
    getStats,
  };
};
