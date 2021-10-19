package com.wangz.controller;

import com.wangz.model.domain.ResultInfo;
import com.wangz.utils.ResultInfoUtil;
import com.wangz.service.SignService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.Map;

/**
 * 签到控制层
 */
@RestController
@RequestMapping("sign")
public class SignController {

    @Resource
    private SignService signService;
    @Resource
    private HttpServletRequest request;

    /**
     * 签到，可以补签
     *
     * @param access_token
     * @param date
     * @return
     */
    @PostMapping
    public ResultInfo sign(String access_token,
                           @RequestParam(required = false) String date) {
        int count = signService.doSign(access_token, date);
        return ResultInfoUtil.buildSuccess(request.getServletPath(), count);
    }

}