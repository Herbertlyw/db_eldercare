import { useCallback, useRef, useState } from "react";
import {
  Alert,
  Button,
  Drawer,
  Form,
  Message,
  Space,
  Typography,
} from "@arco-design/web-react";
import { actions, detailKinds } from "../model/registry";
import { command, messageOf } from "../api/client";
import { entity, primaryKey } from "../model/rules";
import { recordLabel } from "../model/presentation";
import { confirm } from "../components/confirm";
import { BusinessField } from "./BusinessField";
import {
  changedValues,
  consequences,
  createdKinds,
  fieldsFor,
  initialValues,
  confirmations,
  payload,
  sourceFor,
  validate,
} from "./schema";
import type { Operation, Row, User, Values } from "../model/types";
interface Props {
  operation: Operation;
  user: User;
  onClose: () => void;
  onOpen: (
    action: string,
    preset?: Values,
    context?: Operation["context"],
  ) => void;
  onSaved: () => void;
  onDetails: (kind: string, row: Row) => void;
}
export function OperationDrawer({
  operation: op,
  user,
  onClose,
  onOpen,
  onSaved,
  onDetails,
}: Props) {
  const [values, setValues] = useState<Values>(() =>
    initialValues(op.action, user.role, op.preset),
  );
  const [records, setRecords] = useState<Record<string, Row | undefined>>({});
  const [ready, setReady] = useState<Record<string, boolean>>({});
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [error, setError] = useState(""),
    [dirty, setDirty] = useState(false),
    [busy, setBusy] = useState(false),
    [receipt, setReceipt] = useState<{ result: any } | null>(null);
  const sending = useRef(false);
  const change = useCallback(
    (key: string, value: any, row?: Row) => {
      if (sending.current) return;
      setDirty(true);
      setValues((old) => {
        const next = changedValues(op.action, old, key, value);
        if (key === "guardianId") {
          next.relation = row?.relation_name || "";
          next.phone = row?.phone || "";
          next.active = row ? String(!!row.active) : undefined;
        }
        return next;
      });
      setErrors({});
      if (row) setRecords((old) => ({ ...old, [key]: row }));
    },
    [op.action],
  );
  const record = useCallback(
    (key: string, row?: Row) =>
      setRecords((old) => (old[key] === row ? old : { ...old, [key]: row })),
    [],
  );
  const markReady = useCallback(
    (key: string, value: boolean) =>
      setReady((old) => (old[key] === value ? old : { ...old, [key]: value })),
    [],
  );
  async function close() {
    if (sending.current) return;
    if (
      dirty &&
      !receipt &&
      !(await confirm("放弃未提交的内容？", "关闭后需要重新填写。"))
    )
      return;
    onClose();
  }
  async function submit() {
    if (sending.current || receipt) return;
    const errors = validate(
      op.action,
      user.role,
      values,
      ready,
      records.policyId?.settlement_cycle,
    );
    setErrors(errors);
    setError("");
    if (Object.keys(errors).length) {
      setError("请检查标出的字段后再提交。");
      requestAnimationFrame(() => {
        const field = document.querySelector<HTMLElement>(
          `[data-field="${Object.keys(errors)[0]}"]`,
        );
        field?.scrollIntoView({ block: "center", behavior: "smooth" });
        field
          ?.querySelector<HTMLElement>("input, textarea, button")
          ?.focus({ preventScroll: true });
      });
      return;
    }
    sending.current = true;
    setBusy(true);
    try {
      const warning = confirmations[op.action];
      if (warning) {
        const [key, effect] = warning;
        const row = records[key];
        const id = values[key];
        const subject =
          row && String(row.id) === String(id)
            ? recordLabel(
                row,
                entity(sourceFor(key, op.action, user.role) || ""),
              )
            : `${fields.find((f) => f.key === key)?.label || "记录"} #${id}`;
        if (
          !(await confirm(
            "确认" + actions[op.action][0] + "？",
            `${subject}。${effect}`,
          ))
        )
          return;
      }
      const result = await command(
        op.action,
        payload(op.action, user.role, values),
      );
      setReceipt(result);
      setDirty(false);
      setValues((old) =>
        Object.fromEntries(
          Object.entries(old).filter(
            ([k]) => !k.toLowerCase().includes("password"),
          ),
        ),
      );
      Message.success(actions[op.action][0] + "已完成");
      onSaved();
    } catch (e) {
      setError(messageOf(e));
    } finally {
      sending.current = false;
      setBusy(false);
    }
  }
  function view() {
    let kind = createdKinds[op.action],
      id = kind ? receipt?.result : undefined;
    if (!kind && op.context) {
      kind = op.context.kind;
      id = values[primaryKey(kind)] || op.context.row.id;
    }
    if (kind && typeof id === "number" && detailKinds[kind]) {
      onClose();
      onDetails(kind, { id });
    }
  }
  const target = createdKinds[op.action] || op.context?.kind;
  const fields = fieldsFor(op.action, user.role, values);
  return (
    <Drawer
      className="operation-drawer"
      title={<span id="operation-title">{actions[op.action][0]}</span>}
      width={600}
      visible
      maskClosable={false}
      onCancel={close}
      escToExit={!busy}
      unmountOnExit
      footer={
        receipt ? (
          <Space>
            <Button onClick={onClose}>完成</Button>
            {target && detailKinds[target] && (
              <Button type="primary" onClick={view}>
                查看记录
              </Button>
            )}
          </Space>
        ) : (
          <Space>
            <Button disabled={busy} onClick={close}>
              取消
            </Button>
            <Button
              id="submit-operation"
              type="primary"
              loading={busy}
              onClick={submit}
            >
              {op.action === "maintenance.run" ? "开始检查" : "确认提交"}
            </Button>
          </Space>
        )
      }
    >
      <div id="operation-dialog" data-action={op.action} aria-busy={busy}>
        {op.context &&
          !receipt &&
          (values[primaryKey(op.context.kind)] == null ||
            String(values[primaryKey(op.context.kind)]) ===
              String(op.context.row.id)) && (
            <div className="operation-context">
              <span>当前办理对象</span>
              <strong>{recordLabel(op.context.row, op.context.kind)}</strong>
            </div>
          )}
        {receipt ? (
          <div id="operation-receipt">
            <Alert
              type="success"
              title="办理完成"
              content={
                typeof receipt.result === "string"
                  ? receipt.result
                  : "业务记录已保存。"
              }
            />
            {Array.isArray(receipt.result) && (
              <div className="recommendations">
                {receipt.result.length ? (
                  receipt.result.map((r: Row) => (
                    <Button
                      key={r.shift_id}
                      onClick={() =>
                        onOpen(
                          "order.assign",
                          { orderId: values.orderId, shiftId: r.shift_id },
                          op.context,
                        )
                      }
                    >
                      {r.name} · 剩余 {r.available} 个名额
                    </Button>
                  ))
                ) : (
                  <Typography.Text>
                    当前没有符合资格和时间要求的班次。
                  </Typography.Text>
                )}
              </div>
            )}
          </div>
        ) : (
          <Form id="action-form" layout="vertical" onSubmit={submit}>
            <div id="fields">
              <fieldset disabled={busy}>
                {fields.map((field) => (
                  <BusinessField
                    key={field.key}
                    field={field}
                    action={op.action}
                    user={user}
                    values={values}
                    records={records}
                    error={errors[field.key]}
                    onChange={change}
                    onRecord={record}
                    onReady={markReady}
                  />
                ))}
              </fieldset>
            </div>
            {consequences[op.action] && (
              <Alert type="info" content={consequences[op.action]} />
            )}
            <button hidden type="submit" tabIndex={-1} aria-hidden="true" />
          </Form>
        )}
        {error && (
          <Alert id="result" role="alert" type="error" content={error} />
        )}
      </div>
    </Drawer>
  );
}
