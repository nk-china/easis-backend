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
import cn.nkpro.elcube.basic.GUID;
import cn.nkpro.elcube.co.spel.NkSpELManager;
import cn.nkpro.elcube.data.redis.RedisSupport;
import cn.nkpro.elcube.exception.NkDefineException;
import cn.nkpro.elcube.exception.NkException;
import cn.nkpro.elcube.exception.NkInputFailedCaution;
import cn.nkpro.elcube.platform.DeployAble;
import cn.nkpro.elcube.platform.gen.UserAccount;
import cn.nkpro.elcube.platform.gen.UserAccountExample;
import cn.nkpro.elcube.platform.gen.UserAccountMapper;
import cn.nkpro.elcube.security.UserAuthorizationService;
import cn.nkpro.elcube.security.UserBusinessAdapter;
import cn.nkpro.elcube.security.bo.GrantedAuthority;
import cn.nkpro.elcube.security.bo.GrantedAuthoritySub;
import cn.nkpro.elcube.security.bo.UserGroupBO;
import cn.nkpro.elcube.security.gen.*;
import cn.nkpro.elcube.utils.BeanUtilz;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.ibatis.session.RowBounds;
import org.elasticsearch.index.query.QueryBuilders;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.expression.EvaluationContext;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@Order(20)
@Slf4j
@Service
public class UserAuthorizationServiceImpl implements UserAuthorizationService, DeployAble {

    @Autowired@SuppressWarnings("all")
    private GUID guid;
    @Autowired@SuppressWarnings("all")
    private AuthGroupMapper authGroupMapper;
    @Autowired@SuppressWarnings("all")
    private AuthGroupRefMapper authGroupRefMapper;
    @Autowired@SuppressWarnings("all")
    private AuthPermissionMapper authPermissionMapper;
    @Autowired@SuppressWarnings("all")
    private AuthLimitMapper authLimitMapper;
    @Autowired@SuppressWarnings("all")
    private UserAccountMapper accountMapper;

    @Autowired@SuppressWarnings("all")
    private RedisSupport<UserGroupBO> redisSupport;
    @Autowired@SuppressWarnings("all")
    private RedisSupport<AuthLimit> redisSupportLimit;
    @Autowired@SuppressWarnings("all")
    private NkSpELManager spELManager;
    @Autowired@SuppressWarnings("all")
    private UserBusinessAdapter userBusinessAdapter;


    /**
     * 创建指定账号的权限集合
     * @param account 账号
     * @return List<NkGrantedAuthority>
     */
    @Override
    public List<GrantedAuthority> buildGrantedPerms(UserAccount account){

        // 构造权限列表
        List<GrantedAuthority> permList = new ArrayList<>();

        AuthGroupRefExample authGroupRefExample = new AuthGroupRefExample();
        authGroupRefExample.createCriteria()
                .andRefIdEqualTo(account.getId())
                .andRefTypeEqualTo(GROUP_TO_ACCOUNT);

        authGroupRefMapper.selectByExample(authGroupRefExample)
                .forEach((ref->{
                    UserGroupBO group = buildUserGroup(ref.getGroupId());
                    if(group!=null&&group.getAuthorities()!=null){
                        permList.addAll(group.getAuthorities());
                    }
                }));

        // 处理limit
        Set<String> limitIds = new HashSet<>();
        permList.forEach(grantedAuthority -> {
            if(grantedAuthority.getLimitIds()!=null){
                limitIds.addAll(Arrays.asList(grantedAuthority.getLimitIds()));
            }
        });

        Map<String,AuthLimit> limits = limitIds.stream()
                .map(limitId->
                    redisSupportLimit.getIfAbsent(Constants.CACHE_AUTH_LIMIT,limitId,
                            ()-> authLimitMapper.selectByPrimaryKey(limitId))
                ).collect(Collectors.toMap(AuthLimit::getLimitId,v->v));


        final AtomicReference<EvaluationContext> context = new AtomicReference<>();

        permList.forEach(authority -> {
            if(authority.getLimitIds()!=null){

                List<String> query = Arrays.stream(authority.getLimitIds())
                        .map(limits::get)
                        .filter(limit->limit!=null && limit.getContent()!=null)
                        .map(AuthLimit::getContent)
                        .map(limit->{
                            if(spELManager.hasTemplate(limit)){
                                if(context.get()==null){
                                    Object user = userBusinessAdapter.getUser(account);
                                    context.set(spELManager.createContext(user));
                                }
                                return spELManager.convert(limit,context.get());
                            }else{
                                return limit;
                            }
                        })
                        .collect(Collectors.toList());
                if(query.size()>1){
                    authority.setLimitQuery(
                            query.stream()
                                    .collect(Collectors.joining(",", "{\"bool\":{\"must\":[", "]}}"))
                    );
                }else if(query.size()==1){
                    authority.setLimitQuery(query.get(0));
                }
            }
        });

        permList.forEach(perm->
            permList.stream().filter(e -> StringUtils.equals(e.getAuthority(), perm.getAuthority()))
                    .findFirst()
                    .ifPresent(first-> perm.setDisabled(perm!=first))
        );

        return permList.stream()
                    .sorted()
                    .collect(Collectors.toList());
    }

