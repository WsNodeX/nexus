<div align="center">

# Nexus

### A zero-dependency Java framework for building resilient LLM applications

Provider gateway · smart routing · caching · retry · circuit breaking · rate limiting · structured output · streaming

[Why](#-why-nexus) · [Features](#-features) · [Install](#-install) · [Quick Start](#-quick-start) · [Architecture](#-architecture) · [API](#-api-reference) · [Roadmap](#-roadmap) · [中文解读](#-深度解读可作为技术文章底稿)

![Java](https://img.shields.io/badge/Java-21-ED8B00?logo=openjdk&logoColor=white)
![License](https://img.shields.io/badge/License-MIT-green.svg)
![Zero Dependencies](https://img.shields.io/badge/dependencies-zero-0a0a0a)
![Platform](https://img.shields.io/badge/platform-JVM-blue)
![Tests](https://img.shields.io/badge/tests-passing-2ea043)
![CI](https://img.shields.io/badge/CI-passing-2ea043)

</div>

---

## ✨ Why Nexus

Calling an LLM API in a demo is one line. Calling it in production is a different sport:
a single backend goes down, latency spikes, a provider starts returning 429s, and your
users see a blank box. **Nexus** turns that one line into a hardened gateway.

- **No third-party jars.** Everything—JSON parsing, HTTP, streaming, routing,
  resilience—is implemented on the JDK standard library. Drop the `src` folder into
  any Java 21 project and it compiles. No Maven, no Gradle, no supply chain.
- **Production-shaped by default.** Routing, caching, retries, circuit breaking and
  rate limiting are first-class, composable layers rather than something you bolt on
  after the incident.
- **Provider-agnostic.** One `Provider` interface covers OpenAI, self-hosted
  vLLM/llama.cpp endpoints, and offline stubs for tests—so the rest of your code never
  changes when you swap a backend.

## 🚀 Features

- 🔌 **OpenAI-compatible provider** with streaming (SSE) and function/tool calling.
- 🧭 **Four routers**: round-robin, weighted, latency-aware, and capability-based.
- 💾 **Deterministic response cache** keyed on the canonical request, with TTL.
- 🔁 **Retry policy** with exponential backoff + jitter, retryable-predicate aware.
- 🛡️ **Circuit breaker** (closed → open → half-open) that fails fast under outage.
- 🚦 **Token-bucket rate limiter** for local backpressure.
- 🧱 **Structured output** that forces a typed, schema-validated JSON object from any model.
- 📊 **Built-in metrics** (calls, tokens, latency, cost estimate) with zero overhead.
- 🧪 **Fully offline-testable**: scripted providers exercise the whole gateway without a key.

## 📦 Install

Nexus is a single, dependency-free source tree. Copy it or vendor it:

```bash
# Option A — clone and use the sources directly (no build step required)
git clone https://github.com/WsNodeX/nexus.git
javac -d out $(find nexus/src/main/java -name '*.java')

# Option B — copy the nexus package into your own module
cp -r nexus/src/main/java/nexus com/yourcompany/llm/
```

Requires **JDK 21+**. No `pom.xml` or `build.gradle` is needed; the framework uses only
`java.net.http`, `java.util.concurrent` and a small hand-written JSON engine.

## ⚡ Quick Start

Build and run the end-to-end demo (runs fully offline via scripted providers):

```bash
javac -d build/main $(find src/main/java -name '*.java')
java -cp build/main nexus.examples.GatewayDemo
```

Expected output:

```
=== Nexus gateway demo ===
turn 0 -> [1] reply to: hello 0
turn 1 -> [1] reply to: hello 1
turn 2 -> [1] reply to: hello 2
turn 3 -> [1] reply to: hello 3
cached turn -> [1] reply to: hello 0

Meter summary
  (cache)      calls=1 cacheHits=1 prompt=12 completion=8 avgLatency=0.0ms cost=$0.0000
  backend-a    calls=2 cacheHits=0 prompt=24 completion=16 avgLatency=2.5ms cost=$0.0001
  backend-b    calls=2 cacheHits=0 prompt=24 completion=16 avgLatency=0.0ms cost=$0.0001
```

Wiring your own gateway with resilience layers:

```java
Provider openai = new OpenAiProvider("gpt", "https://api.openai.com/v1", System.getenv("OPENAI_API_KEY"));
Provider local   = new OpenAiProvider("vllm", "http://localhost:8000/v1", null);

Nexus nexus = Nexus.builder()
    .addProvider(openai)
    .addProvider(local)
    .router(new LatencyRouter())                 // send traffic to the fastest backend
    .cache(new SemanticCache(60_000))            // 60s response cache
    .circuitBreaker(new CircuitBreaker(5, 5_000, 2))
    .retryPolicy(new RetryPolicy().maxAttempts(3).baseDelayMs(200))
    .meter(new Meter())
    .build();

ChatResponse r = nexus.chat(new ChatRequest("gpt-4o").add(Message.user("Explain quicksort.")));
System.out.println(r.content);
```

## 🏗️ Architecture

```
            ┌──────────────────────────────────────────────────┐
            │                  Nexus.chat(request)              │
            └──────────────────────────────────────────────────┘
                               │
        ┌───────────┐   ┌────────────┐   ┌──────────────────────┐
        │ Rate      │ → │ Semantic   │ → │ Circuit Breaker       │
        │ Limiter   │   │ Cache      │   │        │             │
        └───────────┘   └────────────┘   │        ▼             │
                                         │   Retry Policy       │
                                         │        │             │
                                         │        ▼             │
                                         │   Router.select      │
                                         │        │             │
                                         │        ▼             │
                                         │   Provider.chat ─────┤──► OpenAI
                                         └──────────────────────┘    vLLM / llama.cpp
                                                                     Scripted (tests)
```

Each layer is optional and independently testable. `nexus.core` holds the JSON engine and
error types; `nexus.llm` models messages, tools and providers; `nexus.routing` and
`nexus.resilience` are the load-balancing and fault-tolerance building blocks; `nexus.output`
delivers typed generation; `nexus.observability` records metrics; and `nexus.Nexus` composes
them behind one entry point.

## 🔌 API Reference

| Class | Responsibility | Key members |
| --- | --- | --- |
| `Nexus` | Gateway facade | `builder()`, `chat(ChatRequest)`, `chatStream(ChatRequest)`, `ask(model, text)` |
| `Provider` | Backend abstraction | `name()`, `chat(req)`, `chatStream(req)`, `capabilities()` |
| `OpenAiProvider` | OpenAI-compatible client | `chat`, `chatStream`, `buildBody(req, stream)` |
| `ScriptedProvider` / `EchoProvider` | Offline stubs | `chat`, `chatStream` |
| `Router` | Backend selection | `RoundRobinRouter`, `WeightedRouter`, `LatencyRouter`, `CapabilityRouter` |
| `RetryPolicy` | Transient-failure retries | `maxAttempts`, `baseDelayMs`, `jitter`, `retryOn`, `run` |
| `CircuitBreaker` | Outage isolation | `run`, states `CLOSED/OPEN/HALF_OPEN` |
| `RateLimiter` | Local backpressure | `tryAcquire`, `acquire(timeoutMs)`, `available` |
| `SemanticCache` | Response caching | `get`, `put`, `keyOf`, `clear` |
| `StructuredOutput` | Typed generation | `call(messages, tool, desc, schema)`, `validate` |
| `Schema` | JSON-Schema builder | `create`, `property`, `required`, `enumOf` |
| `Meter` | Metrics | `record`, `recordCacheHit`, `summary`, `estimatedCostUsd` |
| `Message` / `Tool` / `ChatRequest` | Request model | role/content/toolCalls, parameters, temperature… |

## 🧭 Roadmap

- [ ] Batch / multi-turn memory compaction
- [ ] Pluggable cache backends (Redis, Caffeine-style LRU)
- [ ] OpenTelemetry span export for the gateway
- [ ] Automatic provider health scoring for `LatencyRouter`
- [ ] `RagPipeline` helper built on `StructuredOutput` + a vector store
- [ ] Spring Boot / Quarkus auto-configuration starter

## 🤝 Contributing

Fork, branch, and send a pull request. Keep the zero-dependency contract: anything that
would pull in an external jar belongs in a separate, clearly-marked module. Add a JUnit 5
test for every behaviour you change—the suite runs with `junit-platform-console-standalone`
and no network access.

## 📄 License

Released under the [MIT License](LICENSE). Do whatever you want; attribution appreciated.

---

## 📚 深度解读（可作为技术文章底稿）

> 下面是一篇可直接发布到掘金 / 知乎 / 小红书 / B 站的技术科普文底稿。它用读者视角讲清
> Nexus 解决什么问题、核心原理与关键实现点，方便你引流与二次创作。

### 一、它解决什么问题

把大模型接进业务，最难的从来不是"能对话"，而是"稳得住"。真实场景里你会连续撞上这几件事：

1. **后端抖动**：某个模型供应商今晚延迟从 300ms 涨到 8s，你的接口跟着超时。
2. **限流 429**：突发流量把配额打满，整条链路雪崩。
3. **重复烧钱**：同一个问题被问了一万次，每次都现调一次模型。
4. **输出不可控**：你让模型"返回 JSON"，它偏要在前面加一句"好的，这是结果："。

Nexus 的思路是把这些"生产环境才有"的麻烦，做成一组**可插拔、零依赖**的 Java 层，让一次
`chat()` 调用自带路由、缓存、重试、熔断和限流。

### 二、核心原理

整个网关是一条**责任链**：

```
限流 → 缓存 → 熔断[ 重试[ 路由选节点 → 调用供应商 ] ] → 写回缓存 → 打点
```

- **路由（Routing）**：请求进来先由 `Router` 选一个后端。轮询适合均摊；加权适合异构机器；
  延迟路由实时把流量导向当前最快的节点；能力路由则把"要流式"的请求挡在不会流式的后端之外。
- **弹性（Resilience）**：`RetryPolicy` 用指数退避+抖动对付瞬时故障；`CircuitBreaker` 在失败
  累积到阈值后"跳闸"，直接拒绝请求不再雪崩，冷却后进入半开态探活；`RateLimiter` 用令牌桶做本地
  背压。
- **缓存（Cache）**：`SemanticCache` 把"模型+消息+工具+采样参数"规范化成稳定哈希做 key，命中即
  返回，跳过整次调用。注意它故意忽略 `stream` 标志——流式和非流式本质是同一次语义结果。
- **结构化输出（Structured Output）**：把"希望模型返回的对象"做成 JSON Schema，注册成一个工具并
  `tool_choice` 强制模型调用它，再对参数做 schema 校验。这样"模型吐 JSON"从玄学变成契约。

### 三、几个关键实现点

1. **零依赖 JSON 引擎**：手写递归下降解析器（约 200 行），支持转义、Unicode、嵌套，带 builder
   风格的构造 API。好处是整套框架不依赖任何第三方库，拷进项目就能编译。
2. **流式解析**：`SseParser` 用 `Spliterator` 逐行读 SSE，遇到 `data:` 累积、空行切分事件、
   `[DONE]` 收尾，对外暴露成 `Stream<StreamChunk>`，调用方边收边渲染。
3. **熔断状态机**：`CLOSED →（失败达阈值）→ OPEN →（冷却到期）→ HALF_OPEN →（成功达标）→ CLOSED`，
   时钟可注入，单测里不必 sleep 就能驱动状态迁移。
4. **完全可离线测**：`ScriptedProvider` 用函数"演"出模型回复，让路由、缓存、重试、熔断、结构化
   输出全部能在没有 API Key 的情况下跑通单测——这也是仓库里 39 个测试全绿的底气。

### 四、和同类方案对比

| 维度 | 裸调 SDK | 大型 Agent 框架 | Nexus |
| --- | --- | --- | --- |
| 依赖 | 单一 SDK | 重型、传递依赖多 | **零依赖** |
| 路由/熔断/限流 | 自己拼 | 部分内建 | **内建且可组合** |
| 结构化输出 | 自己校验 | 视框架而定 | **内建 + schema 校验** |
| 可嵌入性 | 高 | 低 | **极高（纯源码）** |
| 学习曲线 | 低 | 陡 | **平缓** |

Nexus 不是要取代那些框架，而是给你一个**轻、透明、能读懂每一行**的底座：需要重武器时再往上叠。

### 五、如何扩展

- 接新模型：实现 `Provider` 一个接口即可，其余代码零改动。
- 接 Redis 缓存：替换 `SemanticCache` 的实现，保持 `get/put` 语义。
- 接可观测：在 `Meter` 基础上把 span 推给 OpenTelemetry。
- 做 RAG：用 `StructuredOutput` 抽取查询，再接一个向量库做检索，串成管线。

### 如何把本文扩展成一篇发布稿

1. 开头加一段"我线上被 429 坑了三小时"的真实故事，制造共鸣；
2. 每个原理配一张手绘图 / 架构图（可用本文 ASCII 图作底）；
3. 放一段最小可运行示例的录屏或输出截图；
4. 结尾抛问题："你的 LLM 调用现在有几层防护？"引导评论；
5. 文末挂仓库链接与"下篇预告"（如结构化输出的坑），形成系列。
