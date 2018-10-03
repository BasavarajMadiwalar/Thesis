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
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.node.NodeConnector;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Link;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

public class FlowManager {

    private HostManager hostManager;
    private NetworkGraphService networkGraphService;
    private List<List<Link>> paths = null;
    private FlowWriter flowWriter;
    public FlowManager(HostManager hostManager, NetworkGraphService networkGraphService, FlowWriter flowWriter) {
        this.hostManager = hostManager;
        this.networkGraphService = networkGraphService;
        this.flowWriter = flowWriter;
    }


    /**
     * Recieve IP src&dst , mac src&dst and check if the flow exists for the combination.
     *
     */
    public void handleIpPacket(Ipv4Address srcIP, MacAddress srcMAC, Ipv4Address dstIP, MacAddress dstMAC){

        //ArrayList<Link> path = null;
        List<Link> path = null;
        List<Link> revPath = null;
        NodeId srcNodeId, dstNodeId;
        boolean forwardPath;
        // Use the srcIP and dstIP and get NodeConnector and Node from Host Manager

        NodeConnector srcNodeConn = hostManager.getIpNodeConnector(srcIP);
        NodeConnector dstNodeConn = hostManager.getIpNodeConnector(dstIP);

        Node srcNode = hostManager.getIpNode(srcIP);
        Node dstNode = hostManager.getIpNode(dstIP);

        //System.out.println("The Source Node is : " + srcNode.getKey().getId().getValue());
        //srcNode.getKey().getId().getValue();

        if (srcNode !=null && srcNodeConn != null && dstNode !=null && dstNodeConn != null){
            srcNodeId = new NodeId(srcNode.getKey().getId().getValue());
            dstNodeId = new NodeId(dstNode.getKey().getId().getValue());

            path = FindPath(srcNodeId, dstNodeId);
            if (path!=null){
                forwardPath = flowWriter.addE2Epathflow(srcIP, dstIP,
                        srcMAC, dstMAC, srcNode, dstNode, srcNodeConn, dstNodeConn, path);
                if (forwardPath){
                    //Add Reverse Path
                    revPath = reversePath(path);
                    if (revPath != null){
                        //flowWriter.addE2Epathflow(dstIP,srcIP,dstMAC,srcMAC,dstNode,srcNode,dstNodeConn,srcNodeConn,revPath);
                        flowWriter.addFlowToPathNodes(dstIP, dstMAC,srcIP, srcMAC, dstNode, revPath);
                    }

                }
            }else {
                System.out.println("Could not find the route");
            }
        }
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
