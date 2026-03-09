package com.cw.im.server.consumer;

import com.cw.im.common.protocol.IMMessage;
import com.cw.im.kafka.MessageListener;
import com.cw.im.redis.OnlineStatusService;
import com.cw.im.server.channel.ChannelManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 群聊消息推送消费监听器
 *
 * <p>消费群聊推送消息Topic，推送给群组在线成员</p>
 *
 * <h3>消费逻辑</h3>
 * <ol>
 *     <li>从Kafka拉取消息</li>
 *     <li>反序列化消息为IMMessage对象</li>
 *     <li>获取群组ID</li>
 *     <li>查询群组成员在线状态（需要与业务系统配合）</li>
 *     <li>推送给所有在线成员</li>
 *     <li>记录推送统计</li>
 * </ol>
 *
 * <h3>注意</h3>
 * <p>此Consumer假设业务系统已经完成了群组成员扩散，
 * 为每个成员生成了单独的推送消息。
 * 如果业务系统只发送一条群聊消息，需要在这里实现成员查询和扩散逻辑。</p>
 *
 * @author cw
 * @since 1.0.0
 */
@Slf4j
public class GroupMessagePushConsumer implements MessageListener {

    private final ChannelManager channelManager;
    private final OnlineStatusService onlineStatusService;
    private final ObjectMapper objectMapper;

    /**
     * 推送成功计数
     */
    private long pushSuccessCount = 0;

    /**
     * 推送失败计数
     */
    private long pushFailureCount = 0;

    /**
     * 成员离线跳过计数
     */
    private long offlineSkipCount = 0;

    /**
     * 群组在线成员缓存（groupId -> 成员ID集合）
     * <p>注意：这里只是示例，实际应该从业务系统或Redis获取群组成员列表</p>
     */
    private final ConcurrentHashMap<Long, Set<Long>> groupMembersCache = new ConcurrentHashMap<>();

    /**
     * 构造函数
     *
     * @param channelManager         Channel管理器
     * @param onlineStatusService    在线状态服务
     */
    public GroupMessagePushConsumer(ChannelManager channelManager, OnlineStatusService onlineStatusService) {
        this.channelManager = channelManager;
        this.onlineStatusService = onlineStatusService;
        this.objectMapper = new ObjectMapper();
        log.info("GroupMessagePushConsumer 初始化完成");
    }

    @Override
    public void onMessage(ConsumerRecord<String, String> record) {
        try {
            // 1. 反序列化消息
            IMMessage message = parseMessage(record.value());
            if (message == null || !message.isValid()) {
                log.warn("无效消息，跳过推送: topic={}, partition={}, offset={}",
                        record.topic(), record.partition(), record.offset());
                pushFailureCount++;
                return;
            }

            String msgId = message.getMsgId();
            Long toUserId = message.getTo();
            Long groupId = (Long) message.getBody().getExtras().get("groupId");

            log.debug("开始处理群聊推送消息: msgId={}, groupId={}, toUserId={}, topic={}, partition={}, offset={}",
                    msgId, groupId, toUserId, record.topic(), record.partition(), record.offset());

            // 2. 检查是否是群聊消息
            if (groupId == null && toUserId == null) {
                log.warn("群聊推送消息缺少groupId和toUserId: msgId={}", msgId);
                pushFailureCount++;
                return;
            }

            // 3. 如果有toUserId，说明业务系统已经扩散，直接推送给指定用户
            if (toUserId != null) {
                pushToUser(message, toUserId);
            } else {
                // 4. 如果只有groupId，需要查询群组成员并扩散（示例逻辑）
                pushToGroupMembers(message, groupId);
            }

        } catch (Exception e) {
            log.error("处理群聊推送消息异常: topic={}, partition={}, offset={}",
                    record.topic(), record.partition(), record.offset(), e);
            pushFailureCount++;
        }
    }

    @Override
    public void onException(Exception exception) {
        log.error("群聊推送消息消费异常", exception);
        pushFailureCount++;
    }

    @Override
    public void onComplete() {
        // 批量处理完成后可以做一些清理工作
        if (log.isDebugEnabled()) {
            log.debug("群聊推送消息批量处理完成: success={}, failure={}, offlineSkip={}",
                    pushSuccessCount, pushFailureCount, offlineSkipCount);
        }
    }

