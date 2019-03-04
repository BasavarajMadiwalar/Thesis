/*
 * Copyright © 2016 Basavaraj and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

/**
 * Flow Writer class is used to create Flow objects and
 * add them to config data tree using SALFlow Service.
 */

package org.opendaylight.I4application.impl.flow;

import com.google.common.collect.ImmutableList;
import org.opendaylight.I4application.impl.Topology.HostManager;
import org.opendaylight.I4application.impl.utils.InstanceIdentifierUtils;
import org.opendaylight.openflowplugin.api.OFConstants;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv4Address;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv4Prefix;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Uri;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.MacAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.action.OutputActionCaseBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.action.output.action._case.OutputActionBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.list.Action;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.list.ActionBuilder;
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
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.flow.InstructionsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.flow.Match;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.flow.MatchBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.instruction.ApplyActionsCaseBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.instruction.apply.actions._case.ApplyActions;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.instruction.apply.actions._case.ApplyActionsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.list.Instruction;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.list.InstructionBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeConnectorRef;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeRef;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.node.NodeConnector;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.node.NodeConnectorKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node;
import org.opendaylight.yang.gen.v1.urn.opendaylight.l2.types.rev130827.EtherType;
import org.opendaylight.yang.gen.v1.urn.opendaylight.model.match.types.rev131026.ethernet.match.fields.EthernetDestinationBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.model.match.types.rev131026.ethernet.match.fields.EthernetTypeBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.model.match.types.rev131026.match.EthernetMatch;
import org.opendaylight.yang.gen.v1.urn.opendaylight.model.match.types.rev131026.match.EthernetMatchBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.model.match.types.rev131026.match.layer._3.match.Ipv4Match;
import org.opendaylight.yang.gen.v1.urn.opendaylight.model.match.types.rev131026.match.layer._3.match.Ipv4MatchBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.ethernet.rev140528.KnownEtherType;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Link;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;

public class FlowWriter {
    private final static Logger LOG = LoggerFactory.getLogger(org.opendaylight.I4application.impl.flow.FlowWriter.class);

    private AtomicLong flowCookieInc = new AtomicLong(0x4a00000000000000L);
    private AtomicLong flowIdInc = new AtomicLong();
    private short flowTableId = 0;
    private final String FLOW_ID_PREFIX = "L2switch-normal-";
    private List<Link> path = null;

    private SalFlowService salFlowService;

    public FlowWriter(SalFlowService salFlowService) {
        this.salFlowService = salFlowService;
    }


    public boolean addFlowtoNode(Ipv4Address dstIP, MacAddress dstMac, NodeConnectorRef dstNCref){

        Flow flow = createFlow(dstIP, dstMac, dstNCref);
        // Create Flow tableKey
        TableKey flowTableKey = new TableKey(flowTableId);

        /**
         * Create a Instnace Identifier for flow.
         * But, Since flow is a child of table, table is child of node , we will make use of Utilities to build the tree and hence flow path.
         * So, we create table IID, and Node IID
          */

        InstanceIdentifier<Flow> flowPath = createFlowIID(dstNCref, flowTableKey);
        writeFlowConfigData(flowPath, flow);
        return true;
    }


    public boolean addFlowToPathNodes(Ipv4Address srcIP, MacAddress srcMac,
                                      Ipv4Address dstIP, MacAddress dstMac, Node srcNode, List<Link> path){

        NodeConnectorRef srcNCRef, dstNCRef;
        String prvLinkSrcNodeId = srcNode.getKey().getId().toString();
        String curLinkSrcNodeId = null;
        // Loop through the path and add flow per link
        for (Link link: path){

            // Get the current link src switch id
            curLinkSrcNodeId = link.getSource().getSourceNode().toString();

            /**
             * Both prevlinkDstNode and CurrentLink DstNode should be same.
             * Else, we reverse NodeConnector as the src switch is identified based on the time of switch
             * coming online
             */

            if (prvLinkSrcNodeId.equals(curLinkSrcNodeId)){
                srcNCRef  = HostManager.getSourceNodeConnectorRef(link);//2 Move these methods from Hostmanager to flowwriter
                dstNCRef = HostManager.getDestNodeConnectorRef(link);//3
                prvLinkSrcNodeId = link.getDestination().getDestNode().toString();
            }else {
                srcNCRef = HostManager.getDestNodeConnectorRef(link);
                dstNCRef = HostManager.getSourceNodeConnectorRef(link);
                prvLinkSrcNodeId = link.getSource().getSourceNode().toString();
            }

            addFlowtoNode(dstIP,dstMac, srcNCRef);
            addFlowtoNode(srcIP,srcMac, dstNCRef);
        }
        return true;
    }


