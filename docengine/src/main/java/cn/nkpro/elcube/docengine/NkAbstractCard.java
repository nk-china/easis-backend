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
package cn.nkpro.elcube.docengine;

import cn.nkpro.elcube.annotation.NkNote;
import cn.nkpro.elcube.co.NkAbstractCustomScriptObject;
import cn.nkpro.elcube.co.NkScriptV;
import cn.nkpro.elcube.co.spel.NkSpELManager;
import cn.nkpro.elcube.docengine.model.DocDefHV;
import cn.nkpro.elcube.docengine.model.DocDefIV;
import cn.nkpro.elcube.docengine.model.DocHV;
import cn.nkpro.elcube.exception.NkDefineException;
import cn.nkpro.elcube.exception.NkSystemException;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import lombok.Getter;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public abstract class NkAbstractCard<DT,DDT> extends NkAbstractCustomScriptObject implements NkCard<DT,DDT> {

    protected Logger log = LoggerFactory.getLogger(getClass());

    @Autowired
    protected NkSpELManager spELManager;

    @Getter
    private String cardName;

    @Getter
    private String position = NkCard.POSITION_DEFAULT;

    public NkAbstractCard(){
        super();
        this.cardName = Optional.ofNullable(getClass().getAnnotation(NkNote.class))
                .map(NkNote::value)
                .orElse(beanName);
    }

    @Override
    public boolean isDebug() {
        return getScriptDef().isDebug();
    }

    @Override
    public String getDataComponentName() {
        if(StringUtils.isNotBlank(scriptDef.getVueMain())){
            return beanName;
        }
        return null;
    }

    @Override
    public final String[] getAutoDefComponentNames() {

        NkScriptV scriptDef = scriptDefHV();

        if(scriptDef!=null && StringUtils.isNotBlank(scriptDef.getVueDefs())){
            JSONArray array = JSON.parseArray(scriptDef.getVueDefs());
            String[] names = new String[array.size()];
            for(int i=0;i < array.size();i++){
                names[i]=getBeanName()+(i==0?"Def":("Def"+i));
            }
            return names;
        }

        return getDefComponentNames();
    }

    @Override
    public Map<String,String> getVueTemplate(){

        NkScriptV scriptDef = scriptDefHV();

        if(scriptDef!=null){
            Map<String,String> vueMap = new HashMap<>();
            if(StringUtils.isNotBlank(scriptDef.getVueMain())){
                vueMap.put(scriptDef.getScriptName(),scriptDef.getVueMain());
            }
            if(StringUtils.isNotBlank(scriptDef.getVueDefs())){
                JSONArray array = JSON.parseArray(scriptDef.getVueDefs());
                array.forEach((item)->{
                    int index = array.indexOf(item);
                    vueMap.put(scriptDef.getScriptName()+"Def"+(index==0?"":index), (String) item);
                });
            }
            return vueMap;
        }
        return Collections.emptyMap();
    }

    protected String[] getDefComponentNames() {
        return ArrayUtils.EMPTY_STRING_ARRAY;
    }


    /**
     * 预解析卡片配置
     */
    @Override
    @SuppressWarnings("unchecked")
    public final DDT deserializeDef(Object defContent){
        return (DDT) parse(defContent,getType(1));
    }

    public DDT afterGetDef(DocDefHV defHV, DocDefIV defIV, DDT def){
        return def;
    }

    @Override
    public Object callDef(DDT def, Object options) {
        return null;
    }

    /**
     * 预解析数据，并转换成DT类型
     */
    @Override
    @SuppressWarnings("all")
    public final DT deserialize(Object data){
        return (DT) parse(data,getType(0));
    }

    @Override
    public DT afterCreate(DocHV doc, DocHV preDoc, DT data, DocDefIV defIV, DDT d){
        return data;
    }


    @Override
    public DT afterGetData(DocHV doc, DT data, DocDefIV defIV, DDT d) {
        return data;
    }

    /**
     * 将数据进行一次计算，再返回
     * @param doc
     * @param docDef
     * @return
     * @throws Exception
     */
    @Override
    @SuppressWarnings("all")
    public DT calculate(DocHV doc, DT data, DocDefIV defIV, DDT d, boolean isTrigger, Object options){
        return data;
    }

    @Override
    public Object call(DocHV doc, DT data, DocDefIV defIV, DDT d, Object options) {
        return null;
    }

    @Override
    public DT beforeUpdate(DocHV doc, DT data, DT original, DocDefIV defIV, DDT d) {
        return data;
    }

    @Override
    public DT afterUpdated(DocHV doc, DT data, DT original, DocDefIV defIV, DDT d) {
        return data;
    }

    @Override
    public void stateChanged(DocHV doc, DocHV original, DT data, DocDefIV defIV, DDT d){
    }

    @Override
    public DT random(DocHV docHV, DocDefIV defIV, DDT d) {
        return deserialize(null);
    }

    @SuppressWarnings("all")
    protected final Object parse(Object obj, Type targetType) {

        if(targetType!=null){

            if(obj!=null){
                try{
                    // 简单判断一下，因为先转成string再parse效率会比较低
                    if(obj instanceof String){
                        return JSON.parseObject((String)obj,targetType);
                    }else if(obj instanceof List){
                        return new JSONArray((List) obj).toJavaObject(targetType);
                    }else if(obj instanceof Map){
                        return new JSONObject((Map) obj).toJavaObject(targetType);
                    }
                    return JSON.parseObject(JSON.toJSONString(obj),targetType);
                }catch (Exception e){
                    log.warn("卡片["+getClass()+"]数据解析错误："+e.getMessage(),e);
                }
            }

            Class<?> clazz = null;
            if(targetType instanceof ParameterizedType){
                clazz = (Class<?>) ((ParameterizedType) targetType).getRawType();
            }else if(targetType instanceof Class){
                clazz = (Class)targetType;
            }else{
                throw NkSystemException.of("未知的参数类型："+targetType);
            }

            if(clazz.isInterface()){
                if(List.class.isAssignableFrom(clazz)){
                    return new ArrayList<>();
                }
                if(Map.class.isAssignableFrom(clazz)){
                    return new HashMap<>();
                }
                throw new NkDefineException("卡片数据类型不支持 "+clazz.getName());
            }else{
                try {
                    return ((Class)targetType).getConstructor().newInstance();
                } catch (Exception e) {
                    throw new NkDefineException("卡片数据类型必须声明空构造方法 "+e.getMessage(),e);
                }
            }

        }
        throw new NkDefineException("不能解析卡片对象["+getClass()+"]的数据类型");
    }


    private final static Map<Class,Type[]> cache = new ConcurrentHashMap<>();

    private Type getType(int index){
        Type[] types = getType(getClass());
        return types !=null && types.length > index ? types[index] : null;
    }

    private Type[] getType(Class clazz){
        return cache.computeIfAbsent(clazz,(_clazz)->{
            while(_clazz!=null){
                if(_clazz.getGenericSuperclass()!=null){
                    Type type = _clazz.getGenericSuperclass();
                    if(type instanceof ParameterizedType) {
                        return ((ParameterizedType) type).getActualTypeArguments();
                    }
                }
                _clazz = _clazz.getSuperclass();
            }
            return null;
        });
    }
}
