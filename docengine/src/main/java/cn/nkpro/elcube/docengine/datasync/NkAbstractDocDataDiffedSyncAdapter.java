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
package cn.nkpro.elcube.docengine.datasync;

import cn.nkpro.elcube.co.spel.NkSpELManager;
import cn.nkpro.elcube.docengine.gen.DocDefDataSync;
import cn.nkpro.elcube.utils.BeanUtilz;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.expression.EvaluationContext;
import org.springframework.util.Assert;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@SuppressWarnings({"all"})
@Slf4j
public abstract class NkAbstractDocDataDiffedSyncAdapter<K> extends NkAbstractDocDataSupport implements NkDocDataSyncAdapter<K> {

    @Autowired
    protected NkSpELManager spELManager;

    protected void doSync(Object dataUnmapping, Object dataOriginalUnmapping, EvaluationContext context1, EvaluationContext context2, DocDefDataSync def){
        if(dataUnmapping instanceof List || dataOriginalUnmapping instanceof List){
            // 对数组进行数据逐条处理

            Assert.isTrue(
                    dataUnmapping instanceof List &&
                    (dataOriginalUnmapping == null || dataOriginalUnmapping instanceof List),
                    "数据同步服务 ["+getBeanName() + "] 数据类型错误");

            List<Object> list1 = (List<Object>)dataUnmapping;
            List<Object> list2 = (List<Object>)dataOriginalUnmapping;

            // 如果新数据、原数据均不为空，对比处理
            // delete
            BeanUtilz.diffList(list1,list2,
                    (row)-> {
                        context1.setVariable("row", row);
                        return (K)(spELManager.invoke(def.getKeySpEL(),context1));
                    },
                    (row)-> {
                        context2.setVariable("row", row);
                        return  (K)(spELManager.invoke(def.getKeySpEL(),context2));
                    },
                    (list)->{
                        // insert
                        this.onInsert(mapping(list, def.getMappingSpEL(),context1), def);
                    },
                    (list)->{
                        // modify
                        this.onModify(mapping(list, def.getMappingSpEL(),context1), def);
                    },
                    (list)->{
                        // remove
                        this.onRemove(mapping(list, def.getMappingSpEL(),context2), def);
                    }
            );
        }else{
            // 更新单条数据，判断前后的key是否一致

            context1.setVariable("row",dataUnmapping);
            K key1 = (K) spELManager.invoke(def.getKeySpEL(),context1);
            Assert.notNull(key1,"数据同步服务 ["+getBeanName() + "] 主键id值不能为空");

            if(dataOriginalUnmapping!=null){
                // 判断前后的key是否一致，不一致的话需要先删除
                context2.setVariable("row",dataOriginalUnmapping);
                K key2 = (K) spELManager.invoke(def.getKeySpEL(),context2);
                if(key2!=null && !Objects.equals(key1,key2)){
                    this.onRemove(mapping(Collections.singletonMap(key2, (Map)dataOriginalUnmapping),def.getMappingSpEL(), context2), def);
                }
            }

            // update
            this.onModify(mapping(Collections.singletonMap(key1, (Map)dataUnmapping),def.getMappingSpEL(),context1), def);
        }
    }

    protected Map<K,Map<String,Object>> mapping(Map<K,Object> source,String mappingSqEL, EvaluationContext context){
        return source.entrySet()
                .stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e-> {
                            context.setVariable("row", e.getValue());
                            return (Map)(spELManager.invoke(mappingSqEL, context));
                        }
                ));
    }

//    protected Object mapping(Object source, String mappingSqEL, EvaluationContext context){
//        if(source instanceof List){
//            return ((List)source).stream()
//                    .map(value->{
//                        context.setVariable("row", value);
//                        return (Map)(spELManager.invoke(mappingSqEL, context));
//                    }).collect(Collectors.toList());
//        }else{
//            context.setVariable("row", source);
//            return (Map)(spELManager.invoke(mappingSqEL, context));
//        }
//    }
}