import { defineStore } from "pinia";
import { ref } from "vue";
import { api } from "../api";
import type { Project } from "../types";

export const useProjectsStore = defineStore("projects", () => {
  const projects = ref<Project[]>([]);
  const loading = ref(false);
  const error = ref("");

  async function load() {
    loading.value = true;
    try {
      projects.value = await api.listProjects();
      error.value = "";
    } catch (reason) {
      error.value = reason instanceof Error ? reason.message : "无法加载项目";
    } finally {
      loading.value = false;
    }
  }

  async function create(payload: { name: string; repositoryUrl: string; branch: string }) {
    const project = await api.createProject(payload);
    projects.value.unshift(project);
    return project;
  }

  return { projects, loading, error, load, create };
});

