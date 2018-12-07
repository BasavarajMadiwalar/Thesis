/*
 * Copyright © 2016 Basavaraj and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.I4application.impl;

import org.opendaylight.controller.md.sal.binding.api.NotificationService;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv4Address;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.arp.rev140528.ArpPacketListener;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.arp.rev140528.ArpPacketReceived;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.arp.rev140528.arp.packet.received.packet.chain.packet.ArpPacket;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.basepacket.rev140528.packet.chain.grp.PacketChain;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.ipv4.rev140528.ipv4.packet.received.packet.chain.packet.Ipv4Packet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ArpPacketHandler implements ArpPacketListener {

    private final static Logger LOG = LoggerFactory.getLogger(ArpPacketHandler.class);
    private Ipv4Address opcua_client = Ipv4Address.getDefaultInstance("10.0.0.200");
    private Ipv4Address dstIpAddr = null;
    private Ipv4Address srcIpAddr = null;
    private PacketDispatcher packetDispatcher;
    private NotificationService notificationService;

    public ArpPacketHandler(PacketDispatcher packetDispatcher, NotificationService notificationService) {
        this.packetDispatcher = packetDispatcher;
        this.notificationService = notificationService;
        notificationService.registerNotificationListener(this);
    }

    @Override
    public void onArpPacketReceived(ArpPacketReceived notification) {
        LOG.debug("Arp Packet Received");
        ArpPacket arpPacket = null;
        Ipv4Packet ipv4Packet = null;
        byte[] payload = null;

        if (notification == null || notification.getPacketChain() == null){
            return;
        }

        for(PacketChain packetChain : notification.getPacketChain()){
            if (packetChain.getPacket() instanceof ArpPacket){
                arpPacket = (ArpPacket) packetChain.getPacket();
            }
        }

        if (arpPacket == null){
            return;
        }

        if (!( opcua_client.getValue().matches(arpPacket.getSourceProtocolAddress())
                || opcua_client.getValue().matches(arpPacket.getDestinationProtocolAddress()))){
            LOG.debug("not an OPCUA client arp");;
            return;
        }

        srcIpAddr = Ipv4Address.getDefaultInstance(arpPacket.getSourceProtocolAddress());
        dstIpAddr = Ipv4Address.getDefaultInstance(arpPacket.getDestinationProtocolAddress());
        LOG.debug("Dispatch opcua arp packets");
        packetDispatcher.dispatchPacket(notification.getPayload(), srcIpAddr, dstIpAddr);
        return;
    }
}
