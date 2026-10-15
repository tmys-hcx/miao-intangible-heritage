package com.mic.goods.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mic.common.exception.BusinessException;
import com.mic.common.result.ResultCodeEnum;
import com.mic.goods.mapper.UserAddressMapper;
import com.mic.goods.model.dto.AddressSaveDTO;
import com.mic.goods.model.entity.UserAddress;
import com.mic.goods.service.UserAddressService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 用户收货地址服务实现
 * 约束：同一用户默认地址唯一——新增/编辑设为默认时先清除本人其他默认地址
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserAddressServiceImpl extends ServiceImpl<UserAddressMapper, UserAddress> implements UserAddressService {

    /** 是否默认地址：1是 */
    private static final int DEFAULT_YES = 1;

    /** 是否默认地址：0否 */
    private static final int DEFAULT_NO = 0;

    /**
     * 收货地址列表（登录用户）：本人全部地址，默认地址在前
     */
    @Override
    public List<UserAddress> listMyAddress(Long userId) {
        return lambdaQuery()
                .eq(UserAddress::getUserId, userId)
                // 默认地址排最前，其次按新增时间倒序（新地址在前）
                .orderByDesc(UserAddress::getIsDefault)
                .orderByDesc(UserAddress::getId)
                .list();
    }

    /**
     * 新增收货地址（登录用户）：设为默认时自动清除本人其他默认地址；首个地址自动设为默认
     */
    @Override
    public Long addAddress(AddressSaveDTO dto, Long userId) {
        // 本人暂无地址时首个地址自动设为默认（保证结算页默认有地址可选中）
        boolean firstAddress = lambdaQuery().eq(UserAddress::getUserId, userId).count() == 0;
        boolean wantDefault = dto.getIsDefault() != null && dto.getIsDefault() == DEFAULT_YES;
        int isDefault = firstAddress || wantDefault ? DEFAULT_YES : DEFAULT_NO;
        if (isDefault == DEFAULT_YES && !firstAddress) {
            clearDefault(userId);
        }

        UserAddress address = new UserAddress();
        address.setUserId(userId);
        address.setReceiverName(dto.getReceiverName());
        address.setReceiverPhone(dto.getReceiverPhone());
        address.setProvince(dto.getProvince());
        address.setCity(dto.getCity());
        address.setDistrict(dto.getDistrict());
        address.setDetailAddress(dto.getDetailAddress());
        address.setIsDefault(isDefault);
        save(address);
        log.info("新增收货地址成功：userId={}, addressId={}, isDefault={}", userId, address.getId(), isDefault);
        return address.getId();
    }

    /**
     * 编辑收货地址（登录用户）：仅可编辑本人地址；设为默认时自动清除本人其他默认地址
     */
    @Override
    public void updateAddress(Long id, AddressSaveDTO dto, Long userId) {
        UserAddress exist = getOwnAddress(id, userId);
        boolean wantDefault = dto.getIsDefault() != null && dto.getIsDefault() == DEFAULT_YES;
        if (wantDefault) {
            clearDefault(userId);
        }

        UserAddress update = new UserAddress();
        update.setId(exist.getId());
        update.setReceiverName(dto.getReceiverName());
        update.setReceiverPhone(dto.getReceiverPhone());
        update.setProvince(dto.getProvince());
        update.setCity(dto.getCity());
        update.setDistrict(dto.getDistrict());
        update.setDetailAddress(dto.getDetailAddress());
        update.setIsDefault(wantDefault ? DEFAULT_YES : DEFAULT_NO);
        updateById(update);
        log.info("编辑收货地址成功：userId={}, addressId={}, isDefault={}", userId, id, update.getIsDefault());
    }

    /**
     * 删除收货地址（登录用户）：仅可删除本人地址（逻辑删除）
     */
    @Override
    public void deleteAddress(Long id, Long userId) {
        UserAddress address = getOwnAddress(id, userId);
        removeById(address.getId());
        log.info("删除收货地址成功（逻辑删除）：userId={}, addressId={}", userId, id);
    }

    /**
     * 查询本人指定地址（下单时调用）：不存在或非本人所有时抛出业务异常
     */
    @Override
    public UserAddress getOwnAddress(Long id, Long userId) {
        UserAddress address = getById(id);
        if (address == null) {
            throw new BusinessException(ResultCodeEnum.PARAM_ERROR, "收货地址不存在");
        }
        if (!address.getUserId().equals(userId)) {
            throw new BusinessException(ResultCodeEnum.FORBIDDEN, "仅可操作本人收货地址");
        }
        return address;
    }

    /** 清除本人其他默认地址标记（保障同一用户至多一条默认地址） */
    private void clearDefault(Long userId) {
        lambdaUpdate()
                .eq(UserAddress::getUserId, userId)
                .eq(UserAddress::getIsDefault, DEFAULT_YES)
                .set(UserAddress::getIsDefault, DEFAULT_NO)
                .update();
    }
}
