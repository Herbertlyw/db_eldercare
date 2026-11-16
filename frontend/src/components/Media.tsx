import { useEffect, useRef, useState } from "react";
import {
  Avatar,
  Button,
  Card,
  Carousel,
  Space,
  Typography,
} from "@arco-design/web-react";
import { IconPause, IconPlayArrow } from "@arco-design/web-react/icon";
let peoplePromise:
  Promise<{ id: number; name: string; avatar: string }[]> | undefined;
function people() {
  return (peoplePromise ??= fetch("/demo-people.json")
    .then((r) => (r.ok ? r.json() : Promise.reject()))
    .then((d) => [...(d.elders || []), ...(d.additionalElders || [])])
    .catch(() => []));
}
export function Person({
  id,
  name,
  large = false,
}: {
  id: number;
  name: string;
  large?: boolean;
}) {
  const [src, setSrc] = useState<string>();
  useEffect(() => {
    let live = true;
    setSrc(undefined);
    people().then((rows) => {
      const p = rows.find((p) => p.id === Number(id) && p.name === name);
      if (live && p && /^people\/elder-\d+\.jpg$/.test(p.avatar))
        setSrc("/images/" + p.avatar);
    });
    return () => {
      live = false;
    };
  }, [id, name]);
  return (
    <Space>
      <Avatar
        size={large ? 48 : 32}
        style={{ background: "#f2f3f5", color: "#86909c" }}
      >
        {src ? (
          <img
            src={src}
            alt={name + "的虚构生活照"}
            onError={() => setSrc(undefined)}
          />
        ) : (
          name?.[0] || "人"
        )}
      </Avatar>
      <Typography.Text bold>{name}</Typography.Text>
    </Space>
  );
}
const scenes = [
  ["/images/neighborhood-walk.jpg", "邻里散步", "志愿者陪老人沿社区小路散步"],
  [
    "/images/community/companionship-tea.jpg",
    "坐坐聊聊",
    "三位邻居在社区活动室喝茶聊天",
  ],
  [
    "/images/community/companionship-cooking.jpg",
    "一起包饺子",
    "老人与邻居在社区厨房一起包饺子",
  ],
  [
    "/images/community/companionship-phone.jpg",
    "手机小帮手",
    "社区志愿者陪老人学习使用手机",
  ],
];
export function CommunityCard() {
  const reduced = useRef(matchMedia("(prefers-reduced-motion: reduce)"));
  const [paused, setPaused] = useState(reduced.current.matches),
    [hover, setHover] = useState(false),
    [visible, setVisible] = useState(true),
    [active, setActive] = useState(0),
    [failed, setFailed] = useState<number[]>([]);
  const ref = useRef<HTMLDivElement>(null);
  useEffect(() => {
    const reduce = () => {
        if (reduced.current.matches) setPaused(true);
      },
      hidden = () => setVisible(!document.hidden);
    reduced.current.addEventListener("change", reduce);
    document.addEventListener("visibilitychange", hidden);
    const observer = new IntersectionObserver((e) =>
      setVisible(e[0].isIntersecting && !document.hidden),
    );
    if (ref.current) observer.observe(ref.current);
    return () => {
      reduced.current.removeEventListener("change", reduce);
      document.removeEventListener("visibilitychange", hidden);
      observer.disconnect();
    };
  }, []);
  useEffect(() => {
    if (paused || hover || !visible || failed.length >= scenes.length - 1)
      return;
    const t = setTimeout(() => {
      let next = (active + 1) % scenes.length;
      while (failed.includes(next)) next = (next + 1) % scenes.length;
      setActive(next);
    }, 5500);
    return () => clearTimeout(t);
  }, [paused, hover, visible, failed, active]);
  return (
    <div
      ref={ref}
      onMouseEnter={() => setHover(true)}
      onMouseLeave={() => setHover(false)}
      onFocusCapture={() => setHover(true)}
      onBlurCapture={(e) => {
        if (!e.currentTarget.contains(e.relatedTarget)) setHover(false);
      }}
    >
      <Card
        className="community-card"
        bordered={false}
        bodyStyle={{ padding: 0 }}
      >
        <Carousel
          currentIndex={active}
          onChange={setActive}
          autoPlay={false}
          indicatorType="never"
          showArrow="never"
          animation={reduced.current.matches ? "fade" : "slide"}
        >
          {scenes.map(([src, , alt], i) => (
            <div key={src}>
              {failed.includes(i) ? (
                <div className="photo-empty">照片暂时无法加载</div>
              ) : (
                <img
                  className="community-photo"
                  src={src}
                  alt={"AI 生成的社区生活场景：" + alt}
                  onError={() =>
                    setFailed((old) => (old.includes(i) ? old : [...old, i]))
                  }
                />
              )}
            </div>
          ))}
        </Carousel>
        <div className="photo-controls">
          <div className="photo-dots">
            {scenes.map(([, title], i) => (
              <button
                key={title}
                type="button"
                aria-label={`第 ${i + 1} 张：${title}`}
                aria-current={active === i}
                disabled={failed.includes(i)}
                onClick={() => setActive(i)}
              />
            ))}
          </div>
          <Button
            id="photo-pause"
            type="text"
            size="mini"
            aria-label={paused ? "播放社区照片轮播" : "暂停社区照片轮播"}
            aria-pressed={paused}
            icon={paused ? <IconPlayArrow /> : <IconPause />}
            onClick={() => setPaused((p) => !p)}
          />
        </div>
        <div className="community-caption">
          <Typography.Text bold>社区陪伴</Typography.Text>
          <a href="mailto:2142354019@qq.com">联系社区 ↗</a>
        </div>
      </Card>
    </div>
  );
}
