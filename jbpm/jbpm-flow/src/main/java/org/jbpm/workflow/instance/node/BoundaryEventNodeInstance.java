/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.jbpm.workflow.instance.node;

import java.util.Collection;
import java.util.Date;
import java.util.function.Function;

import org.jbpm.ruleflow.core.Metadata;
import org.jbpm.workflow.core.node.BoundaryEventNode;
import org.jbpm.workflow.instance.NodeInstance;
import org.jbpm.workflow.instance.NodeInstanceContainer;
import org.jbpm.workflow.instance.impl.WorkflowProcessInstanceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BoundaryEventNodeInstance extends EventNodeInstance {

    private static final Logger LOG = LoggerFactory.getLogger(BoundaryEventNodeInstance.class);
    private static final long serialVersionUID = -4958054074031174180L;

    @Override
    public void signalEvent(String type, Object event, Function<String, Object> varResolver) {
        LOG.debug("Received boundary event signal {} and paydload {}", type, event);
        if (triggerTime == null) {
            triggerTime = new Date();
        }
        BoundaryEventNode boundaryNode = (BoundaryEventNode) getEventNode();

        String attachedTo = boundaryNode.getAttachedToNodeId();
        Collection<NodeInstance> nodeInstances = getProcessInstance().getNodeInstances(true);
        if (type != null && type.startsWith(Metadata.EVENT_TYPE_COMPENSATION)) {
            // if not active && completed, signal
            if (!isAttachedToNodeActive(nodeInstances, attachedTo, type, event) && isAttachedToNodeCompleted(attachedTo)) {
                super.signalEvent(type, event, varResolver);
            } else {
                cancel();
            }
        } else {
            if (isAttachedToNodeActive(nodeInstances, attachedTo, type, event)) {
                super.signalEvent(type, event, varResolver);
            } else {
                cancel();
            }
        }
    }

    @Override
    public void signalEvent(String type, Object event) {
        this.signalEvent(type, event, varName -> this.getVariable(varName));
    }

    private boolean isAttachedToNodeActive(Collection<NodeInstance> nodeInstances, String attachedTo, String type, Object event) {
        if (nodeInstances != null && !nodeInstances.isEmpty()) {
            for (NodeInstance nInstance : nodeInstances) {
                String nodeUniqueId = (String) nInstance.getNode().getUniqueId();
                boolean isActivating = ((WorkflowProcessInstanceImpl) nInstance.getProcessInstance()).getActivatingNodeIds().contains(nodeUniqueId);
                if (attachedTo.equals(nodeUniqueId) && !isActivating) {
                    // in case this is timer event make sure it corresponds to the proper node instance
                    if (type.startsWith("Timer-")) {
                        if (nInstance.getId().equals(event)) {
                            return true;
                        }
                    } else {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean isAttachedToNodeCompleted(String attachedTo) {
        WorkflowProcessInstanceImpl processInstance = (WorkflowProcessInstanceImpl) getProcessInstance();
        return processInstance.getCompletedNodeIds().contains(attachedTo);
    }

    @Override
    public void cancel(CancelType cancelType) {
        getProcessInstance().removeEventListener(getEventType(), getEventListener(), true);
        ((NodeInstanceContainer) getNodeInstanceContainer()).removeNodeInstance(this);
    }
}
