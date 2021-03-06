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
import com.google.common.util.concurrent.Futures;
import org.opendaylight.I4application.impl.Topology.HostManager;
import org.opendaylight.I4application.impl.utils.InstanceIdentifierUtils;
import org.opendaylight.controller.md.sal.binding.api.NotificationService;
import org.opendaylight.controller.sal.binding.api.RpcProviderRegistry;
import org.opendaylight.openflowplugin.api.OFConstants;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.*;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.action.GroupActionCaseBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.action.OutputActionCaseBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.action.group.action._case.GroupAction;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.action.group.action._case.GroupActionBuilder;
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
import org.opendaylight.yang.gen.v1.urn.opendaylight.group.service.rev130918.*;
import org.opendaylight.yang.gen.v1.urn.opendaylight.group.service.rev130918.group.update.OriginalGroup;
import org.opendaylight.yang.gen.v1.urn.opendaylight.group.service.rev130918.group.update.OriginalGroupBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.group.service.rev130918.group.update.UpdatedGroup;
import org.opendaylight.yang.gen.v1.urn.opendaylight.group.service.rev130918.group.update.UpdatedGroupBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.group.types.rev131018.BucketId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.group.types.rev131018.GroupId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.group.types.rev131018.GroupRef;
import org.opendaylight.yang.gen.v1.urn.opendaylight.group.types.rev131018.GroupTypes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.group.types.rev131018.group.Buckets;
import org.opendaylight.yang.gen.v1.urn.opendaylight.group.types.rev131018.group.BucketsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.group.types.rev131018.group.buckets.Bucket;
import org.opendaylight.yang.gen.v1.urn.opendaylight.group.types.rev131018.group.buckets.BucketBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.group.types.rev131018.group.buckets.BucketKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.group.types.rev131018.groups.Group;
import org.opendaylight.yang.gen.v1.urn.opendaylight.group.types.rev131018.groups.GroupBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeConnectorRef;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeRef;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.node.NodeConnector;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.node.NodeConnectorKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node;
import org.opendaylight.yang.gen.v1.urn.opendaylight.l2.types.rev130827.EtherType;
import org.opendaylight.yang.gen.v1.urn.opendaylight.model.match.types.rev131026.ethernet.match.fields.EthernetTypeBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.model.match.types.rev131026.match.EthernetMatch;
import org.opendaylight.yang.gen.v1.urn.opendaylight.model.match.types.rev131026.match.EthernetMatchBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.model.match.types.rev131026.match.IpMatchBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.model.match.types.rev131026.match.Layer4Match;
import org.opendaylight.yang.gen.v1.urn.opendaylight.model.match.types.rev131026.match.layer._3.match.Ipv4Match;
import org.opendaylight.yang.gen.v1.urn.opendaylight.model.match.types.rev131026.match.layer._3.match.Ipv4MatchBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.model.match.types.rev131026.match.layer._4.match.UdpMatchBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.ethernet.rev140528.KnownEtherType;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.flushgrouptable.rev181201.FlushGroupTableService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.hostmanagernotification.rev150105.HostAddedNotification;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.hostmanagernotification.rev150105.HostNotificationListener;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.hostmanagernotification.rev150105.HostRemovedNotification;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Link;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;

public class MDNSFlowWriter implements HostNotificationListener, FlushGroupTableService {
    private final static Logger LOG = LoggerFactory.getLogger(MDNSFlowWriter.class);

    private AtomicLong flowCookieInc = new AtomicLong(0x4a00000000000000L);
    private AtomicLong flowIdInc = new AtomicLong();
    private short flowTableId = 0;
    private final String MDNS_FLOW_ID_PREFIX = "L2switch-mDNS-";
    private List<Link> path = null;
    private HashMap<Ipv4Address, Long> groupIdTable= new HashMap<>();
    private AtomicLong groupIdInc =  new AtomicLong(1L);


    /* Variable related to mDNS groups and reverse flow */
    private HashMap<NodeId, HashMap<Ipv4Address, ArrayList<NodeConnectorRef>>> groupTable = new HashMap<>();
    private AtomicLong bucketIdInc = new AtomicLong();

    public final static short UDP = 17;


    private SalFlowService salFlowService;
    private SalGroupService salGroupService;

