package org.opendaylight.I4application.impl;

import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.ipv4.rev140528.Ipv4PacketListener;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.ipv4.rev140528.Ipv4PacketReceived;

public class UdpDecoder implements Ipv4PacketListener {

    public UdpDecoder() {
        // Add this class as notification provider
    }

    @Override
    public void onIpv4PacketReceived(Ipv4PacketReceived notification) {

    }

}
