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

import java.util.List;

public class IncomingPktHandler implements Ipv4PacketListener {

    private NotificationService notificationService;

    private Ipv4Address dstIpAddr = null;
    private Ipv4Address srcIpAddr = null;
    private String multiCastAddr = "224.0.0.251";
    private FlowManager flowManager;

    public IncomingPktHandler(NotificationService notificationService, FlowManager flowManager) {
        // Register this class to MD-SAL notification service
        this.notificationService = notificationService;
        notificationService.registerNotificationListener(this);
        this.flowManager = flowManager;
    }

    @Override
    public void onIpv4PacketReceived(Ipv4PacketReceived notification) {
        processPacket(notification);
    }

    public void processPacket(Ipv4PacketReceived ipv4PacketReceived){
        List<PacketChain> packetChainList = ipv4PacketReceived.getPacketChain();
        Ipv4Packet ipv4Packet = (Ipv4Packet) packetChainList.get(packetChainList.size() - 1).getPacket();
        EthernetPacket ethernetPacket = (EthernetPacket) packetChainList.get(packetChainList.size() - 2).getPacket();

        dstIpAddr = ipv4Packet.getDestinationIpv4();
        //System.out.println("Dst IP address is: " + dstIpAddr.getValue());

        if (dstIpAddr.getValue().toString() == "224.0.0.251" || ipv4Packet.getProtocol().toString() == "Icmp" ){
            System.out.println("Ignore ICMP and mDNS packets");
            return;
        }
        MacAddress srcMac = ethernetPacket.getSourceMac();
        MacAddress dstMac = ethernetPacket.getDestinationMac();

        srcIpAddr = ipv4Packet.getSourceIpv4();

        if (srcMac != null && dstMac != null
                && dstIpAddr !=null && srcIpAddr !=null){
            // Send Packet to Flow Creator to create flow.
            flowManager.handleIpPacket(srcIpAddr, srcMac, dstIpAddr, dstMac);
        }
    }
}
