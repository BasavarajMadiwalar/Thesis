/*
 * Copyright © 2016 Basavaraj and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.I4application.impl;

import org.opendaylight.I4application.impl.utils.BitBufferHelper;
import org.opendaylight.I4application.impl.utils.BufferException;
import org.opendaylight.I4application.impl.utils.NetUtils;
import org.opendaylight.I4application.impl.utils.PacketParsingUtils;
import org.opendaylight.controller.md.sal.binding.api.NotificationService;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv4Address;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.basepacket.rev140528.packet.chain.grp.PacketChain;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.ipv4.rev140528.Ipv4PacketListener;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.ipv4.rev140528.Ipv4PacketReceived;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.ipv4.rev140528.ipv4.packet.received.packet.chain.packet.Ipv4Packet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.Inet4Address;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class mDNSParserImpl implements Ipv4PacketListener {

    private final static Logger LOG = LoggerFactory.getLogger(mDNSParserImpl.class);
    private NotificationService notificationService;

    private Map<Inet4Address, String> urlTable = new HashMap<Inet4Address, String>();
    private Ipv4Address srcInet4Address = null;

    // Register for
    public mDNSParserImpl(NotificationService notificationService) {
        this.notificationService = notificationService;
        notificationService.registerNotificationListener(this);
    }

    @Override
    public void onIpv4PacketReceived(Ipv4PacketReceived notification) {
        //System.out.println("Ipv4 Packet Recieved");
        decodeUDPpacket(notification);
    }


    public void decodeUDPpacket(Ipv4PacketReceived ipv4PacketReceived){

        //Find the latest packet in the packet-chain, which is an IPv4Packet
        List<PacketChain> packetChainList = ipv4PacketReceived.getPacketChain();
        Ipv4Packet ipv4Packet = (Ipv4Packet) packetChainList.get(packetChainList.size() - 1).getPacket();


        // Get the length of IPv4 packet
        int ihl = ipv4Packet.getIhl();
        srcInet4Address = ipv4Packet.getSourceIpv4();
        //System.out.println("The ip header length" + ihl);
        //System.out.println(ipv4Packet.getProtocol());
        byte[] data = ipv4PacketReceived.getPayload();

        byte[] dstMacRaw = PacketParsingUtils.extractDstMac(data);
        byte[] srcMacRaw = PacketParsingUtils.extractSrcMac(data);

        int bitoffset = ipv4Packet.getPayloadOffset() * NetUtils.NumBitsInAByte;
        int srcport = 0;

        try {
            srcport = BitBufferHelper.getInt(BitBufferHelper.getBits(data, bitoffset, 16));
            //System.out.println("Dest UDP port number is: " + srcport);
        }catch (BufferException e){
            System.out.println(e.getMessage());
        }

        if (srcport == 5353){

            byte[] mdnsPayload = PacketParsingUtils.extractmDNSpayload(data, ihl);
            Boolean retValue = mDNSparser.mDNSRecordParser(mdnsPayload, urlTable, srcInet4Address);
            if (retValue){
                System.out.println("Able to Parse Record");
                for (Map.Entry m:urlTable.entrySet()){
                    System.out.println(m.getKey() + " " +  m.getValue());
                }
            }
        }



        //System.out.println("Dest UDP port number is: " + dstport);
        //Remaining part remains as before

    }

}
