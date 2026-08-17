<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from "vue";
import { useRoute } from "vue-router";
import {
  PhArrowRight as ArrowRight, PhArrowsOut as ArrowsOut, PhAtom as Atom,
  PhBracketsCurly as BracketsCurly, PhCaretDown as CaretDown, PhChartLineUp as ChartLineUp,
  PhCode as Code, PhCrosshair as Crosshair, PhCube as Cube, PhFileCode as FileCode,
  PhFiles as Files, PhFunnel as Funnel, PhGitBranch as GitBranch,
  PhMagnifyingGlass as MagnifyingGlass, PhMinus as Minus, PhPaperPlaneTilt as PaperPlaneTilt,
  PhPath as Path, PhPlus as Plus, PhSlidersHorizontal as SlidersHorizontal,
  PhSparkle as Sparkle, PhTerminalWindow as TerminalWindow
} from "@phosphor-icons/vue";
import CodeGraph from "../components/CodeGraph.vue";
import { api } from "../api";
import type { AnalysisSession, ChatMessage, Project, SourceFile } from "../types";

const route = useRoute();
const projectId = route.params.id as string;
const demoMode = computed(() => route.query.demo === "1" || projectId === "demo");
const project = ref<Project | null>(null);
const files = ref<SourceFile[]>([]);
const activeFile = ref<SourceFile | null>(null);
const fileFilter = ref("");
const session = ref<AnalysisSession | null>(null);
const messages = ref<ChatMessage[]>([]);
const question = ref("");
const asking = ref(false);
const error = ref("");
const chatElement = ref<HTMLElement | null>(null);
const activeMode = ref("代码星图");
const showThirdParty = ref(true);
const minimumLinks = ref(2);
let timer: number | undefined;

const filteredFiles = computed(() => {
  const value = fileFilter.value.toLowerCase().trim();
  return files.value.filter(file => !value || file.path.toLowerCase().includes(value));
});
const codeLines = computed(() => activeFile.value?.content.split(/\r?\n/) ?? []);
const folders = computed(() => {
  const result = new Map<string, SourceFile[]>();
  filteredFiles.value.forEach(file => {
    const parts = file.path.split("/");
    const key = parts.length > 2 ? parts[parts.length - 2] : "root";
    result.set(key, [...(result.get(key) || []), file]);
  });
  return [...result.entries()];
});
const evidence = computed(() => {
  const cited = messages.value.flatMap(message => message.citations || []);
  return [...new Set(cited.length ? cited : files.value.slice(0, 4).map(file => file.path))].slice(0, 4);
});

function demoFile(path: string, language: string, content: string): SourceFile {
  return { id: path, projectId: "demo", path, language, content, byteSize: content.length, lineCount: content.split("\n").length, sha256: "demo" };
}

function setupDemo() {
  project.value = {
    id: "demo", name: "spring-petclinic", repositoryUrl: "https://github.com/spring-projects/spring-petclinic",
    branch: "main", status: "READY", statusMessage: "索引完成", fileCount: 628,
    indexedAt: new Date().toISOString(), createdAt: new Date().toISOString(), updatedAt: new Date().toISOString()
  };
  files.value = [
    demoFile("src/main/java/org/springframework/samples/petclinic/owner/OwnerController.java", "Java", `@Controller\nclass OwnerController {\n  private final ClinicService clinicService;\n\n  @GetMapping("/owners/{ownerId}/pets")\n  String showPets(@PathVariable int ownerId, Model model) {\n    Owner owner = clinicService.findOwnerById(ownerId);\n    List<Visit> visits = clinicService.findVisitsByOwnerId(ownerId);\n    model.addAttribute("owner", owner);\n    model.addAttribute("visits", visits);\n    return "owners/visitsList";\n  }\n}`),
    demoFile("src/main/java/org/springframework/samples/petclinic/service/ClinicService.java", "Java", `@Service\n@Transactional(readOnly = true)\npublic class ClinicService {\n  private final VisitRepository visits;\n\n  public List<Visit> findVisitsByOwnerId(int ownerId) {\n    return visits.findByOwnerId(ownerId);\n  }\n}`),
    demoFile("src/main/java/org/springframework/samples/petclinic/repository/VisitRepository.java", "Java", `public interface VisitRepository extends Repository<Visit, Integer> {\n  @Query("SELECT visit FROM Visit visit WHERE visit.pet.id = :petId")\n  List<Visit> findByPetId(Integer petId);\n}`),
    demoFile("src/main/java/org/springframework/samples/petclinic/model/Visit.java", "Java", `@Entity\n@Table(name = "visits")\npublic class Visit extends BaseEntity {\n  @Column(name = "visit_date")\n  private LocalDate date;\n  private String description;\n}`),
    demoFile("src/main/resources/db/h2/schema.sql", "SQL", `CREATE TABLE visits (\n  id INTEGER IDENTITY PRIMARY KEY,\n  pet_id INTEGER NOT NULL,\n  visit_date DATE,\n  description VARCHAR(255)\n);`),
    ...Array.from({ length: 160 }, (_, index) => demoFile(`src/main/java/org/springframework/samples/petclinic/${["owner", "vet", "system", "model", "repository", "service", "web", "config"][index % 8]}/${["Owner", "Pet", "Vet", "Visit", "Specialty", "Clinic", "Router", "Security"][index % 8]}${index}.java`, "Java", `package org.springframework.samples.petclinic;\n\npublic class Module${index} {\n  public void execute() { }\n}`))
  ];
  activeFile.value = files.value[0];
  session.value = { id: "demo-session", projectId: "demo", title: "变更影响分析", createdAt: new Date().toISOString(), updatedAt: new Date().toISOString() };
  messages.value = [{
    id: "demo-answer", sessionId: "demo-session", role: "assistant",
    content: "预约时间解析的改动会影响 Web、Service、Repository 四个模块。入口位于 OwnerController，业务转换集中在 ClinicService，最终落到 VisitRepository 与 visits 表。建议同步补充日期边界和时区测试。",
    citations: files.value.slice(0, 4).map(file => file.path), createdAt: new Date().toISOString()
  }];
}

