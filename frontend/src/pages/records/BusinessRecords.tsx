import type { ReactNode } from "react";
import {
  Button,
  Pagination,
  Progress,
  Spin,
  Tag,
} from "@arco-design/web-react";
import {
  IconBook,
  IconNotification,
  IconCheckCircle,
  IconCalendar,
} from "@arco-design/web-react/icon";
import type { Criteria, Row } from "../../model/types";
import { displayValue } from "../../model/presentation";

export const designedRecords = [
  "shifts",
  "trainings",
  "trainingRecords",
  "notifications",
  "publicNotices",
];
const date = (value: unknown) => String(value ?? "").slice(0, 10);
const time = (value: unknown) =>
  String(value ?? "")
    .replace("T", " ")
    .slice(11, 16);
export function periodLabel(row: Row) {
  const start = date(row.starts_at),
    end = date(row.ends_at);
  return `${start} ${time(row.starts_at)} — ${start === end ? "" : end + " "}${time(row.ends_at)}`;
}
function Shift({ row, actions }: { row: Row; actions: ReactNode }) {
  const total = Number(row.capacity),
    used = Number(row.used_count);
  return (
    <article className="agenda-row" data-id={row.id}>
      <div className="agenda-date">
        <span>{date(row.starts_at).slice(0, 7)}</span>
        <strong>{date(row.starts_at).slice(8, 10)}</strong>
      </div>
      <div className="agenda-main">
        <div className="record-title-line">
          <strong>{row.caregiver_name}</strong>
          <Tag color={row.status === "OPEN" ? "arcoblue" : "gray"}>
            {displayValue("status", row.status, "shifts")}
          </Tag>
        </div>
        <p className="agenda-time">
          {time(row.starts_at)} —{" "}
          {date(row.starts_at) !== date(row.ends_at)
            ? date(row.ends_at) + " "
            : ""}
          {time(row.ends_at)}
        </p>
        <small>班次 #{row.id}</small>
      </div>
      <div className="shift-capacity">
        <div>
          <span>名额占用</span>
          <strong>
            {used}
            <small> / {total} 单</small>
          </strong>
        </div>
        <Progress
          percent={total > 0 ? Math.round((used / total) * 100) : 0}
          showText={false}
          size="small"
          color="#7d9ee8"
        />
      </div>
      <div className="designed-actions">{actions}</div>
    </article>
  );
}
function Training({
  row,
  result,
  actions,
}: {
  row: Row;
  result: boolean;
  actions: ReactNode;
}) {
  const pending = row.result === "PENDING";
  return (
    <article className="training-row" data-id={row.id}>
      <div className="record-illustration">
        <IconBook />
      </div>
      <div className="training-main">
        <strong>{row.title}</strong>
        {result ? (
          <p>
            {row.name}
            <span className="record-meta-divider">·</span>
            {row.recorded_at
              ? `成绩登记于 ${date(row.recorded_at)}`
              : "尚未登记成绩"}
          </p>
        ) : (
          <p>
            <IconCalendar /> {periodLabel(row)}
          </p>
        )}
        <small>
          {result ? "培训记录" : "培训课程"} #{row.id}
        </small>
      </div>
      {result && (
        <div className="training-result">
          <Tag
            color={
              row.result === "PASS"
                ? "arcoblue"
                : row.result === "FAIL"
                  ? "red"
                  : "orange"
            }
          >
            {pending ? "待登记" : row.result === "PASS" ? "通过" : "未通过"}
          </Tag>
          <strong>
            {row.score ?? "—"}
            <small>{row.score != null ? " 分" : ""}</small>
          </strong>
        </div>
      )}
      <div className="designed-actions">{actions}</div>
    </article>
  );
}
function Message({
  row,
  notice,
  actions,
  navigate,
}: {
  row: Row;
  notice: boolean;
  actions: ReactNode;
  navigate?: (kind: string, criteria: Criteria) => void;
}) {
  return (
    <article className="message-row" data-id={row.id}>
      <div className="record-illustration">
        {notice ? <IconCheckCircle /> : <IconNotification />}
      </div>
      <div className="message-main">
        <div className="message-heading">
          <strong>
            {notice
              ? row.title
              : row.assessment_id
                ? "评估到期提醒"
                : row.event_id
                  ? "应急通知"
                  : "业务通知"}
          </strong>
          <time>
            {displayValue(
              "created_at",
              row.created_at || row.published_at,
            ).slice(0, 16)}
          </time>
        </div>
        <p>{notice ? row.body : row.message}</p>
        <div className="message-footer">
          <small>#{row.id}</small>
          <div className="designed-actions">
            {!notice && row.event_id && navigate && (
              <Button
                type="text"
                onClick={() =>
                  navigate("events", { field: "id", value: row.event_id })
                }
              >
                查看关联事件
              </Button>
            )}
            {actions}
          </div>
        </div>
      </div>
    </article>
  );
}
export function BusinessRecords({
  kind,
  rows,
  loading,
  total,
  page,
  size,
  onPage,
  actions,
  navigate,
}: {
  kind: string;
  rows: Row[];
  loading: boolean;
  total: number;
  page: number;
  size: number;
  onPage: (page: number, size: number) => void;
  actions: (row: Row) => ReactNode;
  navigate?: (kind: string, criteria: Criteria) => void;
}) {
  return (
    <Spin loading={loading} style={{ width: "100%" }}>
      <div className={`designed-records ${kind}`}>
        {rows.map((row) =>
          kind === "shifts" ? (
            <Shift key={row.id} row={row} actions={actions(row)} />
          ) : ["trainings", "trainingRecords"].includes(kind) ? (
            <Training
              key={row.id}
              row={row}
              result={kind === "trainingRecords"}
              actions={actions(row)}
            />
          ) : (
            <Message
              key={row.id}
              row={row}
              notice={kind === "publicNotices"}
              actions={actions(row)}
              navigate={navigate}
            />
          ),
        )}
      </div>
      <Pagination
        className="designed-pagination"
        current={page}
        pageSize={size}
        total={total}
        showTotal
        sizeCanChange
        sizeOptions={[10, 20, 50]}
        onChange={onPage}
      />
    </Spin>
  );
}
