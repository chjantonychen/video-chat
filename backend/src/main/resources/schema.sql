-- SQLite Database (auto-created as video_chat.db)

-- User table
CREATE TABLE IF NOT EXISTS user (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    username TEXT NOT NULL UNIQUE,
    password TEXT NOT NULL,
    nickname TEXT,
    avatar TEXT,
    signature TEXT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- Friend table
CREATE TABLE IF NOT EXISTS friend (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id INTEGER NOT NULL,
    friend_id INTEGER NOT NULL,
    status INTEGER DEFAULT 1,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_friend_user_id ON friend(user_id);
CREATE INDEX IF NOT EXISTS idx_friend_friend_id ON friend(friend_id);

-- Friend request table
CREATE TABLE IF NOT EXISTS friend_request (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    from_user_id INTEGER NOT NULL,
    to_user_id INTEGER NOT NULL,
    status INTEGER DEFAULT 0,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_friend_request_from ON friend_request(from_user_id);
CREATE INDEX IF NOT EXISTS idx_friend_request_to ON friend_request(to_user_id);

-- Message table
CREATE TABLE IF NOT EXISTS message (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    from_user_id INTEGER NOT NULL,
    to_user_id INTEGER NOT NULL,
    type INTEGER NOT NULL,
    content TEXT,
    is_read INTEGER DEFAULT 0,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_message_from ON message(from_user_id);
CREATE INDEX IF NOT EXISTS idx_message_to ON message(to_user_id);
CREATE INDEX IF NOT EXISTS idx_message_created ON message(created_at);

-- Call record table
CREATE TABLE IF NOT EXISTS call_record (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    caller_id INTEGER NOT NULL,
    callee_id INTEGER NOT NULL,
    type INTEGER NOT NULL,
    status INTEGER DEFAULT 0,
    duration INTEGER DEFAULT 0,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_call_caller ON call_record(caller_id);
CREATE INDEX IF NOT EXISTS idx_call_callee ON call_record(callee_id);
CREATE INDEX IF NOT EXISTS idx_call_created ON call_record(created_at);
