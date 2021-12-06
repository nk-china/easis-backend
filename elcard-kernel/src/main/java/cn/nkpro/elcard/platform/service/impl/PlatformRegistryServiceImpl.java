/*
 * This file is part of ELCard.
 *
 * ELCard is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * ELCard is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with ELCard.  If not, see <https://www.gnu.org/licenses/>.
 */
package cn.nkpro.elcard.platform.service.impl;

import cn.nkpro.elcard.annotation.Keep;
import cn.nkpro.elcard.basic.Constants;
import cn.nkpro.elcard.basic.TransactionSync;
import cn.nkpro.elcard.data.redis.RedisSupport;
import cn.nkpro.elcard.platform.DeployAble;
import cn.nkpro.elcard.platform.gen.PlatformRegistry;
import cn.nkpro.elcard.platform.gen.PlatformRegistryExample;
import cn.nkpro.elcard.platform.gen.PlatformRegistryKey;
import cn.nkpro.elcard.platform.gen.PlatformRegistryMapper;
import cn.nkpro.elcard.platform.service.PlatformRegistryService;
import cn.nkpro.elcard.utils.BeanUtilz;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Order(0)
@Service
public class PlatformRegistryServiceImpl implements PlatformRegistryService , DeployAble {

    @Autowired@SuppressWarnings("all")
    private PlatformRegistryMapper constantMapper;

    @Autowired@SuppressWarnings("all")
    private RedisSupport<PlatformRegistry> redisSupport;


    @Override
    public List<PlatformRegistry> getAllByType(String type){
        PlatformRegistryExample example = new PlatformRegistryExample();
        if(StringUtils.isNotBlank(type))
            example.createCriteria().andRegTypeEqualTo(type);
        example.setOrderByClause("REG_TYPE,ORDER_BY");
        return constantMapper.selectByExampleWithBLOBs(example);
    }

    private PlatformRegistry getFromCache(String regType, String regKey){
        return redisSupport.getIfAbsent(
                Constants.CACHE_DEF_CONSTANT,
                String.format("%s.%s",regType,regKey),
                ()->{
                    PlatformRegistryKey pk = new PlatformRegistryKey();
                    pk.setRegType(regType);
                    pk.setRegKey(regKey);
                    return constantMapper.selectByPrimaryKey(pk);
                });
    }

    @Override
    public Object getJSON(String regType, String regKey){

        PlatformRegistry registry = getFromCache(regType, regKey);

        if(registry!=null){
            return JSON.parse(registry.getContent());
        }
        return null;
    }

    @Override
    public String getString(String regType, String regKey){

        PlatformRegistry registry = getFromCache(regType, regKey);

        if(registry!=null){
            return registry.getContent();
        }
        return null;
    }


    @Override
    public List<Object> getList(String regType, String regKeyPrefix){

        PlatformRegistryExample example = new PlatformRegistryExample();
        PlatformRegistryExample.Criteria criteria = example.createCriteria()
                .andRegTypeEqualTo(regType);

        if(!StringUtils.equals(regKeyPrefix,"@")){
            criteria.andRegKeyLike(regKeyPrefix+"%");
        }

        example.setOrderByClause("ORDER_BY");
        return constantMapper.selectByExampleWithBLOBs(example)
            .stream()
            .map(item-> JSON.parse(item.getContent()))
            .collect(Collectors.toList());
    }

    @Override
    public PlatformRegistry getValue(String key){

        String regType = StringUtils.substringBefore(key,".");
        String regKey  = StringUtils.substringAfter(key,".");

        return getFromCache(regType, regKey);
    }

    @Override
    public void updateValue(PlatformRegistry registry){
        if(constantMapper.selectByPrimaryKey(registry)==null){
            registry.setOrderBy(0);
            constantMapper.insert(registry);
        }else{
            constantMapper.updateByPrimaryKeyWithBLOBs(registry);
            TransactionSync.runAfterCommit("redis",()->
                redisSupport.deleteHash(
                    Constants.CACHE_DEF_CONSTANT,
                    String.format("%s.%s",registry.getRegType(),registry.getRegKey())
                )
            );
        }
    }

