# Kiki

极简墨水屏 Android 启动器。主屏只报时，应用列表只有字，没有图标、没有动画、没有 Gradle。

| | |
|---|---|
| 包名 | `io.github.kekeqwq.kiki` |
| 版本 | 1.4（2026.8.27） |
| 版本号 | 5 |
| 系统 | Android 7.0+（API 24），目标 API 34 |
| 体积 | 已签名 APK 约 24 KB |
| 许可 | GPL-3.0-or-later |

给海信 A5 / A9、文石 Palma / Leaf、Bigme 这类墨水屏用。颜色跟系统深色模式：浅色是纸 `#F6F1E8` 和墨 `#1C1B19`，深色反过来。字体读系统 `NotoSansCJK`，不打包。

---

## 用法

### 安装并设为桌面

```bash
adb install -r kiki.apk
```

按 Home 键，在系统弹窗里把 **Kiki** 设为默认桌面。以后按 Home 都会回到 Kiki。

若要卸掉默认桌面，到系统设置 → 应用 → 默认应用 → 桌面，改回原来的启动器，再卸载 Kiki。

### 主屏

两种样子，由设置里的壁纸开关决定。

**无壁纸**（默认）

- 整屏纸色，露出墨水屏本色
- 中间两行大号时间：时、分各占一行，数字按墨迹贴着排
- 下面一行日期，例如 `8.27 周四`
- 底部一根加粗短线

**有壁纸**

- 全屏显示你选的那张图（只给 Kiki 用，不改系统壁纸）
- 右上角缩小横排 `HH:MM`，第二行日期，两行右对齐
- 底部短线还在

日期两种模式都有。时钟只在整分刷新，没有秒。

### 手势

全部是点击或长按，没有滑动。

| 动作 | 效果 |
|---|---|
| 点主屏底部短线 | 打开应用列表 |
| 点列表顶部短线 | 回到主屏 |
| 点应用名 | 启动该应用 |
| 长按主屏空白、短线、或应用名 | 打开设置 |
| 系统返回键 / Home 键 | 回到主屏 |

按下时整行反相。从应用返回后再打开列表，焦点会清掉，不会停在上次那个名字上。

### 应用列表

- 顶栏：左边 `APPS`，中间短线，右边安装数量
- 应用名按系统标签排序，中英跟系统语言
- 无图标、无字母轨、无搜索、无滚动条、无分割线
- 名字居中

### 设置

长按进入。右上角「关闭」回到主屏。没有分割线，靠标题加粗和留白分区。

**壁纸**

| 选项 | 作用 |
|---|---|
| 无壁纸 | 关掉壁纸，主屏回到纸色。上次选的路径仍写在配置里 |
| 有壁纸 | 已有路径则直接用，不再弹选择器。从来没选过则弹出系统选图 |
| 更改壁纸 | 重新选一张图。仅在「有壁纸」时可用；无壁纸时灰掉、点不了 |

当前路径显示在三个选项下面。第一次选图系统会要存储权限，请允许，否则重启后可能读不到原图。选过一次之后，Kiki 会同时记住：

- 文件路径（给人看、给下次「有壁纸」用）
- 系统授权的 URI（重启后真正用来打开那张图）

换机或重装后授权会丢，再点一次「更改壁纸」选同一张即可。

选「无壁纸」会立刻清掉屏幕上的图，不会留一张空白 bitmap。

**关于**

Kiki 版本、一句简介、版权、许可证、发布日期。改版本时这几行和 `AndroidManifest` 的 `versionName` 一起改。

---

## 配置

设置里的改动写到应用私有目录：

```
/data/data/io.github.kekeqwq.kiki/files/kiki.conf
```

查看：

```bash
adb shell run-as io.github.kekeqwq.kiki cat files/kiki.conf
```

有的机子 `run-as` 不可用，可：

```bash
adb shell su -c cat /data/data/io.github.kekeqwq.kiki/files/kiki.conf
```