    /**
     * 推送给指定用户
     *
     * @param message  IM消息
     * @param userId   用户ID
     */
    private void pushToUser(IMMessage message, Long userId) {
        try {
            // 1. 查询用户是否在线
            boolean isOnline = onlineStatusService.isOnline(userId);

            if (!isOnline) {
                log.debug("群组成员离线，跳过推送: userId={}, msgId={}", userId, message.getMsgId());
                offlineSkipCount++;
                return;
            }

            // 2. 推送给用户所有设备
            int successCount = channelManager.broadcastToUser(userId, message);

            if (successCount > 0) {
                log.info("群聊消息推送成功: userId={}, msgId={}, devices={}", userId, message.getMsgId(), successCount);
                pushSuccessCount++;
            } else {
                log.warn("群聊消息推送失败（无活跃Channel）: userId={}, msgId={}", userId, message.getMsgId());
                pushFailureCount++;
            }

        } catch (Exception e) {
            log.error("推送给群组成员失败: userId={}, msgId={}", userId, message.getMsgId(), e);
            pushFailureCount++;
        }
    }

    /**
     * 推送给群组成员（示例逻辑）
     *
     * <p>注意：这里只是示例，实际应该从业务系统或Redis获取群组成员列表</p>
     *
     * @param message IM消息
     * @param groupId 群组ID
     */
    private void pushToGroupMembers(IMMessage message, Long groupId) {
        try {
            log.info("推送群聊消息给所有在线成员: groupId={}, msgId={}", groupId, message.getMsgId());

            // 1. 获取群组成员列表（示例：从缓存获取）
            Set<Long> memberIds = groupMembersCache.get(groupId);

            if (memberIds == null || memberIds.isEmpty()) {
                log.warn("群组成员列表为空: groupId={}", groupId);
                return;
            }

            // 2. 遍历成员，推送给在线成员
            int onlineCount = 0;
            int offlineCount = 0;

            for (Long userId : memberIds) {
                boolean isOnline = onlineStatusService.isOnline(userId);

                if (isOnline) {
                    int successCount = channelManager.broadcastToUser(userId, message);
                    if (successCount > 0) {
                        onlineCount++;
                        pushSuccessCount++;
                    }
                } else {
                    offlineCount++;
                    offlineSkipCount++;
                }
            }

            log.info("群聊消息推送完成: groupId={}, msgId={}, onlineMembers={}, offlineMembers={}",
                    groupId, message.getMsgId(), onlineCount, offlineCount);

        } catch (Exception e) {
            log.error("推送群聊消息给成员失败: groupId={}, msgId={}", groupId, message.getMsgId(), e);
            pushFailureCount++;
        }
    }

    /**
     * 解析消息
     *
     * @param jsonStr JSON字符串
     * @return IMMessage对象
     */
    private IMMessage parseMessage(String jsonStr) {
        if (jsonStr == null || jsonStr.trim().isEmpty()) {
            return null;
        }

        try {
            return objectMapper.readValue(jsonStr, IMMessage.class);
        } catch (Exception e) {
            log.error("解析消息失败: json={}", jsonStr, e);
            return null;
        }
    }

    // ==================== 群组成员管理（示例方法） ====================

    /**
     * 更新群组成员列表（示例方法）
     *
     * @param groupId     群组ID
     * @param memberIds   成员ID集合
     */
    public void updateGroupMembers(Long groupId, Set<Long> memberIds) {
        groupMembersCache.put(groupId, memberIds);
        log.info("更新群组成员列表: groupId={}, memberCount={}", groupId, memberIds.size());
    }

    /**
     * 移除群组成员列表
     *
     * @param groupId 群组ID
     */
    public void removeGroupMembers(Long groupId) {
        groupMembersCache.remove(groupId);
        log.info("移除群组成员列表: groupId={}", groupId);
    }

    // ==================== 统计方法 ====================

    /**
     * 获取推送成功数
     *
     * @return 成功数量
     */
    public long getPushSuccessCount() {
        return pushSuccessCount;
    }

    /**
     * 获取推送失败数
     *
     * @return 失败数量
     */
    public long getPushFailureCount() {
        return pushFailureCount;
    }

    /**
     * 获取离线跳过数
     *
     * @return 跳过数量
     */
    public long getOfflineSkipCount() {
        return offlineSkipCount;
    }

    /**
     * 获取统计信息
     *
     * @return 统计信息字符串
     */
    public String getStats() {
        long total = pushSuccessCount + pushFailureCount + offlineSkipCount;
        double successRate = total > 0 ? (double) pushSuccessCount / total * 100 : 0;

        return String.format(
                "GroupMessagePushConsumer{success=%d, failure=%d, offlineSkip=%d, total=%d, successRate=%.2f%%}",
                pushSuccessCount, pushFailureCount, offlineSkipCount, total, successRate
        );
    }

    /**
     * 重置统计信息
     */
    public void resetStats() {
        pushSuccessCount = 0;
        pushFailureCount = 0;
        offlineSkipCount = 0;
        log.info("群聊推送消息统计信息已重置");
    }
}
