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
import org.opendaylight.controller.md.sal.binding.api.ReadOnlyTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv4Address;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.MacAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeConnectorId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeConnectorRef;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.Nodes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.node.NodeConnector;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.node.NodeConnectorKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.NodeKey;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Link;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutionException;

//import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Link;

public class HostManager {

    private static final Logger LOG = LoggerFactory.getLogger(HostManager.class);
    private final DataBroker dataBroker;

    private HashMap<Ipv4Address, InstanceIdentifier<NodeConnector>> ipv4AddressMapping;
    private HashMap<MacAddress, InstanceIdentifier<NodeConnector>> macAddressMapping;
    private HashMap<String, NodeConnectorRef> controllerswitchconnector;


    public HostManager(DataBroker dataBroker) {
        this.dataBroker = dataBroker;

        //Initialize all the Mappings
        ipv4AddressMapping = new HashMap<>();
        macAddressMapping = new HashMap<>();
        controllerswitchconnector = new HashMap<String, NodeConnectorRef>();
    }

    public synchronized NodeConnectorRef getControllerSwitchConnector(String nodeId){
        NodeConnectorRef controllerSwitchNc = controllerswitchconnector.get(nodeId);
        if (controllerSwitchNc != null){
            return controllerSwitchNc;
        }else {
            readInventory();
            return controllerswitchconnector.get(nodeId);
        }
    }

    // Read Inventory to obtain node details
    private void readInventory(){
        synchronized (this) {
            InstanceIdentifier.InstanceIdentifierBuilder<Nodes> nodesInstanceIdentifierBuilder = InstanceIdentifier.<Nodes>builder(Nodes.class);
            Nodes nodes = null;

            ReadOnlyTransaction readOnlyTransaction = dataBroker.newReadOnlyTransaction();

            try {
                Optional<Nodes> dataObjectOptional = null;
                dataObjectOptional = readOnlyTransaction.read(
                        LogicalDatastoreType.OPERATIONAL,
                        nodesInstanceIdentifierBuilder.build()).get();
                if (dataObjectOptional.isPresent())
                    nodes = (Nodes) dataObjectOptional.get();
            } catch (InterruptedException e) {
                LOG.error("Failed to read nodes from Operation data store.");
                readOnlyTransaction.close();
                throw new RuntimeException(
                        "Failed to read nodes from Operation data store.", e);
            } catch (ExecutionException e) {
                LOG.error("Failed to read nodes from Operation data store.");
                readOnlyTransaction.close();
                throw new RuntimeException(
                        "Failed to read nodes from Operation data store.", e);
            }

            if (nodes != null) {
                // Get NodeConnectors for each node
                for (Node node : nodes.getNode()) {
                    ArrayList<NodeConnectorRef> nodeConnectorRefs = new ArrayList<NodeConnectorRef>();
                    List<NodeConnector> nodeConnectors = node
                            .getNodeConnector();
                    if (nodeConnectors != null) {
                        for (NodeConnector nC : nodeConnectors) {
                            if (nC.getKey().toString().contains("LOCAL")) {
                                continue;
                            }
                            nodeConnectorRefs.add(getNCRef(node, nC));
                        }
                    }
                    /*
                     * switchNodeConnectors.put(node.getId().getValue(),
                     * nodeConnectorRefs);
                     */

                    controllerswitchconnector.put(node.getId().getValue(),
                            getLocalNCRef(node));
                }
            }
            readOnlyTransaction.close();
        }

    }

    private NodeConnectorRef getNCRef(Node node, NodeConnector nodeConnector) {
        NodeConnectorRef ncRef = new NodeConnectorRef(InstanceIdentifier
                .<Nodes> builder(Nodes.class)
                .<Node, NodeKey> child(Node.class, node.getKey())
                .<NodeConnector, NodeConnectorKey> child(NodeConnector.class,
                        nodeConnector.getKey()).build());
        return ncRef;
    }

    private NodeConnectorRef getLocalNCRef(Node node) {
        NodeConnectorRef ncRef = new NodeConnectorRef(InstanceIdentifier
                .<Nodes> builder(Nodes.class)
                .<Node, NodeKey> child(Node.class, node.getKey())
                .<NodeConnector, NodeConnectorKey> child(
                        NodeConnector.class,
                        new NodeConnectorKey(new NodeConnectorId(node.getId()
                                .getValue() + ":LOCAL"))).build());
        return ncRef;
    }

