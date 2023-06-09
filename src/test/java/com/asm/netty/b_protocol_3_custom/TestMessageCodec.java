package com.asm.netty.b_protocol_3_custom;

import com.asm.netty.b_protocol_3_custom.message.LoginRequestMessage;
import com.asm.netty.b_protocol_3_custom.message.Message;
import com.asm.netty.b_protocol_3_custom.protocol.MessageCodec;
import com.asm.netty.b_protocol_3_custom.protocol.MessageCodecSharable;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.logging.LoggingHandler;

/**
 * netty做了个注解标记 @Sharable 可以被共享，可以在多线程下安全使用 【一般解码器类 是不能被共享的】
 */
public class TestMessageCodec {

    /**
     * ###########################################################
     * ########    LengthFieldBasedFrameDecoder 不能抽离  #########
     * ###########################################################
     * 1. 两个 Eventloop 线程， 使用同一个对象，会产生线程安全的共享资源
     * 2. 抽离出来的 LengthFieldBasedFrameDecoder对象 主要记录了多次消息之间的状态就是线程不安全的【半包黏包那样保存上一个信息】 ，就不能在多个Eventloop下使用同一个Handler
     */
    public static void main(String[] args) throws Exception {
        /**
         * 这里 注意顺序
         * 帧解码器 处理完，【是完整信息 才会向下一个 handler传递】
         * 抽离出来的 LOGGING_HANDLER  是没有状态信息的Handler, 不会出现这样的问题，来多少数据 就打印多少数据
         */
        final LoggingHandler LOGGIN_HANDLER = new LoggingHandler();
// HEAD >>>>>>>>>>>>>>>
        final MessageCodecSharable MESSAGE_CODEC = new MessageCodecSharable();
// ====================
//        final MessageCodec MESSAGE_CODEC = new MessageCodec();
// <<<<<<<<<<<<<<<<<<<<
        final SimpleChannelInboundHandler<LoginRequestMessage> channelInboundHandler = new SimpleChannelInboundHandler() {
            @Override
            protected void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
                // 这里的 Object msg 已经 【被 MessageCodecSharable 解码成了 Message类型了】
                LoginRequestMessage loginRequestMessage = (LoginRequestMessage) msg;
                System.out.println(loginRequestMessage.getNickname() + "==================" + loginRequestMessage.getPassword());

            }
        };
        final EmbeddedChannel channel = new EmbeddedChannel(
                LOGGIN_HANDLER,                   // 【移动到流水线的最上方 可以 打印出 半包情况】
                new LengthFieldBasedFrameDecoder(
                        1024, 12, 4, 0, 0),
                MESSAGE_CODEC,
                channelInboundHandler
        );

        LoginRequestMessage message = new LoginRequestMessage("张三", "123456", "zs");

        /* ########################################################
           #######    出站测试  【出站 自动编码】  encode   ##########
           ########################################################*/
//        channel.writeOutbound(message);

        /* #########################################################
           #######    入站测试   【入站 自动解码】  decode ############
           #########################################################*/
        final ByteBuf buf = ByteBufAllocator.DEFAULT.buffer(); // 新建一个buf
        new MessageCodec().encode(null, message, buf); // 【编码 入站】

// HEAD >>>>>>>>>>>>>>>
        // 半包测试
        final ByteBuf s1 = buf.slice(0, 100);
        final ByteBuf s2 = buf.slice(100, buf.readableBytes() - 100);

        // 【引用计数 + 1】 才能 调用两次 writeInbound() 保证不会释放内容
        s1.retain();               // retain + 1 = 2
        channel.writeInbound(s1);  // release 1
        channel.writeInbound(s2);  // release 0
// ====================
        // 入站
//        channel.writeInbound(buf);
// <<<<<<<<<<<<<<<<<<<<



    }

}
