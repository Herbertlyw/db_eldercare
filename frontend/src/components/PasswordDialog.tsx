import { useState } from "react";
import {
  Alert,
  Button,
  Form,
  Input,
  Modal,
  Space,
  Typography,
} from "@arco-design/web-react";
import { command, messageOf } from "../api/client";
import { passwordError } from "../forms/schema";
export function PasswordDialog({
  forced,
  onClose,
  onChanged,
  onLogout,
}: {
  forced: boolean;
  onClose: () => void;
  onChanged: () => void;
  onLogout: () => void;
}) {
  const [old, setOld] = useState(""),
    [next, setNext] = useState(""),
    [confirmation, setConfirmation] = useState(""),
    [error, setError] = useState(""),
    [busy, setBusy] = useState(false);
  async function submit() {
    if (busy) return;
    const invalid = !old
      ? "请输入当前密码或临时密码"
      : passwordError(next) ||
        (next !== confirmation ? "两次输入的新密码不一致" : "");
    if (invalid) {
      setError(invalid);
      return;
    }
    setBusy(true);
    try {
      await command("password.change", {
        oldPassword: old,
        newPassword: next,
        confirmPassword: confirmation,
      });
      setOld("");
      setNext("");
      setConfirmation("");
      onChanged();
    } catch (e) {
      setError(messageOf(e));
    } finally {
      setBusy(false);
    }
  }
  return (
    <Modal
      className="password-dialog"
      title={forced ? "设置新密码" : "修改密码"}
      visible
      closable={!forced && !busy}
      escToExit={!forced && !busy}
      maskClosable={false}
      onCancel={onClose}
      footer={
        <Space>
          <Button disabled={busy} onClick={forced ? onLogout : onClose}>
            {forced ? "退出登录" : "取消"}
          </Button>
          <Button
            id="save-password"
            type="primary"
            loading={busy}
            onClick={submit}
          >
            保存密码
          </Button>
        </Space>
      }
    >
      <Typography.Paragraph>
        {forced
          ? "当前使用的是临时密码，请先设置自己的密码。"
          : "修改后需要重新登录。"}
      </Typography.Paragraph>
      <Form layout="vertical" onSubmit={submit}>
        <Form.Item label="当前密码或临时密码" required>
          <Input.Password
            id="old-password"
            aria-label="当前密码或临时密码"
            autoComplete="current-password"
            value={old}
            onChange={setOld}
          />
        </Form.Item>
        <Form.Item label="新密码" required>
          <Input.Password
            id="new-password"
            aria-label="新密码"
            autoComplete="new-password"
            value={next}
            onChange={setNext}
            placeholder="至少 10 个字符，不超过 72 字节"
          />
        </Form.Item>
        <Form.Item label="确认新密码" required>
          <Input.Password
            id="confirm-password"
            aria-label="确认新密码"
            autoComplete="new-password"
            value={confirmation}
            onChange={setConfirmation}
          />
        </Form.Item>
        <button type="submit" hidden />
      </Form>
      {error && <Alert id="password-error" type="error" content={error} />}
    </Modal>
  );
}
