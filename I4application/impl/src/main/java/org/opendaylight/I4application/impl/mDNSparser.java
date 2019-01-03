/*
 * Copyright © 2016 Basavaraj and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.I4application.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class mDNSparser {

    private static final Logger LOG = LoggerFactory.getLogger(mDNSparser.class);
    private static final int SRV_RECORD_VAL = 33;
    private static int SRV_RECORD_POS_OFFSET = 22;
    private static int SRV_RECORD_END;
    private static int PROTOCOL_POS = 0;
    private static byte[] RR_ByteArray;
    private static int record_val = 0;
    private static int dataLength = 0;
    private static int port = 0;
    private static String hostname;

    /**
     * Parses mDNS packet payload and return hostname for URL construction
     */
    public static String mDNSRecordParser(byte[] mDNSbinary){
        LOG.debug("mDNS Record Parser");
        String mDNSString = new String(mDNSbinary, StandardCharsets.UTF_8);

        PROTOCOL_POS = mDNSString.indexOf("_opcua-tcp");
        if(PROTOCOL_POS < 0){
            return null;
        }
        //Copy SRV value
        RR_ByteArray = Arrays.copyOfRange(mDNSbinary, PROTOCOL_POS+SRV_RECORD_POS_OFFSET,
                                                PROTOCOL_POS+SRV_RECORD_POS_OFFSET+2);
        record_val = ByteBuffer.wrap(RR_ByteArray).getShort();
        // check is it SRV record
        if (record_val != SRV_RECORD_VAL){
            LOG.debug("Not SRV Record mDNS packet");
            return null;
        }
        // Skip 6 bytes from record pos
        SRV_RECORD_END = PROTOCOL_POS+SRV_RECORD_POS_OFFSET+2;
        dataLength = ByteBuffer.wrap(Arrays.copyOfRange(mDNSbinary, SRV_RECORD_END+6, SRV_RECORD_END+8)).getShort();
        port = ByteBuffer.wrap(Arrays.copyOfRange(mDNSbinary, SRV_RECORD_END+12, SRV_RECORD_END+14)).getShort();
        byte[] targetarray = Arrays.copyOfRange(mDNSbinary, SRV_RECORD_END + 15, SRV_RECORD_END + 14 + dataLength - 7);
        hostname = new String(targetarray, StandardCharsets.UTF_8) + ":" + port;
        return hostname;
    }
}
