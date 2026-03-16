# Video Chat App Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan.

**Goal:** Build a complete 1-on-1 video chat Android app with instant messaging, using Kotlin (Android) + Java (Spring Boot backend)

**Architecture:** 
- Android: Kotlin + Jetpack Compose + libwebrtc for P2P video/voice calls
- Backend: Spring Boot + WebSocket (STOMP) for signaling + REST APIs
- Database: MySQL (persistent data) + Redis (sessions/caching)

**Tech Stack:**
- Android: Kotlin 1.9, Jetpack Compose, libwebrtc
- Backend: Java 17, Spring Boot 3.x, MyBatis Plus

---

## Project Structure

```
video-chat/
├── backend/                    # Spring Boot Backend
│   └── src/main/java/com/videochat/
│       ├── config/             # Configuration
│       ├── controller/         # REST controllers
│       ├── service/           # Business logic
│       ├── mapper/            # MyBatis mappers
│       ├── model/             # Entity classes
│       └── websocket/         # WebSocket handlers
│
└── android/                   # Android App
    └── app/src/main/java/com/videochat/
        ├── data/              # Repository, API
        ├── ui/                # Compose screens
        └── webrtc/            # WebRTC implementation
```

---

## Chunk 1: Backend - Project Setup & User Module

### Task 1.1: Initialize Spring Boot Project

**Files to create:**
- `backend/pom.xml`
- `backend/src/main/resources/application.yml`
- `backend/src/main/java/com/videochat/VideoChatApplication.java`

**Step 1: Create pom.xml**
```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.2.0</version>
    </parent>
    
    <groupId>com.videochat</groupId>
    <artifactId>video-chat-backend</artifactId>
    <version>1.0.0</version>
    
    <properties>
        <java.version>17</java.version>
    </properties>
    
    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-websocket</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-security</artifactId>
        </dependency>
        <dependency>
            <groupId>com.mysql</groupId>
            <artifactId>mysql-connector-j</artifactId>
        </dependency>
        <dependency>
            <groupId>com.baomidou</groupId>
            <artifactId>mybatis-plus-spring-boot3-starter</artifactId>
            <version>3.5.5</version>
        </dependency>
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-api</artifactId>
            <version>0.12.3</version>
        </dependency>
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>
    </dependencies>
</project>
```

**Step 2: Create application.yml**
```yaml
server:
  port: 8080
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/video_chat?useUnicode=true&characterEncoding=utf8
    username: root
    password: root
    driver-class-name: com.mysql.cj.jdbc.Driver
mybatis-plus:
  configuration:
    map-underscore-to-camel-case: true
jwt:
  secret: video-chat-secret-key-2024
  expiration: 86400000
```

**Step 3: Create VideoChatApplication.java**
```java
package com.videochat;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.videochat.mapper")
public class VideoChatApplication {
    public static void main(String[] args) {
        SpringApplication.run(VideoChatApplication.class, args);
    }
}
```

**Step 4: Create database**
```sql
CREATE DATABASE video_chat DEFAULT CHARACTER SET utf8mb4;
```

---

### Task 1.2: User Entity & Mapper

**Files:**
- `backend/src/main/java/com/videochat/model/User.java`
- `backend/src/main/java/com/videochat/mapper/UserMapper.java`

```java
// User.java
package com.videochat.model;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("user")
public class User {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String username;
    private String password;
    private String nickname;
    private String avatar;
    private String signature;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
```

```java
// UserMapper.java
package com.videochat.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.videochat.model.User;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserMapper extends BaseMapper<User> {}
```

---

### Task 1.3: User Service & Controller

**Files:**
- `backend/src/main/java/com/videochat/dto/LoginRequest.java`
- `backend/src/main/java/com/videochat/dto/RegisterRequest.java`
- `backend/src/main/java/com/videochat/dto/UserDTO.java`
- `backend/src/main/java/com/videochat/service/UserService.java`
- `backend/src/main/java/com/videochat/service/impl/UserServiceImpl.java`
- `backend/src/main/java/com/videochat/service/JwtService.java`
- `backend/src/main/java/com/videochat/service/impl/JwtServiceImpl.java`
- `backend/src/main/java/com/videochat/config/SecurityConfig.java`
- `backend/src/main/java/com/videochat/controller/UserController.java`

