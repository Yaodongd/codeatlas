<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from "vue";
import { useRouter } from "vue-router";
import {
  PhArrowRight, PhCheckCircle, PhClockCountdown, PhCode, PhDatabase,
  PhGitBranch, PhGithubLogo, PhMagnifyingGlass, PhPlus, PhShieldCheck, PhSparkle, PhWarningCircle
} from "@phosphor-icons/vue";
import ProjectOrbit from "../components/ProjectOrbit.vue";
import { useProjectsStore } from "../stores/projects";
import type { ProjectStatus } from "../types";

const store = useProjectsStore();
const router = useRouter();
const submitting = ref(false);
const formError = ref("");
const search = ref("");
const form = reactive({ name: "", repositoryUrl: "", branch: "" });
const lastSuggestedName = ref("");
let timer: number | undefined;

const examples = [
  { name: "Spring PetClinic", repositoryUrl: "https://github.com/spring-projects/spring-petclinic.git", branch: "main" },
  { name: "Pinia", repositoryUrl: "https://github.com/vuejs/pinia.git", branch: "v3" },
  { name: "CodeAtlas", repositoryUrl: "https://github.com/Yaodongd/codeatlas.git", branch: "master" }
];

const readyCount = computed(() => store.projects.filter(project => project.status === "READY").length);
const indexedFiles = computed(() => store.projects.reduce((sum, project) => sum + project.fileCount, 0));
const filteredProjects = computed(() => {
  const value = search.value.trim().toLowerCase();
  return store.projects.filter(project => !value || `${project.name} ${project.repositoryUrl}`.toLowerCase().includes(value));
});
const repositoryState = computed(() => {
  if (!form.repositoryUrl.trim()) return { valid: false, message: "粘贴公开 HTTPS Git 地址" };
  try {
    const url = new URL(form.repositoryUrl.trim());
    if (url.protocol !== "https:") return { valid: false, message: "仅支持 HTTPS 地址" };
    if (!url.pathname.split("/").filter(Boolean).length || url.pathname.split("/").filter(Boolean).length < 2) {
      return { valid: false, message: "地址需要包含 owner/repository" };
    }
    return { valid: true, message: `${url.hostname} · 地址格式有效` };
  } catch {
    return { valid: false, message: "仓库地址格式不正确" };
  }
});
const statusLabel: Record<ProjectStatus, string> = {
  PENDING: "等待中", CLONING: "克隆中", INDEXING: "索引中", READY: "可分析", FAILED: "失败"
};

async function submit() {
  if (!repositoryState.value.valid) {
    formError.value = repositoryState.value.message;
    return;
  }
  submitting.value = true;
  formError.value = "";
  try {
    const project = await store.create({ ...form });
    form.name = "";
    form.repositoryUrl = "";
    form.branch = "";
    await router.push(`/projects/${project.id}`);
  } catch (reason) {
    formError.value = reason instanceof Error ? reason.message : "创建失败";
  } finally {
    submitting.value = false;
  }
}

function useExample(example: typeof examples[number]) {
  Object.assign(form, example);
  lastSuggestedName.value = example.name;
  formError.value = "";
}

function openProject(id: string) {
  void router.push(`/projects/${id}`);
}

onMounted(async () => {
  await store.load();
  timer = window.setInterval(() => void store.load(), 4000);
});
onBeforeUnmount(() => timer && window.clearInterval(timer));

watch(() => form.repositoryUrl, value => {
  try {
    const path = new URL(value).pathname.split("/").filter(Boolean);
    const suggestion = path.at(-1)?.replace(/\.git$/i, "") || "";
    if (suggestion && (!form.name || form.name === lastSuggestedName.value)) {
      form.name = suggestion;
      lastSuggestedName.value = suggestion;
    }
  } catch {
    // Keep the user's manually entered project name while the URL is incomplete.
  }
});
</script>