    /**
     * 创建一个用户组模型
     * @param groupId 用户组Id
     * @return 用户组
     */
    private UserGroupBO buildUserGroup(String groupId){

        return redisSupport.getIfAbsent(Constants.CACHE_AUTH_GROUP,groupId,()->{

            AuthGroup sysAuthGroup = authGroupMapper.selectByPrimaryKey(groupId);

            if(sysAuthGroup!=null){

                UserGroupBO g = BeanUtilz.copyFromObject(sysAuthGroup, UserGroupBO.class);

                AuthGroupRefExample authGroupRefExample = new AuthGroupRefExample();
                authGroupRefExample.createCriteria()
                        .andGroupIdEqualTo(groupId)
                        .andRefTypeEqualTo(GROUP_TO_PERM);

                List<String> permIds = authGroupRefMapper.selectByExample(authGroupRefExample)
                        .stream()
                        .map(AuthGroupRefKey::getRefId)
                        .collect(Collectors.toList());

                if(!permIds.isEmpty()){

                    // 查询Group下的权限定义
                    AuthPermissionExample authPermissionExample = new AuthPermissionExample();
                    authPermissionExample.createCriteria()
                            .andPermIdIn(permIds);
                    authPermissionExample.setOrderByClause("PERM_DESC");

                    g.setPermissions(authPermissionMapper.selectByExampleWithBLOBs(authPermissionExample));

                    // 创建Group下的授权模型
                    List<GrantedAuthority> authoritys = new ArrayList<>();
                    g.getPermissions()
                        .forEach(permission -> {
                            if(StringUtils.startsWith(permission.getPermResource(),Constants.BIZ_DEFAULT_EMPTY)
                                    &&StringUtils.containsAny(permission.getPermResource(),'|',',')){
                                // 多单据权限
                                authoritys.addAll(
                                    Arrays.stream(
                                        permission.getPermResource().substring(1).split("[|,]")
                                    )
                                    .map(resource->
                                        buildAuthority(StringUtils.join(Constants.BIZ_DEFAULT_EMPTY,resource),permission,g)
                                    )
                                    .collect(Collectors.toList())
                                );
                            }else{
                                // 单单据及其他权限
                                authoritys.add(
                                    buildAuthority(permission.getPermResource(),permission,g)
                                );
                            }
                        });

                    g.setAuthorities(authoritys.stream().sorted().collect(Collectors.toList()));
                }
                return g;
            }
            return null;
        });
    }