    public InstanceIdentifier<Flow> createFlowIID(NodeConnectorRef nodeConnectorRef, TableKey flowTableKey){

        FlowId flowId = new FlowId(FLOW_ID_PREFIX + String.valueOf(flowIdInc.getAndIncrement()));
        FlowKey flowKey = new FlowKey(flowId);

        return InstanceIdentifierUtils.generateFlowInstanceIdentifier(nodeConnectorRef, flowTableKey, flowKey);

    }


    public Flow createFlow(Ipv4Address dstIP, MacAddress dstMac, NodeConnectorRef dstPort){

        // Create a flow builder
        FlowBuilder flowBuilder = new FlowBuilder().setTableId(flowTableId).setFlowName("IP_MAC_BASED_FLOW");

        flowBuilder.setId(new FlowId(Long.toString(flowBuilder.hashCode())));

        //Create EthernetMatchBuilder
        EthernetMatchBuilder ethernetMatchBuilder = new EthernetMatchBuilder()
                .setEthernetDestination(new EthernetDestinationBuilder().
                        setAddress(dstMac).build());


        ethernetMatchBuilder.setEthernetType(new EthernetTypeBuilder()
                .setType(new EtherType(Long.valueOf(KnownEtherType.Ipv4.getIntValue()))).build());

        EthernetMatch ethernetMatch = ethernetMatchBuilder.build();

        //Create Ipv4MatchBuilder

        Ipv4MatchBuilder ipv4MatchBuilder = new Ipv4MatchBuilder();
        ipv4MatchBuilder.setIpv4Destination(new Ipv4Prefix(dstIP.getValue() + "/32")).build();

        Ipv4Match ipv4Match = ipv4MatchBuilder.build();


        Match match = new MatchBuilder().setEthernetMatch(ethernetMatch)
                .setLayer3Match(ipv4Match)
                .build();

        // Create an Action that forwards packet to dstport upon successful match
        Uri destPortUri = dstPort.getValue().firstKeyOf(NodeConnector.class, NodeConnectorKey.class).getId();

        Action outputaction = new ActionBuilder()
                .setOrder(0)
                .setAction(new OutputActionCaseBuilder()
                        .setOutputAction(new OutputActionBuilder()
                        .setMaxLength(0xffff)
                        .setOutputNodeConnector(destPortUri)
                        .build())
                .build())
                .build();

        ApplyActions applyActions = new ApplyActionsBuilder().setAction(ImmutableList.of(outputaction)).build();

        // Add the applyaction into an Instruction
        Instruction applyActionsInstruction = new InstructionBuilder()
                                                .setOrder(0)
                                                .setInstruction(new ApplyActionsCaseBuilder()
                                                        .setApplyActions(applyActions)
                                                        .build())
                                                .build();

        // Put the created Instruction in a list of Instructions
        flowBuilder.setMatch(match)
                    .setInstructions(new InstructionsBuilder()
                    .setInstruction(ImmutableList.of(applyActionsInstruction))
                    .build())
                .setPriority(1000)
                .setBufferId(OFConstants.OFP_NO_BUFFER)
                .setHardTimeout(0)
                .setIdleTimeout(15)
                .setCookie(new FlowCookie(BigInteger.valueOf(flowCookieInc.getAndIncrement())))
                .setFlags(new FlowModFlags(false, false, false, false, false));
        return flowBuilder.build();
    }

    private Future<RpcResult<AddFlowOutput>> writeFlowConfigData(InstanceIdentifier<Flow> flowPath, Flow flow){
        final InstanceIdentifier<Table> tableInstanceId = flowPath.<Table>firstIdentifierOf(Table.class);
        final InstanceIdentifier<Node> nodeIID = flowPath.<Node>firstIdentifierOf(Node.class);
        final AddFlowInputBuilder builder = new AddFlowInputBuilder(flow);
        builder.setNode(new NodeRef(nodeIID));
        builder.setFlowRef(new FlowRef(flowPath));
        builder.setFlowTable(new FlowTableRef(tableInstanceId));
        builder.setTransactionUri(new Uri(flow.getId().getValue()));
        return salFlowService.addFlow(builder.build());
    }

}
