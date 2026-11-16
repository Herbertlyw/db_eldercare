import { useEffect, useRef, useState } from "react";
import { isAbort, messageOf } from "../api/client";
export function useResource<T>(
  key: string | null,
  load: (signal: AbortSignal) => Promise<T>,
) {
  const loader = useRef(load);
  loader.current = load;
  const [version, retry] = useState(0);
  const [state, setState] = useState<{
    key: string | null;
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
    setState({ key, loading: true, error: "" });
    loader
      .current(controller.signal)
      .then((data) => {
        if (!controller.signal.aborted)
          setState({ key, data, loading: false, error: "" });
      })
      .catch((e) => {
        if (!controller.signal.aborted && !isAbort(e))
          setState({ key, loading: false, error: messageOf(e) });
      });
    return () => controller.abort();
  }, [key, version]);
  return {
    ...(state.key === key
      ? state
      : { key, data: undefined, loading: key !== null, error: "" }),
    retry: () => retry((n) => n + 1),
  };
}