<template>
  <section class="command-home">
    <div class="home-statusbar">
      <span><i></i> Repository intelligence online</span>
      <span>{{ new Date().toLocaleDateString('zh-CN') }} · READ ONLY</span>
    </div>

    <div class="home-hero">
      <div class="home-intro">
        <p class="eyebrow">CODEBASE OBSERVATORY / LIVE</p>
        <h1>从真实代码中<br><em>找到证据与关系</em></h1>
        <p>导入公开 Git 仓库，CodeAtlas 会建立只读文件索引。你可以查看真实代码、探索依赖图谱，并让 Agent 基于文件引用分析变更影响。</p>
        <div class="home-metrics">
          <div><PhDatabase :size="17" /><span><b>{{ store.projects.length }}</b>已接入仓库</span></div>
          <div><PhCheckCircle :size="17" /><span><b>{{ readyCount }}</b>可立即分析</span></div>
          <div><PhCode :size="17" /><span><b>{{ indexedFiles.toLocaleString() }}</b>真实文件</span></div>
        </div>
      </div>

      <section class="orbit-console">
        <header><div><PhSparkle :size="15" weight="fill" />项目星图</div><span>{{ store.projects.length }} NODES</span></header>
        <ProjectOrbit :projects="store.projects" @select="openProject" />
        <footer><span>拖拽旋转 · 滚轮缩放 · 点击打开真实项目</span><i></i></footer>
      </section>
    </div>

    <div class="home-workbench">
      <section class="repository-console">
        <header class="console-heading">
          <div><p class="eyebrow">REPOSITORIES</p><h2>真实代码仓库</h2></div>
          <label><PhMagnifyingGlass :size="15" /><input v-model="search" placeholder="搜索仓库" /></label>
        </header>
        <p v-if="store.error" class="form-error">{{ store.error }}</p>
        <div v-if="filteredProjects.length" class="repository-table">
          <button v-for="project in filteredProjects" :key="project.id" @click="openProject(project.id)">
            <span class="repo-type"><PhGithubLogo :size="18" /></span>
            <span class="repo-main"><strong>{{ project.name }}</strong><small>{{ project.status === 'READY' ? project.repositoryUrl : project.statusMessage }}</small></span>
            <span class="repo-branch"><PhGitBranch :size="13" />{{ project.branch || "默认分支" }}</span>
            <span class="repo-files">{{ project.fileCount.toLocaleString() }} files</span>
            <span :class="['repo-status', project.status.toLowerCase()]">
              <PhWarningCircle v-if="project.status === 'FAILED'" :size="13" />
              <PhClockCountdown v-else-if="project.status !== 'READY'" :size="13" />
              <PhCheckCircle v-else :size="13" />{{ statusLabel[project.status] }}
            </span>
            <PhArrowRight :size="16" />
          </button>
        </div>
        <div v-else class="command-empty"><PhCode :size="30" /><strong>还没有可显示的仓库</strong><span>使用右侧控制台导入第一个真实项目</span></div>
      </section>

      <form class="import-console" @submit.prevent="submit">
        <header><span>NEW REPOSITORY</span><PhPlus :size="17" /></header>
        <h2>建立代码地图</h2>
        <p>粘贴公开仓库地址即可。分支可以留空，CodeAtlas 不会执行仓库里的代码。</p>
        <div class="example-repositories"><span>快速体验</span><button v-for="example in examples" :key="example.repositoryUrl" type="button" @click="useExample(example)">{{ example.name }}</button></div>
        <label>项目名称<input v-model="form.name" required maxlength="120" placeholder="例如：CodeAtlas" /></label>
        <label>HTTPS 仓库地址<input v-model="form.repositoryUrl" required type="url" placeholder="https://github.com/owner/repo" /><small :class="{ valid: repositoryState.valid }"><PhShieldCheck :size="12" />{{ repositoryState.message }}</small></label>
        <details class="branch-options"><summary><PhGitBranch :size="13" />高级选项：指定分支</summary><label>目标分支<input v-model="form.branch" maxlength="120" placeholder="留空自动使用默认分支" /></label></details>
        <p v-if="formError" class="form-error">{{ formError }}</p>
        <button class="command-submit" :disabled="submitting || !repositoryState.valid"><PhPlus :size="15" />{{ submitting ? "正在创建索引任务…" : "导入并开始分析" }}</button>
        <small>GitHub 直连失败时会自动切换 GitHub 官方源码归档；同时支持 GitLab、Gitee、Codeberg 公共仓库。</small>
      </form>
    </div>
  </section>
</template>
