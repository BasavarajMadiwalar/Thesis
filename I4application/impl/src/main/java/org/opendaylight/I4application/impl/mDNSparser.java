/*
 * Copyright © 2016 Basavaraj and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.I4application.impl;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class mDNSparser {

    private static final int SRV_RECORD_VAL = 33;
    

    public static String mDNSRecordParser(byte[] mDNSbinary){
        String mDNSString = new String(mDNSbinary, StandardCharsets.UTF_8);
        //Obtain the postion of _opcua-tcp
        int protocolpos = mDNSString.indexOf("_opcua-tcp");
        if(protocolpos > 0){
            //Copy SRV value
            byte[] recordfield = Arrays.copyOfRange(mDNSbinary, protocolpos+22, protocolpos+24);
            int recordval = ByteBuffer.wrap(recordfield).getShort();
            // check is it SRV record
            if (recordval == SRV_RECORD_VAL){
                // Skip 6 bytes from record pos
                int recordpos = protocolpos + 24;
                int DL = ByteBuffer.wrap(Arrays.copyOfRange(mDNSbinary, recordpos +6, recordpos+8)).getShort();
                int port = ByteBuffer.wrap(Arrays.copyOfRange(mDNSbinary, recordpos+12, recordpos+14)).getShort();
                //System.out.println("Record val and port number are : " + recordval + " and " + port);
                byte[] targetarray = Arrays.copyOfRange(mDNSbinary, recordpos + 15, recordpos + 14 + DL - 7);
                String hostname = new String(targetarray, StandardCharsets.UTF_8) + ":" + port;
                return hostname;
            }
            return null;
        }
        return null;
    }
}
