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
import org.opendaylight.controller.md.sal.binding.api.NotificationService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.service.rev130819.SalFlowService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class I4applicationProvider {

    private static final Logger LOG = LoggerFactory.getLogger(I4applicationProvider.class);

    private DataBroker dataBroker;
    private NotificationService notificationService;
    private NetworkGraphService networkGraphService;
    private SalFlowService salFlowService;

    /**
     * The Constructor is called when blue print container is created.
     * As I4applicationprovider is added to the blueprint container
      */

    public I4applicationProvider(DataBroker dataBroker, NotificationService notificationService, SalFlowService salFlowService) {
        this.dataBroker = dataBroker;
        this.notificationService = notificationService;
        this.salFlowService = salFlowService;
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

        FlowWriter flowWriter = new FlowWriter(salFlowService, dataBroker); // In Future need to pass SALFlow Serive for flow writing

        /**
         * Create a Flow Manager and pass and Instance of Host Manager
         */
        FlowManager flowManager = new FlowManager(hostManager, networkGraphService, flowWriter);


        /**
         * Create InComingPktHandler - Handle non-mDNS packets, non ICMP packets
         */
        IncomingPktHandler incomingPktHandler = new IncomingPktHandler(notificationService, flowManager);
        LOG.info("Imcoming Packet Handler is instantiated");

    }

    /**
     * Method called when the blueprint container is destroyed.
     */
    public void close() {
        LOG.info("I4applicationProvider Closed");
    }
}