    /**
     * 创建一个授权对象
     * @param resource resource
     * @param perm perm
     * @param group group
     * @return NkGrantedAuthority
     */
    private GrantedAuthority buildAuthority(String resource, AuthPermission perm, AuthGroup group){
        GrantedAuthority authority = new GrantedAuthority();
        authority.setPermResource(resource);
        authority.setPermOperate(perm.getPermOperate());
        authority.parseSubPerm(perm.getSubResource());
        authority.setLimitIds(StringUtils.split(perm.getLimitId(),'|'));
        authority.setLevel(perm.getPermLevel());
        authority.setFromPermissionId(perm.getPermId());
        authority.setFromPermissionDesc(perm.getPermDesc());
        authority.setFromGroupId(group.getGroupId());
        authority.setFromGroupDesc(group.getGroupDesc());
        authority.setAuthority(String.format("%s:%s",authority.getPermResource(),authority.getPermOperate()));
        return authority;
    }

    //// 以下为管理代码

    @Override
    public List<AuthLimit> getLimits(String[] limitIds){
        AuthLimitExample example = new AuthLimitExample();
        if(ArrayUtils.isNotEmpty(limitIds))
            example.createCriteria().andLimitIdIn(Arrays.asList(limitIds));
        example.setOrderByClause("LIMIT_DESC");
        return authLimitMapper.selectByExample(example);
    }

    @Override
    public AuthLimit getLimitDetail(String limitId){
        if(StringUtils.isNotBlank(limitId)){
            return redisSupportLimit.getIfAbsent(Constants.CACHE_AUTH_LIMIT,limitId,
                ()-> authLimitMapper.selectByPrimaryKey(limitId));
        }
        return null;
    }
    @Override
    public void updateLimit(AuthLimit limit){
        Assert.hasText(limit.getLimitDesc(),"限制描述不能为空");

        try{
            QueryBuilders.wrapperQuery(limit.getContent());
        }catch (IllegalArgumentException e){
            throw new NkInputFailedCaution("限制规则配置错误");
        }

        if(StringUtils.isBlank(limit.getLimitId())){
            limit.setLimitId(guid.nextId(AuthLimit.class));
            authLimitMapper.insert(limit);
        }else{
            authLimitMapper.updateByPrimaryKeyWithBLOBs(limit);
            redisSupportLimit.deleteHash(Constants.CACHE_AUTH_LIMIT,limit.getLimitId());
        }
    }
    @Override
    public void removeLimit(String limitId){
        authLimitMapper.deleteByPrimaryKey(limitId);
        redisSupportLimit.deleteHash(Constants.CACHE_AUTH_LIMIT,limitId);
    }


    @Override
    public List<AuthPermission> getPerms(){
        AuthPermissionExample example = new AuthPermissionExample();
        example.setOrderByClause("PERM_DESC");
        return authPermissionMapper.selectByExample(example);
    }

    @Override
    public AuthPermission getPermDetail(String permId){
        return authPermissionMapper.selectByPrimaryKey(permId);
    }
    @Override
    public void updatePerm(AuthPermission perm){
        Assert.hasText(perm.getPermDesc(),"权限描述不能为空");
        Assert.hasText(perm.getPermResource(),"权限资源不能为空");
        Assert.hasText(perm.getPermOperate(),"权限操作不能为空");

        if(StringUtils.isNotBlank(perm.getSubResource())){
            try{
                JSON.parseObject(perm.getSubResource(), GrantedAuthoritySub.class);
            }catch (Exception e){
                throw new NkDefineException("操作限制不合法："+e.getMessage());
            }
        }

        if(StringUtils.isNotBlank(perm.getLimitId())){
            perm.setLimitId(
                Arrays.stream(StringUtils.split(perm.getLimitId(),'|'))
                    .sorted()
                    .collect(Collectors.joining("|"))
            );
        }

        if(StringUtils.isBlank(perm.getPermId())){
            perm.setPermId(guid.nextId(AuthPermission.class));
            authPermissionMapper.insert(perm);
        }else{
            authPermissionMapper.updateByPrimaryKeyWithBLOBs(perm);
            clearGroupByPerm(perm.getPermId());
        }
    }
    @Override
    public void removePerm(String permId){

        Assert.isTrue(!permId.startsWith("nk-default-"),"系统权限不可移除");

        authPermissionMapper.deleteByPrimaryKey(permId);
        clearGroupByPerm(permId);
    }

