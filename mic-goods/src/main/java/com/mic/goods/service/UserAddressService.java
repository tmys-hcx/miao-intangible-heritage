package com.mic.goods.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.mic.goods.model.dto.AddressSaveDTO;
import com.mic.goods.model.entity.UserAddress;

import java.util.List;

/**
 * 用户收货地址服务接口
 */
public interface UserAddressService extends IService<UserAddress> {

    /**
     * 收货地址列表（登录用户）：本人全部地址，默认地址在前
     * @param userId 当前登录用户ID（网关透传）
     * @return 地址列表（默认地址在前，其次按新增时间倒序）
     */
    List<UserAddress> listMyAddress(Long userId);

    /**
     * 新增收货地址（登录用户）：设为默认时自动清除本人其他默认地址；首个地址自动设为默认
     * @param dto 地址信息
     * @param userId 当前登录用户ID
     * @return 新增地址ID
     */
    Long addAddress(AddressSaveDTO dto, Long userId);

    /**
     * 编辑收货地址（登录用户）：仅可编辑本人地址；设为默认时自动清除本人其他默认地址
     * @param id 地址ID
     * @param dto 地址信息
     * @param userId 当前登录用户ID
     */
    void updateAddress(Long id, AddressSaveDTO dto, Long userId);

    /**
     * 删除收货地址（登录用户）：仅可删除本人地址（逻辑删除）
     * @param id 地址ID
     * @param userId 当前登录用户ID
     */
    void deleteAddress(Long id, Long userId);

    /**
     * 查询本人指定地址（下单时调用）：不存在或非本人所有时抛出业务异常
     * @param id 地址ID
     * @param userId 当前登录用户ID
     * @return 地址信息
     */
    UserAddress getOwnAddress(Long id, Long userId);
}