    public MDNSFlowWriter(SalFlowService salFlowService, NotificationService notificationService,
                          SalGroupService salGroupService, RpcProviderRegistry rpcProviderRegistry) {
        this.salFlowService = salFlowService;
        this.salGroupService = salGroupService;
        notificationService.registerNotificationListener(this);
        rpcProviderRegistry.addRpcImplementation(FlushGroupTableService.class, this);
    }



    public InstanceIdentifier<Flow> createmDNSFlowIID(NodeConnectorRef nodeConnectorRef, TableKey flowTableKey){

        FlowId flowId = new FlowId(MDNS_FLOW_ID_PREFIX + String.valueOf(flowIdInc.getAndIncrement()));
        FlowKey flowKey = new FlowKey(flowId);

        return InstanceIdentifierUtils.generateFlowInstanceIdentifier(nodeConnectorRef, flowTableKey, flowKey);
    }


    /* Below Section of code is used for setting up mDNS Packet Flows */
    public boolean mDNSForwardPathFlow(Ipv4Address srcIP, Ipv4Address dstIP, Node srcNode, Node dstNode, NodeConnector srcNC,
                                       NodeConnector dstNC, List<Link> path){
        LOG.debug("mDNS Forward Path Flow");
        NodeConnectorRef srcNCRef, dstNCRef;

        InstanceIdentifier<NodeConnector> srcNCIID = InstanceIdentifierUtils
                .createNodeConnectorIdentifier(srcNode.getId().getValue(),srcNC.getId().getValue());

        InstanceIdentifier<NodeConnector> dstNCIID = InstanceIdentifierUtils
                .createNodeConnectorIdentifier(dstNode.getId().getValue(), dstNC.getId().getValue());

        srcNCRef = new NodeConnectorRef(srcNCIID);
        dstNCRef = new NodeConnectorRef(dstNCIID);

        // Add Flow to Switch Connecting dst host such that for each SRC IP and mDNS MC IP, output to specific port
        boolean dstNodeFlow = addFlowtoNode(srcIP, dstIP, dstNCRef);
        if (dstNodeFlow){
            addmDNSFlowtoPathNode(srcIP, dstIP, srcNode, path);
        }
        return true;
    }

    /*
    *  Here Add Flow to SRC Node and Dst Node
    * */
    public boolean addFlowtoNode(Ipv4Address srcIP, Ipv4Address dstIP,
                                 NodeConnectorRef dstNCref){

        Flow flow = createmDNSFlow(srcIP, dstIP, dstNCref);
        TableKey flowTableKey = new TableKey(flowTableId);
        InstanceIdentifier<Flow> flowPath = createmDNSFlowIID(dstNCref, flowTableKey);

        writeFlowConfigData(flowPath, flow);
        return true;
    }

    /* To do : Implement Forward Rule for mDNS Packets

     */

    public void addFlow(Ipv4Address coordinator, Ipv4Address multicastAddress, NodeConnectorRef dstNCRef){
        LOG.debug("Add Flow to switch");

        Flow flow = createMulticastgroupflow(coordinator, multicastAddress, dstNCRef);
        TableKey flowTableKey = new TableKey((short)flowTableId);
        InstanceIdentifier<Flow> flowPath = createmDNSFlowIID(dstNCRef, flowTableKey);
        writeFlowConfigData(flowPath, flow);
    }


