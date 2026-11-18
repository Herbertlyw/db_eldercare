import { createContext, useContext, type ReactNode } from "react";
import {
  Alert,
  Button,
  Card,
  Empty,
  Pagination,
  Space,
  Spin,
  Tag,
} from "@arco-design/web-react";
import {
  IconArrowRight,
  IconCalendar,
  IconFile,
  IconSafe,
  IconUserGroup,
  IconMessage,
  IconRefresh,
} from "@arco-design/web-react/icon";
import { NavigationIcon } from "../../components/NavigationIcon";
import { list } from "../../api/client";
import { useResource } from "../../hooks/useResource";
import { choices, preset } from "../../model/rules";
import { displayValue } from "../../model/presentation";
import type { Criteria, Operation, Row, User, Values } from "../../model/types";
export interface HomeProps {
  user: User;
  selectedElder?: number;
  selectElder: (id: number) => void;
  revision: number;
  profile?: Row;
  navigate: (kind: string, filter?: Criteria) => void;
  open: (
    action: string,
    values?: Values,
    context?: Operation["context"],
  ) => void;
  details: (kind: string, row: Row) => void;
  refresh: () => void;
}
export const HomeContext = createContext<HomeProps>(null!);
export const useHome = () => useContext(HomeContext);
export function useRows(
  kind: string,
  query: Record<string, string | number | undefined> = {},
  enabled = true,
) {
  const { user, revision } = useHome();
  return useResource(
    enabled ? JSON.stringify([user.id, kind, query, revision]) : null,
    (signal) => list(kind, { size: 4, ...query }, signal),
    JSON.stringify([user.id, kind, query]),
  );
}
export type Resource = ReturnType<typeof useRows>;
export function Load({
  resource,
  children,
  empty = "暂无待办",
}: {
  resource: Resource;
  children: ReactNode;
  empty?: string;
}) {
  if (resource.error)
    return (
      <Alert
        type="error"
        content={resource.error}
        action={
          <Button size="small" onClick={resource.retry}>
            重试
          </Button>
        }
      />
    );
  if (resource.loading && !resource.data)
    return (
      <div className="home-loading">
        <Spin />
        <span>正在加载</span>
      </div>
    );
  if (!resource.data?.rows.length) return <Empty description={empty} />;
  return <>{children}</>;
}
export function Panel({
  title,
  children,
  more,
  onMore,
  className = "",
}: {
  title: string;
  children: ReactNode;
  more?: string;
  onMore?: () => void;
  className?: string;
}) {
  return (
    <Card
      className={"home-panel " + className}
      title={
        <span className="panel-heading">
          <NavigationIcon
            kind={
              title.includes("服务") ||
              title.includes("班次") ||
              title.includes("排班")
                ? "orders"
                : title.includes("评估") || title.includes("计划")
                  ? "assessments"
                  : title.includes("投诉") || title.includes("反馈")
                    ? "complaints"
                    : title.includes("应急") || title.includes("事件")
                      ? "events"
                      : title.includes("补贴") ||
                          title.includes("申报") ||
                          title.includes("费用")
                        ? "claims"
                        : title.includes("培训") || title.includes("证书")
                          ? "trainings"
                          : "organizationReports"
            }
          />
          {title}
        </span>
      }
      extra={
        onMore && (
          <Button type="text" size="small" onClick={onMore}>
            {more || "查看全部"} <IconArrowRight />
          </Button>
        )
      }
    >
      {children}
    </Card>
  );
}
export function HomeHeader({
  title,
  subtitle,
  children,
}: {
  title: string;
  subtitle?: ReactNode;
  children?: ReactNode;
}) {
  const { refresh } = useHome();
  return (
    <header className="home-header">
      <div>
        <h1 id="title">{title}</h1>
        {subtitle && <div className="home-subtitle">{subtitle}</div>}
      </div>
      <Space wrap>
        <Button
          id="refresh"
          icon={<IconRefresh />}
          onClick={refresh}
          aria-label="刷新工作台"
        />
        {children}
      </Space>
    </header>
  );
}
export function Metric({
  label,
  kind,
  status,
  query = {},
  icon,
  children,
}: {
  label: string;
  kind: string;
  status?: string;
  query?: Record<string, string | number | undefined>;
  icon?: ReactNode;
  children?: ReactNode;
}) {
  const r = useRows(kind, { size: 1, status, ...query }),
    { navigate } = useHome();
  return (
    <button
      className="home-metric"
      onClick={() =>
        r.error ? r.retry() : navigate(kind, { status, ...query })
      }
    >
      <span className="home-metric-icon">
        {icon ||
          (kind === "caregivers" ? (
            <IconUserGroup />
          ) : /claim|eligible/i.test(kind) ? (
            <IconSafe />
          ) : kind === "complaints" ? (
            <IconMessage />
          ) : /Due|hours|shift/i.test(kind) ? (
            <IconCalendar />
          ) : (
            <IconFile />
          ))}
      </span>
      <span>
        <span className="metric-label">{label}</span>
        <strong>
          {r.error ? "加载失败" : r.loading ? "—" : (r.data?.total ?? "—")}
        </strong>
        {children}
      </span>
      <IconArrowRight />
    </button>
  );
}
export function Status({ row, kind }: { row: Row; kind: string }) {
  return (
    <Tag
      color={
        ["PENDING", "ASSIGNED", "SUBMITTED", "OPEN", "REVIEW"].includes(
          row.status,
        )
          ? "orange"
          : ["COMPLETED", "CLOSED", "PAID"].includes(row.status)
            ? undefined
            : "arcoblue"
      }
    >
      {displayValue("status", row.status, kind)}
    </Tag>
  );
}
export function RowActions({
  kind,
  row,
  limit = 1,
}: {
  kind: string;
  row: Row;
  limit?: number;
}) {
  const h = useHome();
  return (
    <Space wrap size={4}>
      <Button type="text" size="small" onClick={() => h.details(kind, row)}>
        查看详情
      </Button>
      {choices(kind, row, h.user)
        .slice(0, limit)
        .map((c) => (
          <Button
            size="small"
            type="primary"
            key={c.action}
            data-action={c.action}
            onClick={() =>
              c.action === "order.confirm"
                ? h.details(kind, row)
                : h.open(
                    c.action,
                    { ...preset(kind, row), ...c.preset },
                    { kind, row },
                  )
            }
          >
            {c.label}
          </Button>
        ))}
    </Space>
  );
}
export function RowList({
  resource,
  kind,
  title,
  subtitle,
  empty,
}: {
  resource: Resource;
  kind: string;
  title: (r: Row) => ReactNode;
  subtitle?: (r: Row) => ReactNode;
  empty?: string;
}) {
  return (
    <Load resource={resource} empty={empty}>
      <div className="home-rows">
        {resource.data?.rows.map((r) => (
          <div className="home-row" key={r.id}>
            <div className="home-row-copy">
              <strong>{title(r)}</strong>
              {subtitle && <p>{subtitle(r)}</p>}
            </div>
            <RowActions kind={kind} row={r} />
          </div>
        ))}
      </div>
    </Load>
  );
}
export function Pager({
  resource,
  page,
  onChange,
  size = 5,
}: {
  resource: Resource;
  page: number;
  onChange: (n: number) => void;
  size?: number;
}) {
  return (resource.data?.total || 0) > size ? (
    <Pagination
      size="small"
      simple
      current={page}
      pageSize={size}
      total={resource.data?.total}
      onChange={onChange}
    />
  ) : null;
}
export const dateText = (v: unknown) => (v ? String(v).slice(0, 10) : "—");
export const timeText = (v: unknown) => (v ? String(v).slice(11, 16) : "—");
export const money = (v: unknown) =>
  Number(v ?? 0).toLocaleString("zh-CN", {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  });
export function todayInShanghai() {
  return new Intl.DateTimeFormat("en-CA", {
    timeZone: "Asia/Shanghai",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).format(new Date());
}
export const dateTitle = (day: string) =>
  new Intl.DateTimeFormat("zh-CN", {
    timeZone: "Asia/Shanghai",
    year: "numeric",
    month: "long",
    day: "numeric",
    weekday: "long",
  }).format(new Date(day + "T12:00:00+08:00"));
export function DateLabel({ day }: { day: string }) {
  return (
    <span>
      <IconCalendar /> {dateTitle(day)}
    </span>
  );
}
