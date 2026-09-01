package xyz.luobo.mturrets.core.machine

/**
 * 机器"运行中"共享契约(#57 运转 hum 的抽象运行钩子)。
 * 窑炉/钻头/(后续)发电机实现它;客户端 hum 控制器据此起停,新机器接入只需实现此接口,
 * 不动 hum 代码。[isRunning] 服务端权威、随 update tag 同步到客户端(客户端 ticker 消费)。
 */
interface HummingMachine {
    /** 该机器当前是否处于"运转中"状态(驱动 hum 淡入,停止时淡出)。 */
    val isRunning: Boolean
}
