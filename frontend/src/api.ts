import type {
  AnalysisSession, ChatMessage, ImpactAnalysis, Project, ProjectGraph, ProjectInsights, ProjectProgress, SourceFile
} from "./types";

const basePath = import.meta.env.BASE_URL.replace(/\/$/, "");
const apiBase = `${basePath}/api`;

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`${apiBase}${path}`, {
    ...init,
    headers: { "content-type": "application/json", ...init?.headers }
  });
  if (!response.ok) {
    const payload = await response.json().catch(() => null);
    throw new Error(payload?.message || `请求失败 (${response.status})`);
  }
  if (response.status === 204) return undefined as T;
  return response.json() as Promise<T>;
}

export const api = {
  listProjects: () => request<Project[]>("/projects"),
  getProject: (id: string) => request<Project>(`/projects/${id}`),
  getProjectProgress: (id: string) => request<ProjectProgress>(`/projects/${id}/progress`),
  createProject: (payload: { name: string; repositoryUrl: string; branch: string }) =>
    request<Project>("/projects", { method: "POST", body: JSON.stringify(payload) }),
  listFiles: (id: string) => request<SourceFile[]>(`/projects/${id}/files`),
  getFile: (id: string, path: string) =>
    request<SourceFile>(`/projects/${id}/file?path=${encodeURIComponent(path)}`),
  search: (id: string, query: string) =>
    request<SourceFile[]>(`/projects/${id}/search?query=${encodeURIComponent(query)}`),
  getInsights: (id: string) => request<ProjectInsights>(`/projects/${id}/insights`),
  getGraph: (id: string) => request<ProjectGraph>(`/projects/${id}/graph`),
  getImpact: (id: string, path: string) =>
    request<ImpactAnalysis>(`/projects/${id}/impact?path=${encodeURIComponent(path)}`),
  reindexProject: (id: string) => request<Project>(`/projects/${id}/reindex`, { method: "POST" }),
  deleteProject: (id: string) => request<void>(`/projects/${id}`, { method: "DELETE" }),
  listSessions: (projectId: string) =>
    request<AnalysisSession[]>(`/projects/${projectId}/sessions`),
  createSession: (projectId: string, title = "新的代码分析") =>
    request<AnalysisSession>(`/projects/${projectId}/sessions`, {
      method: "POST",
      body: JSON.stringify({ title })
    }),
  listMessages: (sessionId: string) => request<ChatMessage[]>(`/sessions/${sessionId}/messages`),
  ask: (sessionId: string, content: string) =>
    request<ChatMessage>(`/sessions/${sessionId}/messages`, {
      method: "POST",
      body: JSON.stringify({ content })
    }),
  renameSession: (sessionId: string, title: string) =>
    request<AnalysisSession>(`/sessions/${sessionId}`, {
      method: "PATCH",
      body: JSON.stringify({ title })
    }),
  deleteSession: (sessionId: string) => request<void>(`/sessions/${sessionId}`, { method: "DELETE" })
};