---

## Chunk 2: Backend - Friend & Message Module

### Task 2.1: Friend Entity & Service

**Files:**
- `backend/src/main/java/com/videochat/model/Friend.java`
- `backend/src/main/java/com/videochat/model/FriendRequest.java`
- `backend/src/main/java/com/videochat/mapper/FriendMapper.java`
- `backend/src/main/java/com/videochat/mapper/FriendRequestMapper.java`
- `backend/src/main/java/com/videochat/service/FriendService.java`
- `backend/src/main/java/com/videochat/service/impl/FriendServiceImpl.java`
- `backend/src/main/java/com/videochat/controller/FriendController.java`

---

### Task 2.2: Message Entity & Service

**Files:**
- `backend/src/main/java/com/videochat/model/Message.java`
- `backend/src/main/java/com/videochat/mapper/MessageMapper.java`
- `backend/src/main/java/com/videochat/service/MessageService.java`
- `backend/src/main/java/com/videochat/service/impl/MessageServiceImpl.java`
- `backend/src/main/java/com/videochat/controller/MessageController.java`

---

## Chunk 3: Backend - WebSocket & Call Module

### Task 3.1: WebSocket Configuration

**Files:**
- `backend/src/main/java/com/videochat/config/WebSocketConfig.java`
- `backend/src/main/java/com/videochat/websocket/WebSocketController.java`

---

### Task 3.2: Call Record Service

**Files:**
- `backend/src/main/java/com/videochat/model/CallRecord.java`
- `backend/src/main/java/com/videochat/mapper/CallRecordMapper.java`
- `backend/src/main/java/com/videochat/service/CallService.java`
- `backend/src/main/java/com/videochat/service/impl/CallServiceImpl.java`
- `backend/src/main/java/com/videochat/controller/CallController.java`

---

## Chunk 4: Android - Project Setup

### Task 4.1: Initialize Android Project

**Files:**
- `android/settings.gradle.kts`
- `android/build.gradle.kts`
- `android/app/build.gradle.kts`
- `android/app/src/main/AndroidManifest.xml`
- `android/app/src/main/java/com/videochat/VideoChatApp.kt`

---

### Task 4.2: Data Layer

**Files:**
- `android/app/src/main/java/com/videochat/data/api/ApiService.kt`
- `android/app/src/main/java/com/videochat/data/api/RetrofitClient.kt`
- `android/app/src/main/java/com/videochat/data/model/User.kt`
- `android/app/src/main/java/com/videochat/data/model/Message.kt`
- `android/app/src/main/java/com/videochat/data/local/PreferencesManager.kt`
- `android/app/src/main/java/com/videochat/data/repository/AuthRepository.kt`
- `android/app/src/main/java/com/videochat/data/repository/FriendRepository.kt`
- `android/app/src/main/java/com/videochat/data/repository/MessageRepository.kt`

---

## Chunk 5: Android - UI & WebRTC

### Task 5.1: UI Screens

**Files:**
- `android/app/src/main/java/com/videochat/ui/theme/Theme.kt`
- `android/app/src/main/java/com/videochat/ui/navigation/Navigation.kt`
- `android/app/src/main/java/com/videochat/ui/screens/LoginScreen.kt`
- `android/app/src/main/java/com/videochat/ui/screens/RegisterScreen.kt`
- `android/app/src/main/java/com/videochat/ui/screens/HomeScreen.kt`
- `android/app/src/main/java/com/videochat/ui/screens/ChatScreen.kt`
- `android/app/src/main/java/com/videochat/ui/screens/CallScreen.kt`
- `android/app/src/main/java/com/videochat/ui/MainActivity.kt`

---

### Task 5.2: WebRTC Manager

**Files:**
- `android/app/src/main/java/com/videochat/webrtc/WebRTCManager.kt`

---

## Summary

This plan covers:
1. **Backend**: Spring Boot with User, Friend, Message, Call modules
2. **Android**: Kotlin + Compose UI with WebRTC integration

**Next Steps:**
- Start implementing backend first
- Then implement Android app
- Test integration
