import { useEffect, useState } from "react";
import {
  Button,
  Checkbox,
  Input,
  InputNumber,
  Select,
  Space,
  Typography,
} from "@arco-design/web-react";
import { allRows, api, command } from "../api/client";
import { useResource } from "../hooks/useResource";
import { recordLabel } from "../model/presentation";
import { periodRange } from "./schema";
import type { Row, Values } from "../model/types";
export interface LinkedProps {
  name: string;
  label: string;
  value: any;
  values: Values;
  records: Record<string, Row | undefined>;
  onChange: (name: string, value: any, row?: Row) => void;
  onReady: (name: string, ready: boolean) => void;
}
function LoadNote({
  loading,
  error,
  retry,
  empty,
}: {
  loading: boolean;
  error: string;
  retry: () => void;
  empty?: string;
}) {
  return error ? (
    <Space>
      <Typography.Text type="error">{error}</Typography.Text>
      <Button type="text" size="mini" onClick={retry}>
        重试
      </Button>
    </Space>
  ) : loading ? (
    <Typography.Text type="secondary">正在加载…</Typography.Text>
  ) : empty ? (
    <Typography.Text type="secondary">{empty}</Typography.Text>
  ) : null;
}
export function LinkedSelect({
  mode,
  ...p
}: LinkedProps & { mode: "appointment" | "dispatch" | "guardian" }) {
  const { name, label, value, values: v, onChange, onReady } = p;
  const hint =
    mode === "appointment"
      ? !v.elderId
        ? "请先选择老人"
        : !v.startsAt
          ? "请先选择预约时间"
          : ""
      : mode === "dispatch"
        ? !v.orderId
          ? "请先选择订单"
          : ""
        : !v.elderId
          ? "请先选择老人"
          : "";
  const key = hint
    ? null
    : JSON.stringify([mode, v.elderId, v.startsAt, v.orderId]);
  const resource = useResource<Row[]>(key, async (signal) => {
    if (mode === "dispatch") {
      const result = await command(
        "order.recommend",
        { orderId: Number(v.orderId) },
        signal,
      );
      return result.result.map((r: Row) => ({
        ...r,
        id: r.shift_id,
        label: `${r.name} · 剩余 ${r.available} 个名额 · 班次 #${r.shift_id}`,
      }));
    }
    if (mode === "guardian") {
      const details = await api("/details/elders/" + v.elderId, { signal });
      return details["家属联系方式"].map((r: Row) => ({
        ...r,
        label: `${r.relation_name} · ${r.phone}（#${r.id}）`,
      }));
    }
    const plans = await allRows(
      "plans",
      { status: "ACTIVE", field: "elder_id", value: v.elderId },
      signal,
    );
    const time = new Date(v.startsAt);
    const active = plans.filter(
      (r) => time >= new Date(r.starts_at) && time < new Date(r.ends_at),
    );
    const results = await Promise.all(
      active.map((r) => api("/details/plans/" + r.id, { signal })),
    );
    return [
      ...new Map(
        results
          .flatMap((d) =>
            d["服务项目与频次"].map((r: Row) => ({ ...r, id: r.item_id })),
          )
          .map((r) => [r.id, r]),
      ).values(),
    ] as Row[];
  });
  const rows = resource.data || [];
  const ready =
    !!key &&
    !resource.loading &&
    !resource.error &&
    rows.length > 0 &&
    (!value || rows.some((r) => r.id === value));
  useEffect(() => onReady(name, ready), [ready, name, onReady]);
  return (
    <>
      <Select
        id={"field-" + name}
        aria-label={label}
        showSearch
        value={value}
        disabled={!key || resource.loading || !!resource.error || !rows.length}
        loading={resource.loading}
        placeholder={hint || (!rows.length ? "暂无符合条件的记录" : "请选择")}
        options={rows.map((r) => ({
          value: r.id,
          label: r.label || recordLabel(r),
        }))}
        onChange={(value) =>
          onChange(
            name,
            value,
            rows.find((r) => r.id === value),
          )
        }
      />
      <LoadNote
        {...resource}
        empty={
          !key
            ? hint
            : !rows.length
              ? mode === "appointment"
                ? "该时段没有可预约的计划项目"
                : mode === "dispatch"
                  ? "暂无符合资格、时间和容量条件的班次"
                  : "该老人尚未关联家属"
              : undefined
        }
      />
    </>
  );
}
export function MultiRecords(
  p: LinkedProps & { mode: "communities" | "orders" },
) {
  const { name, mode, values: v, records, onReady, onChange } = p;
  const [search, setSearch] = useState("");
  const policy = records.policyId,
    range = periodRange(policy?.settlement_cycle, v.period || "");
  const hint =
    mode === "communities"
      ? ""
      : !v.elderId
        ? "请先选择老人"
        : !policy
          ? "请先选择补贴政策"
          : !range
            ? "请填写有效结算周期"
            : "";
  const key = hint
    ? null
    : mode === "communities"
      ? "communities"
      : JSON.stringify([v.elderId, policy?.id, v.period]);
  const r = useResource<Row[]>(key, async (signal) => {
    if (mode === "communities") return allRows("communities", {}, signal);
    const [orders, eligible] = await Promise.all([
      allRows(
        "orders",
        { status: "COMPLETED", field: "elder_id", value: v.elderId },
        signal,
      ),
      allRows(
        "eligibleClaims",
        { field: "elder_id", value: v.elderId },
        signal,
      ),
    ]);
    const ids = new Set(
      eligible.filter((r) => Number(r.effective_minutes) > 0).map((r) => r.id),
    );
    return orders.filter(
      (r) =>
        ids.has(r.id) &&
        r.item_id === policy!.item_id &&
        new Date(r.starts_at) >= range![0] &&
        new Date(r.starts_at) < range![1] &&
        new Date(r.starts_at) >= new Date(policy!.starts_at) &&
        new Date(r.ends_at) <= new Date(policy!.ends_at),
    );
  });
  const rows = r.data || [],
    value: number[] = Array.isArray(p.value) ? p.value : [],
    visible = rows.filter((row) => recordLabel(row).includes(search));
  const ready =
    !!key &&
    !r.loading &&
    !r.error &&
    rows.length > 0 &&
    value.every((id) => rows.some((r) => r.id === id));
  useEffect(() => onReady(name, ready), [ready, name, onReady]);
  return (
    <div className="multi-records">
      <LoadNote
        {...r}
        empty={hint || (!rows.length ? "暂无符合条件的记录" : undefined)}
      />
      {rows.length > 0 && (
        <>
          <Input.Search
            aria-label={"搜索" + p.label}
            placeholder={"搜索" + p.label}
            value={search}
            onChange={setSearch}
            allowClear
          />
          <div className="selection-toolbar">
            <Typography.Text type="secondary">
              已选 {value.length} 项
            </Typography.Text>
            <Button
              type="text"
              size="mini"
              onClick={() =>
                onChange(name, [
                  ...new Set([...value, ...visible.map((r) => r.id)]),
                ])
              }
            >
              选择当前结果
            </Button>
            <Button type="text" size="mini" onClick={() => onChange(name, [])}>
              清空
            </Button>
          </div>
          <Checkbox.Group
            value={value}
            onChange={(next) => onChange(name, next)}
          >
            {visible.map((row) => (
              <Checkbox key={row.id} value={row.id}>
                {mode === "communities" ? row.name : recordLabel(row)}
              </Checkbox>
            ))}
          </Checkbox.Group>
        </>
      )}
    </div>
  );
}
export function ServiceItems(p: LinkedProps) {
  const { values: v, onChange, onReady, name } = p;
  const hasBundle = Object.prototype.hasOwnProperty.call(v, "_planItems");
  const key = hasBundle
    ? v.bundleId
      ? "bundle:" + v.bundleId
      : null
    : "catalog";
  const r = useResource<Row[]>(key, async (signal) => {
    if (!hasBundle) return allRows("catalog", {}, signal);
    const detail = await api("/details/bundles/" + v.bundleId, { signal });
    return ((Object.values(detail).find(Array.isArray) || []) as Row[]).map(
      (r) => ({ ...r, id: r.item_id }),
    );
  });
  const rows = r.data || [],
    items: { itemId?: number; frequency: number }[] = Array.isArray(p.value)
      ? p.value
      : [];
  const ready =
    !!key &&
    !r.loading &&
    !r.error &&
    (hasBundle || rows.length > 0) &&
    items.every((i) => !i.itemId || rows.some((r) => r.id === i.itemId));
  useEffect(() => onReady(name, ready), [ready, name, onReady]);
  const update = (index: number, patch: Partial<(typeof items)[number]>) =>
    onChange(
      name,
      items.map((item, i) => (i === index ? { ...item, ...patch } : item)),
    );
  return (
    <div className="service-items">
      <LoadNote
        {...r}
        empty={
          !key
            ? "请先选择服务包"
            : hasBundle && !items.length && rows.length
              ? "不单独添加时，沿用服务包的全部项目和次数"
              : !rows.length
                ? "暂无可添加项目"
                : undefined
        }
      />
      {items.map((item, i) => (
        <div className="service-item" key={i}>
          <Select
            aria-label={"服务项目 " + (i + 1)}
            placeholder="请选择服务项目"
            value={item.itemId}
            options={rows.map((r) => ({
              value: r.id,
              label: recordLabel(r),
              disabled: items.some((v, j) => i !== j && v.itemId === r.id),
            }))}
            onChange={(itemId) => update(i, { itemId })}
          />
          <span>每周</span>
          <InputNumber
            aria-label={"每周次数 " + (i + 1)}
            min={1}
            precision={0}
            value={item.frequency}
            onChange={(frequency) => update(i, { frequency })}
          />
          <span>次</span>
          <Button
            type="text"
            onClick={() =>
              onChange(
                name,
                items.filter((_, j) => i !== j),
              )
            }
          >
            移除
          </Button>
        </div>
      ))}
      <Button
        disabled={!ready || items.length >= rows.length}
        onClick={() => onChange(name, [...items, { frequency: 1 }])}
      >
        添加服务项目
      </Button>
    </div>
  );
}
