# WebRTC 视频通话修复验证计划

## 问题描述
用户A发起视频通话，用户B接通后，双方都只能看到自己本地视频，看不到对方的视频（画面黑色）。

## 根本原因分析
通过分析日志和代码，发现以下问题：
1. SDP方向属性设置不正确，两端都被设置为`a=recvonly`模式
2. WebRTC收发器方向未正确设置为SEND_RECV
3. ICE传输策略可以优化以提高连接成功率

## 已实施的修复
1. 在createOffer和createAnswer方法中添加了收发器方向设置：
   ```kotlin
   peerConnection?.transceivers?.forEach { transceiver ->
       if (transceiver.sender?.track() != null) {
           transceiver.direction = RtpTransceiver.RtpTransceiverDirection.SEND_RECV
       }
   }
   ```

2. 在添加音视频轨道时正确设置传输方向：
   ```kotlin
   val sender = peerConnection?.addTrack(track, listOf("stream"))
   peerConnection?.transceivers?.find { it.sender == sender }?.direction = RtpTransceiver.RtpTransceiverDirection.SEND_RECV
   ```

3. 优化PeerConnection配置：
   ```kotlin
   iceTransportsType = PeerConnection.IceTransportsType.ALL
   ```

4. 在onTrack回调中启用了音频轨道：
   ```kotlin
   track.setEnabled(true)
   ```

## 测试步骤

### 1. 环境准备
- 确保Signaling Server正在运行
- 准备两个设备或模拟器用于测试
- 确保两个设备都有摄像头权限

### 2. 基本功能测试
1. 在设备A上启动应用，登录账户
2. 在设备B上启动应用，登录另一个账户
3. 设备A发起视频通话给设备B
4. 设备B接受视频通话
5. 观察两个设备是否都能看到对方的视频流

### 3. 高级测试
1. 测试不同的网络环境（同一局域网、不同网络）
2. 测试音频是否正常同步
3. 测试通话中断后的重新连接
4. 测试摄像头切换功能
5. 测试静音/取消静音功能

### 4. 验证指标
- 双方都能看到对方的视频流（而非黑色画面）
- 视频质量正常，无明显延迟
- 音频同步正常
- 连接建立时间合理（应在5秒内）

## 预期结果
修复后，用户A和用户B应该能够互相看到对方的视频流，而不仅仅是本地视频。

## 日志检查要点
在测试过程中，需要特别关注以下日志信息：

1. SDP方向属性应显示为sendrecv而非recvonly：
   ```
   SDP direction attributes: a=sendrecv
   ```

2. 收发器方向应正确设置：
   ```
   Audio transceiver direction set to SEND_RECV
   Video transceiver direction set to SEND_RECV
   ```

3. 视频端口应大于0：
   ```
   Remote video port in SDP: 9
   ```

4. 应能收到远程视频轨道：
   ```
   ========== VIDEO TRACK RECEIVED IN onTrack ==========
   ```

## 回滚计划
如果修复引入新问题，可以恢复到原始版本的WebRTCManager.kt文件。