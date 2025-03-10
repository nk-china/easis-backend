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
package cn.nkpro.elcube.docengine.service.impl;

import cn.nkpro.elcube.basic.PageList;
import cn.nkpro.elcube.data.redis.RedisSupport;
import cn.nkpro.elcube.docengine.NkDocSearchService;
import cn.nkpro.elcube.docengine.NkEqlEngine;
import cn.nkpro.elcube.docengine.gen.DocH;
import cn.nkpro.elcube.docengine.model.DocHQL;
import cn.nkpro.elcube.docengine.model.DocHV;
import cn.nkpro.elcube.docengine.service.NkDocEngineFrontService;
import cn.nkpro.elcube.task.NkBpmTaskManager;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class NkDocEngineIndexService {

    @Autowired
    private NkEqlEngine eqlEngine;
    @Autowired
    private NkDocSearchService searchService;
    @Autowired
    private NkDocEngineFrontService docEngineFrontService;
    @Autowired@SuppressWarnings("all")
    private NkBpmTaskManager bpmTaskManager;
    @Autowired
    private RedisSupport<ReindexInfo> redisReindexInfo;

    @Async
    public void reindex(String asyncTaskId, Boolean dropFirst, String eqlWhere) throws IOException {
        redisReindexInfo.set(asyncTaskId,
            new ReindexInfo(false,0L, 0L, "加载索引任务", null)
        );

        if(dropFirst){
            searchService.dropAndInit();
        }


        eqlWhere = StringUtils.isBlank(eqlWhere)?StringUtils.EMPTY:("WHERE "+ eqlWhere);


        int offset = 0;
        int rows   = 1000;
        AtomicLong finished = new AtomicLong(0);
        AtomicLong total = new AtomicLong(0);
        List<DocHQL> list;
        try{
            total.set(eqlEngine.countByEql("SELECT * FROM doc "+eqlWhere));

            String eql = "SELECT * FROM doc %s LIMIT %s,%s";

            while((list = eqlEngine.findByEql(String.format(eql,eqlWhere,offset,rows))).size()>0){

                list.forEach(doc->{
                    try{

                        // 记录日志
                        redisReindexInfo.set(asyncTaskId,
                                new ReindexInfo(
                                        false,
                                        total.get(),
                                        finished.get(),
                                        String.format("重建索引 docId = %s docName = %s",
                                                doc.getDocId(),
                                                doc.getDocName()
                                        ),
                                        null)
                        );
                        // 获取详情
                        DocHV docHV = docEngineFrontService.detail(doc.getDocId());

                        // 创建单据索引
                        docEngineFrontService.reDataSync(docHV);

                        // 创建任务索引
                        bpmTaskManager.indexDocTask(docHV);
                        finished.addAndGet(1);

                    }catch (Exception e){
                        throw new RuntimeException(String.format("docType = %s, docId = %s, error = %s", doc.getDocType(),doc.getDocId(),e.getMessage()),e);
                    }
                });
                offset += rows;
                //finished.set(finished.get() + list.size());
            }
            redisReindexInfo.set(asyncTaskId,
                new ReindexInfo(true,total.get(), finished.get(),"重建索引完成",null)
            );
        }catch (Exception e){
            e.printStackTrace();
            redisReindexInfo.set(asyncTaskId,
                new ReindexInfo(true,total.get(), finished.get(),String.format("重建索引发生错误: %s",e.getMessage()),ExceptionUtils.getRootCauseStackTrace(e))
            );
        }finally {
            redisReindexInfo.expire(asyncTaskId, 10);
        }
    }

    public ReindexInfo getReindexInfo(String asyncTaskId){
        return redisReindexInfo.get(asyncTaskId);
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReindexInfo{
        boolean finished;
        long total;
        long totalS;
        String message;
        String[] exceptions;
    }
}
