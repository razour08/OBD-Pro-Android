# OBD Pro Android

[English](#english) | [中文](#中文) | [العربية](#العربية)

---

<a name="english"></a>
## 🇬🇧 English

**OBD Pro** is a specialized Android diagnostic application built for legacy vehicles using the KWP2000 (ISO 14230-4) protocol over K-Line. It is specifically engineered to communicate with the **Wuhan LinControl LEC3A ECU** (found in the Jiangnan TT and other Suzuki Alto F8B clones), bypassing standard OBD-II limitations to access raw manufacturer-specific live data.

### Features
* **Live Data Dashboard:** Real-time gauges for Engine RPM, Coolant Temperature, MAP, TPS, Battery, and more.
* **Raw Byte Explorer:** Forensic analysis tool to view and decode the proprietary 64-byte data stream from the ECU (Service 21 01).
* **ELM327 Bluetooth Integration:** Connects seamlessly to standard ELM327 Bluetooth adapters using `ATFI` (Fast Init) handshaking.
* **Modern UI:** Built with Kotlin and Jetpack Compose featuring a neon-themed dark mode interface.

### Supported Vehicles
* **Jiangnan TT (江南TT)** - Engine: JN368QA (0.8L) - ECU: WHLD LEC3A
* Vehicles based on the **Suzuki Alto F8B** engine platform utilizing KWP2000 fast-init over K-Line.

### Tech Stack
* Android / Kotlin
* Jetpack Compose (UI)
* Coroutines / Flow (Concurrency)
* ELM327 Bluetooth RFCOMM

---

<a name="中文"></a>
## 🇨🇳 中文

**OBD Pro** 是一款专为使用 KWP2000 (ISO 14230-4) K-Line 协议的老款汽车打造的 Android 诊断应用程序。它专门针对 **武汉菱电 (Wuhan LinControl) LEC3A ECU**（常用于江南TT及其他铃木奥拓 F8B 仿制车型）进行了逆向工程设计，可绕过标准 OBD-II 限制，读取制造商专有的原始实时数据。

### 核心功能
* **实时数据仪表盘:** 实时显示发动机转速 (RPM)、水温、进气歧管绝对压力 (MAP)、节气门位置 (TPS)、电池电压等参数。
* **原始数据资源管理器:** 提供深度数据分析工具，用于查看和解码来自 ECU 的 64 字节专有数据流 (Service 21 01)。
* **ELM327 蓝牙集成:** 使用 `ATFI` (快速初始化) 握手协议，无缝连接标准的 ELM327 蓝牙适配器。
* **现代用户界面:** 使用 Kotlin 和 Jetpack Compose 构建，具有霓虹风格的深色模式界面。

### 支持车型
* **江南TT (Jiangnan TT)** - 发动机：JN368QA (0.8L) - ECU：武汉菱电 LEC3A
* 基于 **铃木奥拓 (Suzuki Alto) F8B** 发动机平台并使用 KWP2000 快速初始化 (K-Line) 的车型。

### 技术栈
* Android / Kotlin
* Jetpack Compose (UI)
* Coroutines / Flow (并发)
* ELM327 Bluetooth RFCOMM

---

<a name="العربية"></a>
## 🇸🇦 العربية

**OBD Pro** هو تطبيق أندرويد تشخيصي مخصص للسيارات القديمة التي تعتمد على بروتوكول KWP2000 (ISO 14230-4) عبر خط K-Line. تم هندسة التطبيق خصيصاً للاتصال بوحدة التحكم **Wuhan LinControl LEC3A ECU** (الموجودة في سيارات Jiangnan TT والنسخ المشابهة لـ Suzuki Alto F8B)، متجاوزاً قيود أنظمة OBD-II القياسية للوصول إلى البيانات الحية الخام الخاصة بالشركة المصنعة.

### المميزات الأساسية
* **لوحة قيادة حية:** عدادات تفاعلية تعرض سرعة المحرك (RPM)، حرارة المحرك، ضغط المنشعب (MAP)، موضع الخانق (TPS)، جهد البطارية، وغيرها في الوقت الفعلي.
* **مستكشف البايتات الخام:** أداة تحليل جنائي لعرض وفك تشفير حزمة البيانات الخاصة المكونة من 64 بايت والقادمة من وحدة التحكم (Service 21 01).
* **دعم محولات ELM327:** اتصال سلس عبر البلوتوث بمحولات ELM327 باستخدام بروتوكول التهيئة السريعة `ATFI`.
* **واجهة عصرية:** مبني باستخدام Kotlin و Jetpack Compose مع واجهة مستخدم ذات طابع ليلي وألوان نيون.

### السيارات المدعومة
* **جيانغنان تي تي (Jiangnan TT)** - المحرك: JN368QA (0.8L) - وحدة التحكم: WHLD LEC3A.
* السيارات المبنية على منصة محرك **سوزوكي ألتو Suzuki Alto F8B** والتي تستخدم بروتوكول KWP2000 عبر K-Line.

### التقنيات المستخدمة
* أندرويد / Kotlin
* Jetpack Compose (لواجهة المستخدم)
* Coroutines / Flow (للبرمجة المتزامنة)
* بلوتوث ELM327 RFCOMM