    private void clearGroupByPerm(String permId){

        AuthGroupRefExample authGroupRefExample = new AuthGroupRefExample();
        authGroupRefExample.createCriteria()
                .andRefIdEqualTo(permId)
                .andRefTypeEqualTo(GROUP_TO_PERM);

        Object[] groupIds = authGroupRefMapper.selectByExample(authGroupRefExample)
                .stream()
                .map(AuthGroupRefKey::getGroupId)
                .distinct()
                .toArray(String[]::new);

        if(groupIds.length>0){
            redisSupport.deleteHash(Constants.CACHE_AUTH_GROUP,groupIds);
        }

    }



    @Override
    public List<AuthGroup> getGroups(){
        AuthGroupExample example = new AuthGroupExample();
        example.setOrderByClause("GROUP_DESC");
        return authGroupMapper.selectByExample(example);
    }

    @Override
    public List<UserGroupBO> getGroupBOs(){
        AuthGroupExample example = new AuthGroupExample();
        example.setOrderByClause("GROUP_DESC");
        return authGroupMapper.selectByExample(example).stream().map(g->buildUserGroup(g.getGroupId())).collect(Collectors.toList());
    }

    @Override
    public UserGroupBO getGroupDetail(String groupId){

        UserGroupBO group = buildUserGroup(groupId);

        // 查询用户组下的账号
        AuthGroupRefExample authGroupRefExample = new AuthGroupRefExample();
        authGroupRefExample.createCriteria()
                .andGroupIdEqualTo(groupId)
                .andRefTypeEqualTo(GROUP_TO_ACCOUNT);

        List<String> accountIds = authGroupRefMapper.selectByExample(authGroupRefExample)
                .stream()
                .map(AuthGroupRefKey::getRefId)
                .collect(Collectors.toList());

        if(!accountIds.isEmpty()){

            UserAccountExample accountExample = new UserAccountExample();
            accountExample.createCriteria()
                    .andIdIn(accountIds);
            accountExample.setOrderByClause("USERNAME");

            group.setAccounts(
                    accountMapper.selectByExample(accountExample)
                            .stream()
                            .peek(a -> a.setPassword(null))
                            .collect(Collectors.toList()));
        }

        return group;
    }

    @SneakyThrows
    @Override
    public UserGroupBO getGroupDetailByKey(String groupKey) {
        AuthGroupExample authGroupExample = new AuthGroupExample();
        authGroupExample.createCriteria().andGroupKeyEqualTo(groupKey);
        List<AuthGroup> authGroups = authGroupMapper.selectByExample(authGroupExample);
        if(authGroups.isEmpty()) throw new NkException("当前用户组id为null,请检查");
        if(authGroups.size() == 1){
            AuthGroup authGroup = authGroups.get(0);
            return getGroupDetail(authGroup.getGroupId());
        }
        return null;
    }

    @SneakyThrows
    @Override
    public void updateGroup(UserGroupBO group,Boolean ignore){


        Assert.isTrue(StringUtils.isBlank(group.getGroupId()) || !group.getGroupId().startsWith("nk-default-"),"系统用户组不可更新");
        Assert.hasText(group.getGroupDesc(),"权限描述不能为空");

        if(StringUtils.isBlank(group.getGroupId())){
            group.setGroupId(guid.nextId(AuthGroup.class));
            authGroupMapper.insert(group);
        }else{
            if(!checkGroupKey(group,ignore)){
                throw new NkException("用户组Key不能重复，请重新填写");
            }
            authGroupMapper.updateByPrimaryKey(group);

            AuthGroupRefExample example = new AuthGroupRefExample();
            example.createCriteria()
                    .andGroupIdEqualTo(group.getGroupId())
                    .andRefTypeEqualTo(GROUP_TO_PERM);
            authGroupRefMapper.deleteByExample(example);
        }

        if(group.getPermissions()!=null){
            group.getPermissions()
                    .forEach(perm->{
                        AuthGroupRefKey ref = new AuthGroupRefKey();
                        ref.setGroupId(group.getGroupId());
                        ref.setRefId(perm.getPermId());
                        ref.setRefType(GROUP_TO_PERM);
                        authGroupRefMapper.insert(ref);

                    });
        }

        redisSupport.deleteHash(Constants.CACHE_AUTH_GROUP,group.getGroupId());
    }
    @Override
    public void removeGroup(String groupId){

        Assert.isTrue(!groupId.startsWith("nk-default-"),"系统用户组不可删除");

        AuthGroupRefExample example = new AuthGroupRefExample();
        example.createCriteria().andGroupIdEqualTo(groupId);
        authGroupRefMapper.deleteByExample(example);

        authGroupMapper.deleteByPrimaryKey(groupId);

        redisSupport.deleteHash(Constants.CACHE_AUTH_GROUP,groupId);
    }

