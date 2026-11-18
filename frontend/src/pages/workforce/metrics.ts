import type { Row } from "../../model/types";

export function duration(minutes: number) {
  const value = Math.max(0, Math.floor(Number(minutes) || 0));
  return `${Math.floor(value / 60)} 小时${value % 60 ? ` ${value % 60} 分钟` : ""}`;
}

const xml = (value: unknown) =>
  String(value ?? "—").replace(
    /[<>&"']/g,
    (c) =>
      ({
        "<": "&lt;",
        ">": "&gt;",
        "&": "&amp;",
        '"': "&quot;",
        "'": "&apos;",
      })[c]!,
  );
const date = (value: unknown) =>
  String(value ?? "—")
    .replace("T", " ")
    .slice(0, 16);

export function certificateSvg(row: Row) {
  const text = (
    x: number,
    y: number,
    value: unknown,
    size = 20,
    fill = "#263a58",
    extra = "",
  ) =>
    `<text x="${x}" y="${y}" font-size="${size}" fill="${fill}" ${extra} ${x === 226 && String(value ?? "").length * size > 620 ? 'textLength="620" lengthAdjust="spacingAndGlyphs"' : ""}>${xml(value)}</text>`;
  return `<svg xmlns="http://www.w3.org/2000/svg" width="960" height="640" viewBox="0 0 960 640">
  <rect width="960" height="640" rx="12" fill="#fcfbf7"/>
  <path d="M0 0H960V130H0Z" fill="#eef3fb"/>
  <rect x="24" y="24" width="912" height="592" rx="3" fill="none" stroke="#b4c4dc"/>
  <rect x="32" y="32" width="896" height="576" fill="none" stroke="#dae2ee"/>
  <path d="M48 102V48H102M858 48H912V102M48 538V592H102M858 592H912V538" fill="none" stroke="#58769c" stroke-width="3"/>
  <g font-family="PingFang SC,Microsoft YaHei,sans-serif">
  ${text(68, 87, "左邻右舍", 24, "#294e80", 'font-weight="600"')}
  ${text(892, 87, "职业资质档案", 17, "#657c9a", 'text-anchor="end"')}
  ${text(480, 199, "职业证书", 42, "#263f64", 'text-anchor="middle" letter-spacing="10"')}
  <path d="M410 224H550" stroke="#a9bbd3"/>
  ${text(92, 285, "持证人", 17, "#78849a")}${text(226, 285, row.caregiver_name, 27, "#263f64", 'font-weight="600"')}
  ${text(92, 339, "证书类型", 17, "#78849a")}${text(226, 339, row.certificate_type, 23)}
  ${text(92, 393, "技能等级", 17, "#78849a")}${text(226, 393, row.certificate_level, 23)}
  ${text(92, 447, "证书编号", 17, "#78849a")}${text(226, 447, row.certificate_no, 20)}
  <path d="M92 479H868" stroke="#d8dfeb"/>
  ${text(92, 518, "生效时间", 16, "#78849a")}${text(92, 548, date(row.valid_from), 19)}
  ${text(528, 518, "有效期截止", 16, "#78849a")}${text(528, 548, date(row.valid_until), 19)}
  ${text(480, 587, "平台登记资料预览 · 非发证机构原件", 13, "#8b95a6", 'text-anchor="middle"')}
  </g></svg>`;
}
export const certificateUrl = (row: Row) =>
  "data:image/svg+xml;charset=utf-8," + encodeURIComponent(certificateSvg(row));
