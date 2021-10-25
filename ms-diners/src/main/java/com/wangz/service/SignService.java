package com.wangz.service;


import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.StrUtil;
import com.wangz.constant.ApiConstant;
import com.wangz.exception.ParameterException;
import com.wangz.model.domain.ResultInfo;
import com.wangz.model.vo.SignInDinerInfo;
import com.wangz.utils.AssertUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.annotation.Resource;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;

@Service
public class SignService {

    @Value("${service.name.ms-oauth-server}")
    private String oauthServerName;
    @Resource
    private RestTemplate restTemplate;
    @Resource
    private RedisTemplate redisTemplate;


    /**
     * 用户签到
     *
     * @param accessToken
     * @param dateStr
     * @return
     */
    public int doSign(String accessToken, String dateStr) {
        // 获取登录用户信息
        SignInDinerInfo dinerInfo = loadSignInDinerInfo(accessToken);
        // 获取日期
        Date date = getDate(dateStr);
        // 获取日期对应的天数，多少号
        int offset = DateUtil.dayOfMonth(date) - 1; // 从 0 开始
        // 构建 Key user:sign:5:yyyyMM
        String signKey = buildSignKey(dinerInfo.getId(), date);
        // 查看是否已签到
        boolean isSigned = redisTemplate.opsForValue().getBit(signKey, offset);
        AssertUtil.isTrue(isSigned, "当前日期已完成签到，无需再签");
        // 签到
        redisTemplate.opsForValue().setBit(signKey, offset, true);
        // 统计连续签到的次数
        int count = getContinuousSignCount(dinerInfo.getId(), date);
        return count;
    }
    /**
     * 获取用户签到次数
     *
     * @param accessToken
     * @param dateStr
     * @return
     */
    public long getSignCount(String accessToken, String dateStr) {
        // 获取登录用户信息
        SignInDinerInfo dinerInfo = loadSignInDinerInfo(accessToken);
        // 获取日期
        Date date = getDate(dateStr);
        // 构建 Key
        String signKey = buildSignKey(dinerInfo.getId(), date);
        // e.g. BITCOUNT user:sign:5:202011
        return (Long) redisTemplate.execute(
                (RedisCallback<Long>) con -> con.bitCount(signKey.getBytes())
        );
    }

