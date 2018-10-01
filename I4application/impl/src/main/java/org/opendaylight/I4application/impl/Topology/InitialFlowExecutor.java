/*
 * Copyright © 2016 Basavaraj and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.I4application.impl.Topology;

import com.google.common.collect.ImmutableList;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataChangeListener;
import org.opendaylight.controller.md.sal.binding.api.NotificationService;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataBroker;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataChangeEvent;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.openflowplugin.api.OFConstants;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Uri;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.action.OutputActionCaseBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.action.output.action._case.OutputActionBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.list.Action;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.list.ActionBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.list.ActionKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowCapableNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.Table;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.TableKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.Flow;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.FlowBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.FlowKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.service.rev130819.AddFlowInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.service.rev130819.AddFlowOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.service.rev130819.FlowTableRef;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.service.rev130819.SalFlowService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.FlowCookie;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.FlowModFlags;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.FlowRef;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.OutputPortValues;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.flow.InstructionsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.flow.Match;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.flow.MatchBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.instruction.ApplyActionsCaseBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.instruction.apply.actions._case.ApplyActions;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.instruction.apply.actions._case.ApplyActionsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.list.Instruction;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.list.InstructionBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeRef;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.Nodes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.NodeKey;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.RpcResult;

import java.math.BigInteger;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;

public class InitialFlowExecutor implements DataChangeListener {

    private DataBroker dataBroker;
    private NotificationService notificationService;
    private SalFlowService salFlowService;

    private final ExecutorService initialFlowExecutor = Executors.newCachedThreadPool();

    // Port Number for Controller Port = 0xfffffffd
    private short flowTableId;
    private int flowPriority;
    private int flowIdleTimeout;
    private int flowHardTimeout;
    private final String FLOW_ID_PREFIX = "L2switch-";

    private final short DEFAULT_FLOW_TABLE_ID = 0;
    private final int DEFAULT_FLOW_PRIORITY = 0;
    private final int DEFAULT_FLOW_IDLE_TIMEOUT = 0;
    private final int DEFAULT_FLOW_HARD_TIMEOUT = 0;

    private AtomicLong flowIdInc = new AtomicLong();
    private AtomicLong flowCookieInc = new AtomicLong(0x2b00000000000000L);


    public InitialFlowExecutor(NotificationService notificationService, DataBroker dataBroker, SalFlowService salFlowService) {
        this.notificationService = notificationService;
        this.dataBroker = dataBroker;
        this.salFlowService = salFlowService;
        registerAsDataChangeListener(dataBroker);
    }

    public ListenerRegistration<DataChangeListener> registerAsDataChangeListener(DataBroker dataBroker){
        InstanceIdentifier<Node> nodeIID = InstanceIdentifier.builder(Nodes.class).child(Node.class).build();
        return dataBroker.registerDataChangeListener(LogicalDatastoreType.OPERATIONAL, nodeIID, this, AsyncDataBroker.DataChangeScope.BASE);

    }

    /**
     * onDataChanged is called even when a switch is turned-off
     * But the keySet becomes zero when node is removed, so the flow execution will not
     * be called
     */

    public void onDataChanged(AsyncDataChangeEvent<InstanceIdentifier<?>, DataObject> change) {
        Map<InstanceIdentifier<?>, DataObject > changednodeData = change.getCreatedData();
        if(changednodeData !=null && !changednodeData.isEmpty()){
            Set<InstanceIdentifier<?>> nodeIds = changednodeData.keySet();
            if(nodeIds != null && !nodeIds.isEmpty()){
                System.out.println("NodId of changedData: " + changednodeData.keySet());
                initialFlowExecutor.submit(new InitialFlowWriter(nodeIds));

            }
        }


    }

    private class InitialFlowWriter implements Runnable{
        Set<InstanceIdentifier<?>> nodeIds = null;

        public InitialFlowWriter(Set<InstanceIdentifier<?>> nodeIds) {
            this.nodeIds = nodeIds;
        }

        @Override
        public void run() {
            if (nodeIds == null){
                return;
            }

            for (InstanceIdentifier<?> nodeId: nodeIds){
                if (Node.class.isAssignableFrom(nodeId.getTargetType())){
                    InstanceIdentifier<Node> invNodeId = (InstanceIdentifier<Node>)nodeId;
                    if (invNodeId.firstKeyOf(Node.class, NodeKey.class).getId().getValue().contains("openflow:")){
                        addInitialFlow(invNodeId);
                    }
                }
            }

        }

        /**
         * Add IntialFlow of forward to controller if none of the flow match
         */
        public void addInitialFlow(InstanceIdentifier<Node> nodeId){
            //new ControllerActionCaseBuilder().build();
            InstanceIdentifier<Table> tableId = getTableInstanceId(nodeId);
            InstanceIdentifier<org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.Flow> flowId = getFlowInstanceId(tableId);

            //Call FlowWriterToController
            writeFlowToController(nodeId, tableId, flowId, createFlow(flowTableId, flowPriority));
        }

        private InstanceIdentifier<Table> getTableInstanceId(InstanceIdentifier<Node> nodeId){
            TableKey flowTableKey = new TableKey(flowTableId);
            return nodeId.builder().augmentation(FlowCapableNode.class)
                    .child(Table.class, flowTableKey).build();
        }

        private InstanceIdentifier<org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.Flow> getFlowInstanceId(InstanceIdentifier<Table> tableId){
            FlowId flowId = new FlowId(FLOW_ID_PREFIX+String.valueOf(flowIdInc.getAndIncrement()));
            FlowKey flowKey = new FlowKey(flowId);
            return tableId.child(Flow.class, flowKey);
        }

        private Flow createFlow(Short tableId, int priority) {

            // start building flow
            FlowBuilder ControllerFlow = new FlowBuilder() //
                    .setTableId(tableId) //
                    .setFlowName("ControllerFlow");

            // use its own hash code for id.
            ControllerFlow.setId(new FlowId(Long.toString(ControllerFlow.hashCode())));

            Match match = new MatchBuilder().build();


            Action ToController = new ActionBuilder() //
                    .setOrder(0)
                    .setKey(new ActionKey(0))
                    .setAction(new OutputActionCaseBuilder()
                            .setOutputAction(new OutputActionBuilder().setMaxLength(0xffff)
                                    .setOutputNodeConnector(new Uri(OutputPortValues.CONTROLLER.toString()))
                                    .build())
                            .build())
                    .build();

            // Create an Apply Action

            ApplyActions applyActions = new ApplyActionsBuilder().setAction(ImmutableList.of(ToController)).build();

            // Wrap our Apply Action in an Instruction
            Instruction applyActionsInstruction = new InstructionBuilder() //
                    .setOrder(0)
                    .setInstruction(new ApplyActionsCaseBuilder()//
                            .setApplyActions(applyActions) //
                            .build()) //
                            .build();

            // Put our Instruction in a list of Instructions
            ControllerFlow
                    .setMatch(match) //
                    .setInstructions(new InstructionsBuilder() //
                            .setInstruction(ImmutableList.of(applyActionsInstruction)) //
                            .build()) //
                    .setPriority(priority) //
                    .setBufferId(OFConstants.OFP_NO_BUFFER) //
                    .setHardTimeout(flowHardTimeout) //
                    .setIdleTimeout(flowIdleTimeout) //
                    .setCookie(new FlowCookie(BigInteger.valueOf(flowCookieInc.getAndIncrement())))
                    .setFlags(new FlowModFlags(false, false, false, false, false));

            return ControllerFlow.build();
        }

        private Future<RpcResult<AddFlowOutput>> writeFlowToController(InstanceIdentifier<Node> nodeInstanceId,
                                                                       InstanceIdentifier<Table> tableInstanceId,
                                                                       InstanceIdentifier<Flow> flowPath,
                                                                       Flow flow){

            final AddFlowInputBuilder builder = new AddFlowInputBuilder(flow);
            builder.setNode(new NodeRef(nodeInstanceId));
            builder.setFlowRef(new FlowRef(flowPath));
            builder.setFlowTable(new FlowTableRef(tableInstanceId));
            builder.setTransactionUri(new Uri(flow.getId().getValue()));
            return salFlowService.addFlow(builder.build());

        }

    }

}

