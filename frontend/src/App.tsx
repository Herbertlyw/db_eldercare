import { useEffect, useState } from "react";
import { ConfigProvider, Message } from "@arco-design/web-react";
import { api, messageOf, session } from "./api/client";
import { Login } from "./pages/Login";
import { Workspace } from "./pages/Workspace";
import { PasswordDialog } from "./components/PasswordDialog";
import type { User } from "./model/types";
export default function App() {
  const [user, setUser] = useState<User>(),
    [initializing, setInitializing] = useState(!!session.get()),
    [notice, setNotice] = useState(""),
    [passwordMode, setPasswordMode] = useState<"forced" | "normal" | null>(
      null,
    );
  useEffect(() => {
    let live = true;
    const expired = () => {
        setUser(undefined);
        setPasswordMode(null);
        setNotice("登录已过期，请重新登录。");
      },
      required = () => {
        setUser(undefined);
        setPasswordMode("forced");
      };
    window.addEventListener("session-expired", expired);
    window.addEventListener("password-required", required);
    if (session.get())
      api<User>("/me")
        .then((u) => {
          if (live) setUser(u);
        })
        .catch((e) => {
          if (live && e.code !== "PASSWORD_CHANGE_REQUIRED")
            setNotice(messageOf(e));
        })
        .finally(() => {
          if (live) setInitializing(false);
        });
    return () => {
      live = false;
      window.removeEventListener("session-expired", expired);
      window.removeEventListener("password-required", required);
    };
  }, []);
  async function login(username: string, password: string) {
    const result = await api<{ token: string; mustChangePassword: boolean }>(
      "/login",
      { method: "POST", body: JSON.stringify({ username, password }) },
    );
    session.set(result.token);
    setNotice("");
    if (result.mustChangePassword) {
      setPasswordMode("forced");
      return;
    }
    setUser(await api<User>("/me"));
  }
  async function logout() {
    try {
      await api("/logout", { method: "POST" });
    } catch (e) {
      Message.warning(messageOf(e));
    } finally {
      session.clear();
      setUser(undefined);
      setPasswordMode(null);
      setNotice("");
      history.replaceState(null, "", location.pathname + location.search);
    }
  }
  return (
    <ConfigProvider>
      {user ? (
        <Workspace
          key={user.id}
          user={user}
          onPassword={() => setPasswordMode("normal")}
          onLogout={logout}
        />
      ) : (
        <Login onLogin={login} notice={notice} loading={initializing} />
      )}{" "}
      {passwordMode && (
        <PasswordDialog
          forced={passwordMode === "forced"}
          onClose={() => {
            if (passwordMode !== "forced") setPasswordMode(null);
          }}
          onLogout={logout}
          onChanged={() => {
            session.clear();
            setUser(undefined);
            setPasswordMode(null);
            setNotice("密码已修改，请使用新密码登录。");
          }}
        />
      )}
    </ConfigProvider>
  );
}
