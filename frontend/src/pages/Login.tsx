import { useState } from "react";
import {
  Alert,
  Button,
  Form,
  Input,
  Modal,
  Spin,
  Typography,
} from "@arco-design/web-react";
import { IconHome, IconArrowRight } from "@arco-design/web-react/icon";
import { api, messageOf } from "../api/client";
import { DetailSections } from "../components/Details";
import type { Row } from "../model/types";
interface Props {
  onLogin: (name: string, password: string) => Promise<void>;
  notice: string;
  loading: boolean;
}
export function Login({ onLogin, notice, loading }: Props) {
  const [name, setName] = useState(""),
    [password, setPassword] = useState(""),
    [error, setError] = useState(""),
    [busy, setBusy] = useState(false),
    [help, setHelp] = useState(false),
    [publicData, setPublicData] = useState<Row[]>();
  async function login() {
    if (busy) return;
    if (!name.trim() || !password) {
      setError("请输入账号和密码");
      return;
    }
    setBusy(true);
    setError("");
    try {
      await onLogin(name.trim(), password);
      setPassword("");
    } catch (e) {
      setError(messageOf(e));
    } finally {
      setBusy(false);
    }
  }
  async function notices() {
    try {
      setPublicData(await api("/public/notices"));
    } catch (e) {
      setError(messageOf(e));
    }
  }
  return (
    <section id="login">
      <header className="login-header">
        <a className="wordmark" href="/" aria-label="左邻右舍首页">
          <img src="/brand-mark.svg" alt="" />
          <span>左邻右舍</span>
        </a>
        <Button className="public-entry" type="text" onClick={notices}>
          服务公示 ↗
        </Button>
      </header>
      <div className="login-layout">
        <div className="intro">
          <span className="intro-label">左邻右舍 · 社区服务</span>
          <h1>
            社区养老
            <br />
            服务工作台
          </h1>
          <img
            className="neighborhood"
            src="/neighborhood.svg"
            alt="社区住宅与邻里服务站的建筑插画"
          />
          <div className="intro-caption">
            <span>社区居家养老智慧服务平台</span>
            <IconArrowRight />
          </div>
        </div>
        <div className="login-panel">
          <Spin loading={loading}>
            <Form id="login-form" layout="vertical" onSubmit={login}>
              <div className="login-symbol">
                <IconHome />
              </div>
              <h2>登录工作台</h2>
              <Form.Item label="账号" required>
                <Input
                  id="username"
                  aria-label="账号"
                  autoComplete="username"
                  value={name}
                  onChange={setName}
                  placeholder="请输入账号"
                />
              </Form.Item>
              <Form.Item label="密码" required>
                <Input.Password
                  id="password"
                  aria-label="密码"
                  autoComplete="current-password"
                  value={password}
                  onChange={setPassword}
                  placeholder="请输入密码"
                />
              </Form.Item>
              {(error || notice) && (
                <Alert
                  id="login-error"
                  type={error ? "error" : "info"}
                  content={error || notice}
                />
              )}
              <Button
                type="primary"
                htmlType="submit"
                className="login-submit"
                long
                loading={busy}
              >
                登录 <IconArrowRight />
              </Button>
              <Button
                id="forgot-password"
                type="text"
                onClick={() => setHelp(true)}
              >
                忘记密码？
              </Button>
              <p className="account-help">
                联系管理员{" "}
                <a href="mailto:2142354019@qq.com">2142354019@qq.com</a>
              </p>
            </Form>
          </Spin>
          <p className="demo-note">左邻右舍 · 社区养老服务</p>
        </div>
      </div>
      <Modal
        title="忘记密码"
        visible={help}
        footer={
          <Button type="primary" onClick={() => setHelp(false)}>
            知道了
          </Button>
        }
        onCancel={() => setHelp(false)}
      >
        <Typography.Paragraph>
          请联系工作人员核实身份并重置密码。
        </Typography.Paragraph>
        <ul className="recovery-list">
          <li>老人及家属：联系所属社区管理员。</li>
          <li>护工：联系所在机构负责人。</li>
          <li>管理及监管人员：联系平台维护人员。</li>
        </ul>
        <Typography.Paragraph>
          请提供登录账号，不要发送原密码。收到临时密码后，首次登录需设置新密码。
        </Typography.Paragraph>
        <a href="mailto:2142354019@qq.com">2142354019@qq.com</a>
      </Modal>
      <Modal
        className="public-notices-dialog"
        title="服务质量公示"
        visible={!!publicData}
        style={{ width: "min(1000px, calc(100vw - 32px))" }}
        footer={null}
        onCancel={() => setPublicData(undefined)}
      >
        {publicData && <DetailSections data={{ 公示记录: publicData }} />}
      </Modal>
    </section>
  );
}
