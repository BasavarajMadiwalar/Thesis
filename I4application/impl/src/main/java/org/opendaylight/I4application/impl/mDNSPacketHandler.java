/*
 * Copyright © 2016 Basavaraj and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.I4application.impl;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.opendaylight.I4application.impl.flow.FlowManager;
import org.opendaylight.I4application.impl.utils.PacketParsingUtils;
import org.opendaylight.controller.md.sal.binding.api.NotificationPublishService;
import org.opendaylight.controller.md.sal.binding.api.NotificationService;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv4Address;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.basepacket.rev140528.packet.chain.grp.PacketChain;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.ipv4.rev140528.Ipv4PacketListener;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.ipv4.rev140528.Ipv4PacketReceived;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.ipv4.rev140528.ipv4.packet.received.packet.chain.packet.Ipv4Packet;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.i4application.rev150105.CoOrdinatorIdentified;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.i4application.rev150105.I4applicationListener;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.urlnotification.rev150105.DiscoveryUrlNotification;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.urlnotification.rev150105.DiscoveryUrlNotificationBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.*;

public class mDNSPacketHandler implements Ipv4PacketListener, I4applicationListener {

    private final static Logger LOG = LoggerFactory.getLogger(org.opendaylight.I4application.impl.mDNSPacketHandler.class);
    private final static String UDP_PROTOCOL = "Udp";
    private final static int MDNS_SRC_PORT = 5353;
    private NotificationService notificationService;
    private FlowManager flowManager;
    private PacketDispatcher packetDispatcher;
    private Ipv4Address coordinator = Ipv4Address.getDefaultInstance("10.0.0.3");
    private Ipv4Address coordinator1 = Ipv4Address.getDefaultInstance("10.0.0.4");
    private Ipv4Address mDNSMCAddr = Ipv4Address.getDefaultInstance("224.0.0.251");

    // Data Structure for bufering mDNS packets and url details.
    public ConcurrentHashMap<Ipv4Address, ArrayList<byte[]>>
            mDNSPackets = new ConcurrentHashMap<Ipv4Address, ArrayList<byte[]>>();

    private static Queue<ImmutablePair<Ipv4Packet, byte[]>> queue = new ConcurrentLinkedQueue<ImmutablePair<Ipv4Packet, byte[]>>();
    private static ConcurrentHashMap<Ipv4Address, String> urlRecord = new ConcurrentHashMap<Ipv4Address, String>();


    //ExecutorService mDNSPacketExecutor = Executors.newSingleThreadExecutor();
    ExecutorService mDNSPacketExecutor = Executors.newFixedThreadPool(5);
    mDNSPacketBuffer mDNSPacketBufferThrd = new mDNSPacketBuffer(mDNSPackets, queue, urlRecord);
    mDNSPacketForwarder mDNSPacketForwarder = new mDNSPacketForwarder(mDNSPackets);
    private NotificationPublishService notificationProvider;

    // Register for
    public mDNSPacketHandler(NotificationService notificationService, NotificationPublishService notificationPublishService,
                             FlowManager flowManager, PacketDispatcher packetDispatcher) {
        this.notificationService = notificationService;
        notificationService.registerNotificationListener(this);
        this.notificationProvider = notificationPublishService;
        this.flowManager = flowManager;
        this.packetDispatcher = packetDispatcher;
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

        if(ipv4Packet.getSourceIpv4().toString().equals(coordinator.toString())
                || ipv4Packet.getSourceIpv4().toString().equals(coordinator1.toString()))
        {
            LOG.info(" Coordinator mDNS packets recieved");
            return;
        }

//        //check for UDP packet
//        if (!(ipv4Packet.getProtocol().toString() == UDP_PROTOCOL)){
//            LOG.info("Non UDP packet in mDNS Packet Handler, ignore packet");
//            return;
//        }
//
//        int bitoffset = ipv4Packet.getPayloadOffset() * NetUtils.NumBitsInAByte;
//        try {
//            srcport = BitBufferHelper.getInt(BitBufferHelper.getBits(data, bitoffset, 16));
//        }catch (BufferException e){
//            LOG.debug("Could not find src port {}", e.getMessage());
//        }


        if (!(ipv4Packet.getDestinationIpv4().toString().equals(mDNSMCAddr.toString()))){
            LOG.debug("non MDNS Packet Received");
            return;
        }


        try {
            queue.add(new ImmutablePair<>(ipv4Packet,data));
        }catch (Exception e){
            LOG.debug("Could not add Packet to mDNSPacketsQueue");
        }
        mDNSPacketExecutor.submit(mDNSPacketBufferThrd);
    }


    @Override
    public void onCoOrdinatorIdentified(CoOrdinatorIdentified notification) {
        LOG.info("Coordinator selection recieved");
        System.out.println("Coordinator selection recieved");
        Boolean flowsetupResult;

        Ipv4Address opcua_server = notification.getOpcuaServerAddress();
        Ipv4Address coordinator = notification.getCoOrdinatorAddress();

        flowsetupResult=flowManager.mDNSPktFlowManager(opcua_server, coordinator);
        if (flowsetupResult){
            CompletableFuture.runAsync(()->sendPacketOut(opcua_server, coordinator));
        }
    }

    public void sendPacketOut(Ipv4Address opcuaServer, Ipv4Address coordinator){
        ArrayList<byte[]> packetList = mDNSPackets.get(opcuaServer);
        int packetcount = packetList.size();
        System.out.println("Packet Count is: " + packetcount);
        if (packetList != null){
            for (byte[] packet: packetList){
                boolean result = packetDispatcher.dispatchmDNSPacket(packet, opcuaServer, coordinator);
                if (result) packetcount--;
            }
        }

        if (packetcount != 0){
            System.out.println("Packet Out failed");;
            LOG.debug("Packet out failed");
        };
        LOG.debug("mDNS Packet out success");
    }


    class mDNSPacketBuffer implements Runnable {
        Ipv4Packet mDNSPacket = null;
        byte[] packetPayload = null;
        byte[] mDNSPayload = null;
        String mDNSPacketSring = null;

        public ConcurrentHashMap<Ipv4Address, ArrayList<byte[]>>
                mDNSPacketsMap;

        //public BlockingQueue<ImmutablePair<Ipv4Packet, byte[]>> mDNSPacketQueue;
        public Queue<ImmutablePair<Ipv4Packet, byte[]>> mDNSPacketQueue;

        public ConcurrentHashMap<Ipv4Address, String> urlRecord;

        ExecutorService srvRecordExecutor = Executors.newFixedThreadPool(15);

        //HashMap<String, ArrayList<byte[]>> ipPacketMap = new HashMap<String, ArrayList<byte[]>>();
        ArrayList<byte[]> oldlist = new ArrayList<byte[]>();

        public mDNSPacketBuffer(ConcurrentHashMap<Ipv4Address, ArrayList<byte[]>> mDNSPackets,
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

            if(!(mDNSPacketsMap.containsKey(mDNSPacket.getSourceIpv4()))){
                LOG.info("Adding IP address to Map" + mDNSPacket.getSourceIpv4().getValue());
                mDNSPacketsMap.put(mDNSPacket.getSourceIpv4(), new ArrayList<>(Arrays.asList(packetPayload)));
            }else{
                try {
                    oldlist = mDNSPacketsMap.get(mDNSPacket.getSourceIpv4());
                }catch (NullPointerException e){
                    LOG.info("Null Pointer Exception {}", e);
                }
                oldlist.add(packetPayload);
                mDNSPacketsMap.put(mDNSPacket.getSourceIpv4(), oldlist);
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

            DiscoveryUrlNotification discoveryUrlNotification = new DiscoveryUrlNotificationBuilder()
                            .setSrcIPAddress(srcIPaddr).setDiscoveryUrl(url).build();
            notificationProvider.offerNotification(discoveryUrlNotification);
            //System.out.println("opc-ua server url" + urlRecord.get(srcIPaddr));
        }
    }
}
