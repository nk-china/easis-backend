/*
 * This file is part of ELCube.
 *
 * ELCube is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * ELCube is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with ELCube.  If not, see <https://www.gnu.org/licenses/>.
 */
package cn.nkpro.elcube.security.impl;

import cn.nkpro.elcube.basic.Constants;
import cn.nkpro.elcube.basic.NkProperties;
import cn.nkpro.elcube.basic.PageList;
import cn.nkpro.elcube.data.mybatis.pagination.PaginationContext;
import cn.nkpro.elcube.data.redis.RedisSupport;
import cn.nkpro.elcube.platform.gen.*;
import cn.nkpro.elcube.security.HashUtil;
import cn.nkpro.elcube.security.JwtHelper;
import cn.nkpro.elcube.security.UserAccountService;
import cn.nkpro.elcube.security.UserAuthorizationService;
import cn.nkpro.elcube.security.bo.UserAccountBO;
import cn.nkpro.elcube.security.bo.UserDetails;
import cn.nkpro.elcube.utils.BeanUtilz;
import cn.nkpro.elcube.utils.DateTimeUtilz;
import cn.nkpro.elcube.utils.UUIDHexGenerator;
import org.apache.commons.lang3.StringUtils;
import org.apache.ibatis.session.RowBounds;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Created by bean on 2019/12/30.
 */
@Component("NkSysAccountService")
public class UserAccountServiceImpl implements UserAccountService {

    @Autowired@SuppressWarnings("all")
    private UserAccountMapper userAccountMapper;
    @Autowired@SuppressWarnings("all")
    private UserAccountSecretMapper userAccountSecretMapper;

    @Autowired@SuppressWarnings("all")
    private RedisSupport<UserAccount> redisTemplateAccount;

    @Autowired@SuppressWarnings("all")
    private RedisSupport<UserAccountBO> redisTemplate;
    @Autowired@SuppressWarnings("all")
    private NkProperties nkProperties;

    @Autowired@SuppressWarnings("all")
    private UserAuthorizationService authorizationService;

//    @Autowired@SuppressWarnings("all")
//    private UserAccountExtendService userAccountExtendService;
//
//    @Autowired@SuppressWarnings("all")
//    private NkDocOperationService nkDocOperationService;

//    @Autowired@SuppressWarnings("all")
//    private NkAccountOperationService nkAccountOperationService;

    @Override
    public UserAccount getAccountById(String id){
        return StringUtils.isBlank(id)?null:userAccountMapper.selectByPrimaryKey(id);
    }

    public List<UserAccount> getAccountsByObjectId(List<String> docIds) {

        if(CollectionUtils.isEmpty(docIds)){
            return Collections.emptyList();
        }

        UserAccountExample example = new UserAccountExample();
        example.createCriteria()
                .andObjectIdIn(docIds);
        return userAccountMapper.selectByExample(example);
    }

    @Override
    public UserAccountBO getAccountDetail(String username) {
        UserAccountBO account = redisTemplate.getIfAbsent(Constants.CACHE_USERS,username,()-> getAccount(username,null));

        if(account!=null){
            UserAccountSecretExample example = new UserAccountSecretExample();
            example.createCriteria().andAccountIdEqualTo(account.getId());
            account.setSecrets(userAccountSecretMapper.selectByExample(example)
                .stream()
                .peek(e->e.setSecret(null)).collect(Collectors.toList()));
        }

        return account;
    }

    @Override
    public UserAccountBO getAccount(String username, boolean preClear) {
        if(preClear){
            redisTemplate.deleteHash(Constants.CACHE_USERS,username);
        }
        return redisTemplate.getIfAbsent(Constants.CACHE_USERS,username,()-> getAccount(username,null));
    }

    @Override
    public UserAccountSecret getAccountSecretByCode(String type, String code) {

        UserAccountSecretExample example = new UserAccountSecretExample();
        example.createCriteria().andCodeEqualTo(code).andTypeEqualTo(type);

        return userAccountSecretMapper.selectByExample(example)
                .stream()
                .findFirst()
                .orElse(null);
    }

    private UserAccountBO getAccount(String username, String accountId){

        UserAccountExample example = new UserAccountExample();
        if(StringUtils.isNotBlank(username)){
            example.createCriteria().andUsernameEqualTo(username);
        }else if(StringUtils.isNotBlank(accountId)){
            example.createCriteria().andIdEqualTo(accountId);
        }

        return userAccountMapper.selectByExample(example)
                .stream()
                .findAny()
                .map(sysAccount -> {
                    UserAccountBO ud = BeanUtilz.copyFromObject(sysAccount, UserAccountBO.class);
                    ud.setAuthorities(authorizationService.buildGrantedPerms(sysAccount));
                    return ud;
                })
                .orElse(null);
    }

