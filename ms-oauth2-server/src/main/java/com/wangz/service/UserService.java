package com.wangz.service;

import com.wangz.mapper.DinnerMapper;
import com.wangz.model.pojo.Diners;
import com.wangz.utils.AssertUtil;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
@Service
public class UserService implements UserDetailsService {
    @Resource
    private DinnerMapper dinersMapper;



    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        AssertUtil.isNotEmpty(username, "请输入用户名");
        Diners diners = dinersMapper.selectByAccountInfo(username);
        if (diners == null){
            throw new UsernameNotFoundException("用户名或密码错误，请重新输入");
        }
        return new User(username, diners.getPassword(),
                AuthorityUtils.commaSeparatedStringToAuthorityList(diners.getRoles()));
    }
}
