<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from "vue";
import { useRoute } from "vue-router";
import { api } from "../api";
import type { AnalysisSession, ChatMessage, Project, SourceFile } from "../types";

const route = useRoute();
const projectId = route.params.id as string;
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
let timer: number | undefined;

const filteredFiles = computed(() => {
  const value = fileFilter.value.toLowerCase().trim();
  return files.value.filter(file => !value || file.path.toLowerCase().includes(value));
});

const codeLines = computed(() => activeFile.value?.content.split(/\r?\n/) ?? []);

async function loadProject() {
  project.value = await api.getProject(projectId);
  if (project.value.status === "READY" && !files.value.length) await initializeWorkspace();
}

async function initializeWorkspace() {
  files.value = await api.listFiles(projectId);
  const sessions = await api.listSessions(projectId);
  session.value = sessions[0] || await api.createSession(projectId);
  messages.value = await api.listMessages(session.value.id);
}

async function openFile(path: string) {
  activeFile.value = await api.getFile(projectId, path);
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
    const answer = await api.ask(session.value.id, content);
    messages.value.push(answer);
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : "Agent 请求失败";
  } finally {
    asking.value = false;
    await nextTick();
    chatElement.value?.scrollTo({ top: chatElement.value.scrollHeight, behavior: "smooth" });
  }
}

onMounted(async () => {
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
  <section v-if="project" class="workspace-page">
    <div class="workspace-titlebar">
      <div><RouterLink to="/">代码库</RouterLink><span>/</span><strong>{{ project.name }}</strong></div>
      <div class="workspace-stats"><span>{{ project.branch }}</span><span>{{ project.fileCount }} files</span><span :class="['status-text', project.status.toLowerCase()]">{{ project.status }}</span></div>
    </div>

    <div v-if="project.status !== 'READY'" class="indexing-screen">
      <div class="scanner"></div>
      <p class="eyebrow">REPOSITORY PIPELINE</p>
      <h1>{{ project.statusMessage }}</h1>
      <p>CodeAtlas 正在安全地克隆并建立只读代码索引。</p>
      <div class="progress-track large"><i :class="project.status.toLowerCase()"></i></div>
      <p v-if="project.status === 'FAILED'" class="form-error">请检查仓库地址和分支是否公开存在。</p>
    </div>

    <div v-else class="workspace-grid">
      <aside class="file-panel">
        <div class="pane-heading"><strong>EXPLORER</strong><span>{{ filteredFiles.length }}</span></div>
        <input v-model="fileFilter" class="file-search" placeholder="筛选路径…" />
        <nav class="file-list">
          <button v-for="file in filteredFiles" :key="file.id" :class="{ active: activeFile?.path === file.path }" @click="openFile(file.path)">
            <span>{{ file.language.slice(0, 2).toUpperCase() }}</span>{{ file.path }}
          </button>
        </nav>
      </aside>

      <section class="code-panel">
        <div class="pane-heading"><strong>{{ activeFile?.path || "CODE VIEWER" }}</strong><span>{{ activeFile ? `${activeFile.lineCount} lines` : "选择文件" }}</span></div>
        <div v-if="activeFile" class="code-viewer">
          <div v-for="(line, index) in codeLines" :key="index" class="code-line"><span>{{ index + 1 }}</span><code>{{ line || " " }}</code></div>
        </div>
        <div v-else class="code-empty"><b>&lt;/&gt;</b><p>从左侧选择文件，或让 Agent 帮你找到入口。</p></div>
      </section>

      <aside class="agent-panel">
        <div class="pane-heading"><strong>ATLAS AGENT</strong><span class="live-dot"></span></div>
        <div ref="chatElement" class="chat-stream">
          <div v-if="!messages.length" class="agent-welcome">
            <span>⌁</span><h3>从一个问题开始</h3>
            <p>例如：这个项目的启动入口在哪里？用户登录会经过哪些类？</p>
          </div>
          <article v-for="message in messages" :key="message.id" :class="['message', message.role]">
            <small>{{ message.role === "user" ? "YOU" : "CODEATLAS" }}</small>
            <p>{{ message.content }}</p>
            <div v-if="message.citations.length" class="citations">
              <button v-for="path in message.citations" :key="path" @click="openFile(path)">{{ path }}</button>
            </div>
          </article>
          <article v-if="asking" class="message assistant thinking"><small>CODEATLAS</small><p>正在选择工具并检索证据…</p></article>
        </div>
        <form class="agent-input" @submit.prevent="ask">
          <textarea v-model="question" rows="3" maxlength="8000" placeholder="询问架构、调用链或变更影响…" @keydown.ctrl.enter.prevent="ask"></textarea>
          <div><span>Ctrl + Enter</span><button :disabled="asking || !question.trim()">发送 ↗</button></div>
        </form>
      </aside>
    </div>
    <div v-if="error" class="toast" @click="error = ''">{{ error }}</div>
  </section>
</template>
