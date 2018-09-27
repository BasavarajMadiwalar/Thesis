/*
 * Copyright © 2016 Basavaraj and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.I4application.impl.utils;

import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.MacAddress;

import java.util.Arrays;

public abstract class PacketParsingUtils {

    /**
     * size of MAC address in octets (6*8 = 48 bits)
     */
    private static final int MAC_ADDRESS_SIZE = 6;

    /**
     * start position of destination MAC address in array
     */
    private static final int DST_MAC_START_POSITION = 0;

    /**
     * end position of destination MAC address in array
     */
    private static final int DST_MAC_END_POSITION = 6;

    /**
     * start position of source MAC address in array
     */
    private static final int SRC_MAC_START_POSITION = 6;

    /**
     * end position of source MAC address in array
     */
    private static final int SRC_MAC_END_POSITION = 12;

    /**
     * start position of ethernet type in array
     */
    private static final int ETHER_TYPE_START_POSITION = 12;

    /**
     * end position of ethernet type in array
     */
    private static final int ETHER_TYPE_END_POSITION = 14;

    /**
     * Start postion of IP Header
     */
    private static final int IP_HEADER_START_POSTION = 14;

    /**
     * End of IP Version and IHL in array
     */

    private static final int IP_HEADER_VER_IHL = 15;

    /**
     * Start of Src IP Addr
     */
    private static final int SRC_IP_ADDR_START = 26;

    /**
     * End of Src IP Addr
     */
    private static final int SRC_IP_ADDR_END = 30;

    /**
     * Start of Dst IP Addr
     */
    private static final int DST_IP_ADDR_START = 30;

    /**
     * End of Dst IP Addr
     */
    private static final int DST_IP_ADDR_END = 34;

    /**
     * Protocol Field of IP Header
     */

    private static final int IP_PROTOCOL_FEILD = 23;




    private PacketParsingUtils() {
        //prohibite to instantiate this class
    }

    /**
     * @param payload
     * @return destination MAC address
     */
    public static byte[] extractDstMac(final byte[] payload) {
        return Arrays.copyOfRange(payload, DST_MAC_START_POSITION, DST_MAC_END_POSITION);
    }

    /**
     * @param payload
     * @return source MAC address
     */
    public static byte[] extractSrcMac(final byte[] payload) {
        return Arrays.copyOfRange(payload, SRC_MAC_START_POSITION, SRC_MAC_END_POSITION);
    }

    /**
     * @param payload
     * @return source MAC address
     */
    public static byte[] extractEtherType(final byte[] payload) {
        return Arrays.copyOfRange(payload, ETHER_TYPE_START_POSITION, ETHER_TYPE_END_POSITION);
    }

    /**
     * @param payload
     * @return IP Header Length
     */
    public static int IPHeaderLength(final byte[] payload){
        byte[] Ver_IHL = Arrays.copyOfRange(payload, IP_HEADER_START_POSTION, IP_HEADER_VER_IHL);
        int IHLValue = Ver_IHL[0] & 0x0F;
        return IHLValue;
    }


    /**
     *
     * @param payload
     * @return Protocol number inside IP Header. Useful to identfy UDP Packets
     */
    public static int ProtocolNum(final byte[] payload){
        byte[] value = Arrays.copyOfRange(payload, IP_PROTOCOL_FEILD, IP_PROTOCOL_FEILD+1);
        int protocolVal = value[0];
        return protocolVal;
    }

    /**
     *
     * @param payload
     * @param ipHeaderLength
     * @return UDP DestPortNumber
     */
    public static int extractUDPDestPort(final byte[] payload, int ipHeaderLength){
        int start = (14 + 4 * ipHeaderLength);
        //int start = 1;
        byte[] DstPort = Arrays.copyOfRange(payload, start+2, start+4);
        System.out.println("Dest port number is: " + DstPort);
        return (int)DstPort[0];
    }

    public static byte[] extractmDNSpayload(final byte[] payload, int ipHeaderLength){
        int start = (14 + 4 * ipHeaderLength + 8);
        byte[] mDNSpayload = Arrays.copyOfRange(payload, start, payload.length);
        return mDNSpayload;
    }
    /**
     *
     * @param payload
     * @return SRC Ip Address
     */
    public static byte[] extractSrcIPAddr(final byte[] payload){
        return Arrays.copyOfRange(payload, SRC_IP_ADDR_START, SRC_IP_ADDR_END);
    }

    /**
     *
     * @param payload
     * @return DST IP address
     */

    public static byte[] extractDstIPAddr(final byte[] payload){
        return Arrays.copyOfRange(payload, DST_IP_ADDR_START, DST_IP_ADDR_END);
    }

    /**
     * @param rawMac
     * @return {@link MacAddress} wrapping string value, baked upon binary MAC
     *         address
     */
    public static MacAddress rawMacToMac(final byte[] rawMac) {
        MacAddress mac = null;
        if (rawMac != null && rawMac.length == MAC_ADDRESS_SIZE) {
            StringBuilder sb = new StringBuilder();
            for (byte octet : rawMac) {
                sb.append(String.format(":%02X", octet));
            }
            mac = new MacAddress(sb.substring(1));
        }
        return mac;
    }

    public static String rawMacToString(byte[] rawMac) {
        if (rawMac != null && rawMac.length == 6) {
            StringBuffer sb = new StringBuffer();
            for (byte octet : rawMac) {
                sb.append(String.format(":%02X", octet));
            }
            return sb.substring(1);
        }
        return null;
    }

    public static String rawIpToString(byte[] rawIP){

        if (rawIP != null && rawIP.length == 4){
            StringBuffer sb = new StringBuffer();
            for (byte octet : rawIP){
                sb.append(String.format(".%02X", octet));
            }
            return sb.substring(1);
        }
        return null;
    }

    public static byte[] stringMacToRawMac(String address) {
        String[] elements = address.split(":");
        if (elements.length != MAC_ADDRESS_SIZE) {
            throw new IllegalArgumentException(
                    "Specified MAC Address must contain 12 hex digits" +
                            " separated pairwise by :'s.");
        }

        byte[] addressInBytes = new byte[MAC_ADDRESS_SIZE];
        for (int i = 0; i < MAC_ADDRESS_SIZE; i++) {
            String element = elements[i];
            addressInBytes[i] = (byte)Integer.parseInt(element, 16);
        }
        return addressInBytes;
    }
}
