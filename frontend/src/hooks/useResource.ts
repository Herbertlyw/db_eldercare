import { useEffect, useRef, useState } from "react";
import { isAbort, messageOf } from "../api/client";
export function useResource<T>(
  key: string | null,
  load: (signal: AbortSignal) => Promise<T>,
  preserveKey?: string,
) {
  const loader = useRef(load);
  loader.current = load;
  const [version, retry] = useState(0);
  const [state, setState] = useState<{
    key: string | null;
    preserveKey?: string;
    data?: T;
    loading: boolean;
    error: string;
  }>({ key: null, loading: false, error: "" });
  useEffect(() => {
    if (key === null) {
      setState({ key, loading: false, error: "" });
      return;
    }
    const controller = new AbortController();
    setState((previous) => ({
      key,
      preserveKey,
      loading: true,
      error: "",
      data:
        preserveKey !== undefined && previous.preserveKey === preserveKey
          ? previous.data
          : undefined,
    }));
    loader
      .current(controller.signal)
      .then((data) => {
        if (!controller.signal.aborted)
          setState({ key, preserveKey, data, loading: false, error: "" });
      })
      .catch((e) => {
        if (!controller.signal.aborted && !isAbort(e))
          setState({ key, loading: false, error: messageOf(e) });
      });
    return () => controller.abort();
  }, [key, version, preserveKey]);
  return {
    ...(state.key === key
      ? state
      : {
          key,
          data:
            key !== null &&
            preserveKey !== undefined &&
            state.preserveKey === preserveKey
              ? state.data
              : undefined,
          loading: key !== null,
          error: "",
        }),
    retry: () => retry((n) => n + 1),
  };
}
