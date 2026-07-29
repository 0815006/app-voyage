# 侧边栏折叠功能 — 复刻提示词

将以下提示词发给 AI，可在一个 Vue 2 + Element UI 项目中复刻完整的侧边栏折叠交互。

---

## 提示词

```
在 Vue 2 + Element UI 项目中，实现一个侧边栏折叠功能，包含以下特性：

### 布局结构
- 使用 CSS Grid 布局：`grid-template-columns: 240px 1fr`，折叠时切换为 `64px 1fr`
- Grid 过渡动画 0.25s ease
- 左侧 sidebar 占 grid-column: 1 / grid-row: 1/3，深色渐变背景
- 右侧 header 占 grid-column: 2 / grid-row: 1，最小高度 76px
- 折叠状态通过 Sidebar 组件的 `collapse-change` 事件通知父组件

### Logo 区域（左侧栏顶部）
- 固定高度 76px（与右侧 header 对齐），`box-sizing: border-box`
- 展开态：flex column + justify-content: center，包含 logo 图标 + 标题文字
- 折叠态：flex row + align-items: center + justify-content: center，仅显示图标居中
- 折叠前后高度不变，无跳动

### 折叠按钮 — 展开态
- 绝对定位于 logo 区域右侧垂直居中：`top: 50%; right: 8px; transform: translateY(-50%)`
- **默认**：纯箭头图标，`background: transparent; border: none`，低调融入背景
- **悬停**：出现蓝色半透明背景框（`rgba(94,170,255,0.25)`）+ 边框（`1px solid rgba(94,170,255,0.45)`）
- 箭头颜色：默认 `rgba(255,255,255,0.5)`，悬停 `rgba(255,255,255,0.9)`
- `z-index: 10`，28×28px，圆角 6px
- 过渡动画：background 0.25s, border-color 0.25s, opacity 0.25s, visibility 0.25s

### 折叠按钮 — 折叠态
- 放入 logo 容器内，绝对居中：`top: 50%; left: 50%; transform: translate(-50%, -50%)`
- **默认隐藏**：`opacity: 0; visibility: hidden; pointer-events: none`
- **logo 区域悬停时出现**：`opacity: 1; visibility: visible; pointer-events: auto`
- 按钮尺寸 40×40px，完全不透明背景 `#304156`，边框 `1px solid rgba(255,255,255,0.2)`，带阴影 `0 2px 10px rgba(0,0,0,0.5)`
- 确保 40px 按钮彻底覆盖住 28px 的 logo 图标
- 鼠标移到按钮上时：边框高亮 `rgba(94,170,255,0.6)`，阴影加深
- **注意**：折叠态的按钮 hover 不要改变 background，否则会变成半透明露出底层图标
- 箭头在折叠态旋转 180°（`transform: rotate(180deg)` 应用于箭头 SVG 元素，不要和按钮的 translate 冲突——它们是不同元素）

### 折叠切换逻辑
- data 中维护 `collapsed: false`
- `toggleCollapse()` 方法翻转 collapsed，并 `$emit('collapse-change', this.collapsed)`
- 折叠时 Element UI 的 `<el-menu :collapse="collapsed">` 自动收起
- 菜单项图标在折叠态居中（`justify-content: center; padding: 0`）
- 箭头 SVG 使用 `<path d="M15.41 7.41L14 6l-6 6 6 6 1.41-1.41L10.83 12z"/>`（左箭头），折叠态加 `arrow-collapsed` class 旋转 180° 变成右箭头

### 关键注意事项
- logo 区域的 `position: relative` 必须设置，作为折叠按钮的定位参考
- 折叠态按钮的 `background` 不要在半透明和透明之间切换——始终用不透明色
- CSS 过渡属性要包含 opacity 和 visibility 以实现平滑显隐
- 折叠态箭头旋转和按钮居中 transform 要作用在不同元素上，避免冲突
```

---

## 核心 CSS 速查

```css
/* === 展开态按钮：纯箭头，悬停出框 === */
.collapse-toggle {
  position: absolute;
  top: 50%;
  right: 8px;
  transform: translateY(-50%);
  width: 28px; height: 28px;
  background: transparent;
  border: none;
  border-radius: 6px;
  transition: background 0.25s, border-color 0.25s, opacity 0.25s, visibility 0.25s;
  z-index: 10;
}
.collapse-toggle:hover {
  background: rgba(94, 170, 255, 0.25);
  border: 1px solid rgba(94, 170, 255, 0.45);
}

/* === 折叠态按钮：隐藏 → 悬停浮现，不透明覆盖图标 === */
.logo-collapsed .collapse-toggle {
  top: 50%; left: 50%; right: auto;
  transform: translate(-50%, -50%);
  opacity: 0; visibility: hidden; pointer-events: none;
  width: 40px; height: 40px;
  background: #304156;  /* 完全不透明 */
  border: 1px solid rgba(255,255,255,0.2);
  box-shadow: 0 2px 10px rgba(0,0,0,0.5);
}
.logo-collapsed:hover .collapse-toggle {
  opacity: 1; visibility: visible; pointer-events: auto;
}
.logo-collapsed .collapse-toggle:hover {
  border-color: rgba(94,170,255,0.6);
  box-shadow: 0 2px 12px rgba(0,0,0,0.6);
  /* 不覆盖 background，保持不透明 */
}

/* === 箭头 === */
.toggle-arrow { color: rgba(255,255,255,0.5); transition: color 0.25s, transform 0.3s; }
.collapse-toggle:hover .toggle-arrow { color: rgba(255,255,255,0.9); }
.arrow-collapsed { transform: rotate(180deg); }
```