    @Override
    public void deleteValue(PlatformRegistry registry){
        constantMapper.deleteByPrimaryKey(registry);
        TransactionSync.runAfterCommit("redis",()->
                redisSupport.deleteHash(
                        Constants.CACHE_DEF_CONSTANT,
                        String.format("%s.%s",registry.getRegType(),registry.getRegKey())
                )
        );
    }

    @Override
    public List<TreeNode> getTree(){
        PlatformRegistryExample example = new PlatformRegistryExample();
        example.setOrderByClause("REG_KEY");


        Map<String,TreeNode> cache = new LinkedHashMap<>();

        List<TreeNode> tree = new ArrayList<>();

        TreeNode meter = new TreeNode("@METER",null,"METER|表盘");
        TreeNode dict  = new TreeNode("@DICT",null, "DICT|字典");
        TreeNode note  = new TreeNode("@DATASET",null, "DATASET|数据集");
        TreeNode page  = new TreeNode("@PAGE",null, "PAGE|页面");

        cache.put(meter.getKey(),meter);
        tree.add(meter);
        cache.put(dict.getKey(),dict);
        tree.add(dict);
        cache.put(note.getKey(),note);
        tree.add(note);
        cache.put(page.getKey(),page);
        tree.add(page);

        constantMapper.selectByExample(example)
            .forEach(registry->{
                TreeNode parentNode = findParent(cache, null, registry.getRegType(), registry.getRegKey().split("[.]"),0);

                TreeNode node = new TreeNode(registry);

                parentNode.getChildren().add(node);
                cache.put(node.getKey(),node);
            });

        return tree;
    }

    private TreeNode findParent(Map<String,TreeNode> cache, TreeNode parent,String type, String[] splitKeys,int level){

        String key =  Arrays.stream(splitKeys)
                .limit(level)
                .collect(Collectors.joining("."));
        String parentKey = type + (StringUtils.isBlank(key)?StringUtils.EMPTY:".") + key;

        TreeNode parentNode = cache.computeIfAbsent(parentKey, (k) -> {
            TreeNode node = new TreeNode(type, key, null);
            if(parent!=null){
                parent.getChildren().add(node);
            }
            return node;
        });

        if(parentNode.getChildren() == null){
            parentNode.setChildren(new ArrayList<>());
            parentNode.setIsLeaf(false);
        }

        if(level<splitKeys.length-1){
            parentNode = findParent(cache, parentNode, type, splitKeys, ++level);
        }

        return parentNode;
    }

    @Override
    public void loadExport(JSONArray exports) {
        JSONObject export = new JSONObject();
        export.put("key","includeRegistry");
        export.put("name","基础配置");
        exports.add(export);
    }

    @Override
    public void exportConfig(JSONObject config, JSONObject export) {
        if(config.getBooleanValue("includeRegistry")){
            export.put("registries",getAllByType(null));
        }
    }

    @Override
    public void importConfig(JSONObject data) {
        if(data.containsKey("registries")){
            doUpdate(data.getJSONArray("registries").toJavaList(PlatformRegistry.class));
        }
    }

    @EqualsAndHashCode(callSuper = true)
    @Keep
    @Data
    public static class TreeNode extends PlatformRegistry{
        private String key;
        private String title;
        private Boolean isLeaf;
        private List<TreeNode> children;

        TreeNode(String type, String key, String title){
            this.key = type+(StringUtils.isBlank(key)?StringUtils.EMPTY:".")+StringUtils.defaultString(key);
            this.setRegType(type);
            this.setRegKey(key);
            this.title = StringUtils.defaultString(title,StringUtils.substringAfterLast(this.key,"."));
        }

        TreeNode(PlatformRegistry registry){
            this.key = registry.getRegType()+'.'+registry.getRegKey();
            this.title = registry.getTitle();
            this.isLeaf = true;
            BeanUtilz.copyFromObject(registry,this);
        }
    }

    @Override
    public void doUpdate(List<PlatformRegistry> list){

        constantMapper.deleteByExample(null);
        list.forEach(defConstant -> constantMapper.insert(defConstant));
        redisSupport.delete(Constants.CACHE_DEF_CONSTANT);
    }
}
