import {
  IconClockCircle,
  IconLocation,
  IconUser,
} from "@arco-design/web-react/icon";
import { Tag } from "@arco-design/web-react";
import type { ReactNode } from "react";
import {
  appointmentSlot,
  displayValue,
  orderStatusLabel,
} from "../model/presentation";
import type { Row } from "../model/types";
import "../styles/order-visit.css";

const dispatchStatus: Record<string, string> = {
  WAITING: "待接单",
  ACCEPTED: "已接单",
  FINISHED: "服务结束",
  REASSIGNED: "已改派",
  REJECTED: "已退回",
};

function Visit({ visit }: { visit: Row }) {
  const events = [
    ["派单", visit.assigned_at],
    ["接单", visit.accepted_at],
    ["签到", visit.check_in],
    ["签退", visit.check_out],
    ["家属确认", visit.confirmed_at],
  ].filter(([, at]) => at);
  const days = new Set(
    events.map(([, at]) => displayValue("time", at).slice(0, 10)),
  );
  const sharedDay = days.size === 1 ? days.values().next().value : undefined;
  return (
    <article className="visit-entry">
      <header className="visit-entry-heading">
        <div>
          <strong>{visit.caregiver_name || "姓名未登记"}</strong>
          <span>{visit.check_in ? "上门护工" : "派单护工"}</span>
        </div>
        <Tag>
          {dispatchStatus[visit.status] || displayValue("status", visit.status)}
        </Tag>
      </header>
      {events.length > 0 && (
        <div className="visit-trace">
          {sharedDay && (
            <time className="visit-trace-day" dateTime={sharedDay}>
              {sharedDay}
            </time>
          )}
          <ol className="visit-times">
            {events.map(([label, at]) => (
              <li key={label}>
                <b>{label}</b>
                <time dateTime={at} title={displayValue("time", at)}>
                  {sharedDay
                    ? displayValue("time", at).slice(11)
                    : displayValue("time", at)}
                </time>
              </li>
            ))}
          </ol>
        </div>
      )}
      {visit.notes && (
        <div className="visit-note">
          <h4>服务记录</h4>
          <p>{visit.notes}</p>
        </div>
      )}
      {visit.confirmation && (
        <div className="visit-note">
          <h4>家属确认</h4>
          <p>{visit.confirmation}</p>
        </div>
      )}
      {visit.reason && ["REASSIGNED", "REJECTED"].includes(visit.status) && (
        <div className="visit-note">
          <h4>{visit.status === "REASSIGNED" ? "改派原因" : "退回原因"}</h4>
          <p>{visit.reason}</p>
        </div>
      )}
      <details className="visit-disclosure">
        <summary>派单与打卡明细</summary>
        <dl className="visit-facts">
          <div>
            <dt>派单编号</dt>
            <dd>#{visit.dispatch_id}</dd>
          </div>
          {visit.reason && (
            <div>
              <dt>派单说明</dt>
              <dd>{visit.reason}</dd>
            </div>
          )}
          {visit.location_note && (
            <div>
              <dt>打卡地点说明</dt>
              <dd>{visit.location_note}</dd>
            </div>
          )}
          {visit.fence_ok != null && (
            <div>
              <dt>签到在围栏内</dt>
              <dd>{displayValue("fence_ok", visit.fence_ok)}</dd>
            </div>
          )}
          {visit.checkout_fence_ok != null && (
            <div>
              <dt>签退在围栏内</dt>
              <dd>
                {displayValue("checkout_fence_ok", visit.checkout_fence_ok)}
              </dd>
            </div>
          )}
          {visit.hours_status && (
            <div>
              <dt>工时状态</dt>
              <dd>{displayValue("status", visit.hours_status, "hours")}</dd>
            </div>
          )}
          {visit.effective_minutes != null && (
            <div>
              <dt>有效工时</dt>
              <dd>{visit.effective_minutes} 分钟</dd>
            </div>
          )}
          {visit.review_reason && (
            <div>
              <dt>工时复核说明</dt>
              <dd>{visit.review_reason}</dd>
            </div>
          )}
        </dl>
      </details>
    </article>
  );
}

export function OrderVisitDetails({
  order,
  visits,
  children,
}: {
  order: Row;
  visits: Row[];
  children: ReactNode;
}) {
  // 接口按派单编号升序返回，最近一次可见派单先展示。
  const slot = appointmentSlot(order);
  const [latest, ...earlier] = visits.slice().reverse();
  return (
    <div className="order-visit-details detail-content">
      <header className="visit-ticket detail-overview">
        <div className="visit-ticket-heading">
          <span className="visit-reference">
            服务订单 <b>#{order.id}</b>
          </span>
          <Tag
            color={
              order.status === "CANCELLED"
                ? undefined
                : order.status === "PENDING" || order.status === "CONFIRM"
                  ? "orange"
                  : "arcoblue"
            }
          >
            {orderStatusLabel(order)}
          </Tag>
        </div>
        <div className="visit-ticket-main">
          <div>
            <h2>{order.item_name || "上门服务"}</h2>
            <p>
              <IconUser />
              {order.elder_name || `老人 #${order.elder_id}`}
            </p>
          </div>
          {order.amount != null && (
            <div className="visit-fee">
              <span>订单金额</span>
              <b>¥{Number(order.amount).toFixed(2)}</b>
            </div>
          )}
        </div>
        <div className="visit-appointment">
          <span>
            <IconClockCircle />
            <span>预约</span>
            {slot.date ? slot.date + " " + slot.time : "预约时间未登记"}
          </span>
          {order.address && (
            <span>
              <IconLocation />
              {order.address}
            </span>
          )}
        </div>
      </header>
      <section className="visit-log" aria-label="上门服务过程">
        <h3>上门服务过程</h3>
        {latest ? (
          <Visit visit={latest} />
        ) : (
          <p className="visit-empty">
            {order.status === "PENDING" && Number(order.dispatch_window) === 2
              ? "本次预约时段已过，需联系家属改约或取消。"
              : order.status === "PENDING"
                ? "等待社区安排护工。"
                : "暂无可查看的派单记录。"}
          </p>
        )}
        {earlier.length > 0 && (
          <details className="visit-disclosure visit-history">
            <summary>此前的派单（{earlier.length}）</summary>
            {earlier.map((visit) => (
              <Visit key={visit.dispatch_id} visit={visit} />
            ))}
          </details>
        )}
      </section>
      <details className="visit-disclosure visit-order-fields">
        <summary>查看订单登记资料</summary>
        {children}
      </details>
    </div>
  );
}
