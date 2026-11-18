import { useEffect, useState } from "react";
import {
  Alert,
  Button,
  Empty,
  Input,
  Space,
  Table,
  Tag,
} from "@arco-design/web-react";
import { api } from "../../api/client";
import { useResource } from "../../hooks/useResource";
import { DetailSections } from "../../components/Details";
import type { Row } from "../../model/types";
import {
  HomeHeader,
  Load,
  Panel,
  Metric,
  Pager,
  dateText,
  money,
  useHome,
  useRows,
} from "./shared";
function Review({ row }: { row: Row }) {
  const h = useHome();
  const r = useResource<Record<string, Row | Row[]>>(
    `claim-preview:${h.user.id}:${row.id}:${h.revision}`,
    (signal) => api("/details/claims/" + row.id, { signal }),
    `claim:${h.user.id}:${row.id}`,
  );
  const orders = (r.data?.["订单与核算依据"] || []) as Row[],
    evidence = (r.data?.["归档材料"] || []) as Row[],
    claim = (r.data?.["补贴申报"] || row) as Row;
  if (r.error)
    return (
      <Alert
        type="error"
        content={r.error}
        action={<Button onClick={r.retry}>重试</Button>}
      />
    );
  if (r.loading && !r.data)
    return <div className="home-loading">正在读取核验依据…</div>;
  return (
    <div className="claim-preview">
      <div className="review-document-label">
        <span>补贴申报核验单</span>
        <span>编号 #{row.id}</span>
      </div>
      <div className="claim-heading">
        <h2>申报核验 · {row.elder_name}</h2>
        <Tag color="arcoblue">
          {claim.status === "SUBMITTED" ? "待街道初审" : "状态已变化"}
        </Tag>
      </div>
      <p className="home-note">
        申报 #{row.id} · {row.period} ·{" "}
        {row.community_name || `社区 #${row.community_id}`}
      </p>
      <div className="claim-numbers">
        <div>
          <span>关联服务费用</span>
          <strong>
            ¥
            {money(orders.reduce((n, r) => n + Number(r.order_amount || 0), 0))}
          </strong>
        </div>
        <div>
          <span>申报补贴</span>
          <strong>¥{money(claim.amount)}</strong>
        </div>
        <div>
          <span>关联订单</span>
          <strong>{orders.length} 笔</strong>
        </div>
      </div>
      <h3 className="review-section-title">
        <span>01</span> 服务与核算依据
      </h3>
      <Space wrap className="claim-checks">
        <Tag>关联订单 {orders.length} 笔</Tag>
        <Tag>已确认 {orders.filter((r) => r.confirmed_at).length} 笔</Tag>
        <Tag>归档材料 {evidence.length} 份</Tag>
      </Space>
      <Table
        size="small"
        border={false}
        rowKey="id"
        scroll={{ x: 600 }}
        data={orders}
        pagination={orders.length > 5 ? { pageSize: 5 } : false}
        columns={[
          { title: "服务日期", render: (_, r) => dateText(r.starts_at) },
          { title: "服务项目", dataIndex: "item_name" },
          {
            title: "核算工时",
            render: (_, r) => `${r.effective_minutes ?? "—"} 分钟`,
          },
          {
            title: "确认记录",
            render: (_, r) =>
              r.confirmed_at ? (
                <Tag color="arcoblue">已确认</Tag>
              ) : (
                <Tag color="orange">待核实</Tag>
              ),
          },
          {
            title: "操作",
            render: (_, r) => (
              <Button
                size="small"
                type="text"
                onClick={() =>
                  h.details("orders", {
                    id: r.order_id,
                    item_name: r.item_name,
                    elder_name: row.elder_name,
                  })
                }
              >
                服务详情
              </Button>
            ),
          },
        ]}
      />
      <details className="claim-evidence">
        <summary>
          <span className="review-section-number">02</span> 证明材料与审核历史
        </summary>
        <DetailSections
          data={Object.fromEntries(
            Object.entries(r.data || {}).filter(([k]) =>
              ["审核记录", "归档材料", "拨付凭据"].includes(k),
            ),
          )}
          kind="claims"
        />
      </details>
      <div className="review-footer">
        <h3 className="review-section-title">
          <span>03</span> 审核处理
        </h3>
        <p>核对上述依据后进入审核，填写意见并确认结果。</p>
        <Space wrap>
          <Button onClick={() => h.details("claims", row)}>完整申报详情</Button>
          <Button
            type="primary"
            disabled={claim.status !== "SUBMITTED"}
            data-action="claim.review"
            onClick={() =>
              h.open(
                "claim.review",
                { claimId: row.id },
                { kind: "claims", row: claim },
              )
            }
          >
            填写审核意见
          </Button>
        </Space>
      </div>
    </div>
  );
}
export function StreetHome() {
  const h = useHome();
  const [query, setQuery] = useState(""),
    [search, setSearch] = useState(""),
    [page, setPage] = useState(1),
    [selected, setSelected] = useState<number>();
  useEffect(() => {
    const t = setTimeout(() => {
      setSearch(query);
      setPage(1);
    }, 250);
    return () => clearTimeout(t);
  }, [query]);
  const claims = useRows("claims", {
    status: "SUBMITTED",
    size: 5,
    page: page - 1,
    search,
  });
  const row =
    claims.data?.rows.find((r) => r.id === selected) || claims.data?.rows[0];
  return (
    <>
      <HomeHeader
        title="补贴审核工作台"
        subtitle="授权社区内的申报初审与材料核验"
      />
      <div className="home-metrics three">
        <Metric label="待初审" kind="claims" status="SUBMITTED" />
        <Metric label="退回申报" kind="claims" status="REJECTED" />
        <Metric
          label="已初审 · 待终审"
          kind="claims"
          status="STREET_APPROVED"
        />
      </div>
      <div className="review-grid">
        <Panel title="待初审申报" className="review-queue">
          <div className="queue-caption">
            <span>当前待办</span>
            <strong>
              {claims.error ? "—" : (claims.data?.total ?? "—")}
              <small> 份申报</small>
            </strong>
          </div>
          <Input.Search
            aria-label="查找待审申报"
            placeholder="姓名或申报编号"
            value={query}
            onChange={setQuery}
            allowClear
          />
          <Load resource={claims} empty="当前没有待初审申报">
            <div className="claim-selection">
              {claims.data?.rows.map((r) => (
                <button
                  key={r.id}
                  aria-pressed={r.id === row?.id}
                  onClick={() => setSelected(r.id)}
                >
                  <div>
                    <strong>{r.elder_name}</strong>
                    <span>
                      {r.period} · #{r.id}
                    </span>
                  </div>
                  <b>¥{money(r.amount)}</b>
                </button>
              ))}
            </div>
            <Pager resource={claims} page={page} onChange={setPage} />
          </Load>
          <Button type="text" onClick={() => h.navigate("claims")}>
            全部申报与审核进度
          </Button>
        </Panel>
        <div className="review-sheet">
          {row ? (
            <Review key={row.id} row={row} />
          ) : (
            <Empty
              description={
                claims.error
                  ? "请先重新加载申报列表"
                  : "从左侧选择一份申报查看依据"
              }
            />
          )}
        </div>
      </div>
    </>
  );
}
