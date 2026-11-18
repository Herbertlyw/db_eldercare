import {
  Alert,
  Button,
  Descriptions,
  Empty,
  Modal,
  Space,
  Spin,
  Steps,
  Table,
  Tag,
  Typography,
} from "@arco-design/web-react";
import { useMobile } from "../hooks/useMobile";
import { OrderVisitDetails } from "./OrderVisitDetails";
import { NavigationIcon } from "./NavigationIcon";
import { api, list } from "../api/client";
import { useResource } from "../hooks/useResource";
import { columns, detailKinds, menus, names } from "../model/registry";
import {
  displayValue,
  recordKey,
  flowSteps,
  pendingStates,
  closedStates,
} from "../model/presentation";
import { choices, entity, preset } from "../model/rules";
import type { Criteria, Operation, Row, User, Values } from "../model/types";
export function DetailSections({
  data,
  kind = "",
  className = "detail-content",
}: {
  data: Record<string, Row | Row[]>;
  kind?: string;
  className?: string;
}) {
  const mobile = useMobile();
  return (
    <div className={className}>
      {Object.entries(data).map(([title, part]) => (
        <section className="detail-section" key={title}>
          <Typography.Title heading={6}>{title}</Typography.Title>
          {Array.isArray(part) ? (
            part.length ? (
              <Table
                border={false}
                size="small"
                rowKey={(row) => JSON.stringify(row)}
                data={part}
                columns={Object.keys(part[0]).map((key) => ({
                  title: columns[key] || key,
                  dataIndex: key,
                  width: [
                    "message",
                    "content",
                    "notes",
                    "evidence",
                    "measures",
                    "result",
                  ].includes(key)
                    ? 280
                    : 140,
                  render: (value) => displayValue(key, value, kind),
                }))}
                scroll={{ x: true }}
                pagination={part.length > 10 ? { pageSize: 10 } : false}
              />
            ) : (
              <Empty description="暂无记录" />
            )
          ) : (
            <Descriptions
              column={mobile ? 1 : 2}
              layout="vertical"
              data={Object.entries(part || {})
                .filter(
                  ([key]) =>
                    !["current_worker_account", "dispatch_window"].includes(
                      key,
                    ),
                )
                .map(([key, value]) => ({
                  label: columns[key] || key,
                  value: displayValue(key, value, kind),
                }))}
            />
          )}
        </section>
      ))}
    </div>
  );
}
interface Props {
  kind: string;
  row: Row;
  user: User;
  onClose: () => void;
  onOpen: (
    action: string,
    preset?: Values,
    context?: Operation["context"],
  ) => void;
  onNavigate: (kind: string, criteria?: Criteria) => void;
}
export function Details({
  kind,
  row,
  user,
  onClose,
  onOpen,
  onNavigate,
}: Props) {
  const isOrder = entity(kind) === "orders";
  const detailKind = isOrder ? "orders" : detailKinds[kind];
  const resource = useResource<Record<string, Row | Row[]>>(
    kind + ":" + recordKey(kind, row),
    async (signal) => {
      if (!detailKind) return { 记录: row };
      const detail = api<Record<string, Row | Row[]>>(
        "/details/" + detailKind + "/" + row.id,
        { signal },
      );
      if (!isOrder || kind === "orders") return detail;
      // 待办队列只含订单编号，用同一账号可查的订单补齐姓名、项目和调度时段。
      const [parts, result] = await Promise.all([
        detail,
        list("orders", { field: "id", value: row.id, size: 1 }, signal),
      ]);
      return { ...parts, 订单: { ...result.rows[0], ...parts["订单"] } };
    },
  );
  const main =
    Object.values(resource.data || {}).find(
      (x): x is Row => !!x && !Array.isArray(x) && x.id != null,
    ) || row;
  const record = { ...row, ...main };
  const steps = flowSteps(entity(kind), record),
    index = steps.findIndex(([s]) => s === record.status);
  const links =
    entity(kind) === "elders"
      ? [
          ["服务订单", "orders", "elder_id"],
          ["照护计划", "plans", "elder_id"],
          ["能力评估", "assessments", "elder_id"],
        ]
      : entity(kind) === "caregivers"
        ? [
            ["班次", "shifts", "caregiver_id"],
            ["职业证书", "credentials", "caregiver_id"],
          ]
        : [];
  const options = choices(kind, record, user);
  const primaryAction = isOrder
    ? options.find((choice) =>
        [
          "order.confirm",
          "order.accept",
          "order.checkin",
          "order.finish",
          "order.assign",
          "order.reschedule",
        ].includes(choice.action),
      )?.action
    : undefined;
  const actionButtons = options.map((c) => (
    <Button
      key={c.action}
      data-action={c.action}
      type={c.action === primaryAction ? "primary" : "secondary"}
      onClick={() => {
        onClose();
        onOpen(
          c.action,
          { ...preset(kind, record), ...c.preset },
          { kind, row: record },
        );
      }}
    >
      {c.label}
    </Button>
  ));
  return (
    <Modal
      className={isOrder ? "detail-dialog order-visit-dialog" : "detail-dialog"}
      title={
        isOrder
          ? "服务订单详情"
          : (row.elder_name || row.name || names[kind]) + " · 详情"
      }
      visible
      footer={
        isOrder && resource.data && !resource.error && actionButtons.length ? (
          <Space className="detail-actions" wrap>
            {actionButtons}
          </Space>
        ) : null
      }
      style={{
        width: isOrder
          ? "min(840px, calc(100vw - 32px))"
          : "min(1100px, calc(100vw - 32px))",
      }}
      onCancel={onClose}
      unmountOnExit
    >
      <Spin loading={resource.loading} style={{ width: "100%" }}>
        {resource.error ? (
          <Alert
            type="error"
            content={resource.error}
            action={<Button onClick={resource.retry}>重试</Button>}
          />
        ) : (
          resource.data &&
          (isOrder ? (
            <OrderVisitDetails
              order={record}
              visits={
                Array.isArray(resource.data["派单与服务过程"])
                  ? resource.data["派单与服务过程"]
                  : []
              }
            >
              <DetailSections
                data={{ 订单: main }}
                kind="orders"
                className="visit-registered-data"
              />
            </OrderVisitDetails>
          ) : (
            <>
              <div className="detail-overview">
                <span className="detail-overview-icon">
                  <NavigationIcon kind={entity(kind)} />
                </span>
                <div className="detail-overview-copy">
                  <strong>
                    {record.elder_name ||
                      record.name ||
                      record.candidate_name ||
                      names[kind]}
                  </strong>
                  <span>
                    {names[kind]} · #{record.id}
                  </span>
                </div>
                {record.status && (
                  <Tag
                    color={
                      pendingStates.includes(record.status)
                        ? "orange"
                        : closedStates.includes(record.status)
                          ? undefined
                          : "arcoblue"
                    }
                  >
                    {displayValue("status", record.status, entity(kind))}
                  </Tag>
                )}
              </div>
              <Space className="detail-actions" wrap>
                {actionButtons}
                {links
                  .filter(([, target]) => menus[user.role].includes(target))
                  .map(([label, target, field]) => (
                    <Button
                      key={target}
                      onClick={() => {
                        onClose();
                        onNavigate(target, { field, value: row.id });
                      }}
                    >
                      {label}
                    </Button>
                  ))}
              </Space>
              {steps.length > 0 &&
                (index < 0 ? (
                  <Tag>
                    {displayValue("status", record.status, entity(kind))}
                  </Tag>
                ) : (
                  <Steps
                    className="workflow-progress"
                    size="small"
                    current={index + 1}
                  >
                    {steps.map(([status, title]) => (
                      <Steps.Step key={status} title={title} />
                    ))}
                  </Steps>
                ))}
              <DetailSections data={resource.data} kind={entity(kind)} />
            </>
          ))
        )}
      </Spin>
    </Modal>
  );
}
