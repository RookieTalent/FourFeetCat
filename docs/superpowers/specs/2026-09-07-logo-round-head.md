# FourFeetCat Logo 改版（去趾 · 圆头笑猫）— 2026-09-07

## 结论

在 2026-09-06「爪印猫」基础上，应主公之命**去掉画面上方四枚脚趾圆**，圆掌垫即猫头独立成标：
一枚蓝色圆头（`c(100,100) r84`），粉鼻 + ω 笑嘴**正脸居中**。不再有爪印、不再有五趾四趾的形状叙事，
就是一只干净的极简笑猫圆头——favicon / 角标小尺寸依然轮廓分明。

## 改动

- 上版立意「四脚趾 = 四脚」随四趾一并去除；保留「掌垫即猫头 / 无耳无眼 / ω 笑嘴」的极简血统。
- 上一版脸部元素偏圆掌垫上方（为迁就爪形）；去趾后把粉鼻与 ω 笑嘴下移、以圆盘中心为焦点重排成正脸。
- 纯蓝圆 `#3D6FD6` + 粉鼻 `#F0A6B5` + 白 ω 笑嘴（stroke 6），与旧版用色一致。

## 最终图形规格（viewBox 0 0 200 200）

| 元素 | 几何 | 颜色 |
|------|------|------|
| 圆头（=猫脸） | 圆 c(100,100) r84 | `#3D6FD6` |
| 粉鼻 | 椭圆 c(100,104) rx10 ry7.5 | `#F0A6B5` |
| ω 笑嘴 | 两条 Q 曲线（白色 stroke 6，圆头） | `#FFFFFF` |

- 横版 `logo.svg`：圆头按 `translate(48,36) scale(0.76)` 置于字标左侧（圆心 124,112），字标不变。

## 落地文件（已收敛，无旧图残留）

- `docs/images/logo.svg` ↔ `website/public/images/logo.svg`（横版组合标，同图）
- `website/public/images/logo-icon.svg` ↔ `docs/images/logo-icon.svg`（独立图标，同图；favicon 即引用此 SVG）

引用处（路径引用，图形变更即自动生效，无需改码）：`README.md`、`docs/fourfeetcat.md`、
`website/.vitepress/theme/Home.vue`（品牌角标/hero/footer）、`website/.vitepress/config.mts`（favicon）。