async function loadProject() {
  project.value = await api.getProject(projectId);
  if (project.value.status === "READY" && !files.value.length) await initializeWorkspace();
}

async function initializeWorkspace() {
  files.value = await api.listFiles(projectId);
  const sessions = await api.listSessions(projectId);
  session.value = sessions[0] || await api.createSession(projectId);
  messages.value = await api.listMessages(session.value.id);
  if (!activeFile.value && files.value.length) await openFile(files.value[0].path);
}

async function openFile(path: string) {
  const local = files.value.find(file => file.path === path);
  activeFile.value = demoMode.value && local ? local : await api.getFile(projectId, path);
}

async function ask() {
  if (!question.value.trim() || !session.value || asking.value) return;
  const content = question.value.trim();
  question.value = "";
  asking.value = true;
  messages.value.push({ id: crypto.randomUUID(), sessionId: session.value.id, role: "user", content, citations: [], createdAt: new Date().toISOString() });
  await nextTick();
  chatElement.value?.scrollTo({ top: chatElement.value.scrollHeight, behavior: "smooth" });
  try {
    if (demoMode.value) {
      await new Promise(resolve => window.setTimeout(resolve, 650));
      messages.value.push({
        id: crypto.randomUUID(), sessionId: session.value.id, role: "assistant",
        content: "我沿着调用关系定位到 4 个直接影响点：请求入口、日期转换、持久化查询和 Visit 实体。图中已经用绿色路径标出相关节点。",
        citations: files.value.slice(0, 4).map(file => file.path), createdAt: new Date().toISOString()
      });
    } else {
      messages.value.push(await api.ask(session.value.id, content));
    }
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : "Agent 请求失败";
  } finally {
    asking.value = false;
    await nextTick();
    chatElement.value?.scrollTo({ top: chatElement.value.scrollHeight, behavior: "smooth" });
  }
}

onMounted(async () => {
  if (demoMode.value) return setupDemo();
  try {
    await loadProject();
    timer = window.setInterval(() => void loadProject(), 3500);
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : "项目加载失败";
  }
});
onBeforeUnmount(() => timer && window.clearInterval(timer));
</script>

