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

export interface ProjectProgress {
  stage: "PENDING" | "CONNECTING" | "DOWNLOADING" | "ARCHIVE_FALLBACK" | "EXTRACTING" | "SCANNING" | "ANALYZING" | "READY" | "FAILED" | string;
  percent: number;
  message: string | null;
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

export interface Metric {
  name: string;
  value: number;
}

export interface FileSummary {
  path: string;
  language: string;
  lineCount: number;
  byteSize: number;
}

export interface ProjectInsights {
  totalFiles: number;
  totalLines: number;
  totalBytes: number;
  languages: Metric[];
  topDirectories: Metric[];
  largestFiles: FileSummary[];
  entryPoints: FileSummary[];
  indexedAt: string | null;
}

export interface GraphNode {
  id: string;
  path: string;
  language: string;
  folder: string;
  lineCount: number;
  byteSize: number;
}

export interface GraphEdge {
  source: string;
  target: string;
  type: "import" | "package" | string;
  weight: number;
}

export interface ProjectGraph {
  nodes: GraphNode[];
  edges: GraphEdge[];
}

export interface ImpactNode {
  path: string;
  depth: number;
}

export interface ImpactAnalysis {
  sourcePath: string;
  risk: "LOW" | "MEDIUM" | "HIGH";
  score: number;
  dependencies: ImpactNode[];
  dependents: ImpactNode[];
}
