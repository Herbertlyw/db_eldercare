import { useState } from "react";
import { Button, Modal, Pagination, Space, Spin } from "@arco-design/web-react";
import { IconDownload, IconExpand } from "@arco-design/web-react/icon";
import type { Row } from "../../model/types";
import { certificateUrl } from "./metrics";

export function Certificates({
  rows,
  loading,
  total,
  page,
  size,
  onPage,
  details,
}: {
  rows: Row[];
  loading: boolean;
  total: number;
  page: number;
  size: number;
  onPage: (page: number, size: number) => void;
  details: (row: Row) => void;
}) {
  const [preview, setPreview] = useState<Row>();
  return (
    <Spin loading={loading} style={{ width: "100%" }}>
      <div className="certificate-grid">
        {rows.map((row) => (
          <article className="certificate-card" key={row.id} data-id={row.id}>
            <button
              className="certificate-image"
              onClick={() => setPreview(row)}
              aria-label={`预览${row.caregiver_name}的职业证书`}
            >
              <img
                src={certificateUrl(row)}
                alt={`${row.caregiver_name} · ${row.certificate_type} · ${row.certificate_level}`}
              />
            </button>
            <div className="certificate-card-footer">
              <div>
                <strong>{row.caregiver_name}</strong>
                <span>
                  {row.certificate_type} · {row.certificate_level}
                </span>
              </div>
              <Space size={4}>
                <Button
                  type="text"
                  icon={<IconExpand />}
                  onClick={() => setPreview(row)}
                >
                  预览
                </Button>
                <Button className="detail-link" onClick={() => details(row)}>
                  详情
                </Button>
              </Space>
            </div>
          </article>
        ))}
      </div>
      <Pagination
        className="certificate-pagination"
        current={page}
        pageSize={size}
        total={total}
        showTotal
        sizeCanChange
        sizeOptions={[10, 20, 50]}
        onChange={onPage}
      />
      <Modal
        className="certificate-preview"
        title="职业证书预览"
        visible={!!preview}
        onCancel={() => setPreview(undefined)}
        style={{ width: 960, maxWidth: "calc(100vw - 32px)" }}
        footer={
          preview && (
            <Button
              type="primary"
              icon={<IconDownload />}
              href={certificateUrl(preview)}
              download={`职业证书-${preview.caregiver_name}-${preview.id}.svg`}
            >
              下载证书图片
            </Button>
          )
        }
      >
        {preview && (
          <img
            src={certificateUrl(preview)}
            alt={`${preview.caregiver_name}的证书登记资料`}
          />
        )}
      </Modal>
    </Spin>
  );
}
