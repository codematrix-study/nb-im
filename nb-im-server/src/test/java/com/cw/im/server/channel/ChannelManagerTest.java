package com.cw.im.server.channel;

import io.netty.channel.Channel;
import io.netty.channel.ChannelId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collection;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * ChannelManager 单元测试
 *
 * @author cw
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ChannelManager 单元测试")
class ChannelManagerTest {

    private ChannelManager channelManager;

    @Mock
    private Channel channel1;

    @Mock
    private Channel channel2;

    @Mock
    private ChannelId channelId1;

    @Mock
    private ChannelId channelId2;

    @BeforeEach
    void setUp() {
        channelManager = new ChannelManager();

        // Setup channel mocks
        when(channel1.id()).thenReturn(channelId1);
        when(channel2.id()).thenReturn(channelId2);
        when(channelId1.asLongText()).thenReturn("channel-1");
        when(channelId2.asLongText()).thenReturn("channel-2");
        when(channel1.isActive()).thenReturn(true);
        when(channel2.isActive()).thenReturn(true);
    }

    @Test
    @DisplayName("测试添加Channel")
    void testAddChannel() {
        // Given
        Long userId = 1001L;
        String deviceId = "device-001";

        // When
        channelManager.addChannel(userId, deviceId, channel1);

        // Then
        Channel retrievedChannel = channelManager.getChannel(userId);
        assertThat(retrievedChannel).isNotNull();
        assertThat(retrievedChannel).isEqualTo(channel1);
    }

    @Test
    @DisplayName("测试添加多个Channel - 同一用户多设备")
    void testAddMultipleChannels_SameUser() {
        // Given
        Long userId = 1002L;
        String deviceId1 = "device-001";
        String deviceId2 = "device-002";

        // When
        channelManager.addChannel(userId, deviceId1, channel1);
        channelManager.addChannel(userId, deviceId2, channel2);

        // Then
        Set<Channel> channels = channelManager.getChannels(userId);
        assertThat(channels).hasSize(2);
        assertThat(channels).contains(channel1, channel2);
    }

    @Test
    @DisplayName("测试移除Channel")
    void testRemoveChannel() {
        // Given
        Long userId = 1003L;
        String deviceId = "device-001";
        channelManager.addChannel(userId, deviceId, channel1);

        // When
        channelManager.removeChannel(userId, deviceId);

        // Then
        Channel retrievedChannel = channelManager.getChannel(userId);
        assertThat(retrievedChannel).isNull();
    }

    @Test
    @DisplayName("测试移除Channel - 多设备中移除一个")
    void testRemoveChannel_OneOfMultiple() {
        // Given
        Long userId = 1004L;
        String deviceId1 = "device-001";
        String deviceId2 = "device-002";
        channelManager.addChannel(userId, deviceId1, channel1);
        channelManager.addChannel(userId, deviceId2, channel2);

        // When
        channelManager.removeChannel(userId, deviceId1);

        // Then
        Set<Channel> channels = channelManager.getChannels(userId);
        assertThat(channels).hasSize(1);
        assertThat(channels).contains(channel2);
        assertThat(channels).doesNotContain(channel1);
    }

    @Test
    @DisplayName("测试获取Channel - 单设备")
    void testGetChannel_SingleDevice() {
        // Given
        Long userId = 1005L;
        String deviceId = "device-001";
        channelManager.addChannel(userId, deviceId, channel1);

        // When
        Channel retrievedChannel = channelManager.getChannel(userId);

        // Then
        assertThat(retrievedChannel).isEqualTo(channel1);
    }

    @Test
    @DisplayName("测试获取Channel - 多设备返回第一个")
    void testGetChannel_MultipleDevices() {
        // Given
        Long userId = 1006L;
        String deviceId1 = "device-001";
        String deviceId2 = "device-002";
        channelManager.addChannel(userId, deviceId1, channel1);
        channelManager.addChannel(userId, deviceId2, channel2);

        // When
        Channel retrievedChannel = channelManager.getChannel(userId);

        // Then
        assertThat(retrievedChannel).isNotNull();
        assertThat(retrievedChannel).isIn(channel1, channel2);
    }

    @Test
    @DisplayName("测试获取Channel - 用户不存在")
    void testGetChannel_UserNotFound() {
        // Given
        Long userId = 9999L;

        // When
        Channel retrievedChannel = channelManager.getChannel(userId);

        // Then
        assertThat(retrievedChannel).isNull();
    }

    @Test
    @DisplayName("测试获取所有Channel")
    void testGetChannels() {
        // Given
        Long userId1 = 1007L;
        Long userId2 = 1008L;

        channelManager.addChannel(userId1, "device-001", channel1);
        channelManager.addChannel(userId2, "device-002", channel2);

        // When
        Collection<Channel> allChannels = channelManager.getAllChannels();

        // Then
        assertThat(allChannels).hasSize(2);
        assertThat(allChannels).contains(channel1, channel2);
    }

