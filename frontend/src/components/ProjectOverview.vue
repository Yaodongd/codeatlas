<script setup lang="ts">
import { PhArrowRight, PhBracketsCurly, PhCode, PhFiles, PhGitBranch, PhPath, PhSparkle } from "@phosphor-icons/vue";
import type { Project, ProjectGraph, ProjectInsights } from "../types";

defineProps<{ project: Project; insights: ProjectInsights | null; graph: ProjectGraph | null }>();
defineEmits<{ explain: []; risks: []; explore: []; openFile: [path: string]; change: [] }>();

function formatNumber(value = 0) {
  return value.toLocaleString("zh-CN");
}
</script>

<template>
  <section class="overview-stage">
    <header class="overview-hero">
      <div><p class="eyebrow">PROJECT OVERVIEW</p><h1>先看懂 {{ project.name }}，<br><em>再开始修改代码</em></h1><p>下面的信息全部来自刚刚建立的真实文件索引。你可以从项目入口、代码关系或一次具体修改开始。</p></div>
      <div class="overview-status"><i></i><span>索引完成</span><b>{{ project.fileCount }}</b><small>个可分析文件</small></div>
    </header>
    <div class="overview-metrics">
      <article><PhFiles :size="18" /><span><small>FILES</small><b>{{ formatNumber(insights?.totalFiles) }}</b><em>索引文件</em></span></article>
      <article><PhCode :size="18" /><span><small>LINES</small><b>{{ formatNumber(insights?.totalLines) }}</b><em>代码与配置行</em></span></article>
      <article><PhPath :size="18" /><span><small>RELATIONS</small><b>{{ formatNumber(graph?.edges.length) }}</b><em>内部代码关系</em></span></article>
      <article><PhGitBranch :size="18" /><span><small>BRANCH</small><b>{{ project.branch || "default" }}</b><em>当前分析分支</em></span></article>
    </div>
    <div class="overview-grid">
      <article class="overview-actions">
        <p class="eyebrow">START HERE</p><h2>你想先完成什么？</h2>
        <button @click="$emit('explain')"><span><PhSparkle :size="18" weight="fill" /><b>带我理解这个项目</b><small>让 Agent 总结架构、入口和关键模块</small></span><PhArrowRight :size="16" /></button>
        <button @click="$emit('explore')"><span><PhPath :size="18" /><b>探索代码关系</b><small>从真实 import 和目录关系开始浏览</small></span><PhArrowRight :size="16" /></button>
        <button @click="$emit('change')"><span><PhBracketsCurly :size="18" /><b>评估一次代码修改</b><small>选择目标文件、修改意图和关注范围</small></span><PhArrowRight :size="16" /></button>
      </article>
      <article class="overview-entrypoints">
        <header><div><p class="eyebrow">ENTRY POINTS</p><h2>建议从这些文件开始</h2></div><span>{{ insights?.entryPoints.length || 0 }}</span></header>
        <button v-for="file in insights?.entryPoints.slice(0, 7)" :key="file.path" @click="$emit('openFile', file.path)"><PhCode :size="14" /><span><b>{{ file.path.split('/').at(-1) }}</b><small>{{ file.path }}</small></span><PhArrowRight :size="14" /></button>
        <p v-if="!insights?.entryPoints.length">暂未识别到常见入口文件，可以从左侧文件树开始。</p>
      </article>
      <article class="overview-stack">
        <p class="eyebrow">CODE PROFILE</p><h2>主要语言</h2>
        <div v-for="item in insights?.languages.slice(0, 6)" :key="item.name"><span>{{ item.name }}</span><i><b :style="{ width: `${Math.max(8, item.value / (insights?.languages[0]?.value || 1) * 100)}%` }"></b></i><em>{{ item.value }}</em></div>
      </article>
    </div>
  </section>
</template>