    @Override
    public void clear(){
        redisTemplate.deleteHash(Constants.CACHE_USERS, SecurityContextHolder.getContext().getAuthentication().getPrincipal());
    }

    @Override
    public void checkPasswordStrategyAndSha1(UserAccount account){

        Assert.notNull(account.getPassword(),"密码不能为空");

        // 检查密码是否健壮
        if(StringUtils.isNotBlank(nkProperties.getPasswordStrategy())){
            Assert.isTrue(Pattern.matches(nkProperties.getPasswordStrategy(),account.getPassword()),"密码强度不符合要求");
        }
        // 加密密码 sha1两次
        String password = account.getPassword();
        password = HashUtil.hash(password,"SHA1");
        password = HashUtil.hash(password,"SHA1");
        account.setPassword(password);
    }

    @Override
    public void doChangePassword(String accountId, String oldPassword, String newPassword){

        UserAccount exists = userAccountMapper.selectByPrimaryKey(accountId);

        oldPassword = HashUtil.hash(oldPassword,"SHA1");
        oldPassword = HashUtil.hash(oldPassword,"SHA1");

        Assert.isTrue(StringUtils.equals(exists.getPassword(),oldPassword),"原始密码错误");

        UserAccount account = new UserAccount();
        account.setId(accountId);
        account.setPassword(newPassword);

        checkPasswordStrategyAndSha1(account);

        account.setUpdatedTime(DateTimeUtilz.nowSeconds());
        userAccountMapper.updateByPrimaryKeySelective(account);
        redisTemplate.deleteHash(Constants.CACHE_USERS, exists.getUsername());
    }

    /**
     *
     * 创建一个token，有效期为30分钟，
     * 返回给前端的过期时间为15分钟，
     * token过期后的15分钟以内，用户仍然可以通过token来刷新token
     * @return token信息
     */
    @Override
    public Map<String, Object> createToken() {

        String username = (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        UserAccountBO account = getAccount(username, false);

        Map<String,Object> map = new HashMap<>();
        map.put("username",username);
        map.put("password",account.getPassword());

        long time   = 1000L * 60 * 45;             // 有效期默认为45分钟
        long expire = 1000L * 60 * 15;             // 过期时间设定为15分钟

        String token = JwtHelper.createJWT(map,time);

        Map<String,Object> tokenInfo = new HashMap<>();
        tokenInfo.put("accessToken",token);
        tokenInfo.put("expire",expire);
        tokenInfo.put("refresh",time);

        return tokenInfo;

    }

    @Override
    public Map<String, Object> refreshToken() {
        return createToken();
    }


    @Override
    public UserDetails loadUserById(String accountId) {
        UserAccount account = getAccountById(accountId);
        if(account!=null){
            return loadUserByUsernameFromCache(account.getUsername());
        }
        return null;
    }
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return Optional.ofNullable(getAccount(username,true))
                .map(account-> BeanUtilz.copyFromObject(account, UserDetails.class))
                .orElse(null);
    }
    @Override
    public UserDetails loadUserByUsernameFromCache(String username) throws UsernameNotFoundException {
        return Optional.ofNullable(getAccount(username,false))
                .map(account->BeanUtilz.copyFromObject(account, UserDetails.class))
                .orElse(null);
    }

    @Override
    public PageList<UserAccount> accountsPage(Integer from, Integer size, String orderField, String order, String keyword){

        PaginationContext context = PaginationContext.init();

        UserAccountExample example = new UserAccountExample();
        example.createCriteria()
                .andUsernameLike(String.format("%%%s%%",keyword));

        if(StringUtils.isNotBlank(orderField)){
            example.setOrderByClause(orderField + " "+ StringUtils.defaultIfBlank(order,"ASC"));
        }

        return new PageList<>(
                userAccountMapper.selectByExample(example,new RowBounds(from,size))
                        .stream()
                        .peek(a -> a.setPassword(null))
                        .collect(Collectors.toList()),
                from,
                size,
                context.getTotal()
        );
    }

    @Override
    public UserAccountBO update(UserAccountBO account) {

        if(StringUtils.isNotBlank(account.getId())){

            if(StringUtils.isNotBlank(account.getPassword()))
                checkPasswordStrategyAndSha1(account);

            UserAccount exists = userAccountMapper.selectByPrimaryKey(account.getId());

            if(!StringUtils.equals(exists.getUsername(),account.getUsername())){


                UserAccountExample example = new UserAccountExample();
                example.createCriteria()
                        .andUsernameEqualTo(account.getUsername());

                Assert.isTrue(userAccountMapper.countByExample(example)==0,"用户名已存在，请更换");
            }

            account.setUpdatedTime(DateTimeUtilz.nowSeconds());
            userAccountMapper.updateByPrimaryKeySelective(account);
            redisTemplate.deleteHash(Constants.CACHE_USERS, exists.getUsername());
            redisTemplate.deleteHash(Constants.CACHE_USERS, account.getUsername());
        }else{
            checkPasswordStrategyAndSha1(account);

            UserAccountExample example = new UserAccountExample();
            example.createCriteria()
                    .andUsernameEqualTo(account.getUsername());

            Assert.isTrue(userAccountMapper.countByExample(example)==0,"用户名已存在，请更换");

            account.setId(UUIDHexGenerator.generate());
            account.setLocked(0);
            account.setCreatedTime(DateTimeUtilz.nowSeconds());
            account.setUpdatedTime(DateTimeUtilz.nowSeconds());
            userAccountMapper.insert(account);
        }

        return account;
    }