    public Flow createmDNSFlow(Ipv4Address srcIP,
                                  Ipv4Address dstIP, NodeConnectorRef dstPort){
        //Create a Flow Builder
        FlowBuilder flowBuilder = new FlowBuilder().setTableId(flowTableId).setFlowName("MDNS_FLOW");
        flowBuilder.setId(new FlowId(Long.toString(flowBuilder.hashCode())));

        // Create Ethernet Match builder
        EthernetMatchBuilder ethernetMatchBuilder = new EthernetMatchBuilder();

        ethernetMatchBuilder.setEthernetType(new EthernetTypeBuilder()
                .setType(new EtherType(Long.valueOf(KnownEtherType.Ipv4.getIntValue()))).build());

        EthernetMatch ethernetMatch = ethernetMatchBuilder.build();

        //Create Ipv4 Match Builder

        Ipv4MatchBuilder ipv4MatchBuilder = new Ipv4MatchBuilder();
        ipv4MatchBuilder.setIpv4Source(new Ipv4Prefix(srcIP.getValue() + "/32")).build();
        ipv4MatchBuilder.setIpv4Destination(new Ipv4Prefix(dstIP.getValue() + "/32")).build();

        Ipv4Match ipv4Match = ipv4MatchBuilder.build();


        // Layer 4 Match
        IpMatchBuilder ipMatchBuilder = new IpMatchBuilder();
        ipMatchBuilder.setIpProto(IpVersion.Ipv4);

        ipMatchBuilder.setIpProtocol(UDP);

        UdpMatchBuilder udpMatchBuilder = new UdpMatchBuilder();
        udpMatchBuilder.setUdpSourcePort(new PortNumber(5353)).build(); // Change port number to a variable
        udpMatchBuilder.setUdpDestinationPort(new PortNumber(5353)).build();

        Layer4Match udpMatch = udpMatchBuilder.build();

        Match match = new MatchBuilder().setEthernetMatch(ethernetMatch)
                    .setLayer3Match(ipv4Match)
                    .setIpMatch(ipMatchBuilder.build())
                    .setLayer4Match(udpMatch)
                    .build();

        Uri destPortUri = dstPort.getValue().firstKeyOf(NodeConnector.class, NodeConnectorKey.class).getId();

        // Create an Action that forwards packet to dstport upon succesful match

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

        Instruction applyActionsInstruction = new InstructionBuilder()
                                                .setOrder(0)
                                                .setInstruction(new ApplyActionsCaseBuilder()
                                                        .setApplyActions(applyActions)
                                                        .build())
                                                .build();

        flowBuilder.setMatch(match)
                    .setInstructions(new InstructionsBuilder()
                    .setInstruction(ImmutableList.of(applyActionsInstruction))
                    .build())
                .setPriority(10)
                .setBufferId(OFConstants.OFP_NO_BUFFER)
                .setHardTimeout(35)
                .setIdleTimeout(0)
                .setCookie(new FlowCookie(BigInteger.valueOf(flowCookieInc.getAndIncrement())))
                .setFlags(new FlowModFlags(false,false,false,false,false));

        return flowBuilder.build();

    }

    public boolean addmDNSFlowtoPathNode(Ipv4Address srcIP, Ipv4Address dstIP , Node srcNode
                                    ,List<Link> path){
        NodeConnectorRef srcNCRef, dstNCRef;
        int TotLinks = path.size();
        NodeId prvSrcNodeId = srcNode.getId();
        NodeId curLinkSrcNodeId = null;

        for(Link link: path){
            curLinkSrcNodeId = new NodeId(link.getSource().getSourceNode());

            if (prvSrcNodeId.equals(curLinkSrcNodeId)){
                srcNCRef = HostManager.getSourceNodeConnectorRef(link);
                dstNCRef = HostManager.getDestNodeConnectorRef(link);
                prvSrcNodeId = new NodeId(link.getDestination().getDestNode());

            }else {
                srcNCRef = HostManager.getDestNodeConnectorRef(link);
                dstNCRef = HostManager.getSourceNodeConnectorRef(link);
                prvSrcNodeId = new NodeId(link.getSource().getSourceNode());

            }

            addFlowtoNode(srcIP, dstIP, srcNCRef);
            TotLinks--;
        }
        if (TotLinks != 0){
            return false;
        }
        return true;
    }

    /* mDNSReverseHandler Manages creation of flows for coordinator to opc-ua
    *  server in multi-cast manner*/

