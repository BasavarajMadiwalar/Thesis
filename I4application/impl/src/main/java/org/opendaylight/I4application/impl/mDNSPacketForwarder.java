/*
 * Copyright © 2016 Basavaraj and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */


package org.opendaylight.I4application.impl;


import org.opendaylight.I4application.impl.flow.FlowManager;
import org.opendaylight.I4application.impl.utils.MDNSPacketsQueue;
import org.opendaylight.controller.md.sal.binding.api.NotificationService;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv4Address;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.i4application.rev150105.CoOrdinatorIdentified;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.i4application.rev150105.I4applicationListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

public class mDNSPacketForwarder implements I4applicationListener {

    private final static Logger LOG = LoggerFactory.getLogger(mDNSPacketForwarder.class);

    private NotificationService notificationService;
    private FlowManager flowManager;
    private PacketDispatcher packetDispatcher;

    public mDNSPacketForwarder(NotificationService notificationService, FlowManager flowManager,
                               PacketDispatcher packetDispatcher) {

        this.notificationService = notificationService;
        notificationService.registerNotificationListener(this);
        this.flowManager = flowManager;
        this.packetDispatcher = packetDispatcher;
    }

    @Override
    public void onCoOrdinatorIdentified(CoOrdinatorIdentified notification) {
        LOG.debug("Coordinator selection received");
        Boolean flowsetupResult;

        Ipv4Address opcua_server = notification.getOpcuaServerAddress();
        Ipv4Address coordinator = notification.getCoOrdinatorAddress();

        sendcoordinatorpkts(opcua_server, coordinator);
        sendOPCUAPkts(opcua_server, coordinator);


//        flowsetupResult=flowManager.mDNSPktFlowManager(opcua_server, coordinator);
//        if (flowsetupResult){
//            sendOPCUAPkts(opcua_server, coordinator);
//        }
    }


    private void sendOPCUAPkts(Ipv4Address opcuaServer, Ipv4Address coordinator){
        ArrayList<byte[]> packetList = MDNSPacketsQueue.mDNSPackets.get(opcuaServer);
        int packetcount = packetList.size();

        for (byte[] packet:packetList){
            boolean result = packetDispatcher.dispatchPacket(packet, opcuaServer, coordinator);
            if (result) packetcount--;
            TimeUnit.SECONDS.toMicros(100);
        }

        if (packetcount != 0){
            LOG.debug("Packet out failed");
        }
        LOG.debug("mDNS Packet out success");
//        MDNSPacketsQueue.mDNSPackets.remove(opcuaServer);
    }

    private void sendcoordinatorpkts(Ipv4Address opcuaserver, Ipv4Address coordinator){

        ArrayList<byte[]> packetList = MDNSPacketsQueue.mDNSPackets.get(coordinator);
        for (byte[] packet:packetList){
            boolean result = packetDispatcher.dispatchPacket(packet, coordinator, opcuaserver);
            TimeUnit.SECONDS.toMicros(100);
        }
    }

}



