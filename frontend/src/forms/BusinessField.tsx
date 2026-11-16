import { useRef, useState } from "react";
import {
  Button,
  DatePicker,
  Form,
  Input,
  InputNumber,
  Select,
  Space,
  Typography,
} from "@arco-design/web-react";
import type { Field } from "./schema";
import { numericBounds, parseSamples, sourceFor } from "./schema";
import { RecordSelect } from "./RecordSelect";
import { LinkedSelect, MultiRecords, ServiceItems } from "./LinkedFields";
import type { Row, User, Values } from "../model/types";
interface Props {
  field: Field;
  action: string;
  user: User;
  values: Values;
  records: Record<string, Row | undefined>;
  error?: string;
  onChange: (name: string, value: any, row?: Row) => void;
  onRecord: (name: string, row?: Row) => void;
  onReady: (name: string, ready: boolean) => void;
}
export function BusinessField({
  field: f,
  action,
  user,
  values: v,
  records,
  error,
  onChange,
  onRecord,
  onReady,
}: Props) {
  const [fileNote, setFileNote] = useState("");
  const file = useRef<HTMLInputElement>(null);
  if (f.hidden) return null;
  const name = f.key,
    value = v[name],
    change = (value: any) => onChange(name, value);
  const linked = {
    name,
    label: f.label,
    value,
    values: action === "plan.revise" ? { ...v, _planItems: true } : v,
    records,
    onChange,
    onReady,
  };
  const source = sourceFor(name, action, user.role);
  let control;
  let disabled =
    action === "guardian.update" &&
    ["relation", "phone", "active"].includes(name) &&
    !v.guardianId;
  let label = f.label,
    help = "";
  const cycle = records.policyId?.settlement_cycle;
  if (name === "itemId" && action === "order.create")
    control = <LinkedSelect {...linked} mode="appointment" />;
  else if (name === "shiftId" && action === "order.assign")
    control = <LinkedSelect {...linked} mode="dispatch" />;
  else if (name === "guardianId")
    control = <LinkedSelect {...linked} mode="guardian" />;
  else if (name === "orderIds" || name === "communityIds")
    control = (
      <MultiRecords
        {...linked}
        mode={name === "orderIds" ? "orders" : "communities"}
      />
    );
  else if (name === "items") control = <ServiceItems {...linked} />;
  else if (source) {
    const requiresElder =
      name === "assessmentId" ||
      (name === "planId" && action === "plan.revise");
    const requiresAssessment =
      name === "bundleId" && actionsHaveAssessment(action);
    const hint =
      requiresElder && !v.elderId
        ? "请先选择老人"
        : requiresAssessment && !records.assessmentId
          ? "请先选择能力评估"
          : undefined;
    control = (
      <RecordSelect
        name={name}
        label={label}
        source={source}
        action={action}
        user={user}
        value={value}
        required={f.required}
        onChange={(value, row) => onChange(name, value, row)}
        onRecord={onRecord}
        onReady={onReady}
        disabledHint={hint}
        scope={
          requiresElder
            ? { field: "elder_id", value: v.elderId }
            : requiresAssessment
              ? { level: records.assessmentId?.ability_level }
              : {}
        }
      />
    );
  } else if (f.options) {
    let options = f.options;
    if (
      name === "requiresRectification" &&
      v.score !== "" &&
      v.score != null &&
      Number(v.score) < 60
    ) {
      options = options.map((o) => ({ ...o, disabled: o.value === "false" }));
      help = "评分低于 60 分，需填写整改要求";
    }
    control = (
      <Select
        id={"field-" + name}
        aria-label={label}
        value={value}
        disabled={disabled}
        placeholder="请选择"
        options={options}
        onChange={change}
      />
    );
  } else if (name === "period" && action === "claim.submit") {
    label =
      cycle === "QUARTER"
        ? "结算季度"
        : cycle === "YEAR"
          ? "结算年度"
          : "结算月份";
    if (cycle === "MONTH")
      control = (
        <DatePicker.MonthPicker
          inputProps={{ id: "field-" + name, "aria-label": label }}
          style={{ width: "100%" }}
          value={value}
          onChange={change}
          placeholder="选择结算月份"
        />
      );
    else if (cycle === "YEAR")
      control = (
        <DatePicker.YearPicker
          inputProps={{ id: "field-" + name, "aria-label": label }}
          style={{ width: "100%" }}
          value={value}
          onChange={change}
          placeholder="选择结算年度"
        />
      );
    else
      control = (
        <Input
          id={"field-" + name}
          aria-label={label}
          disabled={!cycle}
          value={value || ""}
          placeholder={cycle ? "例如：2026-Q4" : "请先选择补贴政策"}
          onChange={change}
        />
      );
  } else if (["date", "datetime-local", "month"].includes(f.type)) {
    const Picker = f.type === "month" ? DatePicker.MonthPicker : DatePicker;
    control = (
      <Picker
        inputProps={{ id: "field-" + name, "aria-label": label }}
        style={{ width: "100%" }}
        showTime={f.type === "datetime-local"}
        format={
          f.type === "datetime-local"
            ? "YYYY-MM-DD HH:mm:ss"
            : f.type === "month"
              ? "YYYY-MM"
              : "YYYY-MM-DD"
        }
        value={typeof value === "string" ? value.replace("T", " ") : value}
        onChange={(date) =>
          change(f.type === "datetime-local" ? date.replace(" ", "T") : date)
        }
        placeholder={
          f.type === "datetime-local" ? "选择日期与时间" : "请选择日期"
        }
      />
    );
  } else if (f.type === "number") {
    const [min, max, step] = numericBounds(name, action);
    control = (
      <InputNumber
        id={"field-" + name}
        aria-label={label}
        style={{ width: "100%" }}
        min={min}
        max={max}
        step={step}
        value={value}
        onChange={change}
        placeholder="请输入"
      />
    );
    if (name === "ratio" && value != null)
      help = "抽查 " + Number((Number(value) * 100).toFixed(4)) + "%";
  } else if (f.type === "password")
    control = (
      <Input.Password
        id={"field-" + name}
        aria-label={label}
        autoComplete={
          name === "operatorPassword" ? "current-password" : "new-password"
        }
        value={value || ""}
        onChange={change}
      />
    );
  else if (f.type === "textarea" || f.type === "json")
    control = (
      <Input.TextArea
        id={"field-" + name}
        aria-label={label}
        autoSize={{ minRows: 3, maxRows: 8 }}
        value={value || ""}
        onChange={change}
        placeholder={
          name === "samples" ? "粘贴 JSON 采样记录，或导入文件" : undefined
        }
      />
    );
  else
    control = (
      <Input
        id={"field-" + name}
        aria-label={label}
        type={name === "phone" ? "tel" : "text"}
        disabled={disabled}
        value={value || ""}
        onChange={change}
        autoComplete="off"
      />
    );
  return (
    <div data-field={name}>
      <Form.Item
        label={label}
        required={f.required}
        validateStatus={error ? "error" : undefined}
        help={error}
        extra={help || undefined}
      >
        {control}
        {name === "samples" && (
          <>
            <Space style={{ marginTop: 8 }}>
              <Button onClick={() => file.current?.click()}>
                导入 JSON 文件
              </Button>
              <Typography.Text type="secondary">{fileNote}</Typography.Text>
            </Space>
            <input
              ref={file}
              type="file"
              hidden
              accept=".json,application/json"
              aria-label="导入采样文件"
              onChange={async (e) => {
                const chosen = e.target.files?.[0];
                if (!chosen) return;
                try {
                  if (chosen.size > 1024 * 1024)
                    throw Error("请选择小于 1 MB 的采样文件");
                  const text = await chosen.text();
                  const rows = parseSamples(text);
                  change(text);
                  setFileNote(`${rows.length} 条采样记录`);
                } catch (error) {
                  setFileNote((error as Error).message + "，已保留原内容");
                } finally {
                  if (file.current) file.current.value = "";
                }
              }}
            />
          </>
        )}
      </Form.Item>
    </div>
  );
}
const actionsHaveAssessment = (action: string) =>
  action === "plan.create" || action === "plan.revise";