    public void mDNSReverseFlowHanlder(Ipv4Address opcua_Server, Ipv4Address coordinator,
                                       Node srcNode, NodeConnector srcNC,  List<Link> path, Ipv4Address multicastAddress)
    {
        LOG.debug("mDNS Reverse Flow Handler");
        NodeId currlinkSRCNodeId, prvlinkDstNodeId;
        NodeConnectorRef dstNCref, opcuaserNCref;

        InstanceIdentifier<NodeConnector> srcNCIID = InstanceIdentifierUtils.createNodeConnectorIdentifier(
                                        srcNode.getId().getValue(), srcNC.getId().getValue());
        dstNCref =  new NodeConnectorRef(srcNCIID);

        prvlinkDstNodeId = srcNode.getId();

        for (Link link: path){
            currlinkSRCNodeId = new NodeId(link.getSource().getSourceNode());
            // Check if src Node is same as prev SRC Node else reverse
            if (currlinkSRCNodeId.equals(prvlinkDstNodeId)){
                prvlinkDstNodeId = new NodeId(link.getDestination().getDestNode());
                // Call mDNS Group Handler create group
                mDNSGroupHandler(coordinator, currlinkSRCNodeId, dstNCref, multicastAddress);
                addFlow(coordinator, multicastAddress, dstNCref);
                dstNCref = HostManager.getDestNodeConnectorRef(link);
            }else {
                currlinkSRCNodeId = new NodeId(link.getDestination().getDestNode());
                prvlinkDstNodeId = new NodeId(link.getSource().getSourceNode());
                mDNSGroupHandler(coordinator, currlinkSRCNodeId, dstNCref, multicastAddress);
                addFlow(coordinator,multicastAddress,dstNCref);
                dstNCref = HostManager.getSourceNodeConnectorRef(link);
            }
        }

        mDNSGroupHandler(coordinator, prvlinkDstNodeId, dstNCref, multicastAddress);
        addFlow(coordinator, multicastAddress, dstNCref);
    }

    public void mDNSGroupHandler(Ipv4Address coordinator, NodeId currentNodeId,
                                 NodeConnectorRef dstNCRef, Ipv4Address multicastAddress){
        LOG.debug("mDNS Group Handler");

        ArrayList<NodeConnectorRef> newPortList = new ArrayList<NodeConnectorRef>();
        ArrayList<NodeConnectorRef> oldPortList = new ArrayList<NodeConnectorRef>();
        HashMap<Ipv4Address, ArrayList<NodeConnectorRef>> newCoordinatorsMap = new HashMap<Ipv4Address, ArrayList<NodeConnectorRef>>();
        Group group = null;
        OriginalGroup originalGroup = null;
        UpdatedGroup updatedGroup = null;


        if (!(groupTable.containsKey(currentNodeId))){
            LOG.debug("Creating an Entry for switch in Group Table");
            newPortList.add(dstNCRef);
            newCoordinatorsMap.put(coordinator, newPortList);
            groupTable.put(currentNodeId, newCoordinatorsMap);
            group = createGroup(coordinator, newPortList);
            addGroup(coordinator, dstNCRef, group);
        }else {
            // check if the if the coordinator group is present for the switch
            if (!(groupTable.get(currentNodeId)).containsKey(coordinator)){
                LOG.debug("Adding new coordinator group");
                newPortList.add(dstNCRef);
                (groupTable.get(currentNodeId)).put(coordinator, newPortList);
                group = createGroup(coordinator, newPortList);
                addGroup(coordinator, dstNCRef, group);
            }else {
                oldPortList = (groupTable.get(currentNodeId)).get(coordinator);
                try {
                    if (!(groupTable.get(currentNodeId)).get(coordinator).contains(dstNCRef)){
                        LOG.debug("Updating coordinator group with new port");
                        (groupTable.get(currentNodeId)).get(coordinator).add(dstNCRef);
                        newPortList = (groupTable.get(currentNodeId)).get(coordinator);
                    }else {
                        LOG.debug("Port Already exist" + dstNCRef);
                        return;
                    }
                }catch (Exception e){
                    LOG.debug("Could not add Port to coordinator port list");
                }

                //Figure out a way to avoid updating group if you there is no change to the list
                LOG.debug("Calling Add group");
                originalGroup = createOriginalGroup(coordinator,oldPortList);
                updatedGroup = createUpdatedGroup(coordinator, newPortList);
                updateGroup(coordinator, dstNCRef, originalGroup, updatedGroup);
            }
        }
    }


    public void addGroup(Ipv4Address coordinator, NodeConnectorRef dstNCRef, Group group){

        InstanceIdentifier<Group> groupIID = InstanceIdentifierUtils.generateGroupInstanceIdentifier(dstNCRef, groupIdTable.get(coordinator));
        Future<RpcResult<AddGroupOutput>> future = addGrouptoConfigfData(groupIID, group);

        try {
            future.get();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        }
    }

