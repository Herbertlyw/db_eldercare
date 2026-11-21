import { useEffect } from "react";
import type { ListResult } from "../model/types";

export function useAvailablePage(
  data: ListResult | undefined,
  setPage: (page: number) => void,
) {
  // 接口页码从 0 开始，分页控件从 1 开始。
  const page = data?.page;
  const lastPage = data && Math.max(1, Math.ceil(data.total / data.size));
  useEffect(() => {
    if (lastPage !== undefined && page !== undefined && page >= lastPage)
      setPage(lastPage);
  }, [page, lastPage, setPage]);
}