格式是普通文本，等号两边可以有空格。`#` 开头是注释。

```
wallpaper = on
wallpaper-file = /storage/emulated/0/Pictures/paper.png
wallpaper-uri = content://com.android.providers.media.documents/document/image%3A12
```

| 键 | 取值 | 含义 |
|---|---|---|
| `wallpaper` | `on` / `off` | 主屏要不要显示壁纸 |
| `wallpaper-file` | 路径或 content URI | 上次选出的文件，给人看；「有壁纸」沿用它 |
| `wallpaper-uri` | content URI | 系统持久授权，重启后用来读图 |

点「无壁纸」只把 `wallpaper` 写成 `off`，另外两行保留。点「有壁纸」写成 `on`。点「更改壁纸」才改路径和 URI。

也可以自己改这份文件。改完按 Home 回到 Kiki，它会重新读。例如手写：

```
wallpaper = off
```

主屏立刻变回大号时钟。再改回 `on`（路径还在）则恢复壁纸。

Kiki **不拷贝**图片。图还在你原来的位置，配置里只记怎么找到它。

开机时存储可能还没挂好，读失败会隔几秒再试，最多数次。若重启后设置显示「有壁纸」但主屏仍是大号时钟，点一次「更改壁纸」重新授权即可。

---

## 权限

| 权限 | 用途 |
|---|---|
| `READ_EXTERNAL_STORAGE`（API ≤ 32） | 按路径读你选的图 |
| `READ_MEDIA_IMAGES`（API ≥ 33） | 同上 |

没有网络、没有通讯录、没有通知监听。壁纸不写进系统壁纸。应用列表用 `<queries>` 看带 `LAUNCHER` 的应用，不用 `QUERY_ALL_PACKAGES`。

---

## 构建

### NixOS（推荐）

仓库根目录：

```bash
nix build
# 产物 result/kiki.apk
```

第一次会拉 Android build-tools 34 和 android-34 platform，之后走 Nix 缓存。

开发壳：

```bash
nix develop
```

### 不用 Nix

需要 JDK 17、Python 3、`unzip`，以及：

- Android build-tools 34：`aapt2`、`zipalign`、`apksigner`、`lib/d8.jar`
- platforms/android-34 的 `android.jar`

```bash
export ANDROID_BUILD_TOOLS=/path/to/build-tools/34.0.0
export ANDROID_JAR=/path/to/platforms/android-34/android.jar
./android/build.sh ./android/dist
```

产物 `android/dist/kiki.apk`。

没有 keystore 时，`build.sh` 会生成调试用 `android/kiki.keystore`（口令写在脚本里，只适合自己玩）。要长期覆盖安装，请自己 `keytool` 生成密钥，改脚本里的别名和口令。换过签名必须先卸载再装。

发新版本时同时改：

- `android/src/.../HomeActivity.java` 里的 `VER`、`REL`
- `android/AndroidManifest.xml` 的 `versionName`、`versionCode`
- `android/build.sh` 的 `--version-name`、`--version-code`
- 本 README 和设置页「关于」

---

## 目录

```
flake.nix                 Nix 构建
LICENSE                   GPL-3.0
README.md                 本文件
android/build.sh          javac + aapt2 + R8 + zipalign + apksigner
android/AndroidManifest.xml
android/proguard.pro
android/src/.../HomeActivity.java   单个 Activity，纯 Java
android/res/drawable/i.xml          线框房子图标
android/res/values/styles.xml       全屏、无动画
```

没有 Kotlin、AndroidX、Material、AppCompat、Gradle。

---

## 刻意不做

图标、文件夹、小部件、dock、搜索、字母轨、隐藏应用、秒针、动画、反色开关、24 小时制开关、日期开关。厂商 EPD SDK 不链接；Onyx / Hisense 全刷接口用反射试探，没有就 `invalidate`。

---

## 许可

GNU General Public License v3.0 或更高版本。全文见 [LICENSE](LICENSE)。

Copyright 2026 kekeqwq
