package cn.nkpro.ts5.config.security;

import com.alibaba.fastjson.JSONObject;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.springframework.security.core.GrantedAuthority;

@Data
public class TfmsGrantedAuthority implements GrantedAuthority,Comparable<TfmsGrantedAuthority> {

    private static final long serialVersionUID  = 521L;

    public static final Integer LEVEL_SINGLE    = 0x020000;
    public static final Integer LEVEL_MULTIPLE  = 0x040000;
    public static final Integer LEVEL_ALL       = 0x080000;
    public static final Integer LEVEL_SUPER     = 0x100000;

    public static final Integer LEVEL_LIMIT     = 0x010000;

    private String authority;

    private String permResource;

    private String permOperate;

    private String subResource;

    private String[] limitIds;
    private String limitQuery;

    private String fromPermissionId;
    private String fromPermissionDesc;

    private String fromGroupId;
    private String fromGroupDesc;

    private String level;


    public String getDocType(){
        return StringUtils.startsWith(getPermResource(),"@")
                ?getPermResource().substring(1)
                :(StringUtils.equals(getPermResource(),"*")?"*":null);
    }

    public void setDocType(String var0){}


    @Override
    public int compareTo(TfmsGrantedAuthority o) {
        return this.level.compareTo(o.level);
    }

    @Override
    public String toString(){
        return JSONObject.toJSONString(this);
    }
}
