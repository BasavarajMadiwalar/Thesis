/*
 * Copyright © 2016 Basavaraj and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.I4application.impl;

import com.google.common.util.concurrent.Futures;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.opendaylight.I4application.impl.flow.FlowManager;
import org.opendaylight.I4application.impl.utils.MDNSPacketsQueue;
import org.opendaylight.I4application.impl.utils.PacketParsingUtils;
import org.opendaylight.controller.md.sal.binding.api.NotificationPublishService;
import org.opendaylight.controller.md.sal.binding.api.NotificationService;
import org.opendaylight.controller.sal.binding.api.RpcProviderRegistry;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv4Address;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.basepacket.rev140528.packet.chain.grp.PacketChain;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.ipv4.rev140528.ipv4.packet.received.packet.chain.packet.Ipv4Packet;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.udp.rev181230.UdpPacketListener;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.udp.rev181230.UdpPacketReceived;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.flushpktrpc.rev181201.FlushPktRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.hostmanagernotification.rev150105.HostAddedNotification;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.hostmanagernotification.rev150105.HostNotificationListener;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.hostmanagernotification.rev150105.HostRemovedNotification;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.*;



public class mDNSPacketHandler implements UdpPacketListener, HostNotificationListener, FlushPktRpcService {

    private final static Logger LOG = LoggerFactory.getLogger(org.opendaylight.I4application.impl.mDNSPacketHandler.class);

    private Queue<ImmutablePair<Ipv4Packet, byte[]>> queue = new ConcurrentLinkedQueue<>();
    private ConcurrentHashMap<Ipv4Address, String> urlRecord = new ConcurrentHashMap<>();

    private NotificationService notificationService;
    private NotificationPublishService notificationProvider;
    private FlowManager flowManager;
    private PacketDispatcher packetDispatcher;

    ExecutorService mDNSPacketExecutor = Executors.newFixedThreadPool(5);
    ExecutorService checkUDPExecutor =  Executors.newFixedThreadPool(10);
    mDNSPacketBuffer mDNSPacketBufferThrd = new mDNSPacketBuffer();
    private final Ipv4Address mDNSMCAddr = Ipv4Address.getDefaultInstance("224.0.0.251");
    mDNS_packet_parser mDNS_packet_parser;



    public mDNSPacketHandler(NotificationService notificationService, mDNS_packet_parser mDNS_packet_parser,
                             FlowManager flowManager, PacketDispatcher packetDispatcher, RpcProviderRegistry rpcProviderRegistry) {
        this.notificationService = notificationService;
        notificationService.registerNotificationListener(this);
        rpcProviderRegistry.addRpcImplementation(FlushPktRpcService.class, this);
        this.mDNS_packet_parser = mDNS_packet_parser;
        this.flowManager = flowManager;
        this.packetDispatcher = packetDispatcher;
    }


    @Override
    public void onUdpPacketReceived(UdpPacketReceived notification) {
        LOG.debug("Received an UDP Packet");
        checkUDPacket(notification);
    }


    public void checkUDPacket(UdpPacketReceived udpPacketReceived){

        //Find the latest packet in the packet-chain, which is an UDP Packet
        List<PacketChain> packetChainList = udpPacketReceived.getPacketChain();
        Ipv4Packet ipv4Packet = (Ipv4Packet) packetChainList.get(packetChainList.size() - 2).getPacket();
        byte[] data = udpPacketReceived.getPayload();


        if (!(ipv4Packet.getDestinationIpv4().equals(mDNSMCAddr))){
            LOG.debug("Not an mDNS packet");
            return;
        }

        // New on 29-04-2019
        if((MDNSPacketsQueue.urlRecord.containsKey(ipv4Packet.getSourceIpv4()))){
            LOG.info("URL Record Exist");
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

        ExecutorService srvRecordExecutor = Executors.newFixedThreadPool(5);
        ArrayList<byte[]> oldlist = new ArrayList<>();

        @Override
        public void run() {
            // Pop an Element from Queue
            ImmutablePair<Ipv4Packet, byte[]> immutablePair = queue.remove();
            this.packetPayload = immutablePair.getValue();
            this.mDNSPacket = immutablePair.getKey();
            mDNSPayload = PacketParsingUtils.extractmDNSpayload(packetPayload, mDNSPacket.getIhl());
            mDNSPacketSring = new String(mDNSPayload, StandardCharsets.UTF_8);
            int protocol_pos = mDNSPacketSring.indexOf("_opcua-tcp");

            if (protocol_pos<0){
                LOG.debug("Not an OPC-UA mDNS Packet");
                return;
            }

            if(!(MDNSPacketsQueue.mDNSPackets.containsKey(mDNSPacket.getSourceIpv4()))){
                LOG.debug("Adding IP address to Map" + mDNSPacket.getSourceIpv4().getValue());
                MDNSPacketsQueue.mDNSPackets.put(mDNSPacket.getSourceIpv4(), new ArrayList<>(Arrays.asList(packetPayload)));
            }else{
                try {
                    oldlist = MDNSPacketsQueue.mDNSPackets.get(mDNSPacket.getSourceIpv4());
                }catch (NullPointerException e){
                    LOG.debug("Null Pointer Exception {}", e);
                }
                oldlist.add(packetPayload);
                MDNSPacketsQueue.mDNSPackets.put(mDNSPacket.getSourceIpv4(), oldlist);
            }


            /* Use executors and runnable to implements this. As Completable Future sometimes may block execution. */

//            CompletableFuture.runAsync(()->SRVRecHandler(mDNSPacket, mDNSPayload), srvRecordExecutor);
            CompletableFuture.runAsync(()->mDNS_packet_parser.mDNSSRVRecordParser(mDNSPayload, protocol_pos, mDNSPacket.getSourceIpv4()), srvRecordExecutor);
        }
    }


    /**
     * Method is called when HostManager removed any hosts from it's database upon
     *  shutdown of a host.
     * @param notification
     */

    @Override
    public void onHostRemovedNotification(HostRemovedNotification notification) {

        if (notification != null){
            if (MDNSPacketsQueue.mDNSPackets.containsKey(notification.getIPAddress())) {
                LOG.debug("Remove mDNS packets for " + notification.getIPAddress());
                System.out.println("Remove mDNS packets for " + notification.getIPAddress());
                MDNSPacketsQueue.mDNSPackets.remove(notification.getIPAddress());
                mDNS_packet_parser.clear_url_record(notification.getIPAddress());
            } else {
                return;
            }
        }
        return;
    }

    @Override
    public void onHostAddedNotification(HostAddedNotification notification) {
        // Do Nothing
    }

    private void flushpkts(){
        System.out.println("Removing Cached Packets");
        MDNSPacketsQueue.mDNSPackets.clear();
    }

    @Override
    public Future<RpcResult<Void>> flushPkts() {
        LOG.debug("Flush stored mDNS Packets");
        flushpkts();
        return Futures.immediateFuture(RpcResultBuilder.<Void>success().build());
    }

}
