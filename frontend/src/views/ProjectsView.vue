<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from "vue";
import { useRouter } from "vue-router";
import { useProjectsStore } from "../stores/projects";
import type { ProjectStatus } from "../types";

const store = useProjectsStore();
const router = useRouter();
const submitting = ref(false);
const formError = ref("");
const form = reactive({ name: "", repositoryUrl: "", branch: "" });
let timer: number | undefined;

const readyCount = computed(() => store.projects.filter(project => project.status === "READY").length);
const indexedFiles = computed(() => store.projects.reduce((sum, project) => sum + project.fileCount, 0));

const statusLabel: Record<ProjectStatus, string> = {
  PENDING: "等待中", CLONING: "克隆中", INDEXING: "索引中", READY: "可分析", FAILED: "失败"
};

async function submit() {
  submitting.value = true;
  formError.value = "";
  try {
    const project = await store.create({ ...form });
    form.name = "";
    form.repositoryUrl = "";
    await router.push(`/projects/${project.id}`);
  } catch (reason) {
    formError.value = reason instanceof Error ? reason.message : "创建失败";
  } finally {
    submitting.value = false;
  }
}

onMounted(async () => {
  await store.load();
  timer = window.setInterval(() => void store.load(), 4000);
});
onBeforeUnmount(() => timer && window.clearInterval(timer));
</script>

<template>
  <section class="projects-page">
    <div class="hero-grid">
      <div class="hero-copy">
        <p class="eyebrow">CODEBASE INTELLIGENCE / 01</p>
        <h1>看懂陌生代码，<br><em>从证据开始。</em></h1>
        <p class="hero-description">导入公共 Git 仓库，让 Agent 搜索符号、读取文件、追踪调用关系，并输出带文件引用的变更影响分析。</p>
        <div class="metrics">
          <div><strong>{{ store.projects.length }}</strong><span>仓库</span></div>
          <div><strong>{{ readyCount }}</strong><span>可分析</span></div>
          <div><strong>{{ indexedFiles.toLocaleString() }}</strong><span>索引文件</span></div>
        </div>
      </div>

      <form class="import-panel" @submit.prevent="submit">
        <div class="panel-index">IMPORT / PUBLIC REPOSITORY</div>
        <h2>建立代码地图</h2>
        <label>项目名称<input v-model="form.name" required maxlength="120" placeholder="例如 Spring Petclinic" /></label>
        <label>HTTPS 仓库地址<input v-model="form.repositoryUrl" required type="url" placeholder="https://github.com/..." /></label>
        <label>目标分支（可选）<input v-model="form.branch" maxlength="120" placeholder="留空使用仓库默认分支" /></label>
        <p v-if="formError" class="form-error">{{ formError }}</p>
        <button class="primary-button" :disabled="submitting">{{ submitting ? "正在创建…" : "导入并建立索引" }}</button>
        <small>第一版仅接受 GitHub、GitLab、Gitee、Codeberg 公共仓库，不执行仓库代码。</small>
      </form>
    </div>

    <div class="section-heading">
      <div><p class="eyebrow">REPOSITORIES / 02</p><h2>代码库</h2></div>
      <span>{{ store.loading ? "同步中" : "已同步" }}</span>
    </div>

    <p v-if="store.error" class="form-error">{{ store.error }}</p>
    <div v-if="store.projects.length" class="project-grid">
      <RouterLink v-for="project in store.projects" :key="project.id" :to="`/projects/${project.id}`" class="project-card">
        <div class="card-top">
          <span class="repo-icon">&lt;/&gt;</span>
          <span class="status-pill" :class="project.status.toLowerCase()">{{ statusLabel[project.status] }}</span>
        </div>
        <h3>{{ project.name }}</h3>
        <p>{{ project.repositoryUrl }}</p>
        <div class="card-meta"><span>{{ project.branch || "默认分支" }}</span><span>{{ project.fileCount }} files</span></div>
        <div v-if="project.status !== 'READY'" class="progress-track"><i :class="project.status.toLowerCase()"></i></div>
        <small>{{ project.statusMessage || "等待分析" }}</small>
      </RouterLink>
    </div>
    <div v-else class="empty-state">还没有代码库，从上方导入一个公共仓库。</div>
  </section>
</template>
