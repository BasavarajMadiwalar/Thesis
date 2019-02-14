/*
 * Copyright © 2016 Basavaraj and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.I4application.impl;

import org.opendaylight.controller.md.sal.binding.api.NotificationPublishService;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv4Address;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.urlnotification.rev150105.DiscoveryUrlNotification;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.urlnotification.rev150105.DiscoveryUrlNotificationBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;

public class mDNS_packet_parser {

    private static final Logger LOG = LoggerFactory.getLogger(mDNS_packet_parser.class);
    private static final int SRV_RECORD_VAL = 33;
    private static int SRV_RECORD_POS_OFFSET = 22;
    private static int SRV_RECORD_END;
//    private static int PROTOCOL_POS = 0;
    private static byte[] RR_ByteArray;
    private static int record_val = 0;
    private static int dataLength = 0;
    private static int port = 0;
    private static String discovery_url;

    private ConcurrentHashMap<Ipv4Address, String> urlRecord = new ConcurrentHashMap<>();
    private NotificationPublishService notificationProvider;

    public mDNS_packet_parser(NotificationPublishService notificationPublishService) {
        this.notificationProvider = notificationPublishService;
    }

    /**
     * Parses mDNS packet payload and return hostname for URL construction
     */
    public void mDNSRecordParser(byte[] mDNSbinary, int PROTOCOL_POS, Ipv4Address src_address){
        LOG.debug("mDNS Record Parser");

        if (urlRecord.containsKey(src_address)){
            LOG.debug("URL already exists");
            return;
        }

        //Copy SRV value
        RR_ByteArray = Arrays.copyOfRange(mDNSbinary, PROTOCOL_POS+SRV_RECORD_POS_OFFSET,
                                                PROTOCOL_POS+SRV_RECORD_POS_OFFSET+2);
        record_val = ByteBuffer.wrap(RR_ByteArray).getShort();
        // check is it SRV record
        if (record_val != SRV_RECORD_VAL){
            LOG.debug("Not SRV Record mDNS packet");
            return;
        }
        // Skip 6 bytes from record pos
        SRV_RECORD_END = PROTOCOL_POS+SRV_RECORD_POS_OFFSET+2;
        dataLength = ByteBuffer.wrap(Arrays.copyOfRange(mDNSbinary, SRV_RECORD_END+6, SRV_RECORD_END+8)).getShort();
        port = ByteBuffer.wrap(Arrays.copyOfRange(mDNSbinary, SRV_RECORD_END+12, SRV_RECORD_END+14)).getShort();
        byte[] targetarray = Arrays.copyOfRange(mDNSbinary, SRV_RECORD_END + 15, SRV_RECORD_END + 14 + dataLength - 7);
        discovery_url = new String(targetarray, StandardCharsets.UTF_8) + ":" + port;


        urlRecord.put(src_address, discovery_url);
        DiscoveryUrlNotification discoveryUrlNotification = new DiscoveryUrlNotificationBuilder()
                .setSrcIPAddress(src_address)
                .setDiscoveryUrl(discovery_url)
                .build();
        notificationProvider.offerNotification(discoveryUrlNotification);
    }


    public void publish_url(String discovery_url, Ipv4Address src_address){

        DiscoveryUrlNotification discoveryUrlNotification = new DiscoveryUrlNotificationBuilder()
                                                            .setSrcIPAddress(src_address)
                                                            .setDiscoveryUrl(discovery_url)
                                                            .build();
        notificationProvider.offerNotification(discoveryUrlNotification);
        System.out.println("Published URL");
    }

    public void clear_url_record(){
        urlRecord.clear();
    }

}
