/*
 * Copyright © 2016 Basavaraj and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.I4application.impl;

import org.opendaylight.I4application.impl.Topology.HostManager;
import org.opendaylight.I4application.impl.Topology.NetworkGraphImpl;
import org.opendaylight.I4application.impl.Topology.NetworkGraphService;
import org.opendaylight.I4application.impl.Topology.TopologyChangeListener;
import org.opendaylight.I4application.impl.flow.FlowManager;
import org.opendaylight.I4application.impl.flow.FlowWriter;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.NotificationPublishService;
import org.opendaylight.controller.md.sal.binding.api.NotificationService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.service.rev130819.SalFlowService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.group.service.rev130918.SalGroupService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.PacketProcessingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class I4applicationProvider {

    private static final Logger LOG = LoggerFactory.getLogger(I4applicationProvider.class);

    private DataBroker dataBroker;
    private NotificationService notificationService;
    private NetworkGraphService networkGraphService;
    private SalFlowService salFlowService;
    private NotificationPublishService notificationPublishService;
    private PacketProcessingService packetProcessingService;
    private SalGroupService salGroupService;

    /**
     * The Constructor is called when blue print container is created.
     * As I4applicationprovider is added to the blueprint container
      */

    public I4applicationProvider(DataBroker dataBroker, NotificationService notificationService,
                                 SalFlowService salFlowService, NotificationPublishService notificationPublishService,
                                 PacketProcessingService packetProcessingService,
                                 SalGroupService salGroupService) {
        this.dataBroker = dataBroker;
        this.notificationService = notificationService;
        this.salFlowService = salFlowService;
        this.notificationPublishService = notificationPublishService;
        this.packetProcessingService = packetProcessingService;
        this.salGroupService = salGroupService;
    }


    /**
     * Method called when the blueprint container is created.
     */
    public void init() {
        LOG.info("I4applicationProvider Session Initiated");

        HostManager hostManager = new HostManager(dataBroker);
        LOG.info("Host Manager is instantiated");

        networkGraphService = new NetworkGraphImpl();

        /**
         * TopologyChangeListerner - Listen for topology changes
         */
        TopologyChangeListener topologyChangeListener = new TopologyChangeListener(dataBroker, hostManager, networkGraphService);
        topologyChangeListener.registerAsListener();
        LOG.info("Topology Data Change Listner registered");

        /**
         * Flow writer - Create OF Flow objects
         *
         */

        FlowWriter flowWriter = new FlowWriter(salFlowService, dataBroker, salGroupService); // In Future need to pass SALGroup Serive for flow writing

        /**
         * Create a Flow Manager and pass and Instance of Host Manager
         */
        FlowManager flowManager = new FlowManager(hostManager, networkGraphService, flowWriter);


        /**
         * Packet Handler - used to perform PacketOut
         */

        PacketDispatcher packetDispatcher = new PacketDispatcher(packetProcessingService, hostManager);

        /**
         * Create InComingPktHandler - Handle non-mDNS packets, non ICMP packets
         */
        IncomingPktHandler incomingPktHandler = new IncomingPktHandler(notificationService, flowManager, packetDispatcher);
        LOG.info("Imcoming Packet Handler is instantiated");

        /**
         * Create arp Packet Handler - Handle Arp packets from to opcuaclient
         */
        ArpPacketHandler arpPacketHandler = new ArpPacketHandler(packetDispatcher, notificationService);

        /**
         * Create an Instance of mDNSPacket Handler
         */

        mDNSPacketHandler mDNSPacketHandler = new mDNSPacketHandler(notificationService, notificationPublishService, flowManager, packetDispatcher);
        LOG.info("Instance of mDNS Packet Handler created");
    }

    /**
     * Method called when the blueprint container is destroyed.
     */
    public void close() {
        LOG.info("I4applicationProvider Closed");
    }
}