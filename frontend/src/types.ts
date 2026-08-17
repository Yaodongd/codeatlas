export type ProjectStatus = "PENDING" | "CLONING" | "INDEXING" | "READY" | "FAILED";

export interface Project {
  id: string;
  name: string;
  repositoryUrl: string;
  branch: string;
  status: ProjectStatus;
  statusMessage: string | null;
  fileCount: number;
  indexedAt: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface SourceFile {
  id: string;
  projectId: string;
  path: string;
  language: string;
  content: string;
  byteSize: number;
  lineCount: number;
  sha256: string;
}

export interface AnalysisSession {
  id: string;
  projectId: string;
  title: string;
  createdAt: string;
  updatedAt: string;
}

export interface ChatMessage {
  id: string;
  sessionId: string;
  role: "user" | "assistant";
  content: string;
  citations: string[];
  createdAt: string;
}