    /**
     * 统计连续签到的次数
     *
     * @param dinerId
     * @param date
     * @return
     */
    private int getContinuousSignCount(Integer dinerId, Date date) {
        // 获取日期对应的天数，多少号，假设是 30
        int dayOfMonth = DateUtil.dayOfMonth(date);
        // 构建 Key
        String signKey = buildSignKey(dinerId, date);
        // bitfield user:sgin:5:202011 u30 0
        BitFieldSubCommands bitFieldSubCommands = BitFieldSubCommands.create()
                .get(BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth))
                .valueAt(0);
        List<Long> list = redisTemplate.opsForValue().bitField(signKey, bitFieldSubCommands);
        if (list == null || list.isEmpty()) {
            return 0;
        }
        int signCount = 0;
        long v = list.get(0) == null ? 0 : list.get(0);
        /**
         * 高位							                    低位
         * 1 1 1 1 1 1 0 1 0 0 1 0 1 1 1 0 0 1 0 1 1 1 1 0 0 0 0 0 0 0 0    -- 31号	 2,123,837,184
         * 右移以后
         * 0 1 1 1 1 1 1 0 1 0 0 1 0 1 1 1 0 0 1 0 1 1 1 1 0 0 0 0 0 0 0
         * 然后再左移
         * 1 1 1 1 1 1 0 1 0 0 1 0 1 1 1 0 0 1 0 1 1 1 1 0 0 0 0 0 0 0 0
         * 结论：右移再左移如果等于自己，则表示未签到
         *
         *
         * 1 1 1 1 1 1 0 1 0 0 1 0 1 1 1 0 0 1 0 1 1 1 1 0 0 0 0 0 0 0 1
         * 右移以后
         * 0 1 1 1 1 1 1 0 1 0 0 1 0 1 1 1 0 0 1 0 1 1 1 1 0 0 0 0 0 0 0
         * 然后再左移
         * 1 1 1 1 1 1 0 1 0 0 1 0 1 1 1 0 0 1 0 1 1 1 1 0 0 0 0 0 0 0 0
         * 结论：右移再左移如果不等于自己，则表示已签到
         *
         *
         * 1 1 1 1 1 1 0 1 0 0 1 0 1 1 1 0 0 1 0 1 1 1 1 0 0 0 0 0 0 0      -- 30号    1,061,918,592
         *
         * 1 1 1 1 1 1 0 1 0 0 1 0 1 1 1 0 0 1 0 1 1 1 1 0 0 0 0 0 0        -- 29号
         *
         * 1 1 1 1 1 1 0 1 0 0 1 0 1 1 1 0 0 1 0 1 1 1 1 0 0 0 0 0		 -- 28号
         *
         * 1 1 1 1 1 1 0 1 0 0 1 0 1 1 1 0 0 1 0 1 1 1 1 0 0 0 0 		 -- 27号
         *
         * 1 1 1 1 1 1 0 1 0 0 1 0 1 1 1 0 0 1 0 1 1 1 1 0 0 0 		       -- 26号
         *
         * 1 1 1 1 1 1 0 1 0 0 1 0 1 1 1 0 0 1 0 1 1 1 1 0 0 		       -- 25号
         *
         * 1 1 1 1 1 1 0 1 0 0 1 0 1 1 1 0 0 1 0 1 1 1 1 0 		            -- 24号
         *
         * 1 1 1 1 1 1 0 1 0 0 1 0 1 1 1 0 0 1 0 1 1 1 1			       -- 23号
         */
        for (int i = dayOfMonth; i > 0; i--) {// i 表示位移操作次数
            // 右移再左移，如果等于自己说明最低位是 0，表示未签到
            if (v >> 1 << 1 == v) {
                // 低位 0 且非当天说明连续签到中断了
                if (i != dayOfMonth) break;
            } else {
                signCount++;
            }
            // 右移一位并重新赋值，相当于把最低位丢弃一位
            v >>= 1;
        }
        return signCount;
    }
    /**
     * 构建 Key -- user:sign:5:yyyyMM
     *
     * @param dinerId
     * @param date
     * @return
     */
    private String buildSignKey(Integer dinerId, Date date) {
        return String.format("user:sign:%d:%s", dinerId,
                DateUtil.format(date, "yyyyMM"));
    }

    /**
     * 获取日期
     *
     * @param dateStr
     * @return
     */
    private Date getDate(String dateStr) {
        if (StrUtil.isBlank(dateStr)) {
            return new Date();
        }
        try {
            return DateUtil.parseDate(dateStr);
        } catch (Exception e) {
            throw new ParameterException("请传入yyyy-MM-dd的日期格式");
        }
    }

    /**
     * 获取登录用户信息
     *
     * @param accessToken
     * @return
     */
    private SignInDinerInfo loadSignInDinerInfo(String accessToken) {
        // 必须登录
        AssertUtil.mustLogin(accessToken);
        String url = oauthServerName + "user/me?access_token={accessToken}";
        ResultInfo resultInfo = restTemplate.getForObject(url, ResultInfo.class, accessToken);
        if (resultInfo.getCode() != ApiConstant.SUCCESS_CODE) {
            throw new ParameterException(resultInfo.getCode(), resultInfo.getMessage());
        }
        SignInDinerInfo dinerInfo = BeanUtil.fillBeanWithMap((LinkedHashMap) resultInfo.getData(),
                new SignInDinerInfo(), false);
        if (dinerInfo == null) {
            throw new ParameterException(ApiConstant.NO_LOGIN_CODE, ApiConstant.NO_LOGIN_MESSAGE);
        }
        return dinerInfo;
    }
}
