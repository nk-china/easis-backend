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
package cn.nkpro.elcard.docengine.controller;

import cn.nkpro.elcard.annotation.NkNote;
import cn.nkpro.elcard.co.DebugContextManager;
import cn.nkpro.elcard.co.spel.NkSpELManager;
import cn.nkpro.elcard.data.redis.RedisSupport;
import cn.nkpro.elcard.docengine.NkDocEngine;
import cn.nkpro.elcard.docengine.RandomDocCreator;
import cn.nkpro.elcard.security.SecurityUtilz;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.expression.EvaluationContext;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collection;
import java.util.List;

/**
 * Created by bean on 2020/7/17.
 */
@NkNote("31.[DevDef]调试工具")
@RestController
@RequestMapping("/debug")
public class SysDebugController {

    @Autowired
    private DebugContextManager debugSupport;
    @Autowired
    private NkSpELManager spELManager;
    @Autowired
    private NkDocEngine docEngine;

    @Autowired
    private RedisSupport<String> redis;

    @NkNote("1.获取正在调试的上下文列表")
    @RequestMapping("/contexts")
    public Collection<DebugContextManager.ContextDescribe> list(){
        return debugSupport.getDebugContextList();
    }

    @NkNote("2.停止一个调试")
    @RequestMapping("/stop/{debugId}")
    public void stop(@PathVariable String debugId){
        debugSupport.removeContext(debugId);
    }

    @NkNote("3.创建一个调试")
    @RequestMapping("/start")
    public String start(@RequestParam String desc){
        return debugSupport.createContext(desc, SecurityUtilz.getUser().getUsername());
    }

    @NkNote("4.获取调试中的资源")
    @RequestMapping("/resources")
    public List<Object> resources(){
        return debugSupport.getDebugResources("@","#");
    }

    @NkNote("5.调试SpEL")
    @RequestMapping("/spel/test")
    public R spELTest(
            @RequestParam String el,
            @RequestParam(required = false)String docId,
            @RequestParam(required = false,defaultValue = "false")boolean isTemplate){

        try{
            EvaluationContext context = spELManager.createContext(StringUtils.isNotBlank(docId)?docEngine.detail(docId):null);
            if(isTemplate){
                return new R(spELManager.convert(el, context),null,null);
            }else {
                return new R(spELManager.invoke(el, context),null,null);
            }
        }catch (Exception e){
            return new R(null, e.getMessage(), ExceptionUtils.getRootCauseStackTrace(e));
        }
    }

    @NkNote("6.获取调试过程中的日志")
    @RequestMapping("/log/{id}")
    public String resources(@PathVariable String id){
        return redis.get(id);
    }

    @Data
    @AllArgsConstructor
    static class R{
        Object result;
        String errorMessage;
        String[] causeStackTrace;
    }

    @Autowired
    private RandomDocCreator randomDocCreator;
    @NkNote("13.随机生成单据")
    @RequestMapping(value = "/random")
    public void random() {
        randomDocCreator.random(1000);
    }
}
