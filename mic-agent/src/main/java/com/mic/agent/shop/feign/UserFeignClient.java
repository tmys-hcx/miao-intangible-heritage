package com.mic.agent.shop.feign;

import com.mic.agent.shop.model.vo.ShopUserInfoVO;
import com.mic.common.model.feign.FeignConstant;
import com.mic.common.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * mic-user 用户服务 Feign 客户端（Agent 直连，按 Nacos 服务名负载均衡）：
 * 查询当前登录用户基础信息（昵称 / 账号等），供助手个性化称呼与身份确认；
 * 用户身份由 FeignAuthRelayConfig 拦截器统一透传 X-User-Id 可信头，未登录时下游返回 401 业务码
 */
@FeignClient(name = FeignConstant.SERVICE_USER, path = "/api/user")
public interface UserFeignClient {

    /**
     * 获取当前登录用户信息：网关 / 拦截器透传 X-User-Id，缺失视为未登录
     */
    @GetMapping("/info")
    Result<ShopUserInfoVO> getUserInfo();
}