    public void updateGroup(Ipv4Address coordinator, NodeConnectorRef dstNCRef, OriginalGroup oldGroup, UpdatedGroup newGroup){
        LOG.debug("Updating Group");

        InstanceIdentifier<Group> groupIID = InstanceIdentifierUtils.generateGroupInstanceIdentifier(dstNCRef, groupIdTable.get(coordinator));
        Future<RpcResult<UpdateGroupOutput>> future = updateGrouptoConfigData(groupIID, oldGroup, newGroup);
    }


    public Group createGroup(Ipv4Address coordinator, List<NodeConnectorRef> portlist)
    {
        LOG.debug("Creating Group");

        List<Bucket> bucketList = new ArrayList<Bucket>();
        Long groupId = 0L;
        if (!(groupIdTable.containsKey(coordinator.getValue()))){
            groupId = groupIdInc.getAndIncrement();
            groupIdTable.put(coordinator, new Long(groupId));
        }else{
            groupId = groupIdTable.get(coordinator);
        }
        // Create a bucket and associated action for each port
        for (NodeConnectorRef dstPort : portlist){

            Uri dstPortUri = dstPort.getValue().firstKeyOf(NodeConnector.class, NodeConnectorKey.class).getId();
            //Build an actions
            Action outputaction = new ActionBuilder()
                                    .setOrder(0)
                                    .setAction(new OutputActionCaseBuilder().setOutputAction(new OutputActionBuilder()
                                            .setMaxLength(0xffff)
                                            .setOutputNodeConnector(dstPortUri)
                                            .build())
                                            .build())
                                            .build();
            List<Action> actionList = new ArrayList<Action>();
            actionList.add(outputaction);


            // Create Bucket
            BucketId bucketId = new BucketId(bucketIdInc.incrementAndGet());
            Bucket outputbucket = new BucketBuilder().setBucketId(bucketId)
                                        .setKey(new BucketKey(bucketId))
                                        .setAction(ImmutableList.of(outputaction))
                                        .build();

            bucketList.add(outputbucket);
        }

        // Add bucket to buckets.
        BucketsBuilder bucketsBuilder = new BucketsBuilder().setBucket(bucketList);
        Buckets buckets = bucketsBuilder.build();
        // Build a group
        GroupBuilder groupBuilder = new GroupBuilder();
        groupBuilder.setGroupId(new GroupId(groupId))
                    .setGroupType(GroupTypes.GroupAll)
                    .setGroupName(coordinator.getValue().toString())
                    .setContainerName(coordinator.getValue().toString())
                    .setBarrier(false)
                    .setBuckets(buckets);

        Group group = groupBuilder.build();
        return group;
    }

    public OriginalGroup createOriginalGroup(Ipv4Address coordinator, List<NodeConnectorRef> portlist){

        LOG.debug("Creating Original Group");

        List<Bucket> bucketList = new ArrayList<Bucket>();
        Long groupId = groupIdTable.get(coordinator);

        // Create a bucket and associated action for each port
        for (NodeConnectorRef dstPort : portlist){

            Uri dstPortUri = dstPort.getValue().firstKeyOf(NodeConnector.class, NodeConnectorKey.class).getId();
            //Build an actions
            Action outputaction = new ActionBuilder()
                    .setOrder(0)
                    .setAction(new OutputActionCaseBuilder().setOutputAction(new OutputActionBuilder()
                            .setMaxLength(0xffff)
                            .setOutputNodeConnector(dstPortUri)
                            .build())
                            .build())
                    .build();
            List<Action> actionList = new ArrayList<Action>();
            actionList.add(outputaction);


            // Create Bucket
            BucketId bucketId = new BucketId(bucketIdInc.incrementAndGet());
            Bucket outputbucket = new BucketBuilder().setBucketId(bucketId)
                    .setKey(new BucketKey(bucketId))
                    .setAction(ImmutableList.of(outputaction))
                    .build();

            bucketList.add(outputbucket);
        }

        // Add bucket to buckets.
        BucketsBuilder bucketsBuilder = new BucketsBuilder().setBucket(bucketList);
        Buckets buckets = bucketsBuilder.build();
        // Build a group
        OriginalGroupBuilder originalGroupBuilder = new OriginalGroupBuilder();
        originalGroupBuilder.setGroupId(new GroupId(groupId))
                .setGroupType(GroupTypes.GroupAll)
                .setGroupName(coordinator.getValue().toString())
                .setContainerName(coordinator.getValue().toString())
                .setBarrier(false)
                .setBuckets(buckets);

        OriginalGroup originalGroup = originalGroupBuilder.build();

        return originalGroup;

    }