    @Override
    public void clearLoginLock(UserAccountBO user) {
        redisTemplate.delete(Constants.CACHE_AUTH_ERROR+user.getUsername());
    }

//    @Override
//    public UserDetails getAccountByMobileTerminal(String phone, String openId, String appleId) {
//        if(StringUtils.isBlank(phone) && StringUtils.isBlank(openId) && StringUtils.isBlank(appleId)){
//            return null;
//        }
//
//        UserAccountExtendExample example = new UserAccountExtendExample();
//        if(StringUtils.isNoneBlank(phone)){
//            example.createCriteria().andPhoneEqualTo(phone);
//        }else if(StringUtils.isNoneBlank(openId)){
//            example.createCriteria().andOpenidEqualTo(openId);
//        }else if(StringUtils.isNoneBlank(appleId)){
//            example.createCriteria().andAppleidEqualTo(appleId);
//        }
//        List<UserAccountExtend> userAccountExtends = userAccountExtendService.selectByExample(example);
//        if(!CollectionUtils.isEmpty(userAccountExtends)){
//            UserAccountExtend userAccountExtend = userAccountExtends.get(0);
//            if(StringUtils.equals(openId,userAccountExtend.getOpenid())){
//                userAccountExtend.setOpenid(openId);
//                userAccountExtendService.updateUserAccountExtend(userAccountExtend);
//            }
//            if(StringUtils.equals(appleId,userAccountExtend.getAppleid())){
//                userAccountExtend.setAppleid(appleId);
//                userAccountExtendService.updateUserAccountExtend(userAccountExtend);
//            }
//            return loadUserByAccountId(userAccountExtends.get(0).getAccountId());
//        }
//        return null;
//    }
//
//
//    @Override
//    public UserAccountBO getAccountById(String accountId, boolean preClear) {
//        if(preClear){
//            redisTemplate.deleteHash(Constants.CACHE_USERS,accountId);
//        }
//        return redisTemplate.getIfAbsent(Constants.CACHE_USERS,accountId,()-> {
//            UserAccount sysAccount = getAccount(null, accountId);
//            UserAccountBO ud = BeanUtilz.copyFromObject(sysAccount, UserAccountBO.class);
//            ud.setAuthorities(authorizationService.buildGrantedPerms(sysAccount));
//            return ud;
//        });
//    }
//
//    /**
//     *
//     * 创建一个token，有效期为30分钟，
//     * 返回给前端的过期时间为15分钟，
//     * token过期后的15分钟以内，用户仍然可以通过token来刷新token
//     * @return token信息
//     */
//    @Override
//    public Map<String, Object> createTokenMobileTerminal(String phone, String openId, String appleId) {
//
//        Map<String,Object> map = new HashMap<>();
//        map.put("phone", phone);
//        map.put("openId", openId);
//        map.put("appleId", appleId);
//
//        long time   = 1000L * 60 * 45;             // 有效期默认为45分钟
//        long expire = 1000L * 60 * 15;             // 过期时间设定为15分钟
//
//        String token = JwtHelper.createJWT(map,time);
//
//        Map<String,Object> tokenInfo = new HashMap<>();
//        tokenInfo.put("accessToken",token);
//        tokenInfo.put("expire",expire);
//        tokenInfo.put("refresh",time);
//
//        return tokenInfo;
//
//    }
//
//    @Override
//    public Map<String, Object> refreshTokenMobileTerminal() {
//        AuthMobileTerminal authMobileTerminal = JSONObject.parseObject((String) SecurityContextHolder.getContext().getAuthentication().getPrincipal(),AuthMobileTerminal.class);
//        return createTokenMobileTerminal(authMobileTerminal.getPhone(),authMobileTerminal.getOpenId(),authMobileTerminal.getAppleId());
//    }
//
//    @Override
//    public Map<String,Object> appLogin(String phone, String openId, String appleId) {
//        if(StringUtils.isAllBlank(phone,openId,appleId)){
//            return null;
//        }
//        return createTokenMobileTerminal(phone, openId, appleId);
//    }
}