<template>
  <section v-if="project" class="atlas-workspace">
    <header class="atlas-toolbar">
      <div class="toolbar-project"><TerminalWindow :size="16" /><strong>{{ project.name }}</strong><CaretDown :size="12" /></div>
      <nav class="mode-tabs" aria-label="分析模式">
        <button v-for="mode in ['代码星图', '依赖关系', '洞察', '变更']" :key="mode" :class="{ active: activeMode === mode }" @click="activeMode = mode">
          <Atom v-if="mode === '代码星图'" :size="15" /><Path v-else-if="mode === '依赖关系'" :size="15" /><ChartLineUp v-else :size="15" />{{ mode }}
        </button>
      </nav>
      <div class="toolbar-actions"><button><MagnifyingGlass :size="16" />搜索</button><span class="index-badge"><i></i>索引完成</span></div>
    </header>

    <div v-if="project.status !== 'READY'" class="indexing-screen">
      <div class="scanner"><Cube :size="28" /></div><p class="eyebrow">REPOSITORY PIPELINE</p>
      <h1>{{ project.statusMessage }}</h1><p>CodeAtlas 正在安全地克隆并建立只读代码索引。</p>
    </div>

    <div v-else class="atlas-layout">
      <aside class="atlas-explorer">
        <div class="rail-heading"><span>仓库</span><Files :size="15" /></div>
        <button class="branch-button"><GitBranch :size="14" />{{ project.branch || "main" }}<CaretDown :size="12" /></button>
        <label class="explorer-search"><MagnifyingGlass :size="13" /><input v-model="fileFilter" placeholder="按名称筛选" /></label>
        <nav class="atlas-filetree">
          <section v-for="[folder, entries] in folders" :key="folder">
            <h4><CaretDown :size="11" />{{ folder }}<span>{{ entries.length }}</span></h4>
            <button v-for="file in entries" :key="file.id" :class="{ active: activeFile?.path === file.path }" @click="openFile(file.path)">
              <FileCode :size="13" /><span>{{ file.path.split('/').at(-1) }}</span>
            </button>
          </section>
        </nav>
        <div class="scope-panel">
          <div class="rail-heading"><span>范围过滤</span><CaretDown :size="12" /></div>
          <label><input v-model="showThirdParty" type="checkbox" />显示第三方库</label>
          <label><input type="checkbox" />仅显示直接依赖</label>
          <span>最小连接强度</span>
          <input v-model="minimumLinks" type="range" min="1" max="5" />
        </div>
        <footer><i></i><span>索引完成</span><small>1 分钟前</small></footer>
      </aside>

      <main class="atlas-center">
        <div class="graph-stage">
          <div class="graph-stats">
            <span><b>{{ project.fileCount }}</b>节点</span><span><b>{{ files.length * 11 + 27 }}</b>连接</span><span><b>{{ folders.length }}</b>包</span>
          </div>
          <div class="graph-tools"><button title="定位"><Crosshair :size="16" /></button><button title="放大"><Plus :size="16" /></button><button title="缩小"><Minus :size="16" /></button><button title="全屏"><ArrowsOut :size="16" /></button></div>
          <div class="graph-legend"><strong>图例</strong><span><i class="lime"></i>当前链路</span><span><i class="cyan"></i>内部依赖</span><span><i class="violet"></i>配置</span></div>
          <CodeGraph :files="files" :active-path="activeFile?.path" @select="openFile" />
          <div class="graph-labels" aria-hidden="true"><span class="web">web</span><span class="owner">owner</span><span class="visit">visit</span><span class="service">service</span><span class="repository">repository</span><span class="model">model</span><span class="config">configuration</span></div>
          <div class="graph-caption">拖拽旋转 · 滚轮缩放 · 点击节点查看代码</div>
        </div>

        <section class="atlas-code">
          <header><div><Code :size="14" /><strong>{{ activeFile?.path.split('/').at(-1) || 'CODE VIEWER' }}</strong></div><span>{{ activeFile?.lineCount || 0 }} 行 · {{ activeFile?.language || '—' }}</span></header>
          <div v-if="activeFile" class="code-scroll">
            <div v-for="(line, index) in codeLines" :key="index" :class="['atlas-code-line', { focused: index === 7 }]">
              <span>{{ index + 1 }}</span><code>{{ line || " " }}</code>
            </div>
          </div>
        </section>
      </main>

      <aside class="atlas-agent">
        <header><div><Sparkle :size="16" weight="fill" /><strong>Atlas Agent</strong></div><span><i></i>在线</span></header>
        <form class="agent-question" @submit.prevent="ask">
          <textarea v-model="question" rows="2" maxlength="8000" placeholder="询问架构、调用链或变更影响…"></textarea>
          <button :disabled="asking || !question.trim()"><PaperPlaneTilt :size="15" weight="fill" /></button>
        </form>
        <div ref="chatElement" class="agent-stream">
          <article v-for="message in messages" :key="message.id" :class="['atlas-message', message.role]">
            <small>{{ message.role === 'user' ? 'YOU' : '分析结论' }}</small><p>{{ message.content }}</p>
          </article>
          <article v-if="asking" class="atlas-message thinking"><small>ANALYZING</small><p>正在检索符号、读取文件并追踪调用关系…</p></article>

          <section class="impact-path">
            <div class="agent-section-title"><span>影响路径</span><small>依赖链路</small></div>
            <ol><li v-for="(item, index) in ['web / 请求入口', 'service / 业务转换', 'repository / 持久化', 'model / 日期字段']" :key="item"><b>{{ index + 1 }}</b><span>{{ item }}</span><em>{{ index < 2 ? 2 : 1 }}</em></li></ol>
          </section>
          <section class="evidence-list">
            <div class="agent-section-title"><span>证据</span><small>{{ evidence.length }} 处</small></div>
            <button v-for="(path, index) in evidence" :key="path" @click="openFile(path)"><b>{{ index + 1 }}</b><span>{{ path.split('/').at(-1) }}<small>{{ path }}</small></span><ArrowRight :size="13" /></button>
          </section>
          <section class="impact-summary">
            <div class="agent-section-title"><span>影响摘要</span><small>直接 / 间接</small></div>
            <div><span>web</span><b>1</b><b>1</b><em>2</em></div><div><span>service</span><b>2</b><b>3</b><em>5</em></div><div><span>repository</span><b>1</b><b>1</b><em>2</em></div><div><span>model</span><b>1</b><b>0</b><em>1</em></div>
          </section>
        </div>
        <div class="agent-footer"><button><BracketsCurly :size="15" />生成变更建议</button><button title="筛选证据"><Funnel :size="15" /></button><button title="参数"><SlidersHorizontal :size="15" /></button></div>
      </aside>
    </div>
    <button v-if="error" class="toast" @click="error = ''">{{ error }}</button>
  </section>
</template>
