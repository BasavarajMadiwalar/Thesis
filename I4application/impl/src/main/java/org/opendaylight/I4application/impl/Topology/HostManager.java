/*
 * Copyright © 2016 Basavaraj and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.I4application.impl.Topology;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.ListenableFuture;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.NotificationPublishService;
import org.opendaylight.controller.md.sal.binding.api.ReadOnlyTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv4Address;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.MacAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeConnectorRef;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.Nodes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.node.NodeConnector;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.node.NodeConnectorKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.NodeKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.hostmanagernotification.rev150105.HostAddedNotification;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.hostmanagernotification.rev150105.HostAddedNotificationBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.hostmanagernotification.rev150105.HostRemovedNotification;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.hostmanagernotification.rev150105.HostRemovedNotificationBuilder;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Link;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.concurrent.ExecutionException;

public class HostManager {

    private static final Logger LOG = LoggerFactory.getLogger(HostManager.class);
    private final DataBroker dataBroker;

    private HashMap<Ipv4Address, InstanceIdentifier<NodeConnector>> ipv4Address_port_Map;
    private HashMap<MacAddress, InstanceIdentifier<NodeConnector>> macAddress_port_Map;
    private HashMap<String, NodeConnectorRef> controllerswitchconnector;

    private NotificationPublishService notificationProvider;

    public HostManager(DataBroker dataBroker, NotificationPublishService notificationPublishService) {
        this.dataBroker = dataBroker;
        this.notificationProvider = notificationPublishService;

//        //Initialize all the Mappings
        ipv4Address_port_Map = new HashMap<>();
        macAddress_port_Map = new HashMap<>();
        controllerswitchconnector = new HashMap<>();
    }


    private NodeConnectorRef getNCRef(Node node, NodeConnector nodeConnector) {

        NodeConnectorRef ncRef = new NodeConnectorRef(InstanceIdentifier
                .<Nodes> builder(Nodes.class)
                .<Node, NodeKey> child(Node.class, node.getKey())
                .<NodeConnector, NodeConnectorKey> child(NodeConnector.class,
                        nodeConnector.getKey()).build());
        return ncRef;
    }


    public synchronized void addIpv4Address(Ipv4Address ipv4Address,
                                            InstanceIdentifier<NodeConnector> ncid) {
        if (ncid != null) {
            NodeConnector nc = getNodeConnectorFromII(ncid);                           // We need to access the inventory to the value stored at the given IID
            if (nc != null) {
                if (!ipv4Address_port_Map.containsKey(ipv4Address)) {
                    LOG.debug("Adding " + ipv4Address.getValue()
                            + " node Connector: " + nc.getId());
                    ipv4Address_port_Map.put(ipv4Address, ncid);
                    // Notify about Host Addition
                    HostAddedNotification hostAddedNotification =  new HostAddedNotificationBuilder()
                            .setIPAddress(ipv4Address)
                            .setSwitchId(getIpNode(ipv4Address).getId().toString())
                            .build();
                    notificationProvider.offerNotification(hostAddedNotification);
                } else {
                    NodeConnector oldNc = getIpNodeConnector(ipv4Address);
                    if(oldNc!=null) {
                        LOG.debug("Duplicate entry for "
                                + ipv4Address.getValue() + "(new in " + nc.getId()
                                + ", old in " + oldNc.getId() + ")");
                    }
                }
            }
        }
    }

    public synchronized void removeIpv4Address(Ipv4Address ipv4Address){

        if (ipv4Address != null){
            LOG.debug("Remove Ipv4Address {} from HostManager", ipv4Address.getValue());
            ipv4Address_port_Map.remove(ipv4Address);
            HostRemovedNotification hostRemovedNotification = new HostRemovedNotificationBuilder()
                                        .setIPAddress(ipv4Address).build();
            notificationProvider.offerNotification(hostRemovedNotification);
        }
    }

    public synchronized void removeMacAddress(MacAddress macAddress){
        if (macAddress != null){
            LOG.debug("Remove MacAddress {} from HostManager", macAddress.getValue());
            macAddress_port_Map.remove(macAddress);
        }
    }



    public synchronized void addMacAddress(MacAddress macAddress,
                                           InstanceIdentifier<NodeConnector> ncid) {
        NodeConnector nc = getNodeConnectorFromII(ncid);
        if (nc != null) {
            if (macAddress_port_Map.containsKey(macAddress)) {
                LOG.info("Adding " + macAddress.getValue().toString()
                        + " node Connector: " + nc.getId());
                macAddress_port_Map.put(macAddress, ncid);
            } else {
                NodeConnector oldNc = getMacNodeConnector(macAddress);
                if(oldNc != null) {
                    LOG.warn("Duplicate entry for " + macAddress.getValue()
                            + "(new in " + nc.getId() + ", old in " + oldNc.getId());
                }
            }
        }
    }

    /*
    For the given node connector IID, it returns the NodeConnector
     */

    private NodeConnector getNodeConnectorFromII(
            InstanceIdentifier<NodeConnector> nodeConnectorId) {

        ListenableFuture<Optional<NodeConnector>> futureNodeConnector;

        try (ReadOnlyTransaction readTx = dataBroker.newReadOnlyTransaction()) {
            futureNodeConnector = readTx.read(LogicalDatastoreType.OPERATIONAL,
                    nodeConnectorId);
        }
        Optional<NodeConnector> opNodeConnector = null;
        try {
            opNodeConnector = futureNodeConnector.get();
        } catch (ExecutionException | InterruptedException ex) {
            LOG.warn("Couldn't get optional nodeConnector");
        }
        if (opNodeConnector != null && opNodeConnector.isPresent()) {
            return opNodeConnector.get();
        }
        return null;
    }

    /*
    Returns Node for give Node IID
     */

    private Node getNodeFromII(InstanceIdentifier<Node> nodeId) {

        ListenableFuture<Optional<Node>> futureNode;

        try (ReadOnlyTransaction readTx = dataBroker.newReadOnlyTransaction()) {
            futureNode = readTx.read(LogicalDatastoreType.OPERATIONAL, nodeId);
        }

        Optional<Node> opNode = null;
        try {
            opNode = futureNode.get();
        } catch (ExecutionException | InterruptedException ex) {
            LOG.warn("Couldn't get optional node");
        }
        if (opNode != null && opNode.isPresent()) {
            return opNode.get();
        }
        return null;
    }

    public NodeConnector getIpNodeConnector(Ipv4Address ipv4Address) {
        NodeConnector nodeConnector = null;

        if (ipv4Address_port_Map.containsKey(ipv4Address)) {
            InstanceIdentifier<NodeConnector> nodeConnectorId = ipv4Address_port_Map
                    .get(ipv4Address);
            nodeConnector = getNodeConnectorFromII(nodeConnectorId);
        }

        return nodeConnector;
    }

    public Node getIpNode(Ipv4Address ipv4Address) {
        Node node = null;
        if (ipv4Address_port_Map.containsKey(ipv4Address)) {


            InstanceIdentifier<NodeConnector> nodeConnectorId = ipv4Address_port_Map
                    .get(ipv4Address);

            InstanceIdentifier<Node> nodeId = nodeConnectorId
                    .firstIdentifierOf(Node.class);
            node = getNodeFromII(nodeId);
        }
        return node;
    }

    public NodeConnector getMacNodeConnector(MacAddress macAddress) {
        NodeConnector nodeConnector = null;

        if (macAddress_port_Map.containsKey(macAddress)) {
            InstanceIdentifier<NodeConnector> nodeConnectorId = macAddress_port_Map
                    .get(macAddress);
            nodeConnector = getNodeConnectorFromII(nodeConnectorId);
        }
        return nodeConnector;
    }

    public Node getMacNode(MacAddress macAddress) {
        Node node = null;

        if (macAddress_port_Map.containsKey(macAddress)) {
            InstanceIdentifier<NodeConnector> nodeConnectorId = macAddress_port_Map
                    .get(macAddress);
            InstanceIdentifier<Node> nodeId = nodeConnectorId
                    .firstIdentifierOf(Node.class);
            node = getNodeFromII(nodeId);
        }
        return node;
    }

    public NodeConnectorRef getIpNodeConnectorRef(Ipv4Address ipv4Address) {
        NodeConnectorRef ncRef = null;
        Node node;
        NodeConnector nc;
        if (ipv4Address_port_Map.containsKey(ipv4Address)) {
            node = getIpNode(ipv4Address);
            nc = getIpNodeConnector(ipv4Address);
            return getNCRef(node, nc);
        }
        return ncRef;
    }

    // Move these methods to Flow Writer class
    public static NodeConnectorRef getSourceNodeConnectorRef(Link link) {
        InstanceIdentifier<NodeConnector> ncII = org.opendaylight.I4application.impl.utils.InstanceIdentifierUtils
                .createNodeConnectorIdentifier(link.getSource().getSourceNode()
                        .getValue(), link.getSource().getSourceTp().getValue());
        return new NodeConnectorRef(ncII);
    }

    public static NodeConnectorRef getDestNodeConnectorRef(Link link) {
        InstanceIdentifier<NodeConnector> ncII = org.opendaylight.I4application.impl.utils.InstanceIdentifierUtils
                .createNodeConnectorIdentifier(link.getDestination()
                        .getDestNode().getValue(), link.getDestination()
                        .getDestTp().getValue());
        return new NodeConnectorRef(ncII);
    }
}