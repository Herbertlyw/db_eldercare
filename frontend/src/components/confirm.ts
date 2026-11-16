import { Modal } from "@arco-design/web-react";
export function confirm(title: string, content?: string): Promise<boolean> {
  return new Promise((resolve) =>
    Modal.confirm({
      title,
      content,
      okText: "确认",
      cancelText: "返回",
      onOk: () => resolve(true),
      onCancel: () => resolve(false),
    }),
  );
}
