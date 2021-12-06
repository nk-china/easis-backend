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
package cn.nkpro.elcube.task.impl;

import cn.nkpro.elcube.basic.PageList;
import cn.nkpro.elcube.docengine.model.DocHV;
import cn.nkpro.elcube.docengine.service.NkDocEngineFrontService;
import cn.nkpro.elcube.task.NkBpmTaskManager;
import cn.nkpro.elcube.task.model.BpmInstance;
import cn.nkpro.elcube.task.model.BpmTask;
import cn.nkpro.elcube.utils.BeanUtilz;
import org.apache.commons.lang3.StringUtils;
import org.camunda.bpm.engine.history.HistoricProcessInstanceQuery;
import org.camunda.bpm.engine.history.HistoricVariableInstance;
import org.camunda.bpm.engine.impl.pvm.PvmActivity;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.camunda.bpm.engine.task.Comment;
import org.camunda.bpm.engine.task.IdentityLink;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class NkBpmTaskManagerImpl extends AbstractNkBpmSupport implements NkBpmTaskManager {

    @Autowired
    private NkDocEngineFrontService docEngine;



    @Override
    public PageList<BpmInstance> processInstancePage(Integer from, Integer rows){

        HistoricProcessInstanceQuery query = processEngine.getHistoryService()
                .createHistoricProcessInstanceQuery()
                .orderByProcessInstanceStartTime()
                .desc();

        return new PageList<>(
                BeanUtilz.copyFromList(query.listPage(from, rows),BpmInstance.class),
                from,
                rows,
                query.count());
    }

    @Override
    public BpmInstance processInstanceDetail(String instanceId){

        BpmInstance processInstance = BeanUtilz.copyFromObject(
                processEngine.getHistoryService()
                        .createHistoricProcessInstanceQuery()
                        .processInstanceId(instanceId)
                        .singleResult(),BpmInstance.class);

        Assert.notNull(processInstance, "流程实例不存在");

        // 获取所有流程变量
        List<HistoricVariableInstance> variables = getHistoricVariableInstances(processInstance.getId());

        // 设置实例变量
        processInstance.setBpmVariables(
            variables
                .stream()
                .filter(instance->StringUtils.equals(instance.getExecutionId(),processInstance.getId()))
                .collect(Collectors.toMap(HistoricVariableInstance::getName,HistoricVariableInstance::getValue))
        );

        // 获取流程图内所有的节点
        List<? extends PvmActivity> activities = getProcessDefinitionActivities(processInstance.getProcessDefinitionId());

        // 获取实例备注
        List<Comment> comments = processEngine.getTaskService()
                .getProcessInstanceComments(processInstance.getId());

        // 获取实例下的所有任务
        processInstance.setBpmTask(
            BeanUtilz.copyFromList(
                processEngine.getHistoryService()
                    .createHistoricTaskInstanceQuery()
                    .processInstanceId(instanceId)
                    .orderByHistoricActivityInstanceStartTime().asc()
                    .list(),
                BpmTask.class,
                (task)->{

                    // 设置任务的备注
                    task.setComments(
                        comments.stream()
                                .filter(comment -> StringUtils.equals(comment.getTaskId(),task.getId()))
                                .map(Comment::getFullMessage)
                                .collect(Collectors.toList())
                    );

                    // 任务的流程变量
                    task.setBpmVariables(
                        variables
                            .stream()
                            .filter(instance->StringUtils.equals(instance.getExecutionId(),task.getExecutionId()))
                            .collect(Collectors.toMap(HistoricVariableInstance::getName,HistoricVariableInstance::getValue))
                    );

                    // 如果任务是活动的，获取更详细的信息
                    if(task.getEndTime()==null){

                        // 获取任务候选人
                        if(StringUtils.isBlank(task.getAssignee())){
                            task.setCandidate(
                                processEngine.getTaskService().getIdentityLinksForTask(task.getId())
                                    .stream()
                                    .filter(identityLink -> StringUtils.equals(identityLink.getType(),"candidate"))
                                    .map(IdentityLink::getUserId)
                                    .collect(Collectors.toList())
                            );
                        }

                        // 当前任务节点的对外连接线
                        task.setTransitions(getTaskTransition(activities,task.getTaskDefinitionKey()));
                    }
                }
            )
        );

        return processInstance;
    }

    @Override
    @Transactional
    public void deleteProcessInstance(String instanceId, String deleteReason){
        ProcessInstance processInstance = processEngine.getRuntimeService().createProcessInstanceQuery()
                .processInstanceId(instanceId)
                .singleResult();

        if(StringUtils.isNotBlank(processInstance.getBusinessKey())){
            docEngine.onBpmKilled(
                    processInstance.getBusinessKey(),
                    processInstance.getProcessDefinitionId().split(":")[0],
                    deleteReason);
        }

        processEngine.getRuntimeService().setVariable(instanceId,"NK$DELETE",deleteReason);
        processEngine.getRuntimeService().deleteProcessInstance(instanceId,deleteReason);
    }

    @Override
    public Boolean taskExists(String taskId) {
        return processEngine.getTaskService()
                .createTaskQuery()
                .taskId(taskId).count()>0;
    }

    @Override
    public void indexDocTask(DocHV doc){

        processEngine.getHistoryService()
                .createHistoricTaskInstanceQuery()
                .processInstanceBusinessKey(doc.getDocId())
                .list()
                .forEach(historicTaskInstance ->
                        super.indexDocTask(historicTaskInstance, doc)
                );
    }

    @Override
    public void revoke(String instanceId) {
        ProcessInstance processInstance = processEngine.getRuntimeService().createProcessInstanceQuery()
                .processInstanceId(instanceId)
                .singleResult();

//        System.out.println(processInstance.getProcessVariables());
//
//        if(StringUtils.isNotBlank(processInstance.getBusinessKey())){
//            docService.onBpmKilled(processInstance.getBusinessKey());
//        }

        processEngine.getRuntimeService().deleteProcessInstance(instanceId,"撤回流程");
    }
}
