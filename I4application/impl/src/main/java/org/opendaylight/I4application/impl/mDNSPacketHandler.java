/*
 * Copyright © 2016 Basavaraj and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.I4application.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

public class mDNSPacketHandler implements Ipv4PacketListener, I4applicationListener {

    private final static Logger LOG = LoggerFactory.getLogger(org.opendaylight.I4application.impl.mDNSPacketHandler.class);
    private final static String UDP_PROTOCOL = "Udp";
    private final static int MDNS_SRC_PORT = 5353;
    private Ipv4Address coordinator = Ipv4Address.getDefaultInstance("10.0.0.3");
    private Ipv4Address coordinator1 = Ipv4Address.getDefaultInstance("10.0.0.4");
    private Ipv4Address mDNSMCAddr = Ipv4Address.getDefaultInstance("224.0.0.251");
    private static ConcurrentHashMap<Ipv4Address, ArrayList<byte[]>>
            mDNSPackets = new ConcurrentHashMap<Ipv4Address, ArrayList<byte[]>>();

    private static Queue<ImmutablePair<Ipv4Packet, byte[]>> queue = new ConcurrentLinkedQueue<ImmutablePair<Ipv4Packet, byte[]>>();
    private static ConcurrentHashMap<Ipv4Address, String> urlRecord = new ConcurrentHashMap<Ipv4Address, String>();
    private static HashMap<String, ArrayList<String>> coordinatorList = null;

    private NotificationService notificationService;
    private NotificationPublishService notificationProvider;
    private FlowManager flowManager;
    private PacketDispatcher packetDispatcher;


    ExecutorService mDNSPacketExecutor = Executors.newFixedThreadPool(5);
    mDNSPacketBuffer mDNSPacketBufferThrd = new mDNSPacketBuffer();
    mDNSPacketForwarder mDNSPacketForwarder = new mDNSPacketForwarder(mDNSPackets);

    // Register for
    public mDNSPacketHandler(NotificationService notificationService, NotificationPublishService notificationPublishService,
                             FlowManager flowManager, PacketDispatcher packetDispatcher) {
        this.notificationService = notificationService;
        notificationService.registerNotificationListener(this);
        this.notificationProvider = notificationPublishService;
        this.flowManager = flowManager;
        this.packetDispatcher = packetDispatcher;
        JsontoArraylist();
    }

    public void JsontoArraylist(){
        ObjectMapper objectMapper = new ObjectMapper();
        File coordinator_list = new File("/home/basavaraj/ODL/Thesis/I4application/impl/src/main/resources/coordinatorList.json");
        try {
            coordinatorList = objectMapper.readValue(coordinator_list,
                    new TypeReference<HashMap<String,ArrayList<String>>>() {
                    });

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onIpv4PacketReceived(Ipv4PacketReceived notification) {
        LOG.info("Received an Ipv4 Packet");
        checkUDPacket(notification);
    }

    public void checkUDPacket(Ipv4PacketReceived ipv4PacketReceived){

        int srcport = 0;

        //Find the latest packet in the packet-chain, which is an IPv4Packet
        List<PacketChain> packetChainList = ipv4PacketReceived.getPacketChain();
        Ipv4Packet ipv4Packet = (Ipv4Packet) packetChainList.get(packetChainList.size() - 1).getPacket();
        byte[] data = ipv4PacketReceived.getPayload();

        if (!(ipv4Packet.getDestinationIpv4().toString().equals(mDNSMCAddr.toString()))){
            LOG.debug("not an mDNS Packet Received");
            return;
        }

        if(coordinatorList.get("coordinators").contains(ipv4Packet.getSourceIpv4().getValue()))
        {
            LOG.debug("Coordinator mDNS packets received");
            return;
        }

        try {
            queue.add(new ImmutablePair<>(ipv4Packet,data));
        }catch (Exception e){
            LOG.debug("Could not add Packet to mDNSPacketsQueue");
        }
        mDNSPacketExecutor.submit(mDNSPacketBufferThrd);
    }

    class mDNSPacketBuffer implements Runnable {
        Ipv4Packet mDNSPacket = null;
        byte[] packetPayload = null;
        byte[] mDNSPayload = null;
        String mDNSPacketSring = null;

        ExecutorService srvRecordExecutor = Executors.newFixedThreadPool(15);
        ArrayList<byte[]> oldlist = new ArrayList<byte[]>();

        @Override
        public void run() {
            LOG.info("In thread {}", Thread.currentThread().getName());

            // Pop an Element from Queue
            ImmutablePair<Ipv4Packet, byte[]> immutablePair = queue.remove();
            this.packetPayload = immutablePair.getValue();
            this.mDNSPacket = immutablePair.getKey();
            mDNSPayload = PacketParsingUtils.extractmDNSpayload(packetPayload, mDNSPacket.getIhl());
            mDNSPacketSring = new String(packetPayload, StandardCharsets.UTF_8);

            if (!(mDNSPacketSring.contains("_opcua-tcp"))){
                LOG.info("Not an OPC-UA mDNS Packet");
                return;
            }

            if(!(mDNSPackets.containsKey(mDNSPacket.getSourceIpv4()))){
                LOG.info("Adding IP address to Map" + mDNSPacket.getSourceIpv4().getValue());
                mDNSPackets.put(mDNSPacket.getSourceIpv4(), new ArrayList<>(Arrays.asList(packetPayload)));
            }else{
                try {
                    oldlist = mDNSPackets.get(mDNSPacket.getSourceIpv4());
                }catch (NullPointerException e){
                    LOG.info("Null Pointer Exception {}", e);
                }
                oldlist.add(packetPayload);
                mDNSPackets.put(mDNSPacket.getSourceIpv4(), oldlist);
            }
            // check for SRV Record using Future
            CompletableFuture.runAsync(()->SRVRecHandler(mDNSPacket, mDNSPayload), srvRecordExecutor);
        }

        /**
         * Method makes uses of mDNS parser to get the url if it's SRV RR packet.
         * mDNSParser itself checks if it's SRV record packet, and returns URL if it's an SRV record only.
         * @param mDNSPacket
         * @param mDNSPayload
         */

        public void SRVRecHandler(Ipv4Packet mDNSPacket, byte[] mDNSPayload){
            LOG.info("SRV Record Handler");
            String url = null;
            Ipv4Address srcIPaddr = mDNSPacket.getSourceIpv4();

            // Check foir
            CompletableFuture<String> future = CompletableFuture.supplyAsync(()->mDNSparser.mDNSRecordParser(mDNSPayload));
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
        }
    }


    @Override
    public void onCoOrdinatorIdentified(CoOrdinatorIdentified notification) {
        LOG.info("Coordinator selection received");
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
            for (byte[] packet:packetList){
                boolean result = packetDispatcher.dispatchmDNSPacket(packet, opcuaServer, coordinator);
                if (result) packetcount--;
            }
        }

        if (packetcount != 0){
            LOG.debug("Packet out failed");
        }
        LOG.debug("mDNS Packet out success");
        mDNSPackets.remove(opcuaServer);
    }
}
