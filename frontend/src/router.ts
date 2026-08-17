import { createRouter, createWebHistory } from "vue-router";
import ProjectsView from "./views/ProjectsView.vue";
import WorkspaceView from "./views/WorkspaceView.vue";

export default createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    { path: "/", name: "projects", component: ProjectsView },
    { path: "/projects/:id", name: "workspace", component: WorkspaceView }
  ]
});

