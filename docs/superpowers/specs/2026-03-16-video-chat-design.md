# Video Chat App Design Specification

## Project Overview

A 1-on-1 video chat application with instant messaging capabilities. Users can register, add friends via user ID, send text/picture/voice messages, and make peer-to-peer voice and video calls.

## Tech Stack

| Layer | Technology |
|-------|------------|
| Android | Kotlin, Jetpack Compose, libwebrtc |
| Backend | Java, Spring Boot, WebSocket |
| Database | MySQL, Redis |
| Media | WebRTC P2P |

## Architecture

```
┌─────────────┐     WebSocket      ┌─────────────────┐
│  Android   │◄─────────────────►  │  Spring Boot   │
│  (Kotlin)  │     Signaling      │  Backend        │
│             │                    │  (Java)         │
│  libwebrtc │◄─────────────────►  │  + MySQL        │
│   (P2P)    │   Media Stream     │  + Redis        │
└─────────────┘                    └─────────────────┘
```

## Database Schema

### user

| Column | Type | Description |
|--------|------|-------------|
| id | BIGINT | Primary key, auto-increment |
| username | VARCHAR(50) | Unique username |
| password | VARCHAR(255) | Encrypted password |
| nickname | VARCHAR(50) | Display name |
| avatar | VARCHAR(255) | Avatar URL |
| signature | VARCHAR(255) | Personal signature |
| created_at | DATETIME | Creation timestamp |
| updated_at | DATETIME | Update timestamp |

### friend

| Column | Type | Description |
|--------|------|-------------|
| id | BIGINT | Primary key |
| user_id | BIGINT | User ID |
| friend_id | BIGINT | Friend ID |
| status | TINYINT | 0=pending, 1=friend |
| created_at | DATETIME | Creation timestamp |

### friend_request

| Column | Type | Description |
|--------|------|-------------|
| id | BIGINT | Primary key |
| from_user_id | BIGINT | Request sender |
| to_user_id | BIGINT | Request receiver |
| status | TINYINT | 0=pending, 1=accepted, 2=rejected |
| created_at | DATETIME | Creation timestamp |

### message

| Column | Type | Description |
|--------|------|-------------|
| id | BIGINT | Primary key |
| from_user_id | BIGINT | Sender ID |
| to_user_id | BIGINT | Receiver ID |
| type | TINYINT | 1=text, 2=image, 3=voice |
| content | TEXT | Message content or URL |
| is_read | TINYINT | 0=unread, 1=read |
| created_at | DATETIME | Creation timestamp |

### call_record

| Column | Type | Description |
|--------|------|-------------|
| id | BIGINT | Primary key |
| caller_id | BIGINT | Caller ID |
| callee_id | BIGINT | Callee ID |
| type | TINYINT | 1=voice, 2=video |
| status | 0=missed, 1=answered, 2=rejected |
| duration | INT | Call duration in seconds |
| created_at | DATETIME | Creation timestamp |

## API Endpoints

### User

| Method | Path | Description |
|--------|------|-------------|
| POST | /api/user/register | Register new user |
| POST | /api/user/login | Login, returns JWT |
| GET | /api/user/{id} | Get user info |
| PUT | /api/user | Update user info |

### Friend

| Method | Path | Description |
|--------|------|-------------|
| GET | /api/user/search?username=xxx | Search user by ID |
| POST | /api/friend/request | Send friend request |
| GET | /api/friend/request | Get friend request list |
| PUT | /api/friend/request/{id} | Accept/reject request |
| GET | /api/friend/list | Get friend list |
| DELETE | /api/friend/{id} | Remove friend |

### Message

| Method | Path | Description |
|--------|------|-------------|
| GET | /api/message/list/{friendId} | Get message history |
| POST | /api/message/send | Send message |
| PUT | /api/message/read/{friendId} | Mark as read |

### Call

| Method | Path | Description |
|--------|------|-------------|
| POST | /api/call/invite | Initiate call |
| PUT | /api/call/response | Accept/reject call |
| GET | /api/call/history | Get call history |

### WebSocket

| Endpoint | Description |
|----------|-------------|
| /ws | Signaling server (STOMP) |
| /topic/call/{userId} | Call invitation push |
| /queue/message/{userId} | Message push |

## WebRTC Signaling Flow

```
User A → Server : Call invite {to: B, type: video, callId}
Server → User B : Push call invitation {from: A, type: video, callId}
User B → Server : Response {callId, accept: true/false}
Server → User A : B's response

User A → Server : Send SDP Offer
Server → User B : Forward SDP Offer
User B → Server : Send SDP Answer
Server → User A : Forward SDP Answer

User A → Server : Send ICE Candidate
Server → User B : Forward ICE Candidate

P2P connection established → Call in progress
Either party hangs up → Notify the other party
```

## Module Design

### User Module

- Registration (username, password, avatar)
- Login with JWT token
- Profile update (avatar, nickname, signature)
- Get user info by ID

### Friend Module

- Search user by username
- Send friend request
- Accept/reject friend request
- Remove friend
- Get friend list
- Online status tracking

### Message Module

- Text messages with real-time push
- Image messages (upload, send URL)
- Voice messages (record, upload, send URL)
- Message history storage
- Read/unread status

### Call Module

- Voice call (WebRTC P2P)
- Video call (WebRTC P2P)
- Call invitation handling
- Call status (ringing, in-progress, ended)
- Device switching (speaker/microphone/camera)

## Constraints

- User scale: thousands to tens of thousands
- Call mode: P2P direct connection
- Friend addition: search by user ID
- Open source only, no third-party IM SDKs