    public UpdatedGroup createUpdatedGroup(Ipv4Address coordinator, List<NodeConnectorRef> portlist){
        LOG.debug("Creating Updated Group");

        List<Bucket> bucketList = new ArrayList<Bucket>();
        Long groupId = groupIdTable.get(coordinator);

        // Create a bucket and associated action for each port
        for (NodeConnectorRef dstPort : portlist){

            Uri dstPortUri = dstPort.getValue().firstKeyOf(NodeConnector.class, NodeConnectorKey.class).getId();
            //Build an actions
            Action outputaction = new ActionBuilder()
                    .setOrder(0)
                    .setAction(new OutputActionCaseBuilder().setOutputAction(new OutputActionBuilder()
                            .setMaxLength(0xffff)
                            .setOutputNodeConnector(dstPortUri)
                            .build())
                            .build())
                    .build();
            List<Action> actionList = new ArrayList<Action>();
            actionList.add(outputaction);


            // Create Bucket
            BucketId bucketId = new BucketId(bucketIdInc.incrementAndGet());
            Bucket outputbucket = new BucketBuilder().setBucketId(bucketId)
                    .setKey(new BucketKey(bucketId))
                    .setAction(ImmutableList.of(outputaction))
                    .build();

            bucketList.add(outputbucket);
        }

        // Add bucket to buckets.
        BucketsBuilder bucketsBuilder = new BucketsBuilder().setBucket(bucketList);
        Buckets buckets = bucketsBuilder.build();
        // Build a group
        UpdatedGroupBuilder updatedGroupBuilder = new UpdatedGroupBuilder();
        updatedGroupBuilder.setGroupId(new GroupId(groupId))
                .setGroupType(GroupTypes.GroupAll)
                .setGroupName(coordinator.getValue().toString())
                .setContainerName(coordinator.getValue().toString())
                .setBarrier(false)
                .setBuckets(buckets);

        UpdatedGroup updatedGroup = updatedGroupBuilder.build();

        return updatedGroup;
    }

    /* writeGroupConfigData writes group info to config Data */

