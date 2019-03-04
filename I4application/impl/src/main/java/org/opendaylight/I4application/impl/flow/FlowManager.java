/*
 * Copyright © 2016 Basavaraj and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

/**
 * Class used as Flow Manager. Finds shortest Path using shortest Path
 * calculator module. Use Host Manager to get the Src Node , Dst Node and Node connector
 * for both src and dst nodes.
 */

package org.opendaylight.I4application.impl.flow;

import org.opendaylight.I4application.impl.Topology.HostManager;
import org.opendaylight.I4application.impl.Topology.NetworkGraphService;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv4Address;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.MacAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeConnectorRef;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.node.NodeConnector;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Link;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

public class FlowManager {

    private final static Logger LOG = LoggerFactory.getLogger(org.opendaylight.I4application.impl.flow.FlowManager.class);

    private HostManager hostManager;
    private NetworkGraphService networkGraphService;
    private List<List<Link>> paths = null;
    private FlowWriter flowWriter;
    private MDNSFlowWriter mDNSFlowWriter;

    private Ipv4Address mDNSMulticastAddr = Ipv4Address.getDefaultInstance("224.0.0.251");


    public FlowManager(HostManager hostManager, NetworkGraphService networkGraphService,
                       FlowWriter flowWriter, MDNSFlowWriter mDNSFlowWriter) {
        this.hostManager = hostManager;
        this.networkGraphService = networkGraphService;
        this.flowWriter = flowWriter;
        this.mDNSFlowWriter = mDNSFlowWriter;
    }


    /**
     * Recieve IP src&dst , mac src&dst and check if the path exists for the combination.
     *
     */
    public boolean ipPktFlowManager(Ipv4Address srcIP, MacAddress srcMAC, Ipv4Address dstIP, MacAddress dstMAC){

        List<Link> path = null;
        List<Link> revPath = null;
        NodeId srcNodeId, dstNodeId;
        boolean forwardPath;

        // Use the srcIP, dstIP and get NodeConnectorRef and Node from Host Manager
        NodeConnectorRef srcNodeConnRef = hostManager.getIpNodeConnectorRef(srcIP);
        NodeConnectorRef dstNodeConnRef = hostManager.getIpNodeConnectorRef(dstIP);

        Node srcNode = hostManager.getIpNode(srcIP);
        Node dstNode = hostManager.getIpNode(dstIP);

        if (srcNode !=null && dstNode !=null){

            srcNodeId = new NodeId(srcNode.getKey().getId().getValue());
            dstNodeId = new NodeId(dstNode.getKey().getId().getValue());

            flowWriter.addFlowtoNode(srcIP, srcMAC, srcNodeConnRef);
            flowWriter.addFlowtoNode(dstIP, dstMAC, dstNodeConnRef);

            if (srcNodeId.equals(dstNodeId)) {
                LOG.debug("Both source and destination attached to same switch");
                return true;
            }

            path = FindPath(srcNodeId, dstNodeId);
            if (path!=null){
                forwardPath = flowWriter.addFlowToPathNodes(srcIP, srcMAC, dstIP, dstMAC, srcNode, path);
                if (forwardPath){
                    //Add Reverse Path
                    revPath = reversePath(path);
                    if (revPath != null){
                        return flowWriter.addFlowToPathNodes(dstIP, dstMAC,srcIP, srcMAC, dstNode, revPath);
                    }
                }
            }else {
                System.out.println("Could not find the route");
                return false;
            }
        }
        return false;
    }

    /**
     * Method is used to find path between srchost and identified co-ordinator
     * @param opcuaServer source host ip address
     * @param coordinator Identifed coordinator ip address
     */

    public boolean mDNSPktFlowManager(Ipv4Address opcuaServer, Ipv4Address coordinator){
        LOG.info("mDNS Packet Flow Manager");

        List<Link> path = null;
        List<Link> reversePath = null;
        boolean forPath, revPath;

        NodeId srcNodeId, dstNodeId;

        // Use the SrcIP and dstIP to get the NodeConnector and Node from the host Manager
        NodeConnector srcNodeConn = hostManager.getIpNodeConnector(opcuaServer);
        NodeConnector dstNodeConn = hostManager.getIpNodeConnector(coordinator);

        Node srcNode = hostManager.getIpNode(opcuaServer);
        Node dstNode = hostManager.getIpNode(coordinator);

        if (srcNode != null && dstNode != null && srcNodeConn != null && dstNodeConn != null){
            srcNodeId = new NodeId(srcNode.getKey().getId().getValue());
            dstNodeId = new NodeId(dstNode.getKey().getId().getValue());

            path = FindPath(srcNodeId, dstNodeId);

            if(path != null){
                LOG.info("Shortest Path found");
                // Path is between src host and coordinator, but we set the match field as the src Ip and multicast IP address
                forPath = mDNSFlowWriter.mDNSForwardPathFlow(opcuaServer, mDNSMulticastAddr,
                        srcNode, dstNode, srcNodeConn, dstNodeConn, path);
                if (forPath){
                    mDNSFlowWriter.mDNSReverseFlowHanlder(opcuaServer,coordinator,srcNode,srcNodeConn, path, mDNSMulticastAddr);
                }
                return forPath;
            }
            return false;
        }
        return false;
    }

    public List<Link> FindPath(NodeId srcNodeId, NodeId dstNodeId){
        List<Link> shortestPath = null;
        //ArrayList<Link> shortestPath = null;

        if (srcNodeId !=null && dstNodeId != null){
            shortestPath = networkGraphService.getPath(srcNodeId, dstNodeId);
            return shortestPath;
        }
        return null;
    }

    public List<Link> reversePath(List<Link> forwardPath){

        List<Link> revereLinkSet = new LinkedList<Link>(forwardPath);
        if((forwardPath != null)){
            Collections.reverse(revereLinkSet);
            return revereLinkSet;
        }
        return null;
    }
}
