/*
 * Copyright © 2016 Basavaraj and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.I4application.impl;

import org.apache.commons.lang3.tuple.ImmutablePair;
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

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.*;

public class mDNSParserImpl implements Ipv4PacketListener {

    private final static Logger LOG = LoggerFactory.getLogger(mDNSParserImpl.class);
    private final static String UDP_PROTOCOL = "Udp";
    private final static int MDNS_SRC_PORT = 5353;
    private NotificationService notificationService;


    // Data Structure for bufering mDNS packets and url details.
    public static ConcurrentHashMap<String, ArrayList<byte[]>>
            mDNSPackets = new ConcurrentHashMap<String, ArrayList<byte[]>>();
    private static Queue<ImmutablePair<Ipv4Packet, byte[]>> queue = new ConcurrentLinkedQueue<ImmutablePair<Ipv4Packet, byte[]>>();
    private static ConcurrentHashMap<Ipv4Address, String> urlRecord = new ConcurrentHashMap<Ipv4Address, String>();


    ExecutorService mDNSPacketExecutor = Executors.newSingleThreadExecutor();

    mDNSPacketHandler mDNSPacketHandlerThrd = new mDNSPacketHandler(mDNSPackets, queue, urlRecord);


    private Ipv4Address srcInet4Address = null;

    // Register for
    public mDNSParserImpl(NotificationService notificationService) {
        this.notificationService = notificationService;
        notificationService.registerNotificationListener(this);
    }

    @Override
    public void onIpv4PacketReceived(Ipv4PacketReceived notification) {
        //System.out.println("Ipv4 Packet Recieved");
        LOG.info("Recieved an Ipv4 Packet");
        checkUDPacket(notification);
    }


    public void checkUDPacket(Ipv4PacketReceived ipv4PacketReceived){

        int srcport = 0;

        //Find the latest packet in the packet-chain, which is an IPv4Packet
        List<PacketChain> packetChainList = ipv4PacketReceived.getPacketChain();
        Ipv4Packet ipv4Packet = (Ipv4Packet) packetChainList.get(packetChainList.size() - 1).getPacket();
        byte[] data = ipv4PacketReceived.getPayload();

        LOG.info("IP Protocol of recieved Packet is : " + ipv4Packet.getProtocol().toString());
        //check for UDP packet
        if (!(ipv4Packet.getProtocol().toString() == UDP_PROTOCOL)){
            LOG.info("Non UDP packet in mDNS Packet Handler, ignore packet");
            return;
        }

        int bitoffset = ipv4Packet.getPayloadOffset() * NetUtils.NumBitsInAByte;
        try {
            srcport = BitBufferHelper.getInt(BitBufferHelper.getBits(data, bitoffset, 16));
        }catch (BufferException e){
            LOG.debug("Could not find src port {}", e.getMessage());
        }

        if (srcport != MDNS_SRC_PORT){
            LOG.info("non MDNS packet Recieved");
            return;
        }

        try {
            queue.add(new ImmutablePair<>(ipv4Packet,data));
        }catch (Exception e){
            LOG.debug("Could not add Packet to mDNSPacketsQueue");
        }
        mDNSPacketExecutor.submit(mDNSPacketHandlerThrd);
    }


    class mDNSPacketHandler implements Runnable {
        Ipv4Packet mDNSPacket = null;
        byte[] packetPayload = null;
        byte[] mDNSPayload = null;
        String mDNSPacketSring = null;

        public ConcurrentHashMap<String, ArrayList<byte[]>>
                mDNSPacketsMap;

        //public BlockingQueue<ImmutablePair<Ipv4Packet, byte[]>> mDNSPacketQueue;
        public Queue<ImmutablePair<Ipv4Packet, byte[]>> mDNSPacketQueue;

        public ConcurrentHashMap<Ipv4Address, String> urlRecord;

        ExecutorService srvRecordExecutor = Executors.newFixedThreadPool(15);

        //HashMap<String, ArrayList<byte[]>> ipPacketMap = new HashMap<String, ArrayList<byte[]>>();
        ArrayList<byte[]> oldlist = new ArrayList<byte[]>();

        public mDNSPacketHandler(ConcurrentHashMap<String, ArrayList<byte[]>> mDNSPackets,
                                 Queue<ImmutablePair<Ipv4Packet, byte[]>> blockingQueue,
                                 ConcurrentHashMap<Ipv4Address, String> urlRecord) {
            this.mDNSPacketsMap = mDNSPackets;
            this.mDNSPacketQueue = blockingQueue;
            this.urlRecord = urlRecord;
        }

        @Override
        public void run() {
            LOG.info("In thread {}", Thread.currentThread().getName());

            // Pop an Element from Queue
            ImmutablePair<Ipv4Packet, byte[]> immutablePair = mDNSPacketQueue.remove();
            this.packetPayload = immutablePair.getValue();
            this.mDNSPacket = immutablePair.getKey();
            mDNSPayload = PacketParsingUtils.extractmDNSpayload(packetPayload, mDNSPacket.getIhl());
            mDNSPacketSring = new String(packetPayload, StandardCharsets.UTF_8);

            if (!(mDNSPacketSring.contains("_opcua-tcp"))){
                LOG.info("Not an OPC-UA mDNS Packet");
                return;
            }

            if(!(mDNSPacketsMap.containsKey(mDNSPacket.getSourceIpv4().getValue()))){
                LOG.info("Adding IP address to Map" + mDNSPacket.getSourceIpv4().getValue());
                mDNSPacketsMap.put(mDNSPacket.getSourceIpv4().getValue(), new ArrayList<>(Arrays.asList(packetPayload)));
            }else{
                try {
                    oldlist = mDNSPacketsMap.get(mDNSPacket.getSourceIpv4().getValue());
                }catch (NullPointerException e){
                    LOG.info("Null Pointer Exception {}", e);
                }
                oldlist.add(packetPayload);
                mDNSPacketsMap.put(mDNSPacket.getSourceIpv4().getValue(), oldlist);
            }
            // check for SRV Record using Future
            CompletableFuture.runAsync(()->SRVRecHandler(mDNSPacket, mDNSPayload), srvRecordExecutor);
//            for(Map.Entry me:mDNSPacketsMap.entrySet()){
//                System.out.println("Key: " + me.getKey() +" & value length is : " + me.getValue());
//            }
        }

        public void SRVRecHandler(Ipv4Packet ipv4Packet, byte[] mDNSPayload){
            LOG.info("SRV Record Handler");
            Ipv4Address srcIPaddr = ipv4Packet.getSourceIpv4();

            CompletableFuture<String> future = CompletableFuture.supplyAsync(()->mDNSparser.mDNSRecordParser(mDNSPayload));
            String url = null;
            try {
                url = future.get();
            } catch (InterruptedException e) {
                e.printStackTrace();
            } catch (ExecutionException e) {
                e.printStackTrace();
            }
            if ((url.equals(null))|| urlRecord.containsKey(srcIPaddr)){
                LOG.info("Not an SRV Record or Record already exist");
                return;
            }
            urlRecord.put(srcIPaddr, url);
            System.out.println("The IP Address is: " + urlRecord.get(srcIPaddr));
        }
    }
}