    public Flow createMulticastgroupflow(Ipv4Address coordinator, Ipv4Address dstIP, NodeConnectorRef dstPort){
        LOG.debug("Create Multicast Group");

//        long groupid = coordinator.hashCode();
        Long groupId = groupIdTable.get(coordinator);

        FlowBuilder flowBuilder = new FlowBuilder().setTableId(flowTableId).setFlowName("MULTICAST_FLOW");
        flowBuilder.setId(new FlowId(Long.toString(flowBuilder.hashCode())));

        // Create EhternetMatchBuilder
        EthernetMatchBuilder ethernetMatchBuilder = new EthernetMatchBuilder();
        ethernetMatchBuilder.setEthernetType(new EthernetTypeBuilder()
                            .setType(new EtherType(Long.valueOf(KnownEtherType.Ipv4.getIntValue()))).build());
        EthernetMatch ethernetMatch = ethernetMatchBuilder.build();

        // Create an Ipv4 Match builder

        Ipv4MatchBuilder ipv4MatchBuilder = new Ipv4MatchBuilder();
        ipv4MatchBuilder.setIpv4Source(new Ipv4Prefix(coordinator.getValue() + "/32")).build();
        ipv4MatchBuilder.setIpv4Destination(new Ipv4Prefix(dstIP.getValue() + "/32")).build();
        Ipv4Match ipv4Match = ipv4MatchBuilder.build();

        //Layer 4 Match
        IpMatchBuilder ipMatchBuilder = new IpMatchBuilder();
        ipMatchBuilder.setIpProto(IpVersion.Ipv4);

        ipMatchBuilder.setIpProtocol(UDP);

        UdpMatchBuilder udpMatchBuilder = new UdpMatchBuilder();
        udpMatchBuilder.setUdpSourcePort(new PortNumber(5353)).build();
        udpMatchBuilder.setUdpDestinationPort(new PortNumber(5353)).build();

        Layer4Match udpMatch = udpMatchBuilder.build();

        Match match = new MatchBuilder().setEthernetMatch(ethernetMatch)
                                        .setLayer3Match(ipv4Match)
                                        .setIpMatch(ipMatchBuilder.build())
                                        .setLayer4Match(udpMatch)
                                        .build();

        GroupAction groupAction = new GroupActionBuilder()
                                .setGroupId(groupId)
                                .setGroup("mDNS Multicast")
                                .build();

        Action outputaction = new ActionBuilder()
                                .setOrder(0)
                                .setAction(new GroupActionCaseBuilder()
                                        .setGroupAction(groupAction).build()).build();


        ApplyActions applyActions = new ApplyActionsBuilder().setAction(ImmutableList.of(outputaction)).build();

        Instruction applyActionsInstruction = new InstructionBuilder()
                                                .setOrder(0)
                                                .setInstruction(new ApplyActionsCaseBuilder()
                                                                .setApplyActions(applyActions)
                                                                .build())
                                                .build();
        flowBuilder.setMatch(match)
                    .setInstructions(new InstructionsBuilder()
                    .setInstruction(ImmutableList.of(applyActionsInstruction))
                    .build())
                .setPriority(10)
                .setBufferId(OFConstants.OFP_NO_BUFFER)
                .setHardTimeout(35)
                .setIdleTimeout(720)
                .setCookie(new FlowCookie(BigInteger.valueOf(flowCookieInc.getAndIncrement())))
                .setFlags(new FlowModFlags(false,false,false,false,false));

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

    private Future<RpcResult<AddGroupOutput>> addGrouptoConfigfData(InstanceIdentifier<Group> groupPath
            ,Group group){

        final InstanceIdentifier<Node> nodeIID = groupPath.<Node>firstIdentifierOf(Node.class);
        final AddGroupInputBuilder builder = new AddGroupInputBuilder(group);
        builder.setNode(new NodeRef(nodeIID));
        builder.setGroupRef(new GroupRef(groupPath));
        builder.setTransactionUri(new Uri(group.getGroupId().getValue().toString()));
        AddGroupInput addGroupInput = builder.build();
        return salGroupService.addGroup(addGroupInput);

    }

    private Future<RpcResult<UpdateGroupOutput>> updateGrouptoConfigData(InstanceIdentifier<Group> groupPath,
                                                                         OriginalGroup oldGroup, UpdatedGroup newGroup){
        final InstanceIdentifier<Node> nodeIID = groupPath.<Node>firstIdentifierOf(Node.class);
        final UpdateGroupInputBuilder builder = new UpdateGroupInputBuilder()
                                                .setNode(new NodeRef(nodeIID))
                                                .setGroupRef(new GroupRef(groupPath))
                                                .setTransactionUri(new Uri(newGroup.getGroupId().getValue().toString()));
        builder.setOriginalGroup(oldGroup).build();
        builder.setUpdatedGroup(newGroup).build();

        return salGroupService.updateGroup(builder.build());
    }

    @Override
    public void onHostRemovedNotification(HostRemovedNotification notification) {
        LOG.debug("Remove GroupId Entry");

        for (NodeId nodeId: groupTable.keySet()){
            if (groupTable.get(nodeId).containsKey(notification.getIPAddress())){
                System.out.println("Removing Group Table entry for: " + notification.getIPAddress());
                groupTable.remove(nodeId);
            }
        }

        if (groupIdTable.containsKey(notification.getIPAddress())){
            System.out.println("Removing Group Id for: " + notification.getIPAddress());
            groupIdTable.remove(notification.getIPAddress());
        }
    }

    @Override
    public void onHostAddedNotification(HostAddedNotification notification) {

    }

    @Override
    public Future<RpcResult<Void>> flushGrpTable() {
        LOG.debug("Flush Group Table Entries");
        groupTable.clear();
        groupIdTable.clear();
        groupIdInc.set(0L);
        return Futures.immediateFuture(RpcResultBuilder.<Void>success().build());
    }
}
