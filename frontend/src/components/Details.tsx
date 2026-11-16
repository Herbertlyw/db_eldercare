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
import { api } from "../api/client";
import { useResource } from "../hooks/useResource";
import { columns, detailKinds, menus, names } from "../model/registry";
import { displayValue, flowSteps } from "../model/presentation";
import { choices, entity, preset } from "../model/rules";
import type { Criteria, Operation, Row, User, Values } from "../model/types";
export function DetailSections({
  data,
  kind = "",
}: {
  data: Record<string, Row | Row[]>;
  kind?: string;
}) {
  return (
    <div className="detail-content">
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
              column={2}
              layout="vertical"
              border
              data={Object.entries(part || {})
                .filter(([key]) => key !== "current_worker_account")
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
  const resource = useResource<Record<string, Row | Row[]>>(
    kind + ":" + row.id,
    (signal) =>
      detailKinds[kind]
        ? api("/details/" + detailKinds[kind] + "/" + row.id, { signal })
        : Promise.resolve({ 记录: row }),
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
  return (
    <Modal
      className="detail-dialog"
      title={(row.elder_name || row.name || names[kind]) + " · 详情"}
      visible
      footer={null}
      style={{ width: "min(1100px, calc(100vw - 32px))" }}
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
          resource.data && (
            <>
              <Space className="detail-actions" wrap>
                {choices(kind, record, user).map((c) => (
                  <Button
                    key={c.action}
                    data-action={c.action}
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
                ))}
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
          )
        )}
      </Spin>
    </Modal>
  );
}
