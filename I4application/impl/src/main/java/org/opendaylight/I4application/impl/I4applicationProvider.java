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
import org.opendaylight.I4application.impl.flow.MDNSFlowWriter;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.NotificationPublishService;
import org.opendaylight.controller.md.sal.binding.api.NotificationService;
import org.opendaylight.controller.sal.binding.api.RpcProviderRegistry;
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
    private RpcProviderRegistry rpcProviderRegistry;


    /**
     * The Constructor is called when blue print container is created.
     * As I4applicationprovider is added to the blueprint container
      */

    public I4applicationProvider(DataBroker dataBroker, NotificationService notificationService,
                                 SalFlowService salFlowService, NotificationPublishService notificationPublishService,
                                 PacketProcessingService packetProcessingService,
                                 SalGroupService salGroupService,
                                 RpcProviderRegistry rpcProviderRegistry) {
        this.dataBroker = dataBroker;
        this.notificationService = notificationService;
        this.salFlowService = salFlowService;
        this.notificationPublishService = notificationPublishService;
        this.packetProcessingService = packetProcessingService;
        this.salGroupService = salGroupService;
        this.rpcProviderRegistry = rpcProviderRegistry;
    }


    /**
     * Method called when the blueprint container is created.
     */
    public void init() {
        LOG.info("I4applicationProvider Session Initiated");

        HostManager hostManager = new HostManager(dataBroker, notificationPublishService);
        LOG.info("Host Manager is instantiated");

        networkGraphService = new NetworkGraphImpl();

        /**
         * TopologyChangeListerner - Listen for topology changes
         */
        TopologyChangeListener topologyChangeListener = new TopologyChangeListener(dataBroker, hostManager, networkGraphService);

        /**
         * Flow writer - Create OF Flow objects
         *
         */

        FlowWriter flowWriter = new FlowWriter(salFlowService); // In Future need to pass SALGroup Serive for flow writing
        MDNSFlowWriter mdnsFlowWriter = new MDNSFlowWriter(salFlowService, notificationService, salGroupService, rpcProviderRegistry);

        /**
         * Create a Flow Manager and pass and Instance of Host Manager
         */
        FlowManager flowManager = new FlowManager(hostManager, networkGraphService, flowWriter, mdnsFlowWriter);


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
         * Create UrlNotificationHandler - Handle urlNotifications.
         */
        UrlNotificationHandler urlNotificationHandler = new UrlNotificationHandler(notificationService, notificationPublishService,
                                                                                    hostManager, rpcProviderRegistry);


        /**
         * Create arp Packet Handler - Handle Arp packets from to opcuaclient
         */
        ArpPacketHandler arpPacketHandler = new ArpPacketHandler(packetDispatcher, notificationService);

        /**
         * Create an Instance of mDNSPacket Handler
         */

        mDNS_packet_parser mDNS_packet_parser = new mDNS_packet_parser(notificationPublishService);

        mDNSPacketHandler mDNSPacketHandler = new mDNSPacketHandler(notificationService, mDNS_packet_parser, flowManager,
                packetDispatcher, rpcProviderRegistry);

        mDNSPacketForwarder mDNSPacketForwarder = new mDNSPacketForwarder(notificationService, flowManager, packetDispatcher);
    }

    /**
     * Method called when the blueprint container is destroyed.
     */
    public void close() {
        LOG.info("I4applicationProvider Closed");
    }
}