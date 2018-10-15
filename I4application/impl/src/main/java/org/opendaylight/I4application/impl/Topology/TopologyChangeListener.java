/*
 * Copyright © 2016 Basavaraj and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.I4application.impl.Topology;

import com.google.common.base.Optional;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataChangeListener;
import org.opendaylight.controller.md.sal.binding.api.ReadOnlyTransaction;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataBroker;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataChangeEvent;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv4Address;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.MacAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.address.tracker.rev140617.AddressCapableNodeConnector;
import org.opendaylight.yang.gen.v1.urn.opendaylight.address.tracker.rev140617.address.node.connector.Addresses;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.Nodes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.node.NodeConnector;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NetworkTopology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.TopologyId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.Topology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.TopologyKey;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Link;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class TopologyChangeListener implements DataChangeListener {

    private DataBroker dataBroker;
    private String topologyId = "flow:1";
    private boolean threadReschedule = false;
    private boolean networkGraphRefreshScheduled = false;
    private HostManager hostManager;
    private NetworkGraphService networkGraphService;
    private Long graphRefreshDelay = 1000L;



    private ListenerRegistration<DataChangeListener> linkDataChangeListener;
    private ListenerRegistration<DataChangeListener> addressDataChangeListener;

    private final ScheduledExecutorService topologyDataChangeEventProcessor = Executors.newScheduledThreadPool(1);


    public TopologyChangeListener(DataBroker dataBroker, HostManager hostManager, NetworkGraphService networkGraphService) {
        this.dataBroker = dataBroker;
        this.hostManager = hostManager;
        this.networkGraphService = networkGraphService;

    }



    public void registerAsListener(){
        // Use it to listen for data changes to specific nodes in
        // Create an IID for link data tree
        InstanceIdentifier<Link> linkIID = InstanceIdentifier.builder(NetworkTopology.class)
                .child(Topology.class, new TopologyKey(new TopologyId(topologyId)))
                .child(Link.class).build();
        linkDataChangeListener = dataBroker.registerDataChangeListener(LogicalDatastoreType.OPERATIONAL,
                linkIID, this, AsyncDataBroker.DataChangeScope.BASE);//Listen for change to node only(not child)

        // Create an IID for Addresses of l2switch-addressTrackerModule
        InstanceIdentifier<Addresses> addressesIID = InstanceIdentifier.builder(Nodes.class)
                .child(Node.class).child(NodeConnector.class).augmentation(AddressCapableNodeConnector.class)
                .child(Addresses.class).build();

        // Register for Address
        addressDataChangeListener = dataBroker.registerDataChangeListener(LogicalDatastoreType.OPERATIONAL,
                addressesIID, this, AsyncDataBroker.DataChangeScope.SUBTREE);
    }

    private boolean handleLinkCreated(Link link){
        //To do Some
        if(!(link.getLinkId().getValue().contains("host"))){
            // It's not a link with host connected, so we need to updated graph
            return true;
        }else {
            // It's host link added to network, so not need to update
            return false;
        }
    }

    private boolean handleLinkRemoved(Link link){
        //To do
        if (!(link.getLinkId().getValue().contains("host"))){
            return true;
        }else {
            return false;
        }
    }

    private void handleAddressesCreated(InstanceIdentifier<?> id, Addresses addresses){
        //Add Address to HostManager
        InstanceIdentifier<NodeConnector> nodeConnectorId = id.firstIdentifierOf(NodeConnector.class);

        IpAddress ip = addresses.getIp();
        MacAddress mac = addresses.getMac();

        Ipv4Address ipv4Address = ip.getIpv4Address();
        if ((ipv4Address !=null)){
            hostManager.addIpv4Address(ipv4Address, nodeConnectorId);
        }
        if (mac !=null){
            hostManager.addMacAddress(mac, nodeConnectorId);
        }
        if (mac!=null && ipv4Address!=null){
            //Add a log ipv4 Address and Mac address is added
            System.out.println("New Ipv4 Address Added: " + ipv4Address.getValue() + " & new mac added: " + mac.getValue());
        }else if (mac != null){
            // Add a log saying only Mac address is added
            System.out.println("New mac added: " + mac);
        }
    }


    private void handleAddressesRemoved(InstanceIdentifier<?> id, Addresses addresses){
        // Remove Address from HostManager
    }


    @Override
    public void onDataChanged(AsyncDataChangeEvent<InstanceIdentifier<?>, DataObject> change) {
        if (change == null){
            return;
        }

        Map<InstanceIdentifier<?>, DataObject> changedData = change.getCreatedData();
        Set<InstanceIdentifier<?>> removedPath = change.getRemovedPaths();
        Map<InstanceIdentifier<?>, DataObject> originalData = change.getOriginalData();

        boolean isGraphUpdated = false;

        if (changedData !=null && !changedData.isEmpty()){
            Set<InstanceIdentifier<?>> ids = changedData.keySet();
            for (InstanceIdentifier<?> id : ids){
                // Get the type of DataObject is in Notified Data
                Class<?> targetType = id.getTargetType();
                if (targetType.equals(Link.class)){
                    /**
                     * Get the Link value using key-id. It equivalent to getting
                     * the entire row of table.
                     */
                    Link link = (Link) changedData.get(id);
                    isGraphUpdated = isGraphUpdated ? true : handleLinkCreated(link);
                }else if (targetType.equals(Addresses.class)){
                    Addresses addresses = (Addresses) changedData.get(id);
                    handleAddressesCreated(id, addresses);
                } else {
                    //Add a log statment
                    // LOG.info("Created TargetType: " + targetType);
                }
            }
        }

        if (removedPath!=null && !removedPath.isEmpty() && originalData !=null
             && !originalData.isEmpty()){
            for (InstanceIdentifier<?> id : removedPath){
                // Class<?> is used as we are not sure about the object type
                Class<?> targetType = id.getTargetType();
                if (targetType.equals(Link.class)){
                    Link link = (Link) originalData.get(id);
                    isGraphUpdated = isGraphUpdated ? true : handleLinkRemoved(link);
                }else if (targetType.equals(Addresses.class)){
                    Addresses addresses = (Addresses) originalData.get(id);
                    handleAddressesRemoved(id, addresses);
                }else {
                    //Add a log
                    // LOG.info("Created TargetType: " + targetType);
                }
            }

        }

        if (!isGraphUpdated){
            return;
        }

        // We reach this stage only if we need to update Network Graph
        if (!networkGraphRefreshScheduled){
            synchronized (this){
                if (!networkGraphRefreshScheduled){
                    topologyDataChangeEventProcessor.schedule(new TopologyDataChangeEventProcessor(),
                            graphRefreshDelay, TimeUnit.MICROSECONDS);
                    networkGraphRefreshScheduled = true;
                }
            }
        }else {
                //AlreadyScheduled for Network Graph refresh
                threadReschedule=true;
        }

    }


    private class TopologyDataChangeEventProcessor implements Runnable{
        @Override
        public void run() {

            networkGraphRefreshScheduled = false;
            networkGraphService.clear();
            List<Link> links = getLinksFromTopology();
            if (links == null || links.isEmpty()){
                return;
            }
            networkGraphService.addLinks(links);

        }

        private List<Link> getLinksFromTopology(){
            InstanceIdentifier<Topology> topologyIID = InstanceIdentifier
                    .builder(NetworkTopology.class).child(Topology.class, new TopologyKey(new TopologyId(topologyId)))
                    .build();
            Topology topology = null;
            ReadOnlyTransaction readOnlyTransaction = dataBroker.newReadOnlyTransaction();
            try {
                //Optional<Topology> topologyOptional = readOnlyTransaction.read(LogicalDatastoreType.OPERATIONAL, topologyIID).get();
                Optional<Topology> topologyOptional = readOnlyTransaction.read(LogicalDatastoreType.OPERATIONAL, topologyIID).get();
                if (topologyOptional.isPresent()){
                    topology = topologyOptional.get();
                }
            }catch (Exception e){
                readOnlyTransaction.close();
                throw new RuntimeException(
                        "Error reading from operational store, topology : "
                                + topologyIID, e);
            }
            readOnlyTransaction.close();
            if (topology == null){
                return null;
            }
            List<Link> links = topology.getLink();
            if (links == null || links.isEmpty()){
                return null;
            }

            // We Add only NodeConnecting Links
            List<Link> nodeConnLinks = new ArrayList<>();
            for (Link link : links){
                if (!(link.getLinkId().getValue().contains("host"))){
                    nodeConnLinks.add(link);
                }
            }
            return nodeConnLinks;
        }
    }
    public void close(){
        if (linkDataChangeListener!=null){
            linkDataChangeListener.close();
        }
        if (addressDataChangeListener!=null){
            addressDataChangeListener.close();
        }
    }
}
