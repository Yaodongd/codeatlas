<script setup lang="ts">
import { PhArrowLeft, PhArrowsClockwise, PhCheck, PhCube, PhWarningCircle } from "@phosphor-icons/vue";
import { computed } from "vue";
import type { Project, ProjectProgress } from "../types";

const props = defineProps<{ project: Project; progress: ProjectProgress | null }>();
defineEmits<{ retry: []; back: [] }>();

const steps = [
  { threshold: 8, label: "连接仓库", hint: "验证地址并连接代码托管平台" },
  { threshold: 24, label: "获取源码", hint: "浅克隆，失败时切换 GitHub 官方归档" },
  { threshold: 48, label: "展开文件", hint: "安全解压并过滤敏感与二进制文件" },
  { threshold: 58, label: "扫描代码", hint: "识别语言、入口、目录与源文件" },
  { threshold: 82, label: "分析关系", hint: "建立引用关系并写入只读索引" }
];
const percent = computed(() => props.progress?.percent ?? (props.project.status === "FAILED" ? 100 : 5));
const failed = computed(() => props.project.status === "FAILED");
</script>

<template>
  <main class="pipeline-screen">
    <section class="pipeline-card">
      <header>
        <div class="pipeline-symbol"><PhWarningCircle v-if="failed" :size="30" /><PhCube v-else :size="30" /></div>
        <div><p class="eyebrow">REPOSITORY PIPELINE</p><h1>{{ failed ? "仓库接入未完成" : "正在建立代码地图" }}</h1></div>
        <strong :class="{ failed }">{{ percent }}%</strong>
      </header>
      <div class="pipeline-progress"><i :style="{ width: `${percent}%` }"></i></div>
      <p class="pipeline-message">{{ project.statusMessage || progress?.message || "正在准备索引任务" }}</p>
      <ol>
        <li v-for="step in steps" :key="step.label" :class="{ done: percent > step.threshold, active: !failed && percent >= step.threshold - 12 && percent <= step.threshold + 18 }">
          <span><PhCheck v-if="percent > step.threshold" :size="13" /><i v-else></i></span>
          <div><b>{{ step.label }}</b><small>{{ step.hint }}</small></div>
        </li>
      </ol>
      <aside v-if="failed">
        <PhWarningCircle :size="18" />
        <div><b>为什么会失败？</b><p>服务器访问代码托管平台可能受网络限制。CodeAtlas 已自动尝试 Git 克隆和 GitHub 官方源码归档，重试不会删除项目配置。</p></div>
      </aside>
      <footer><button class="secondary" @click="$emit('back')"><PhArrowLeft :size="14" />返回项目列表</button><button v-if="failed" class="primary" @click="$emit('retry')"><PhArrowsClockwise :size="14" />重新尝试</button><span v-else>页面会自动更新，可以安全离开后再回来</span></footer>
    </section>
  </main>
</template>
