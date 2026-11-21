import { useEffect, useRef, useState } from "react";
import {
  Alert,
  Avatar,
  Breadcrumb,
  Button,
  Card,
  DatePicker,
  Dropdown,
  Empty,
  Input,
  Layout,
  Menu,
  Pagination,
  Spin,
  Select,
  Space,
  Table,
  Tag,
  Tooltip,
  Typography,
} from "@arco-design/web-react";
import {
  IconApps,
  IconArrowRight,
  IconBook,
  IconCalendar,
  IconDown,
  IconFile,
  IconHeart,
  IconMenu,
  IconMore,
  IconLock,
  IconExport,
  IconNotification,
  IconPlus,
  IconRefresh,
  IconSafe,
  IconSettings,
  IconUserGroup,
} from "@arco-design/web-react/icon";
import type { Criteria, Operation, Row, User, Values } from "../model/types";
import {
  actions,
  columns,
  groups,
  menus,
  names,
  relatedActions,
  roleNames,
  tasks,
} from "../model/registry";
import { choices, preset } from "../model/rules";
import {
  appointmentSlot,
  orderStatusLabel,
  closedStates,
  columnTitle,
  displayValue,
  pageHint,
  pendingStates,
  recordColumns,
  recordKey,
} from "../model/presentation";
import { list } from "../api/client";
import { useMobile } from "../hooks/useMobile";
import { useResource } from "../hooks/useResource";
import { useAvailablePage } from "../hooks/useAvailablePage";
import { Person } from "../components/Media";
import { Details } from "../components/Details";
import { NavigationIcon } from "../components/NavigationIcon";
import { OperationDrawer } from "../forms/OperationDrawer";
import { BusinessRecords, designedRecords } from "./records/BusinessRecords";
import "../styles/business-records.css";
import { Certificates } from "./workforce/Certificates";
import { HoursSummary } from "./workforce/HoursSummary";
import { duration } from "./workforce/metrics";
import "../styles/workforce.css";
import { RoleHome } from "./workspaces/RoleHome";
import {
  primaryNavigation,
  readLocation,
  writeLocation,
} from "./workspaces/navigation";
import { dateTitle, dateText, todayInShanghai } from "./workspaces/shared";
import "../styles/workspace-polish.css";
import "../styles/role-personas.css";
const icons = [
  IconBook,
  IconFile,
  IconHeart,
  IconSafe,
  IconApps,
  IconCalendar,
  IconUserGroup,
  IconSettings,
];
export function Workspace({
  user,
  onPassword,
  onLogout,
}: {
  user: User;
  onPassword: () => void;
  onLogout: () => void;
}) {
  const [kind, setKind] = useState(() => readLocation(user.role).kind),
    [criteria, setCriteria] = useState<Criteria>(
      () => readLocation(user.role).criteria,
    ),
    [search, setSearch] = useState(
      () => readLocation(user.role).criteria.search || "",
    ),
    [page, setPage] = useState(() => readLocation(user.role).page),
    [size, setSize] = useState(10),
    [revision, setRevision] = useState(0),
    [poll, setPoll] = useState(0),
    [openMenu, setOpenMenu] = useState(false),
    [expanded, setExpanded] = useState<string[]>([]),
    [operation, setOperation] = useState<Operation>(),
    [detail, setDetail] = useState<{ kind: string; row: Row }>();
  const [selectedElder, setSelectedElder] = useState<number>(() =>
    history.state?.homeUser === user.id ? history.state.homeElder : undefined,
  );
  function selectElder(id: number) {
    setSelectedElder(id);
    history.replaceState(
      { ...history.state, homeUser: user.id, homeElder: id },
      "",
    );
  }
  const opSerial = useRef(0);
  const mobile = useMobile();
  function dispatchCriteria(k: string, c: Criteria): Criteria {
    return user.role === "ADMIN" &&
      k === "orders" &&
      c.status === "PENDING" &&
      !c.field
      ? { ...c, field: "dispatch_window", value: 1 }
      : c;
  }
  const key = JSON.stringify([user.id, kind, criteria, page, size, revision]);
  const resource = useResource(kind === "home" ? null : key, (signal) =>
    list(
      kind,
      { ...dispatchCriteria(kind, criteria), page: page - 1, size },
      signal,
    ),
  );
  const data = resource.data;
  useAvailablePage(data, setPage);
  const counts = useResource(
    kind === "home" ? null : "counts:" + user.id + ":" + revision + ":" + poll,
    async (signal) =>
      Promise.all(
        tasks[user.role].map(async ([, k, status]) => {
          try {
            return (
              await list(
                k,
                { size: 1, ...dispatchCriteria(k, { status }) },
                signal,
              )
            ).total;
          } catch {
            return null;
          }
        }),
      ),
  );
  const profile = useResource("profile:" + user.id + ":" + poll, (signal) =>
    list("workspaceProfile", { size: 1 }, signal),
  );
  const notifications = useResource(
    "notifications:" + user.id + ":" + revision + ":" + poll,
    (signal) => list("notifications", { size: 1 }, signal),
  );
  useEffect(() => {
    const t = setInterval(() => setPoll((n) => n + 1), 30000);
    return () => clearInterval(t);
  }, []);
  useEffect(() => {
    const t = setTimeout(() => {
      const value = search.trim() || undefined;
      if (criteria.search !== value) {
        setCriteria((old) => ({ ...old, search: value }));
        setPage(1);
      }
    }, 280);
    return () => clearTimeout(t);
  }, [search, criteria.search]);
  useEffect(() => {
    const index = groups.findIndex(([, items]) => items.includes(kind));
    const primary = primaryNavigation[user.role].some(([k]) => k === kind);
    setExpanded(index >= 0 && !primary ? ["more", String(index)] : []);
  }, [kind, user.role]);
  useEffect(() => {
    const hash = writeLocation(kind, criteria, page);
    if (location.hash !== hash) history.replaceState(history.state, "", hash);
  }, [kind, criteria, page]);
  useEffect(() => {
    const handler = () => {
      const next = readLocation(user.role);
      if (history.state?.homeUser === user.id)
        setSelectedElder(history.state.homeElder);
      setKind(next.kind);
      setCriteria(next.criteria);
      setSearch(next.criteria.search || "");
      setPage(next.page);
      setOpenMenu(false);
    };
    window.addEventListener("hashchange", handler);
    window.addEventListener("popstate", handler);
    return () => {
      window.removeEventListener("hashchange", handler);
      window.removeEventListener("popstate", handler);
    };
  }, [user.role]);
  function navigate(next: string, filter: Criteria = {}) {
    if (next !== "home" && !menus[user.role].includes(next)) return;
    history.pushState(
      { homeUser: user.id, homeElder: selectedElder },
      "",
      writeLocation(next, filter, 1),
    );
    setKind(next);
    setPage(1);
    setSearch(filter.search || "");
    setCriteria(filter);
    setOpenMenu(false);
  }
  function open(
    action: string,
    value: Values = {},
    context?: Operation["context"],
  ) {
    if (!actions[action]?.[1].includes(user.role)) return;
    setDetail(undefined);
    setOperation({
      action,
      preset: value,
      context,
      serial: ++opSerial.current,
    });
  }
  function refresh() {
    setRevision((n) => n + 1);
  }
  function showDetails(kind: string, row: Row) {
    setDetail({ kind, row });
  }
  function rowButton(
    row: Row,
    action: string,
    label: string,
    extra: Values = {},
  ) {
    return (
      <Button
        key={action}
        data-action={action}
        type="text"
        size="small"
        onClick={() =>
          open(action, { ...preset(kind, row), ...extra }, { kind, row })
        }
      >
        {label}
      </Button>
    );
  }
  function cell(key: string, value: any, row: Row) {
    if (kind === "orders" && !mobile) {
      if (key === "item_name")
        return (
          <span className="order-list-project">
            <strong>{value}</strong>
            <small>订单 #{row.id}</small>
          </span>
        );
      if (key === "elder_name")
        return <Person id={row.elder_id} name={value} />;
      if (key === "starts_at") {
        const slot = appointmentSlot(row);
        return (
          <span className="order-list-slot">
            <strong>{slot.date || "—"}</strong>
            <small>{slot.time}</small>
          </span>
        );
      }
    }
    if (key === "name" && kind === "elders")
      return <Person id={row.id} name={value} />;
    if (key === "status")
      return (
        <Tag
          color={
            kind === "hours" && value === "INVALID"
              ? "gray"
              : pendingStates.includes(value)
                ? "orange"
                : closedStates.includes(value)
                  ? undefined
                  : "arcoblue"
          }
        >
          {kind === "orders"
            ? orderStatusLabel(row)
            : displayValue(key, value, kind)}
        </Tag>
      );
    if (key === "tags" && value)
      return (
        <Space size={4} wrap>
          {String(value)
            .split(",")
            .map((t) => (
              <Tag key={t}>{displayValue("tags", t)}</Tag>
            ))}
        </Space>
      );
    if (
      (/_at$/.test(key) ||
        ["check_in", "check_out", "valid_from", "valid_until"].includes(key)) &&
      typeof value === "string" &&
      /^\d{4}-\d{2}-\d{2}[T ]/.test(value)
    ) {
      const date = displayValue(key, value);
      return (
        <span className="attendance-time">
          <span>{date.slice(0, 10)}</span>
          <small>{date.slice(11, 19)}</small>
        </span>
      );
    }
    if (["hours", "monthlyHours"].includes(kind) && key === "effective_minutes")
      return duration(Number(value));
    if (key === "id") return <span className="business-cell-id">{value}</span>;
    if (
      ["name", "elder_name", "caregiver_name", "candidate_name"].includes(key)
    )
      return <span className="business-cell-name">{value || "—"}</span>;
    if (["amount", "self_pay", "paid_subsidy"].includes(key) && value != null)
      return (
        <span className="business-cell-money">
          ¥{" "}
          {Number(value).toLocaleString("zh-CN", {
            minimumFractionDigits: 2,
            maximumFractionDigits: 2,
          })}
        </span>
      );
    return displayValue(key, value, kind);
  }
  const listKeys =
    kind === "orders" && !mobile
      ? ["item_name", "elder_name", "starts_at", "status", "amount"]
      : recordColumns(kind, data?.rows[0] || {});
  const tableColumns = data?.rows.length
    ? listKeys.map((key) => ({
        title:
          kind === "orders" && key === "starts_at"
            ? "预约时段"
            : columnTitle(key, kind),
        dataIndex: key,
        width:
          kind === "elders"
            ? {
                id: 70,
                name: 150,
                birth_date: 130,
                address: 190,
                tags: 100,
                status: 90,
              }[key] || 120
            : key === "id"
              ? 85
              : /name/.test(key)
                ? 165
                : ["address", "content", "summary", "message"].includes(key)
                  ? 250
                  : /_at$|_date$/.test(key)
                    ? 165
                    : 135,
        render: (value: any, row: Row) => cell(key, value, row),
      }))
    : [];
  const tableActions = {
    title: "操作",
    dataIndex: "_actions",
    width: data?.rows.some((row) => choices(kind, row, user).length > 1)
      ? 240
      : data?.rows.some((row) => choices(kind, row, user).length)
        ? 180
        : 100,
    fixed: "right" as const,
    render: (_: any, row: Row) => (
      <Space size={0}>
        <Button
          type="text"
          size="small"
          className="detail-link"
          onClick={() => showDetails(kind, row)}
        >
          详情
        </Button>
        {choices(kind, row, user)
          .slice(0, 2)
          .map((c) => rowButton(row, c.action, c.label, c.preset))}
        {choices(kind, row, user).length > 2 && (
          <Button
            type="text"
            size="small"
            onClick={() => showDetails(kind, row)}
          >
            更多
          </Button>
        )}
      </Space>
    ),
  };
  const related = relatedActions(kind, user.role),
    primary = related[0];
  const statuses = [
    ...new Set([
      ...(data?.states || []),
      ...(criteria.status ? [criteria.status] : []),
    ]),
  ];
  const filtered = Object.values(criteria).some(Boolean);
  const quickActions = related
    .filter((a) => a !== primary && !/deactivate|delete|withdraw/.test(a))
    .slice(0, 4);
  const moreActions = related.filter(
    (action) => action !== primary && !quickActions.includes(action),
  );
  function clearFilters() {
    setSearch("");
    setCriteria({});
    setPage(1);
  }
  const group = groups.find(([_, items]) => items.includes(kind));
  const nav = (
    <Menu
      id="nav"
      levelIndent={12}
      selectedKeys={[kind]}
      openKeys={expanded}
      onClickSubMenu={(_, keys) => setExpanded(keys)}
      onClickMenuItem={(next) => navigate(next)}
    >
      <Menu.Item key="home">
        <IconApps />
        <span data-kind="home">工作台</span>
      </Menu.Item>
      {primaryNavigation[user.role].map(([k, label]) => (
        <Menu.Item key={k}>
          <NavigationIcon kind={k} />
          <span data-kind={k}>{label}</span>
        </Menu.Item>
      ))}
      <Menu.SubMenu
        key="more"
        className="more-navigation"
        title={
          <>
            <IconMore />
            更多业务
          </>
        }
      >
        {groups.map(([label, items], i) => {
          const visible = items.filter(
            (k) =>
              menus[user.role].includes(k) &&
              !primaryNavigation[user.role].some(([p]) => p === k),
          );
          const Icon = icons[i] || IconApps;
          return visible.length ? (
            <Menu.SubMenu
              key={String(i)}
              title={
                <>
                  <Icon />
                  <span className="nav-group-label">{label}</span>
                </>
              }
            >
              {visible.map((k) => (
                <Menu.Item key={k}>
                  <span data-kind={k}>{names[k]}</span>
                </Menu.Item>
              ))}
            </Menu.SubMenu>
          ) : null;
        })}
      </Menu.SubMenu>
    </Menu>
  );
  return (
    <Layout id="workspace" className="app-layout">
      <Layout.Sider
        width={232}
        className={"sidebar" + (openMenu ? " nav-open" : "")}
      >
        <div className="sidebar-brand">
          <img src="/brand-mark.svg" alt="" />
          <div>
            <strong>左邻右舍</strong>
            <small>社区养老服务</small>
          </div>
          <Button
            id="toggle-nav"
            type="text"
            icon={<IconMenu />}
            aria-expanded={openMenu}
            aria-controls="nav"
            onClick={() => setOpenMenu((v) => !v)}
          >
            菜单
          </Button>
        </div>
        <nav aria-label="业务导航">{nav}</nav>
        <div className="sidebar-footer">
          <Space>
            <Avatar
              size={34}
              style={{ background: "#e8f3ff", color: "#165dff" }}
            >
              {roleNames[user.role][0]}
            </Avatar>
            <div>
              <Typography.Text bold>{roleNames[user.role]}</Typography.Text>
              <p id="identity">
                <span>{profile.data?.rows[0]?.display_name || user.name}</span>
                <span className="identity-scope">
                  {(user.role === "MANAGER" || user.role === "CAREGIVER"
                    ? profile.data?.rows[0]?.organization_name
                    : undefined) ||
                    profile.data?.rows[0]?.community_name ||
                    ""}
                </span>
              </p>
            </div>
          </Space>
          <div className="account-actions">
            <Button
              id="change-password"
              type="text"
              icon={<IconLock />}
              onClick={onPassword}
            >
              修改密码
            </Button>
            <Button
              id="logout"
              type="text"
              icon={<IconExport />}
              onClick={onLogout}
            >
              退出登录
            </Button>
          </div>
        </div>
      </Layout.Sider>
      <Layout.Content className="workspace-content">
        <header className="workspace-topline">
          <Breadcrumb>
            <Breadcrumb.Item onClick={() => navigate("home")}>
              工作台
            </Breadcrumb.Item>
            <Breadcrumb.Item>
              {kind === "home"
                ? dateTitle(
                    dateText(profile.data?.rows[0]?.today || todayInShanghai()),
                  )
                : group?.[0] || "业务查询"}
            </Breadcrumb.Item>
          </Breadcrumb>
          <Space>
            <Tooltip content="业务数据为课程示例，人物与场景照片由 AI 生成">
              <Typography.Text type="secondary">关于平台</Typography.Text>
            </Tooltip>
            <Button
              type="text"
              icon={<IconNotification />}
              onClick={() => navigate("notifications")}
            >
              通知 <Tag>{notifications.data?.total ?? "—"}</Tag>
            </Button>
          </Space>
        </header>
        <main>
          {kind === "home" ? (
            <RoleHome
              selectedElder={selectedElder}
              selectElder={selectElder}
              user={user}
              revision={revision + poll}
              profile={profile.data?.rows[0]}
              navigate={navigate}
              open={open}
              details={showDetails}
              refresh={refresh}
            />
          ) : (
            <>
              <header className="page-header">
                <div className="page-title-group">
                  <span className="page-title-icon" aria-hidden>
                    <NavigationIcon kind={kind} />
                  </span>
                  <div>
                    <Typography.Title heading={4} id="title">
                      {names[kind]}
                    </Typography.Title>
                    {pageHint(kind, user.role) && (
                      <Typography.Text type="secondary" id="page-description">
                        {pageHint(kind, user.role)}
                      </Typography.Text>
                    )}
                  </div>
                </div>
                <Space>
                  <Button
                    id="refresh"
                    loading={resource.loading}
                    icon={<IconRefresh />}
                    onClick={refresh}
                  >
                    刷新
                  </Button>
                  {primary && (
                    <Button
                      id="open-operation"
                      type="primary"
                      icon={<IconPlus />}
                      onClick={() => open(primary)}
                    >
                      {actions[primary][0]}
                    </Button>
                  )}
                </Space>
              </header>
              {!["hours", "credentials", ...designedRecords].includes(kind) && (
                <div className="business-summary" aria-label="角色待办">
                  <span className="business-summary-label">待办</span>
                  {tasks[user.role].map(([label, k, status], i) => (
                    <button
                      className="task-card"
                      key={label}
                      type="button"
                      aria-label={`${label}，${counts.data?.[i] ?? "加载中"} 条，点击查看`}
                      onClick={() =>
                        navigate(k, { status: status || undefined })
                      }
                    >
                      <span>{label}</span>
                      <strong>{counts.data?.[i] ?? "—"}</strong>
                      <IconArrowRight />
                    </button>
                  ))}
                </div>
              )}
              {related.length > 1 && (
                <div className="quick-action-bar" aria-label="快捷办理">
                  <span>快捷办理</span>
                  {quickActions.map((action) => (
                    <Button
                      key={action}
                      size="small"
                      onClick={() => open(action)}
                    >
                      {actions[action][0]} <IconArrowRight />
                    </Button>
                  ))}
                  {moreActions.length > 0 && (
                    <Dropdown
                      trigger="click"
                      droplist={
                        <Menu onClickMenuItem={(action) => open(action)}>
                          {moreActions.map((action) => (
                            <Menu.Item key={action}>
                              {actions[action][0]}
                            </Menu.Item>
                          ))}
                        </Menu>
                      }
                    >
                      <Button size="small" aria-haspopup="menu">
                        更多办理 <IconDown />
                      </Button>
                    </Dropdown>
                  )}
                </div>
              )}
              <div className="content-grid records-layout">
                <Card
                  className="records"
                  bordered={false}
                  title={
                    <Space>
                      <span>{filtered ? "筛选结果" : "全部记录"}</span>
                      <span className="record-count">
                        <b id="total">{data?.total ?? "—"}</b> 条
                      </span>
                    </Space>
                  }
                  extra={
                    <Input.Search
                      id="record-search"
                      aria-label="搜索姓名、名称或编号"
                      placeholder="搜索姓名、名称或编号"
                      value={search}
                      onChange={setSearch}
                      allowClear
                    />
                  }
                >
                  <div className="record-filters">
                    {statuses.length > 0 && (
                      <Select
                        id="record-state"
                        aria-label="按状态筛选"
                        value={criteria.status || ""}
                        style={{ width: 160 }}
                        options={[
                          { value: "", label: "全部状态" },
                          ...statuses.map((s) => ({
                            value: s,
                            label: displayValue("status", s, kind),
                          })),
                        ]}
                        onChange={(status) => {
                          setPage(1);
                          setCriteria((old) => ({
                            ...old,
                            ...(old.field === "dispatch_window"
                              ? { field: undefined, value: undefined }
                              : {}),
                            status: status || undefined,
                          }));
                        }}
                      />
                    )}
                    {data?.dateField && (
                      <Space className="date-filter">
                        <Typography.Text type="secondary">
                          {columns[data.dateField] || "日期"}
                        </Typography.Text>
                        <DatePicker.RangePicker
                          aria-label="按日期范围筛选"
                          value={
                            criteria.from && criteria.to
                              ? [criteria.from, criteria.to]
                              : undefined
                          }
                          onChange={(dates) => {
                            setPage(1);
                            setCriteria((old) => ({
                              ...old,
                              from: dates?.[0],
                              to: dates?.[1],
                            }));
                          }}
                        />
                      </Space>
                    )}
                    {kind === "orders" &&
                      user.role === "ADMIN" &&
                      criteria.status === "PENDING" &&
                      (!criteria.field ||
                        criteria.field === "dispatch_window") && (
                        <Select
                          aria-label="预约时段"
                          value={Number(criteria.value || 1)}
                          options={[
                            { value: 1, label: "可继续派单" },
                            { value: 2, label: "已过预约时段 · 待联系" },
                          ]}
                          onChange={(value) => {
                            setPage(1);
                            setCriteria((old) => ({
                              ...old,
                              field: "dispatch_window",
                              value,
                            }));
                          }}
                        />
                      )}
                    {criteria.field && criteria.field !== "dispatch_window" && (
                      <Tag
                        closable
                        onClose={() => {
                          setCriteria(({ field, value, ...rest }) => rest);
                          setPage(1);
                        }}
                      >
                        {columns[criteria.field] || criteria.field} #
                        {criteria.value}
                      </Tag>
                    )}
                    {filtered && (
                      <Button type="text" onClick={clearFilters}>
                        清除筛选
                      </Button>
                    )}
                  </div>
                  {kind === "hours" && !resource.error && (
                    <HoursSummary
                      stats={data?.summary}
                      loading={resource.loading}
                    />
                  )}
                  {resource.error ? (
                    <Alert
                      id="page-error"
                      type="error"
                      content={resource.error}
                      action={<Button onClick={refresh}>重试</Button>}
                    />
                  ) : !resource.loading && !data?.rows.length ? (
                    <div className="records-empty">
                      <Empty
                        description={
                          filtered ? "没有符合条件的记录" : "暂无记录"
                        }
                      />
                      {filtered ? (
                        <Button onClick={clearFilters}>清除筛选</Button>
                      ) : primary ? (
                        <Button type="primary" onClick={() => open(primary)}>
                          {actions[primary][0]}
                        </Button>
                      ) : null}
                    </div>
                  ) : kind === "credentials" ? (
                    <Certificates
                      rows={data?.rows || []}
                      loading={resource.loading}
                      total={data?.total || 0}
                      page={page}
                      size={size}
                      onPage={(p, s) => {
                        setPage(p);
                        setSize(s);
                      }}
                      details={(row) => showDetails(kind, row)}
                    />
                  ) : designedRecords.includes(kind) ? (
                    <BusinessRecords
                      kind={kind}
                      rows={data?.rows || []}
                      loading={resource.loading}
                      total={data?.total || 0}
                      page={page}
                      size={size}
                      onPage={(p, s) => {
                        setPage(p);
                        setSize(s);
                      }}
                      actions={(row) => tableActions.render(undefined, row)}
                      navigate={
                        menus[user.role].includes("events")
                          ? navigate
                          : undefined
                      }
                    />
                  ) : mobile ? (
                    <Spin loading={resource.loading} style={{ width: "100%" }}>
                      <div className="business-cards">
                        {(data?.rows || []).map((row, index) => {
                          const keys = recordColumns(kind, row).filter(
                            (k) => k !== "id",
                          );
                          const titleKey = keys.find((k) =>
                            [
                              "elder_name",
                              "name",
                              "candidate_name",
                              "title",
                              "caregiver_name",
                            ].includes(k),
                          );
                          return (
                            <article
                              className="record-card"
                              data-id={row.id}
                              key={recordKey(kind, row)}
                            >
                              <div className="record-card-heading">
                                <strong>
                                  {titleKey
                                    ? cell(titleKey, row[titleKey], row)
                                    : names[kind]}
                                </strong>
                                <span>#{row.id ?? index + 1}</span>
                              </div>
                              <dl>
                                {keys
                                  .filter((k) => k !== titleKey)
                                  .slice(0, 6)
                                  .map((k) => (
                                    <div key={k}>
                                      <dt>{columnTitle(k, kind)}</dt>
                                      <dd>{cell(k, row[k], row)}</dd>
                                    </div>
                                  ))}
                              </dl>
                              <Space
                                wrap
                                size={4}
                                className="record-card-actions"
                              >
                                <Button
                                  className="detail-link"
                                  size="small"
                                  onClick={() => showDetails(kind, row)}
                                >
                                  详情
                                </Button>
                                {choices(kind, row, user)
                                  .slice(0, 2)
                                  .map((c) =>
                                    rowButton(row, c.action, c.label, c.preset),
                                  )}
                                {choices(kind, row, user).length > 2 && (
                                  <Button
                                    type="text"
                                    size="small"
                                    className="record-more"
                                    onClick={() => showDetails(kind, row)}
                                  >
                                    更多操作
                                  </Button>
                                )}
                              </Space>
                            </article>
                          );
                        })}
                        {!resource.loading && !data?.rows.length && (
                          <Empty description="暂无记录" />
                        )}
                      </div>
                      <Pagination
                        simple
                        current={page}
                        pageSize={size}
                        total={data?.total || 0}
                        onChange={setPage}
                        style={{ justifyContent: "center", marginTop: 16 }}
                      />
                    </Spin>
                  ) : (
                    <Table
                      className="business-table"
                      rowKey={(row) => recordKey(kind, row)}
                      border={false}
                      loading={resource.loading}
                      columns={
                        tableColumns.length
                          ? [...tableColumns, tableActions]
                          : []
                      }
                      data={data?.rows || []}
                      scroll={{ x: true }}
                      onRow={(row) =>
                        ({
                          "data-id": row.id,
                        }) as React.HTMLAttributes<HTMLTableRowElement>
                      }
                      pagination={{
                        current: page,
                        pageSize: size,
                        total: data?.total || 0,
                        showTotal: true,
                        sizeCanChange: true,
                        sizeOptions: [10, 20, 50],
                        onChange: (page, size) => {
                          setPage(page);
                          setSize(size);
                        },
                      }}
                      noDataElement={<Empty description="暂无记录" />}
                    />
                  )}
                </Card>
              </div>
            </>
          )}
          <footer className="workspace-footer">
            <img src="/brand-mark.svg" alt="" />
            左邻右舍
          </footer>
        </main>
      </Layout.Content>
      {operation && (
        <OperationDrawer
          key={operation.serial}
          operation={operation}
          user={user}
          onClose={() => setOperation(undefined)}
          onOpen={open}
          onSaved={refresh}
          onDetails={showDetails}
        />
      )}{" "}
      {detail && (
        <Details
          {...detail}
          user={user}
          onClose={() => setDetail(undefined)}
          onOpen={open}
          onNavigate={navigate}
        />
      )}
    </Layout>
  );
}
