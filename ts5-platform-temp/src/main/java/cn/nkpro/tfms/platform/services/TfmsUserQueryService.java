package cn.nkpro.tfms.platform.services;

import cn.nkpro.tfms.platform.model.po.SysUserSavedQuery;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Created by bean on 2020/7/22.
 */
public interface TfmsUserQueryService {
    List<SysUserSavedQuery> getList(String source);

    @Transactional
    void create(SysUserSavedQuery query);

    void delete(String queryId);
}
