/*
 * Copyright © 2016 Basavaraj and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.I4application.impl;

import org.opendaylight.I4application.impl.flow.FlowManager;
import org.opendaylight.controller.md.sal.binding.api.NotificationService;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv4Address;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.MacAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.basepacket.rev140528.packet.chain.grp.PacketChain;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.ethernet.rev140528.ethernet.packet.received.packet.chain.packet.EthernetPacket;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.ipv4.rev140528.Ipv4PacketListener;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.ipv4.rev140528.Ipv4PacketReceived;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.ipv4.rev140528.ipv4.packet.received.packet.chain.packet.Ipv4Packet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class IncomingPktHandler implements Ipv4PacketListener {
    private final static Logger LOG = LoggerFactory.getLogger(org.opendaylight.I4application.impl.IncomingPktHandler.class);

    private NotificationService notificationService;
    private PacketDispatcher packetDispatcher;
    private FlowManager flowManager;

    private Ipv4Address opcua_client = Ipv4Address.getDefaultInstance("10.0.0.200");
    private Ipv4Address dstIpAddr = null;
    private Ipv4Address srcIpAddr = null;
    private Ipv4Address mDNSMCAddr = Ipv4Address.getDefaultInstance("224.0.0.251");
    private boolean rulecreated;

    public IncomingPktHandler(NotificationService notificationService
                                , FlowManager flowManager
                                , PacketDispatcher packetDispatcher) {
        // Register this class to MD-SAL notification service
        this.notificationService = notificationService;
        notificationService.registerNotificationListener(this);
        this.flowManager = flowManager;
        this.packetDispatcher = packetDispatcher;
    }

    @Override
    public void onIpv4PacketReceived(Ipv4PacketReceived notification) {
        LOG.info("Incoming Packet Handler recvied notification");
        processPacket(notification);
    }

    public void processPacket(Ipv4PacketReceived ipv4PacketReceived){
        LOG.info("Process Incoming Packet");

        List<PacketChain> packetChainList = ipv4PacketReceived.getPacketChain();
        Ipv4Packet ipv4Packet = (Ipv4Packet) packetChainList.get(packetChainList.size() - 1).getPacket();
        EthernetPacket ethernetPacket = (EthernetPacket) packetChainList.get(packetChainList.size() - 2).getPacket();
        byte[] payload = ipv4PacketReceived.getPayload();

        dstIpAddr = ipv4Packet.getDestinationIpv4();

        if(ipv4Packet.getDestinationIpv4().toString().equals(mDNSMCAddr.toString())){
            LOG.debug("Recieved mDNS packet destination IP Address: " + ipv4Packet.getDestinationIpv4().toString());
            return;
        }

        if (ipv4Packet.getProtocol().toString().equals("Igmp")){
            LOG.debug("Recieved IGMP packet destination IP Address: " + ipv4Packet.getDestinationIpv4().toString());
            return;
        }

        MacAddress srcMac = ethernetPacket.getSourceMac();
        MacAddress dstMac = ethernetPacket.getDestinationMac();

        srcIpAddr = ipv4Packet.getSourceIpv4();


        if (srcIpAddr.toString().equals(opcua_client.toString())
                || dstIpAddr.toString().equals(opcua_client.toString())){
            boolean result = packetDispatcher.dispatchPacket(payload, srcIpAddr, dstIpAddr);
            if (result){
                return;
            }
            return;
        }

        if (srcMac == null || dstMac == null
                || dstIpAddr == null || srcIpAddr ==null){
            return;
        }
        //Create a rule and packetout received packet-out
        rulecreated = flowManager.handleIpPacket(srcIpAddr, srcMac, dstIpAddr, dstMac);
        if (rulecreated){
            packetDispatcher.dispatchPacket(payload, srcIpAddr, dstIpAddr);
        }
    }
}
