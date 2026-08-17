CREATE TABLE projects (
    id UUID PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    repository_url TEXT NOT NULL,
    branch VARCHAR(120) NOT NULL,
    status VARCHAR(30) NOT NULL,
    status_message TEXT,
    file_count INTEGER NOT NULL DEFAULT 0,
    indexed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE source_files (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    path TEXT NOT NULL,
    language VARCHAR(40) NOT NULL,
    content TEXT NOT NULL,
    byte_size BIGINT NOT NULL,
    line_count INTEGER NOT NULL,
    sha256 VARCHAR(64) NOT NULL,
    indexed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT source_files_project_path_unique UNIQUE(project_id, path)
);

CREATE INDEX source_files_project_idx ON source_files(project_id);
CREATE INDEX source_files_path_idx ON source_files(project_id, path);

CREATE TABLE analysis_sessions (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    title VARCHAR(200) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE chat_messages (
    id UUID PRIMARY KEY,
    session_id UUID NOT NULL REFERENCES analysis_sessions(id) ON DELETE CASCADE,
    role VARCHAR(20) NOT NULL,
    content TEXT NOT NULL,
    citations TEXT NOT NULL DEFAULT '[]',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX chat_messages_session_idx ON chat_messages(session_id, created_at);

