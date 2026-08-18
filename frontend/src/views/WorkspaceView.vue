<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import {
  PhArrowLeft as ArrowLeft, PhArrowRight as ArrowRight, PhArrowsClockwise as ArrowsClockwise,
  PhArrowsOut as ArrowsOut, PhAtom as Atom, PhBracketsCurly as BracketsCurly,
  PhCaretDown as CaretDown, PhChartLineUp as ChartLineUp, PhCode as Code,
  PhCopy as Copy, PhCrosshair as Crosshair, PhCube as Cube, PhFileCode as FileCode,
  PhFiles as Files, PhFunnel as Funnel, PhGitBranch as GitBranch, PhGithubLogo as GithubLogo,
  PhMagnifyingGlass as MagnifyingGlass, PhMinus as Minus, PhPaperPlaneTilt as PaperPlaneTilt,
  PhPath as Path, PhPlus as Plus, PhSlidersHorizontal as SlidersHorizontal,
  PhSparkle as Sparkle, PhTerminalWindow as TerminalWindow, PhTrash as Trash, PhX as X
} from "@phosphor-icons/vue";
import CodeGraph from "../components/CodeGraph.vue";
import { api } from "../api";
import type {
  AnalysisSession, ChatMessage, ImpactAnalysis, Project, ProjectGraph, ProjectInsights, SourceFile
} from "../types";

type WorkspaceMode = "代码星图" | "依赖关系" | "洞察" | "变更";
type GraphHandle = { resetView: () => void; zoomIn: () => void; zoomOut: () => void; focusActive: () => void };

const route = useRoute();
const router = useRouter();
const projectId = route.params.id as string;
const project = ref<Project | null>(null);
const files = ref<SourceFile[]>([]);
const activeFile = ref<SourceFile | null>(null);
const insights = ref<ProjectInsights | null>(null);
const graph = ref<ProjectGraph | null>(null);
const impact = ref<ImpactAnalysis | null>(null);
const sessions = ref<AnalysisSession[]>([]);
const session = ref<AnalysisSession | null>(null);
const messages = ref<ChatMessage[]>([]);
const question = ref("");
const fileFilter = ref("");
const globalQuery = ref("");
const searchResults = ref<SourceFile[]>([]);
const searching = ref(false);
const asking = ref(false);
const error = ref("");
const activeMode = ref<WorkspaceMode>("代码星图");
const showSearch = ref(false);
const showProjectMenu = ref(false);
const showSessions = ref(false);
const showConfig = ref(true);
const onlyDirect = ref(false);
const evidenceOnly = ref(false);
const minimumLinks = ref(1);
const collapsedFolders = ref(new Set<string>());
const chatElement = ref<HTMLElement | null>(null);
const graphElement = ref<HTMLElement | null>(null);
const graphRef = ref<GraphHandle | null>(null);
let timer: number | undefined;
let initialized = false;

const filteredFiles = computed(() => {
  const value = fileFilter.value.toLowerCase().trim();
  return files.value.filter(file => {
    const isConfig = ["yaml", "text", "sql"].includes(file.language)
      || /(^|\/)(Dockerfile|compose\.|.*\.config\.)/i.test(file.path);
    return (showConfig.value || !isConfig) && (!value || file.path.toLowerCase().includes(value));
  });
});
const codeLines = computed(() => activeFile.value?.content.split(/\r?\n/) ?? []);
const folders = computed(() => {
  const result = new Map<string, SourceFile[]>();
  filteredFiles.value.forEach(file => {
    const parts = file.path.split("/");
    const key = parts.length > 1 ? parts.slice(0, -1).join("/") : "root";
    result.set(key, [...(result.get(key) || []), file]);
  });
  return [...result.entries()];
});
const visibleGraph = computed<ProjectGraph | null>(() => {
  if (!graph.value) return null;
  let edges = graph.value.edges;
  if (activeMode.value === "依赖关系") edges = edges.filter(edge => edge.type === "import");
  if (onlyDirect.value && activeFile.value) {
    edges = edges.filter(edge => edge.source === activeFile.value?.path || edge.target === activeFile.value?.path);
  }
  return { nodes: graph.value.nodes, edges };
});
const evidence = computed(() => {
  const cited = messages.value.flatMap(message => message.citations || []);
  return [...new Set(cited)].filter(path => !evidenceOnly.value || path === activeFile.value?.path).slice(0, 20);
});
const impactGroups = computed(() => {
  const result = new Map<string, number>();
  (impact.value?.dependents || []).forEach(item => result.set(topFolder(item.path), (result.get(topFolder(item.path)) || 0) + 1));
  return [...result.entries()].sort((a, b) => b[1] - a[1]).slice(0, 6);
});
const maxLanguage = computed(() => Math.max(...(insights.value?.languages.map(item => item.value) || [1])));

