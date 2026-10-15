package com.mic.goods.config;

import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRule;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRuleManager;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Sentinel 保护规则配置（代码内置，应用启动即加载，重启不丢失；不部署控制台也能生效）：
 * 1、限流：创建订单 order_create 按 QPS 快速失败——超出阈值直接拒绝，走 Controller 的 blockHandler 降级提示；
 * 2、熔断：发起支付 pay_prepare 异常比例熔断——统计窗口内请求数达到最小请求数且异常占比超阈值时熔断，
 * 熔断打开期间请求直接拒绝（走 blockHandler），熔断时长结束后自动进入半开恢复探测；
 * 3、熔断：模拟支付 pay_mock 慢调用比例熔断——统计窗口内慢调用（RT 超阈值）占比超阈值时熔断，规则同上。
 * 资源名与 GoodsOrderController 中 @SentinelResource(value) 一一对应；
 * 熔断统计只针对系统/运行异常（业务异常已在注解中 exceptionsToIgnore 排除，不参与熔断）。
 * 可选：接入控制台（application.yml 已配 127.0.0.1:9999）后可实时查看 QPS/拒绝数，并临时调整规则演示。
 */
@Slf4j
@Configuration
public class SentinelRuleConfig {

    /** 资源名：创建订单（GoodsOrderController#add） */
    private static final String RESOURCE_ORDER_CREATE = "order_create";
    /** 资源名：发起支付（GoodsOrderController#preparePay） */
    private static final String RESOURCE_PAY_PREPARE = "pay_prepare";
    /** 资源名：模拟支付（GoodsOrderController#mockPay） */
    private static final String RESOURCE_PAY_MOCK = "pay_mock";

    /** 下单限流阈值：QPS=5（每秒最多 5 次下单请求，超出快速失败） */
    private static final double ORDER_CREATE_QPS = 5;

    /** 熔断统计窗口时长（毫秒） */
    private static final int DEGRADE_STAT_INTERVAL_MS = 10_000;
    /** 熔断最小请求数：统计窗口内请求数达到该值才参与熔断判定（调低便于观察） */
    private static final int DEGRADE_MIN_REQUEST_AMOUNT = 5;
    /** 熔断时长（秒）：熔断打开后经过该时长进入半开探测，探测通过自动恢复 */
    private static final int DEGRADE_TIME_WINDOW_SECONDS = 10;
    /** 异常比例熔断阈值：统计窗口内异常占比超 50% 触发熔断 */
    private static final double DEGRADE_EXCEPTION_RATIO = 0.5;
    /** 慢调用 RT 阈值（毫秒）：调用耗时超过该值判定为慢调用 */
    private static final int DEGRADE_SLOW_RT_MS = 300;
    /** 慢调用比例熔断阈值：统计窗口内慢调用占比超 80% 触发熔断 */
    private static final double DEGRADE_SLOW_RATIO = 0.8;

    /**
     * 应用启动时加载规则：流控（限流）+ 熔断（异常比例 / 慢调用比例）
     */
    @PostConstruct
    public void initSentinelRules() {
        // 流控规则：创建订单按 QPS 限流（快速失败；limitApp 缺省为 default 表示不区分来源）
        FlowRule orderCreateFlowRule = new FlowRule();
        orderCreateFlowRule.setResource(RESOURCE_ORDER_CREATE);
        orderCreateFlowRule.setGrade(RuleConstant.FLOW_GRADE_QPS);
        orderCreateFlowRule.setCount(ORDER_CREATE_QPS);
        FlowRuleManager.loadRules(List.of(orderCreateFlowRule));

        // 熔断规则一：发起支付——异常比例熔断（窗口内请求数达最小请求数且异常占比超阈值 → 打开熔断≥熔断时长）
        DegradeRule payPrepareDegradeRule = new DegradeRule();
        payPrepareDegradeRule.setResource(RESOURCE_PAY_PREPARE);
        payPrepareDegradeRule.setGrade(RuleConstant.DEGRADE_GRADE_EXCEPTION_RATIO);
        payPrepareDegradeRule.setCount(DEGRADE_EXCEPTION_RATIO);
        payPrepareDegradeRule.setStatIntervalMs(DEGRADE_STAT_INTERVAL_MS);
        payPrepareDegradeRule.setMinRequestAmount(DEGRADE_MIN_REQUEST_AMOUNT);
        payPrepareDegradeRule.setTimeWindow(DEGRADE_TIME_WINDOW_SECONDS);

        // 熔断规则二：模拟支付——慢调用比例熔断（RT 超阈值判定为慢调用，慢调用占比超阈值 → 打开熔断）
        DegradeRule payMockDegradeRule = new DegradeRule();
        payMockDegradeRule.setResource(RESOURCE_PAY_MOCK);
        payMockDegradeRule.setGrade(RuleConstant.DEGRADE_GRADE_RT);
        payMockDegradeRule.setCount(DEGRADE_SLOW_RT_MS);
        payMockDegradeRule.setSlowRatioThreshold(DEGRADE_SLOW_RATIO);
        payMockDegradeRule.setStatIntervalMs(DEGRADE_STAT_INTERVAL_MS);
        payMockDegradeRule.setMinRequestAmount(DEGRADE_MIN_REQUEST_AMOUNT);
        payMockDegradeRule.setTimeWindow(DEGRADE_TIME_WINDOW_SECONDS);

        DegradeRuleManager.loadRules(List.of(payPrepareDegradeRule, payMockDegradeRule));

        log.info("[Sentinel] 保护规则加载完成：创建订单限流 QPS={}；发起支付异常比例熔断；模拟支付慢调用熔断（RT>{}ms 占比>{}%）",
                ORDER_CREATE_QPS, DEGRADE_SLOW_RT_MS, (int) (DEGRADE_SLOW_RATIO * 100));
    }
}
