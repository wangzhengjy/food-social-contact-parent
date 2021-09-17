package com.wangz.config;

import cn.hutool.crypto.digest.DigestUtil;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.provider.token.store.redis.RedisTokenStore;

import javax.annotation.Resource;

/**
 * Security 配置类
 */
@Configuration
@EnableWebSecurity
public class SecurityConfiguration extends WebSecurityConfigurerAdapter {

    // 注入 Redis 连接工厂
    @Resource
    private RedisConnectionFactory redisConnectionFactory;

    // 初始化 RedisTokenStore 用于将 token 存储至 Redis
    @Bean
    public RedisTokenStore redisTokenStore() {
        RedisTokenStore redisTokenStore = new RedisTokenStore(redisConnectionFactory);
        redisTokenStore.setPrefix("TOKEN:"); // 设置key的层级前缀，方便查询
        return redisTokenStore;
    }

    // 初始化密码编码器，用 MD5 加密密码
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new PasswordEncoder() {
            /**
             * spring security中的BCryptPasswordEncoder方法采用SHA-256+随机盐+密钥对密码进行加密。
             * SHA系列是Hash算法，不是加密算法，使用加密算法意味着可以解密（这个与编码/解码一样），但是采用Hash处理，其过程是不可逆的。
             *
             * （1）加密(encode)：注册用户时，使用SHA-256+随机盐+密钥把用户输入的密码进行hash(BCrypt)处理，得到密码的hash值，然后将其存入数据库中。
             *
             * （2）密码匹配(matches)：用户登录时，密码匹配阶段并没有进行密码解密（因为密码经过Hash处理，是不可逆的），而是使用相同的算法把用户输入的密码进行hash处理，得到密码的hash值，然后将其与从数据库中查询到的密码hash值进行比较。如果两者相同，说明用户输入的密码正确。
             *
             * 这正是为什么处理密码时要用hash算法，而不用加密算法。因为这样处理即使数据库泄漏，黑客也很难破解密码（破解密码只能用彩虹表）。
             */

            /**
             * 加密
             * @param rawPassword 原始密码
             * @return
             */
            @Override
            public String encode(CharSequence rawPassword) {
                return DigestUtil.md5Hex(rawPassword.toString());
            }

            /**
             * 校验密码
             * @param rawPassword       原始密码
             * @param encodedPassword   加密密码
             * @return
             */
            @Override
            public boolean matches(CharSequence rawPassword, String encodedPassword) {
                return DigestUtil.md5Hex(rawPassword.toString()).equals(encodedPassword);
            }
        };
    }

    // 初始化认证管理对象
    @Bean
    @Override
    public AuthenticationManager authenticationManagerBean() throws Exception {
        return super.authenticationManagerBean();
    }

    // 放行和认证规则
    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http.csrf().disable()
                .authorizeRequests()
                // 放行的请求
                .antMatchers("/oauth/**", "/actuator/**").permitAll()
                .and()
                .authorizeRequests()
                // 其他请求必须认证才能访问
                .anyRequest().authenticated();
    }

}