    @Override
    public Boolean checkGroupKey(UserGroupBO group,Boolean ignore) {
        AuthGroupExample authGroupExample = new AuthGroupExample();
        AuthGroupExample.Criteria criteria = authGroupExample.createCriteria();
        // ignore表示是否忽略判断(目前用于配置的导入问题)
        if(ignore || group.getGroupKey() == null || StringUtils.isBlank(group.getGroupKey())){
            return true;
        }
        criteria.andGroupKeyEqualTo(group.getGroupKey());
        if(group.getGroupId() != null){
            criteria.andGroupIdNotEqualTo(group.getGroupId());
        }
        List<AuthGroup> authGroups = authGroupMapper.selectByExample(authGroupExample);
        return authGroups.isEmpty();
    }

    @Override
    public void removeAccountFromGroup(String groupId, String accountId){

        Assert.isTrue(!(groupId.startsWith("nk-default-") && accountId.startsWith("nk-default-")),"系统用户不可移除");

        AuthGroupRefExample example = new AuthGroupRefExample();
        example.createCriteria()
                .andGroupIdEqualTo(groupId)
                .andRefIdEqualTo(accountId)
                .andRefTypeEqualTo(GROUP_TO_ACCOUNT);
        authGroupRefMapper.deleteByExample(example);
    }

    @Override
    public void addAccountFromGroup(String groupId, String accountId){
        removeAccountFromGroup(groupId,accountId);

        AuthGroupRefKey ref = new AuthGroupRefKey();
        ref.setGroupId(groupId);
        ref.setRefId(accountId);
        ref.setRefType(GROUP_TO_ACCOUNT);
        authGroupRefMapper.insert(ref);
    }

    @Override
    public List<UserAccount> accounts(String keyword){
        UserAccountExample example = new UserAccountExample();
        example.createCriteria()
                .andUsernameLike(String.format("%%%s%%",keyword));

        return accountMapper.selectByExample(example,new RowBounds(0,10))
                .stream()
                .peek(a -> a.setPassword(null))
                .collect(Collectors.toList());
    }

    @Override
    public void loadExport(JSONArray exports) {
        JSONObject export = new JSONObject();
        export.put("key","includeAuth");
        export.put("name","用户组与权限");
        exports.add(export);
    }

    @Override
    public void exportConfig(JSONObject config, JSONObject export) {

        if(config.getBooleanValue("includeAuth")){
            export.put("groups",getGroupBOs()
                    .stream()
                    .filter(group->!group.getGroupId().startsWith("nk-default-"))
                    .collect(Collectors.toList()));
            export.put("perms",getPerms());
            export.put("limits",getLimits(null)
                    .stream().map(limit->getLimitDetail(limit.getLimitId()))
                    .collect(Collectors.toList()));
        }
    }

    @Override
    public void importConfig(JSONObject data) {

        if(data.containsKey("limits")){
            data.getJSONArray("limits").toJavaList(AuthLimit.class)
                    .forEach(this::updateLimit);
        }
        if(data.containsKey("perms")){
            data.getJSONArray("perms").toJavaList(AuthPermission.class)
                    .forEach(this::updatePerm);
        }
        if(data.containsKey("groups")){
            for (UserGroupBO group : data.getJSONArray("groups").toJavaList(UserGroupBO.class)) {
                if (!group.getGroupId().startsWith("nk-default-")) {
                    updateGroup(group,true);
                }
            }
        }
    }
}
