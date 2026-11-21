import { useEffect, useRef, useState } from "react";
import { Button, Select, Space, Typography } from "@arco-design/web-react";
import { list, messageOf, isAbort } from "../api/client";
import { recordLabel } from "../model/presentation";
import { choices } from "../model/rules";
import type { Row, User } from "../model/types";
interface Props {
  name: string;
  label: string;
  source: string;
  action: string;
  user: User;
  value?: string | number;
  onChange: (value: any, row?: Row) => void;
  onRecord: (name: string, row?: Row) => void;
  onReady: (name: string, ready: boolean) => void;
  scope?: { field?: string; value?: number; level?: string };
  disabledHint?: string;
  required: boolean;
}
const scoped = [
  "orderId",
  "complaintId",
  "eventId",
  "claimId",
  "inspectionId",
  "recruitmentId",
  "recordId",
  "reportId",
];
export function RecordSelect({
  name,
  label,
  source,
  action,
  user,
  value,
  onChange,
  onRecord,
  onReady,
  scope = {},
  disabledHint,
  required,
}: Props) {
  const [search, setSearch] = useState(""),
    [query, setQuery] = useState(""),
    [page, setPage] = useState(0),
    [revision, setRevision] = useState(0),
    [state, setState] = useState<{
      key: string;
      rows: Row[];
      total: number;
      loading: boolean;
      error: string;
    }>({ key: "", rows: [], total: 0, loading: true, error: "" });
  const selected = useRef(value);
  selected.current = value;
  const key = JSON.stringify({ source, action, scope, query, disabledHint });
  const currentKey = useRef(key);
  currentKey.current = key;
  useEffect(() => {
    const t = setTimeout(() => {
      setQuery(search);
      setPage(0);
    }, 250);
    return () => clearTimeout(t);
  }, [search]);
  useEffect(() => {
    setPage(0);
    setSearch("");
    setQuery("");
  }, [source, scope.field, scope.value, scope.level, disabledHint]);
  useEffect(() => {
    const controller = new AbortController();
    if (disabledHint) {
      setState({ key, rows: [], total: 0, loading: false, error: "" });
      return () => controller.abort();
    }
    setState((old) => ({
      ...old,
      key,
      rows: page ? old.rows : [],
      loading: true,
      error: "",
    }));
    async function load() {
      const data = await list(
        source,
        {
          page,
          size: 50,
          search: query,
          field: scope.field,
          value: scope.value,
        },
        controller.signal,
      );
      const rows = [...data.rows];
      const exactId = /^[1-9]\d{0,14}$/.test(query)
        ? Number(query)
        : !query
          ? selected.current
          : null;
      if (
        page === 0 &&
        exactId != null &&
        name !== "username" &&
        !rows.some((r) => String(r.id) === String(exactId))
      ) {
        const extra = await list(
          source,
          { size: 1, field: "id", value: exactId },
          controller.signal,
        );
        rows.unshift(
          ...extra.rows.filter(
            (r) =>
              !scope.field || String(r[scope.field]) === String(scope.value),
          ),
        );
      }
      if (!controller.signal.aborted && currentKey.current === key)
        setState((old) => ({
          key,
          rows: [
            ...new Map(
              [...(page ? old.rows : []), ...rows].map((r) => [r.id, r]),
            ).values(),
          ],
          total: data.total,
          loading: false,
          error: "",
        }));
    }
    load().catch((e) => {
      if (!controller.signal.aborted && !isAbort(e))
        setState((old) => ({ ...old, loading: false, error: messageOf(e) }));
    });
    return () => controller.abort();
  }, [key, page, revision]);
  const candidates = (state.key === key ? state.rows : []).filter(
    (r) =>
      !(
        action === "elder.transfer" &&
        name === "communityId" &&
        Number(r.id) === user.community
      ) &&
      (!scope.level || r.ability_level === scope.level) &&
      (!scoped.includes(name) ||
        choices(source, r, user).some(
          (c) =>
            c.action ===
            (action === "order.recommend" ? "order.assign" : action),
        ) ||
        (action === "inspection.order" && r.status === "COMPLETED")),
  );
  const valueKey = name === "username" ? "username" : "id";
  const record = candidates.find((r) => String(r[valueKey]) === String(value));
  const ready =
    !disabledHint &&
    state.key === key &&
    !state.loading &&
    !state.error &&
    (!value || !!record) &&
    (!required || candidates.length > 0);
  useEffect(() => {
    onReady(name, ready);
  }, [ready, name, onReady]);
  useEffect(() => {
    onRecord(name, record);
  }, [record, name, onRecord]);
  return (
    <div className="record-picker">
      <Select
        id={"field-" + name}
        aria-label={label}
        showSearch
        filterOption={false}
        allowClear
        loading={state.loading}
        disabled={!!disabledHint}
        value={value}
        placeholder={disabledHint || "搜索姓名、名称或编号"}
        onSearch={(text) => {
          setSearch(text);
          if (text && value != null) onChange(undefined);
        }}
        onChange={(v) =>
          onChange(
            v,
            candidates.find((r) => r[valueKey] === v),
          )
        }
        options={candidates.map((r) => ({
          value: r[valueKey],
          label: recordLabel(r, source),
        }))}
        notFoundContent={
          state.error ? "加载失败" : state.loading ? "加载中…" : "没有匹配记录"
        }
      />
      {state.error ? (
        <Space>
          <Typography.Text type="error">{state.error}</Typography.Text>
          <Button
            size="mini"
            type="text"
            onClick={() => setRevision((v) => v + 1)}
          >
            重试
          </Button>
        </Space>
      ) : !disabledHint && !state.loading && candidates.length === 0 ? (
        <Typography.Text type="secondary">没有匹配记录</Typography.Text>
      ) : null}
      {!disabledHint && (page + 1) * 50 < state.total && (
        <Button
          className="picker-more"
          type="text"
          size="mini"
          disabled={state.loading}
          onClick={() => setPage((p) => p + 1)}
        >
          加载更多
        </Button>
      )}
    </div>
  );
}
