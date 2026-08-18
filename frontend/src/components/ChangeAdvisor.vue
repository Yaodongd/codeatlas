<script setup lang="ts">
import { PhBracketsCurly, PhCheck, PhFileCode, PhX } from "@phosphor-icons/vue";
import { ref } from "vue";
import type { SourceFile } from "../types";

defineProps<{ file: SourceFile }>();
const emit = defineEmits<{ close: []; generate: [intent: string, focus: string[]] }>();
const intent = ref("");
const focus = ref(["调用关系", "回归测试"]);
const options = ["调用关系", "接口兼容", "数据库", "缓存", "部署配置", "回归测试"];

function toggle(option: string) {
  focus.value = focus.value.includes(option) ? focus.value.filter(item => item !== option) : [...focus.value, option];
}
</script>

<template>
  <div class="advisor-overlay" @click.self="$emit('close')">
    <section class="advisor-dialog">
      <header><div><PhBracketsCurly :size="19" /><span><small>CHANGE ADVISOR</small><b>评估一次真实代码修改</b></span></div><button @click="$emit('close')"><PhX :size="16" /></button></header>
      <div class="advisor-step"><span>1</span><div><small>目标文件</small><b><PhFileCode :size="14" />{{ file.path }}</b></div></div>
      <label><span><b>2</b>准备修改什么？</span><textarea v-model="intent" rows="4" maxlength="1200" placeholder="例如：准备调整登录失败次数限制，并把计数放入 Redis。留空时将执行通用影响分析。"></textarea></label>
      <fieldset><legend><b>3</b>重点关注</legend><button v-for="option in options" :key="option" type="button" :class="{ active: focus.includes(option) }" @click="toggle(option)"><PhCheck v-if="focus.includes(option)" :size="12" />{{ option }}</button></fieldset>
      <aside><b>将生成</b><span>直接依赖</span><span>间接影响</span><span>风险说明</span><span>可执行测试清单</span></aside>
      <footer><button class="secondary" @click="$emit('close')">取消</button><button class="primary" @click="emit('generate', intent.trim(), focus)"><PhBracketsCurly :size="15" />开始分析</button></footer>
    </section>
  </div>
</template>
