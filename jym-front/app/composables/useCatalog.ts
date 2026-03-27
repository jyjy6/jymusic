import type { AxiosInstance } from "axios";
import type {
  Category,
  ProductDetail,
  ProductListResponse,
  ProductSearchParams,
  ProductSummary,
} from "~/types/catalog";

export const useCategoryAdmin = () => {
  const { $axios } = useNuxtApp();

  const isSubmitting = ref(false);

  const createCategory = async (name: string): Promise<Category> => {
    isSubmitting.value = true;
    try {
      const response = await ($axios as AxiosInstance).post<Category>(
        "/api/v1/categories",
        { name },
      );
      return response.data;
    } finally {
      isSubmitting.value = false;
    }
  };

  const updateCategory = async (id: number, name: string): Promise<Category> => {
    isSubmitting.value = true;
    try {
      const response = await ($axios as AxiosInstance).put<Category>(
        `/api/v1/categories/${id}`,
        { name },
      );
      return response.data;
    } finally {
      isSubmitting.value = false;
    }
  };

  const deleteCategory = async (id: number): Promise<void> => {
    isSubmitting.value = true;
    try {
      await ($axios as AxiosInstance).delete(`/api/v1/categories/${id}`);
    } finally {
      isSubmitting.value = false;
    }
  };

  return { isSubmitting, createCategory, updateCategory, deleteCategory };
};

interface FetchProductsOptions {
  page?: number;
  size?: number;
  categoryId?: number | null;
}

export const useProducts = () => {
  const { $axios } = useNuxtApp();

  const products = useState<ProductSummary[]>("catalog-products", () => []);
  const totalElements = useState<number>("catalog-total-elements", () => 0);
  const totalPages = useState<number>("catalog-total-pages", () => 0);
  const currentPage = useState<number>("catalog-current-page", () => 0);
  const selectedCategoryId = useState<number | null>(
    "catalog-selected-category-id",
    () => null,
  );
  const isLoading = useState<boolean>("catalog-products-loading", () => false);
  const errorMessage = useState<string>("catalog-products-error", () => "");

  const fetchProducts = async (options: FetchProductsOptions = {}) => {
    isLoading.value = true;
    errorMessage.value = "";

    const page = options.page ?? currentPage.value;
    const size = options.size ?? 12;
    const categoryId =
      options.categoryId === undefined
        ? selectedCategoryId.value
        : options.categoryId;

    try {
      const response = await ($axios as AxiosInstance).get<ProductListResponse>(
        "/api/v1/products",
        {
          params: {
            page,
            size,
            ...(categoryId !== null ? { categoryId } : {}),
          },
        },
      );

      products.value = response.data.content;
      totalElements.value = response.data.totalElements;
      totalPages.value = response.data.totalPages;
      currentPage.value = page;
      selectedCategoryId.value = categoryId;
    } catch (err: unknown) {
      const error = err as { response?: { data?: { message?: string } } };
      products.value = [];
      totalElements.value = 0;
      totalPages.value = 0;
      errorMessage.value =
        error.response?.data?.message ?? "상품 목록을 불러오지 못했습니다.";
    } finally {
      isLoading.value = false;
    }
  };

  return {
    products,
    totalElements,
    totalPages,
    currentPage,
    selectedCategoryId,
    isLoading,
    errorMessage,
    fetchProducts,
  };
};

export const useProductSearch = () => {
  const { $axios } = useNuxtApp();

  const searchResults = useState<ProductSummary[]>("search-results", () => []);
  const totalElements = useState<number>("search-total-elements", () => 0);
  const totalPages = useState<number>("search-total-pages", () => 0);
  const currentPage = useState<number>("search-current-page", () => 0);
  const isLoading = useState<boolean>("search-loading", () => false);
  const errorMessage = useState<string>("search-error", () => "");

  const searchProducts = async (params: ProductSearchParams) => {
    isLoading.value = true;
    errorMessage.value = "";

    try {
      const queryParams: Record<string, string | number> = {};
      if (params.keyword) queryParams.keyword = params.keyword;
      if (params.categoryId != null) queryParams.categoryId = params.categoryId;
      if (params.minPrice != null) queryParams.minPrice = params.minPrice;
      if (params.maxPrice != null) queryParams.maxPrice = params.maxPrice;
      queryParams.page = params.page ?? 0;
      queryParams.size = params.size ?? 12;
      if (params.sort) queryParams.sort = params.sort;

      const response = await ($axios as AxiosInstance).get<ProductListResponse>(
        "/api/v1/products/search",
        { params: queryParams },
      );

      searchResults.value = response.data.content;
      totalElements.value = response.data.totalElements;
      totalPages.value = response.data.totalPages;
      currentPage.value = params.page ?? 0;
    } catch (err: unknown) {
      const error = err as { response?: { data?: { message?: string } } };
      searchResults.value = [];
      totalElements.value = 0;
      totalPages.value = 0;
      errorMessage.value =
        error.response?.data?.message ?? "검색 결과를 불러오지 못했습니다.";
    } finally {
      isLoading.value = false;
    }
  };

  const resetSearch = () => {
    searchResults.value = [];
    totalElements.value = 0;
    totalPages.value = 0;
    currentPage.value = 0;
    errorMessage.value = "";
  };

  return {
    searchResults,
    totalElements,
    totalPages,
    currentPage,
    isLoading,
    errorMessage,
    searchProducts,
    resetSearch,
  };
};

export const useCategories = () => {
  const { $axios } = useNuxtApp();

  const categories = useState<Category[]>("catalog-categories", () => []);
  const isLoading = useState<boolean>(
    "catalog-categories-loading",
    () => false,
  );
  const errorMessage = useState<string>("catalog-categories-error", () => "");
  const hasLoaded = useState<boolean>("catalog-categories-loaded", () => false);

  const fetchCategories = async () => {
    if (hasLoaded.value) return;

    isLoading.value = true;
    errorMessage.value = "";

    try {
      const response = await ($axios as AxiosInstance).get<Category[]>(
        "/api/v1/categories",
      );
      categories.value = response.data;
      hasLoaded.value = true;
    } catch (err: unknown) {
      const error = err as { response?: { data?: { message?: string } } };
      errorMessage.value =
        error.response?.data?.message ?? "카테고리를 불러오지 못했습니다.";
    } finally {
      isLoading.value = false;
    }
  };

  return {
    categories,
    isLoading,
    errorMessage,
    fetchCategories,
  };
};

export const useProductDetail = async (id: number): Promise<ProductDetail> => {
  const { $axios } = useNuxtApp();
  const response = await ($axios as AxiosInstance).get<ProductDetail>(
    `/api/v1/products/${id}`,
  );
  return response.data;
};