    @Test
    @DisplayName("测试获取用户的Channel集合")
    void testGetChannels_UserChannels() {
        // Given
        Long userId = 1009L;
        String deviceId1 = "device-001";
        String deviceId2 = "device-002";

        channelManager.addChannel(userId, deviceId1, channel1);
        channelManager.addChannel(userId, deviceId2, channel2);

        // When
        Set<Channel> userChannels = channelManager.getChannels(userId);

        // Then
        assertThat(userChannels).hasSize(2);
        assertThat(userChannels).contains(channel1, channel2);
    }

    @Test
    @DisplayName("测试获取用户的Channel集合 - 用户不存在")
    void testGetChannels_UserNotFound() {
        // Given
        Long userId = 9999L;

        // When
        Set<Channel> userChannels = channelManager.getChannels(userId);

        // Then
        assertThat(userChannels).isEmpty();
    }

    @Test
    @DisplayName("测试广播消息")
    void testBroadcast() {
        // Given
        Long userId1 = 1010L;
        Long userId2 = 1011L;

        channelManager.addChannel(userId1, "device-001", channel1);
        channelManager.addChannel(userId2, "device-002", channel2);

        // When
        channelManager.broadcast("Test message");

        // Then
        verify(channel1).writeAndFlush("Test message");
        verify(channel2).writeAndFlush("Test message");
    }

    @Test
    @DisplayName("测试获取当前连接数")
    void testGetCurrentConnectionCount() {
        // Given
        channelManager.addChannel(1012L, "device-001", channel1);
        channelManager.addChannel(1013L, "device-002", channel2);

        // When
        int count = channelManager.getCurrentConnectionCount();

        // Then
        assertThat(count).isEqualTo(2);
    }

    @Test
    @DisplayName("测试获取峰值连接数")
    void testGetPeakConnectionCount() {
        // Given
        channelManager.addChannel(1014L, "device-001", channel1);
        channelManager.addChannel(1015L, "device-002", channel2);

        // When
        int peakCount = channelManager.getPeakConnectionCount();

        // Then
        assertThat(peakCount).isGreaterThanOrEqualTo(2);
    }

    @Test
    @DisplayName("测试移除用户的所有Channel")
    void testRemoveUser_AllChannels() {
        // Given
        Long userId = 1016L;
        channelManager.addChannel(userId, "device-001", channel1);
        channelManager.addChannel(userId, "device-002", channel2);

        // When
        channelManager.removeUser(userId);

        // Then
        Set<Channel> userChannels = channelManager.getChannels(userId);
        assertThat(userChannels).isEmpty();
    }

    @Test
    @DisplayName("测试移除用户的部分Channel")
    void testRemoveChannel_ByChannelId() {
        // Given
        Long userId = 1017L;
        channelManager.addChannel(userId, "device-001", channel1);
        channelManager.addChannel(userId, "device-002", channel2);

        // When
        channelManager.removeChannel(userId, channelId1.asLongText());

        // Then
        Set<Channel> userChannels = channelManager.getChannels(userId);
        assertThat(userChannels).hasSize(1);
        assertThat(userChannels).contains(channel2);
    }

    @Test
    @DisplayName("测试判断用户是否在线")
    void testIsUserOnline() {
        // Given
        Long userId = 1018L;
        channelManager.addChannel(userId, "device-001", channel1);

        // When
        boolean isOnline = channelManager.isUserOnline(userId);

        // Then
        assertThat(isOnline).isTrue();
    }

    @Test
    @DisplayName("测试判断用户是否在线 - 用户不存在")
    void testIsUserOnline_UserNotFound() {
        // Given
        Long userId = 9999L;

        // When
        boolean isOnline = channelManager.isUserOnline(userId);

        // Then
        assertThat(isOnline).isFalse();
    }

    @Test
    @DisplayName("测试获取在线用户数量")
    void testGetOnlineUserCount() {
        // Given
        channelManager.addChannel(1019L, "device-001", channel1);
        channelManager.addChannel(1020L, "device-002", channel2);

        // When
        int userCount = channelManager.getOnlineUserCount();

        // Then
        assertThat(userCount).isEqualTo(2);
    }

    @Test
    @DisplayName("测试清理不活跃的Channel")
    void testCleanInactiveChannels() {
        // Given
        Long userId = 1021L;
        channelManager.addChannel(userId, "device-001", channel1);
        when(channel1.isActive()).thenReturn(false);

        // When
        channelManager.cleanInactiveChannels();

        // Then
        Set<Channel> userChannels = channelManager.getChannels(userId);
        assertThat(userChannels).isEmpty();
    }

    @Test
    @DisplayName("测试获取统计信息")
    void testGetStats() {
        // Given
        channelManager.addChannel(1022L, "device-001", channel1);
        channelManager.addChannel(1023L, "device-002", channel2);

        // When
        String stats = channelManager.getStats();

        // Then
        assertThat(stats).contains("ChannelManager");
        assertThat(stats).contains("currentConnections");
        assertThat(stats).contains("peakConnections");
        assertThat(stats).contains("onlineUsers");
    }
}