    public synchronized void addIpv4Address(Ipv4Address ipv4Address,
                                            InstanceIdentifier<NodeConnector> ncid) {
        if (ncid != null) {
            NodeConnector nc = getNodeConnectorFromII(ncid);
            if (nc != null) {
                if (!ipv4AddressMapping.containsKey(ipv4Address)) {
                    LOG.info("Adding " + ipv4Address.getValue()
                            + " node Connector: " + nc.getId());
                    ipv4AddressMapping.put(ipv4Address, ncid);
                } else {
                    NodeConnector oldNc = getIpNodeConnector(ipv4Address);
                    if(oldNc != null) {
                        LOG.warn("Duplicate entry for "
                                + ipv4Address.getValue() + "(new in " + nc.getId()
                                + ", old in " + oldNc.getId() + ")");
                    }
                }
            }
        }
    }

    public synchronized void addMacAddress(MacAddress macAddress,
                                           InstanceIdentifier<NodeConnector> ncid) {
        NodeConnector nc = getNodeConnectorFromII(ncid);
        if (nc != null) {
            if (macAddressMapping.containsKey(macAddress)) {
                LOG.info("Adding " + macAddress.getValue().toString()
                        + " node Connector: " + nc.getId());
                macAddressMapping.put(macAddress, ncid);
            } else {
                NodeConnector oldNc = getMacNodeConnector(macAddress);
                if(oldNc != null) {
                    LOG.warn("Duplicate entry for " + macAddress.getValue()
                            + "(new in " + nc.getId() + ", old in " + oldNc.getId());
                }
            }
        }

    }


    private NodeConnector getNodeConnectorFromII(
            InstanceIdentifier<NodeConnector> nodeConnectorId) {
        ListenableFuture<Optional<NodeConnector>> futureNodeConnector;
        try (ReadOnlyTransaction readTx = dataBroker.newReadOnlyTransaction()) {
            futureNodeConnector = readTx.read(LogicalDatastoreType.OPERATIONAL,
                    nodeConnectorId);
            readTx.close();
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

    private Node getNodeFromII(InstanceIdentifier<Node> nodeId) {
        ListenableFuture<Optional<Node>> futureNode;
        try (ReadOnlyTransaction readTx = dataBroker.newReadOnlyTransaction()) {
            futureNode = readTx.read(LogicalDatastoreType.OPERATIONAL, nodeId);
            readTx.close();
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

        if (ipv4AddressMapping.containsKey(ipv4Address)) {
            InstanceIdentifier<NodeConnector> nodeConnectorId = ipv4AddressMapping
                    .get(ipv4Address);
            nodeConnector = getNodeConnectorFromII(nodeConnectorId);
        }

        return nodeConnector;
    }

    public Node getIpNode(Ipv4Address ipv4Address) {
        Node node = null;
        if (ipv4AddressMapping.containsKey(ipv4Address)) {
            InstanceIdentifier<NodeConnector> nodeConnectorId = ipv4AddressMapping
                    .get(ipv4Address);
            InstanceIdentifier<Node> nodeId = nodeConnectorId
                    .firstIdentifierOf(Node.class);
            node = getNodeFromII(nodeId);
        }

        return node;
    }

    public NodeConnector getMacNodeConnector(MacAddress macAddress) {
        NodeConnector nodeConnector = null;

        if (macAddressMapping.containsKey(macAddress)) {
            InstanceIdentifier<NodeConnector> nodeConnectorId = macAddressMapping
                    .get(macAddress);
            nodeConnector = getNodeConnectorFromII(nodeConnectorId);
        }

        return nodeConnector;
    }


    public Node getMacNode(MacAddress macAddress) {
        Node node = null;

        if (macAddressMapping.containsKey(macAddress)) {
            InstanceIdentifier<NodeConnector> nodeConnectorId = macAddressMapping
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
        if (ipv4AddressMapping.containsKey(ipv4Address)) {
            node = getIpNode(ipv4Address);
            nc = getIpNodeConnector(ipv4Address);
            return getNCRef(node, nc);
        }
        return ncRef;
    }

    public HashMap<String, NodeConnectorRef> getControllerSwitchConnectors() {
        return controllerswitchconnector;
    }


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