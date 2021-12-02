package cn.nkpro.tfms.platform.services.impl;

import cn.nkpro.tfms.platform.basis.Constants;
import cn.nkpro.tfms.platform.mappers.gen.SysWebappMenuMapper;
import cn.nkpro.tfms.platform.model.SysWebappMenuBO;
import cn.nkpro.tfms.platform.model.po.SysWebappMenu;
import cn.nkpro.tfms.platform.model.po.SysWebappMenuExample;
import cn.nkpro.tfms.platform.services.TfmsDefDeployAble;
import cn.nkpro.tfms.platform.services.TfmsSysWebappMenuService;
import cn.nkpro.ts5.supports.RedisSupport;
import cn.nkpro.ts5.utils.BeanUtilz;
import cn.nkpro.ts5.utils.DateTimeUtilz;
import cn.nkpro.ts5.utils.SecurityUtilz;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Created by bean on 2020/1/3.
 */
@Slf4j
@Service
public class TfmsSysWebappMenuServiceImpl implements TfmsSysWebappMenuService, TfmsDefDeployAble,InitializingBean {

    @Autowired
    private SysWebappMenuMapper sysWebappMenuMapper;

    @Autowired
    private RedisSupport<List<SysWebappMenuBO>> redisSupport;

    /**
     * 根据当前用户的权限获取对应的菜单
     * @return
     */
    @Override
    public List<SysWebappMenuBO> getMenus(boolean filterAuth){

        List<SysWebappMenuBO> menus = redisSupport.getIfAbsent(Constants.CACHE_NAV_MENUS,()-> {

            SysWebappMenuExample example = new SysWebappMenuExample();
            example.setOrderByClause("ORDER_BY");

            List<SysWebappMenuBO> ret = sysWebappMenuMapper.selectByExample(example).stream()
                    .map(m-> BeanUtilz.copyFromObject(m,SysWebappMenuBO.class))
                    .collect(Collectors.toList());

            ret.stream()
                    .filter(m-> StringUtils.isNotBlank(m.getParentId()))
                    .forEach(m->
                            ret.stream()
                                    .filter(p->StringUtils.equals(p.getMenuId(),m.getParentId()))
                                    .findAny()
                                    .ifPresent(p->{
                                        if(p.getChildren()==null){
                                            p.setChildren(new ArrayList<>());
                                        }
                                        p.getChildren().add(m);
                                    })
                    );

            ret.removeIf(sysWebappMenuBO -> StringUtils.isNotBlank(sysWebappMenuBO.getParentId()));
            return ret;
        });

        if(filterAuth){

            menus.forEach(menu->{
                if(menu.getChildren()!=null){
                    menu.getChildren().removeIf(sub->!(
                            StringUtils.isBlank(sub.getAuthorityOptions())
                                    ||SecurityUtilz.hasAnyAuthority(sub.getAuthorityOptions().split("[|,]"))
                    ));
                }
            });

            menus.removeIf(menu->
                    (menu.getChildren()!=null && menu.getChildren().isEmpty())
                            ||
                            !(
                                    StringUtils.isBlank(menu.getAuthorityOptions())
                                            ||SecurityUtilz.hasAnyAuthority(menu.getAuthorityOptions().split("[|,]"))
                            )
            );
        }

        return menus;
    }

    @Override
    public SysWebappMenu getDetail(String id){

//        try{
//            JSONObject object = JSONObject.parseObject(menu.getMenuOptions());
//            if(object.containsKey("creatable")){
//                object.put(
//                        "creatable",
//                        object.getJSONArray("creatable").stream()
//                                .filter(item->
//                                        SecurityUtilz.hasAnyAuthority(((JSONObject)item).getString("docType")+":WRITE")
//                                )
//                                .collect(Collectors.toList())
//                );
//                menu.setMenuOptions(object.toJSONString());
//            }
//        }catch (Exception e){
//            log.warn(e.getMessage(),e);
//        }

        return sysWebappMenuMapper.selectByPrimaryKey(id);
    }

    @Transactional
    @Override
    public void doUpdate(List<SysWebappMenuBO> menus){
        Long updateTime = DateTimeUtilz.nowSeconds();
        menus.forEach(menu->{
            menu.setOrderBy((menus.indexOf(menu)+1) * 10000L);
            update(menu,updateTime);
            if(menu.getChildren()!=null){
                    menu.getChildren().forEach(m->{
                        m.setParentId(menu.getMenuId());
                        m.setOrderBy(menu.getOrderBy()+menu.getChildren().indexOf(m));
                        update(m,updateTime);
                    });
            }
        });

        SysWebappMenuExample example = new SysWebappMenuExample();
        example.createCriteria().andUpdatedTimeLessThan(updateTime);
        sysWebappMenuMapper.deleteByExample(example);

        redisSupport.delete(Constants.CACHE_NAV_MENUS);
    }

    private void update(SysWebappMenu menu,Long updateTime){
        menu.setUpdatedTime(updateTime);
        if(sysWebappMenuMapper.selectByPrimaryKey(menu.getMenuId())==null){
            sysWebappMenuMapper.insert(menu);
        }else if(menu.getMenuOptions()==null){
            sysWebappMenuMapper.updateByPrimaryKeySelective(menu);
        }else{
            sysWebappMenuMapper.updateByPrimaryKeyWithBLOBs(menu);
        }
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        redisSupport.delete(Constants.CACHE_NAV_MENUS);
    }

    @Override
    public int deployOrder() {
        return Integer.MAX_VALUE;
    }

    @Override
    public List<SysWebappMenu> deployExport(JSONObject config) {
        if(config.containsKey("includeMenu")&&config.getBoolean("includeMenu")){
            return loadMenuOptions(getMenus(false));
        }
        return Collections.emptyList();
    }
    private List<SysWebappMenu> loadMenuOptions(List<? extends SysWebappMenu> menus){
        return menus.stream()
                .map(sysWebappMenuBO -> {
                    if(StringUtils.startsWith(sysWebappMenuBO.getUrl(),"/apps/q")){
                        return getDetail(sysWebappMenuBO.getMenuId());
                    }
                    if(sysWebappMenuBO instanceof SysWebappMenuBO && !CollectionUtils.isEmpty(((SysWebappMenuBO) sysWebappMenuBO).getChildren())){
                        ((SysWebappMenuBO) sysWebappMenuBO).setChildren(loadMenuOptions(((SysWebappMenuBO) sysWebappMenuBO).getChildren()));
                    }
                    return sysWebappMenuBO;
                }).collect(Collectors.toList());
    }

    @Override
    public void deployImport(Object data) {
        if(data!=null)
            doUpdate(((JSONArray)data).toJavaList(SysWebappMenuBO.class));
    }
}
