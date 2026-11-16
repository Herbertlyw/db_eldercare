import { useEffect, useRef, useState } from "react";
import {
  Alert,
  Avatar,
  Breadcrumb,
  Button,
  Card,
  DatePicker,
  Empty,
  Input,
  Layout,
  Menu,
  Pagination,
  Spin,
  Select,
  Space,
  Statistic,
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
  IconFile,
  IconHeart,
  IconMenu,
  IconNotification,
  IconPlus,
  IconRefresh,
  IconSafe,
  IconSettings,
  IconUserGroup,
  IconThunderbolt,
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
  closedStates,
  columnTitle,
  displayValue,
  pageHint,
  pendingStates,
  recordColumns,
} from "../model/presentation";
import { list } from "../api/client";
import { useMobile } from "../hooks/useMobile";
import { useResource } from "../hooks/useResource";
import { CommunityCard, Person } from "../components/Media";
import { Details } from "../components/Details";
import { OperationDrawer } from "../forms/OperationDrawer";
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
const initialKind = (user: User) =>
  menus[user.role].includes(location.hash.slice(1))
    ? location.hash.slice(1)
    : menus[user.role][0];
export function Workspace({
  user,
  onPassword,
  onLogout,
}: {
  user: User;
  onPassword: () => void;
  onLogout: () => void;
}) {
  const [kind, setKind] = useState(() => initialKind(user)),
    [criteria, setCriteria] = useState<Criteria>({}),
    [search, setSearch] = useState(""),
    [page, setPage] = useState(1),
    [size, setSize] = useState(10),
    [revision, setRevision] = useState(0),
    [poll, setPoll] = useState(0),
    [openMenu, setOpenMenu] = useState(false),
    [expanded, setExpanded] = useState<string[]>([]),
    [operation, setOperation] = useState<Operation>(),
    [detail, setDetail] = useState<{ kind: string; row: Row }>();
  const opSerial = useRef(0);
  const mobile = useMobile();
  const key = JSON.stringify([user.id, kind, criteria, page, size, revision]);
  const resource = useResource(key, (signal) =>
    list(kind, { ...criteria, page: page - 1, size }, signal),
  );
  const data = resource.data;
  const counts = useResource(
    "counts:" + user.id + ":" + revision + ":" + poll,
    async (signal) =>
      Promise.all(
        tasks[user.role].map(async ([, k, status]) => {
          try {
            return (await list(k, { size: 1, status }, signal)).total;
          } catch {
            return null;
          }
        }),
      ),
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
      setCriteria((old) =>
        old.search === (search.trim() || undefined)
          ? old
          : { ...old, search: search.trim() || undefined },
      );
      setPage(1);
    }, 280);
    return () => clearTimeout(t);
  }, [search]);
  useEffect(() => {
    const index = groups.findIndex(([, items]) => items.includes(kind));
    setExpanded((old) => [...new Set([...old, String(index)])]);
    location.hash = kind;
  }, [kind]);
  useEffect(() => {
    const handler = () => {
      const next = location.hash.slice(1);
      if (menus[user.role].includes(next) && next !== kind) navigate(next);
    };
    window.addEventListener("hashchange", handler);
    return () => window.removeEventListener("hashchange", handler);
  }, [kind, user.role]);
  function navigate(next: string, filter: Criteria = {}) {
    if (!menus[user.role].includes(next)) return;
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
    if (key === "name" && kind === "elders")
      return <Person id={row.id} name={value} />;
    if (key === "status")
      return (
        <Tag
          color={
            pendingStates.includes(value)
              ? "orange"
              : closedStates.includes(value)
                ? undefined
                : "arcoblue"
          }
        >
          {displayValue(key, value, kind)}
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
    return displayValue(key, value, kind);
  }
  const tableColumns = data?.rows.length
    ? recordColumns(kind, data.rows[0]).map((key) => ({
        title: columnTitle(key, kind),
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
    width: 240,
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
  const group = groups.find(([_, items]) => items.includes(kind));
  const nav = (
    <Menu
      id="nav"
      selectedKeys={[kind]}
      openKeys={expanded}
      onClickSubMenu={(_, keys) => setExpanded(keys)}
      onClickMenuItem={(next) => navigate(next)}
    >
      {groups.map(([label, items], i) => {
        const visible = items.filter((k) => menus[user.role].includes(k));
        const Icon = icons[i] || IconApps;
        return visible.length ? (
          <Menu.SubMenu
            key={String(i)}
            title={
              <>
                <Icon />
                {label}
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
    </Menu>
  );
  return (
    <Layout id="workspace" className="app-layout">
      <Layout.Sider
        width={220}
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
                {user.name} · 社区 {user.community}
              </p>
            </div>
          </Space>
          <div className="account-actions">
            <Button id="change-password" type="text" onClick={onPassword}>
              修改密码
            </Button>
            <Button id="logout" type="text" onClick={onLogout}>
              退出登录
            </Button>
          </div>
        </div>
      </Layout.Sider>
      <Layout.Content className="workspace-content">
        <header className="workspace-topline">
          <Breadcrumb>
            <Breadcrumb.Item>工作台</Breadcrumb.Item>
            <Breadcrumb.Item>{group?.[0] || "业务查询"}</Breadcrumb.Item>
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
          <header className="page-header">
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
          <Card bordered={false} className="role-tasks">
            <div className="task-grid">
              {tasks[user.role].map(([label, k, status], i) => (
                <button
                  key={label}
                  className="task-card"
                  type="button"
                  aria-label={`${label}，${counts.data?.[i] ?? "加载中"} 条，点击查看`}
                  onClick={() => navigate(k, { status: status || undefined })}
                >
                  <span className="task-icon">
                    {k.includes("vent") ? (
                      <IconHeart />
                    ) : k.includes("laim") ? (
                      <IconSafe />
                    ) : (
                      <IconFile />
                    )}
                  </span>
                  <Statistic title={label} value={counts.data?.[i] ?? "—"} />
                  <IconArrowRight />
                </button>
              ))}
            </div>
          </Card>
          <div className="content-grid">
            <Card
              className="records"
              bordered={false}
              title={
                <Space>
                  全部记录 <Tag id="total">{data?.total ?? "—"}</Tag>
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
                {criteria.field && (
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
                {Object.values(criteria).some(Boolean) && (
                  <Button
                    type="text"
                    onClick={() => {
                      setSearch("");
                      setCriteria({});
                      setPage(1);
                    }}
                  >
                    清除筛选
                  </Button>
                )}
              </div>
              {resource.error ? (
                <Alert
                  id="page-error"
                  type="error"
                  content={resource.error}
                  action={<Button onClick={refresh}>重试</Button>}
                />
              ) : !resource.loading && !data?.rows.length ? (
                <Empty description="暂无记录" style={{ padding: "32px 0" }} />
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
                          key={row.id ?? index}
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
                          <Space wrap size={4} className="record-card-actions">
                            <Button
                              className="detail-link"
                              size="small"
                              onClick={() => showDetails(kind, row)}
                            >
                              详情
                            </Button>
                            {choices(kind, row, user).map((c) =>
                              rowButton(row, c.action, c.label, c.preset),
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
                  rowKey={(row) => String(row.id ?? JSON.stringify(row))}
                  border={false}
                  loading={resource.loading}
                  columns={
                    tableColumns.length ? [...tableColumns, tableActions] : []
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
            <aside className="workspace-side">
              <CommunityCard />
              {related.length > 1 && (
                <Card
                  title={
                    <Space>
                      <IconThunderbolt />
                      快捷办理
                    </Space>
                  }
                  bordered={false}
                >
                  <div className="quick-actions">
                    {related
                      .filter(
                        (a) =>
                          a !== primary &&
                          !/deactivate|delete|withdraw/.test(a),
                      )
                      .slice(0, 4)
                      .map((action) => (
                        <Button key={action} onClick={() => open(action)}>
                          {actions[action][0]}
                        </Button>
                      ))}
                  </div>
                </Card>
              )}
            </aside>
          </div>
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