function topFolder(path: string) {
  return path.split("/")[0] || "root";
}
function formatBytes(bytes: number) {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`;
}
function notify(reason: unknown, fallback: string) {
  error.value = reason instanceof Error ? reason.message : fallback;
}

function createClientId() {
  if (typeof crypto !== "undefined" && typeof crypto.randomUUID === "function") {
    return crypto.randomUUID();
  }
  return `local-${Date.now()}-${Math.random().toString(16).slice(2)}`;
}

async function loadProject() {
  project.value = await api.getProject(projectId);
  if (project.value.status === "READY" && !initialized) await initializeWorkspace();
}

async function initializeWorkspace() {
  initialized = true;
  try {
    const [fileList, insightData, graphData, sessionList] = await Promise.all([
      api.listFiles(projectId), api.getInsights(projectId), api.getGraph(projectId), api.listSessions(projectId)
    ]);
    files.value = fileList;
    insights.value = insightData;
    graph.value = graphData;
    sessions.value = sessionList;
    session.value = sessions.value[0] || await api.createSession(projectId);
    if (!sessions.value.length) sessions.value = [session.value];
    messages.value = await api.listMessages(session.value.id);
    if (files.value.length) await openFile(files.value[0].path);
  } catch (reason) {
    initialized = false;
    throw reason;
  }
}

async function openFile(path: string) {
  try {
    const [file, impactData] = await Promise.all([api.getFile(projectId, path), api.getImpact(projectId, path)]);
    activeFile.value = file;
    impact.value = impactData;
    showSearch.value = false;
    await nextTick();
    if (activeMode.value === "代码星图" || activeMode.value === "依赖关系") graphRef.value?.focusActive();
  } catch (reason) {
    notify(reason, "文件加载失败");
  }
}

async function runSearch() {
  if (!globalQuery.value.trim()) return;
  searching.value = true;
  try {
    searchResults.value = await api.search(projectId, globalQuery.value.trim());
  } catch (reason) {
    notify(reason, "搜索失败");
  } finally {
    searching.value = false;
  }
}

async function switchSession(next: AnalysisSession) {
  session.value = next;
  messages.value = await api.listMessages(next.id);
  showSessions.value = false;
}

async function createSession() {
  try {
    const created = await api.createSession(projectId, `分析会话 ${sessions.value.length + 1}`);
    sessions.value = [created, ...sessions.value];
    session.value = created;
    messages.value = [];
  } catch (reason) {
    notify(reason, "创建会话失败");
  }
}

async function renameCurrentSession() {
  if (!session.value) return;
  const title = window.prompt("输入新的会话名称", session.value.title);
  if (!title?.trim()) return;
  try {
    const updated = await api.renameSession(session.value.id, title.trim());
    sessions.value = sessions.value.map(item => item.id === updated.id ? updated : item);
    session.value = updated;
  } catch (reason) {
    notify(reason, "重命名失败");
  }
}

async function deleteCurrentSession() {
  if (!session.value || sessions.value.length <= 1) return;
  if (!window.confirm(`删除会话“${session.value.title}”及其全部消息？`)) return;
  try {
    await api.deleteSession(session.value.id);
    sessions.value = sessions.value.filter(item => item.id !== session.value?.id);
    await switchSession(sessions.value[0]);
  } catch (reason) {
    notify(reason, "删除会话失败");
  }
}

async function ask(contentOverride?: string) {
  const content = (contentOverride || question.value).trim();
  if (!content || !session.value || asking.value) return;
  question.value = "";
  asking.value = true;
  messages.value.push({ id: createClientId(), sessionId: session.value.id, role: "user", content, citations: [], createdAt: new Date().toISOString() });
  await nextTick();
  chatElement.value?.scrollTo({ top: chatElement.value.scrollHeight, behavior: "smooth" });
  try {
    messages.value.push(await api.ask(session.value.id, content));
    sessions.value = await api.listSessions(projectId);
  } catch (reason) {
    messages.value = messages.value.filter(message => message.content !== content || message.role !== "user");
    notify(reason, "Agent 请求失败");
  } finally {
    asking.value = false;
    await nextTick();
    chatElement.value?.scrollTo({ top: chatElement.value.scrollHeight, behavior: "smooth" });
  }
}

function generateChangeAdvice() {
  const target = activeFile.value?.path;
  if (!target) return notify(null, "请先选择一个文件");
  void ask(`请分析修改 ${target} 的影响范围，列出直接依赖、间接影响、风险和建议的测试清单。`);
}

async function reindexProject() {
  if (!project.value || !window.confirm("重新索引会替换现有文件索引，继续吗？")) return;
  try {
    project.value = await api.reindexProject(projectId);
    initialized = false;
    files.value = [];
    graph.value = null;
    insights.value = null;
    showProjectMenu.value = false;
  } catch (reason) {
    notify(reason, "重新索引失败");
  }
}

async function deleteProject() {
  if (!project.value || !window.confirm(`永久删除项目“${project.value.name}”及其索引和会话？`)) return;
  try {
    await api.deleteProject(projectId);
    await router.push("/");
  } catch (reason) {
    notify(reason, "删除项目失败");
  }
}

function toggleFolder(folder: string) {
  const next = new Set(collapsedFolders.value);
  next.has(folder) ? next.delete(folder) : next.add(folder);
  collapsedFolders.value = next;
}

async function copyRepositoryUrl() {
  if (!project.value) return;
  await navigator.clipboard.writeText(project.value.repositoryUrl);
  error.value = "仓库地址已复制";
}

async function toggleFullscreen() {
  if (!graphElement.value) return;
  if (document.fullscreenElement) await document.exitFullscreen();
  else await graphElement.value.requestFullscreen();
}

onMounted(async () => {
  try {
    await loadProject();
    timer = window.setInterval(() => void loadProject().catch(reason => notify(reason, "项目刷新失败")), 3500);
  } catch (reason) {
    notify(reason, "项目加载失败");
  }
});
onBeforeUnmount(() => timer && window.clearInterval(timer));
</script>

<template>
  <section v-if="project" class="atlas-workspace">
    <header class="atlas-toolbar">
      <div class="toolbar-project-wrap">
        <button class="toolbar-project" @click="showProjectMenu = !showProjectMenu"><TerminalWindow :size="16" /><strong>{{ project.name }}</strong><CaretDown :size="12" /></button>
        <div v-if="showProjectMenu" class="project-menu">
          <button @click="router.push('/')"><ArrowLeft :size="14" />返回项目列表</button>
          <a :href="project.repositoryUrl" target="_blank" rel="noreferrer"><GithubLogo :size="14" />打开远程仓库</a>
          <button @click="copyRepositoryUrl"><Copy :size="14" />复制仓库地址</button>
          <button @click="reindexProject"><ArrowsClockwise :size="14" />重新建立索引</button>
          <button class="danger" @click="deleteProject"><Trash :size="14" />删除项目</button>
        </div>
      </div>
      <nav class="mode-tabs" aria-label="分析模式">
        <button v-for="mode in (['代码星图', '依赖关系', '洞察', '变更'] as WorkspaceMode[])" :key="mode" :class="{ active: activeMode === mode }" @click="activeMode = mode">
          <Atom v-if="mode === '代码星图'" :size="15" /><Path v-else-if="mode === '依赖关系'" :size="15" /><ChartLineUp v-else :size="15" />{{ mode }}
        </button>
      </nav>
      <div class="toolbar-actions"><button @click="showSearch = true"><MagnifyingGlass :size="16" />搜索</button><span :class="['index-badge', project.status.toLowerCase()]"><i></i>{{ project.status === 'READY' ? '索引完成' : project.statusMessage }}</span></div>
    </header>

    <div v-if="project.status !== 'READY'" class="indexing-screen">
      <div class="scanner"><Cube :size="28" /></div><p class="eyebrow">REPOSITORY PIPELINE</p>
      <h1>{{ project.statusMessage }}</h1><p>CodeAtlas 正在安全地克隆并建立只读代码索引，页面会自动刷新。</p>
      <button v-if="project.status === 'FAILED'" @click="reindexProject"><ArrowsClockwise :size="15" />重新尝试</button>
    </div>

    <div v-else class="atlas-layout">
      <aside class="atlas-explorer">
        <div class="rail-heading"><span>仓库文件</span><b>{{ filteredFiles.length }}</b></div>
        <button class="branch-button" @click="showProjectMenu = !showProjectMenu"><GitBranch :size="14" />{{ project.branch || "默认分支" }}<CaretDown :size="12" /></button>
        <label class="explorer-search"><MagnifyingGlass :size="13" /><input v-model="fileFilter" placeholder="按文件路径筛选" /></label>
        <nav class="atlas-filetree">
          <section v-for="[folder, entries] in folders" :key="folder">
            <button class="folder-row" @click="toggleFolder(folder)"><CaretDown :size="11" :class="{ folded: collapsedFolders.has(folder) }" />{{ folder }}<span>{{ entries.length }}</span></button>
            <template v-if="!collapsedFolders.has(folder)">
              <button v-for="file in entries" :key="file.id" :class="{ active: activeFile?.path === file.path }" @click="openFile(file.path)">
                <FileCode :size="13" /><span>{{ file.path.split('/').at(-1) }}</span>
              </button>
            </template>
          </section>
        </nav>
        <div class="scope-panel">
          <div class="rail-heading"><span>图谱过滤</span><SlidersHorizontal :size="13" /></div>
          <label><input v-model="showConfig" type="checkbox" />显示配置与脚本</label>
          <label><input v-model="onlyDirect" type="checkbox" />仅显示当前文件直接关系</label>
          <span>最小连接强度：{{ minimumLinks }}</span>
          <input v-model="minimumLinks" type="range" min="1" max="3" />
        </div>
        <footer><i></i><span>真实索引</span><small>{{ project.fileCount }} files</small></footer>
      </aside>

      <main class="atlas-center">
        <div v-if="activeMode === '代码星图' || activeMode === '依赖关系'" ref="graphElement" class="graph-stage">
          <div class="graph-stats">
            <span><b>{{ visibleGraph?.nodes.length || 0 }}</b>节点</span><span><b>{{ visibleGraph?.edges.length || 0 }}</b>连接</span><span><b>{{ insights?.topDirectories.length || 0 }}</b>目录</span>
          </div>
          <div class="graph-tools">
            <button title="定位当前文件" @click="graphRef?.focusActive()"><Crosshair :size="16" /></button>
            <button title="放大" @click="graphRef?.zoomIn()"><Plus :size="16" /></button>
            <button title="缩小" @click="graphRef?.zoomOut()"><Minus :size="16" /></button>
            <button title="恢复视图" @click="graphRef?.resetView()"><ArrowsClockwise :size="16" /></button>
            <button title="全屏" @click="toggleFullscreen"><ArrowsOut :size="16" /></button>
          </div>
          <div class="graph-legend"><strong>{{ activeMode }}</strong><span><i class="lime"></i>当前文件</span><span><i class="cyan"></i>索引文件</span><span><i class="violet"></i>import 依赖</span></div>
          <CodeGraph ref="graphRef" :files="filteredFiles" :graph="visibleGraph" :minimum-links="minimumLinks" :active-path="activeFile?.path" @select="openFile" />
          <div class="graph-caption">真实 import 关系 · 拖拽旋转 · 滚轮缩放 · 点击节点查看代码</div>
        </div>

        <section v-else-if="activeMode === '洞察'" class="insights-stage">
          <header><div><p class="eyebrow">REPOSITORY INSIGHTS</p><h2>代码库健康概览</h2></div><span>{{ insights?.indexedAt ? new Date(insights.indexedAt).toLocaleString('zh-CN') : '—' }}</span></header>
          <div class="insight-metrics"><article><small>FILES</small><b>{{ insights?.totalFiles.toLocaleString() }}</b><span>已索引文件</span></article><article><small>LINES</small><b>{{ insights?.totalLines.toLocaleString() }}</b><span>代码与配置行</span></article><article><small>SIZE</small><b>{{ formatBytes(insights?.totalBytes || 0) }}</b><span>索引文本体积</span></article><article><small>EDGES</small><b>{{ graph?.edges.length.toLocaleString() }}</b><span>真实依赖关系</span></article></div>
          <div class="insight-grid">
            <article class="language-chart"><h3>语言分布</h3><div v-for="item in insights?.languages" :key="item.name"><span>{{ item.name }}</span><i><b :style="{ width: `${item.value / maxLanguage * 100}%` }"></b></i><em>{{ item.value }}</em></div></article>
            <article><h3>入口文件</h3><button v-for="file in insights?.entryPoints" :key="file.path" @click="openFile(file.path)"><FileCode :size="13" /><span>{{ file.path }}</span><ArrowRight :size="12" /></button><p v-if="!insights?.entryPoints.length">未识别到常见入口文件</p></article>
            <article><h3>最大文件</h3><button v-for="file in insights?.largestFiles" :key="file.path" @click="openFile(file.path)"><span>{{ file.path }}</span><em>{{ file.lineCount }} L · {{ formatBytes(file.byteSize) }}</em></button></article>
          </div>
        </section>

        <section v-else class="change-stage">
          <header><div><p class="eyebrow">CHANGE IMPACT</p><h2>{{ activeFile?.path.split('/').at(-1) || '选择一个文件' }}</h2><span>{{ activeFile?.path }}</span></div><div :class="['risk-orb', impact?.risk.toLowerCase()]"><b>{{ impact?.score || 0 }}</b><small>{{ impact?.risk || 'LOW' }} RISK</small></div></header>
          <div class="change-columns">
            <article><h3>被哪些文件依赖 <span>{{ impact?.dependents.length || 0 }}</span></h3><button v-for="item in impact?.dependents" :key="item.path" @click="openFile(item.path)"><b>D{{ item.depth }}</b><span>{{ item.path }}</span></button><p v-if="!impact?.dependents.length">没有发现上游依赖</p></article>
            <article><h3>依赖哪些文件 <span>{{ impact?.dependencies.length || 0 }}</span></h3><button v-for="item in impact?.dependencies" :key="item.path" @click="openFile(item.path)"><b>D{{ item.depth }}</b><span>{{ item.path }}</span></button><p v-if="!impact?.dependencies.length">没有发现内部依赖</p></article>
          </div>
        </section>

        <section class="atlas-code">
          <header><div><Code :size="14" /><strong>{{ activeFile?.path || 'CODE VIEWER' }}</strong></div><span>{{ activeFile?.lineCount || 0 }} 行 · {{ activeFile?.language || '—' }} · {{ formatBytes(activeFile?.byteSize || 0) }}</span></header>
          <div v-if="activeFile" class="code-scroll"><div v-for="(line, index) in codeLines" :key="index" class="atlas-code-line"><span>{{ index + 1 }}</span><code>{{ line || " " }}</code></div></div>
          <div v-else class="code-empty">从左侧文件树或关系图选择文件</div>
        </section>
      </main>

      <aside class="atlas-agent">
        <header><button @click="showSessions = !showSessions"><Sparkle :size="16" weight="fill" /><strong>{{ session?.title || 'Atlas Agent' }}</strong><CaretDown :size="11" /></button><span><i></i>在线</span></header>
        <div v-if="showSessions" class="session-panel">
          <header><strong>分析会话</strong><button @click="showSessions = false"><X :size="13" /></button></header>
          <button v-for="item in sessions" :key="item.id" :class="{ active: item.id === session?.id }" @click="switchSession(item)"><span>{{ item.title }}</span><small>{{ new Date(item.updatedAt).toLocaleDateString('zh-CN') }}</small></button>
          <div><button @click="createSession"><Plus :size="13" />新建</button><button @click="renameCurrentSession"><Code :size="13" />重命名</button><button :disabled="sessions.length <= 1" @click="deleteCurrentSession"><Trash :size="13" />删除</button></div>
        </div>
        <form class="agent-question" @submit.prevent="ask()"><textarea v-model="question" rows="2" maxlength="8000" placeholder="询问架构、调用链或变更影响…"></textarea><button :disabled="asking || !question.trim()"><PaperPlaneTilt :size="15" weight="fill" /></button></form>
        <div ref="chatElement" class="agent-stream">
          <div v-if="!messages.length" class="agent-empty"><Sparkle :size="24" /><strong>基于真实代码开始分析</strong><button @click="ask('概览这个项目的架构、入口和关键模块，并给出文件证据。')">生成项目概览</button><button @click="ask('找出这个项目最值得关注的变更风险和测试边界。')">扫描变更风险</button></div>
          <article v-for="message in messages" :key="message.id" :class="['atlas-message', message.role]"><small>{{ message.role === 'user' ? 'YOU' : '分析结论' }}</small><p>{{ message.content }}</p></article>
          <article v-if="asking" class="atlas-message thinking"><small>ANALYZING</small><p>正在检索符号、读取文件并追踪调用关系…</p></article>
          <section v-if="impact" class="impact-path"><div class="agent-section-title"><span>当前文件影响</span><small>{{ impact.risk }} · {{ impact.score }}</small></div><ol><li v-for="item in impact.dependents.slice(0, 6)" :key="item.path"><b>{{ item.depth }}</b><span>{{ item.path }}</span><em>上游</em></li></ol></section>
          <section v-if="evidence.length" class="evidence-list"><div class="agent-section-title"><span>回答证据</span><small>{{ evidence.length }} 处</small></div><button v-for="(path, index) in evidence" :key="path" @click="openFile(path)"><b>{{ index + 1 }}</b><span>{{ path.split('/').at(-1) }}<small>{{ path }}</small></span><ArrowRight :size="13" /></button></section>
          <section v-if="impactGroups.length" class="impact-summary"><div class="agent-section-title"><span>影响目录</span><small>依赖文件数</small></div><div v-for="[name, count] in impactGroups" :key="name"><span>{{ name }}</span><b></b><b></b><em>{{ count }}</em></div></section>
        </div>
        <div class="agent-footer"><button @click="generateChangeAdvice"><BracketsCurly :size="15" />生成变更建议</button><button :class="{ active: evidenceOnly }" title="仅显示当前文件证据" @click="evidenceOnly = !evidenceOnly"><Funnel :size="15" /></button><button title="管理会话" @click="showSessions = !showSessions"><SlidersHorizontal :size="15" /></button></div>
      </aside>
    </div>

    <div v-if="showSearch" class="search-overlay" @click.self="showSearch = false">
      <section><header><MagnifyingGlass :size="17" /><input v-model="globalQuery" autofocus placeholder="搜索文件路径或代码内容" @keyup.enter="runSearch" /><button @click="showSearch = false"><X :size="16" /></button></header><div class="search-hint"><span>ENTER 搜索</span><span>{{ searching ? '正在检索…' : `${searchResults.length} 个结果` }}</span></div><button v-for="file in searchResults" :key="file.id" @click="openFile(file.path)"><FileCode :size="15" /><span><strong>{{ file.path }}</strong><small>{{ file.language }} · {{ file.lineCount }} 行</small></span><ArrowRight :size="14" /></button><p v-if="globalQuery && !searching && !searchResults.length">没有找到匹配的路径或代码</p></section>
    </div>
    <button v-if="error" class="toast" @click="error = ''">{{ error }}</button>
  </section>
</template>
