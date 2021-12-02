package cn.nkpro.tfms.platform.model.bpm;

import lombok.Data;
import org.apache.commons.lang3.StringUtils;

@Data
public class BpmTaskFlow {
    private String id;
    private String name;
    private String documentation;

    public String getName(){
        return StringUtils.defaultIfBlank(name,id);
    }
}