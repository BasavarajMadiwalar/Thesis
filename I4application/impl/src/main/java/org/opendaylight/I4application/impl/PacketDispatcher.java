/*
 * Copyright © 2016 Basavaraj and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.I4application.impl;

import org.opendaylight.I4application.impl.Topology.HostManager;
import org.opendaylight.I4application.impl.utils.InstanceIdentifierUtils;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv4Address;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeConnectorRef;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeRef;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.PacketProcessingService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.TransmitPacketInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.TransmitPacketInputBuilder;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PacketDispatcher {
    private final static Logger LOG = LoggerFactory.getLogger(PacketDispatcher.class);

    private PacketProcessingService packetProcessingService;
    private HostManager hostManager;

    public PacketDispatcher(PacketProcessingService packetProcessingService, HostManager hostManager) {
        this.packetProcessingService = packetProcessingService;
        this.hostManager = hostManager;
    }


    public boolean dispatchmDNSPacket(byte[] payload, Ipv4Address srcIP, Ipv4Address dstIP){
        LOG.debug("DispatchmDNS packet");
        NodeConnectorRef srcNCRef = hostManager.getIpNodeConnectorRef(srcIP);
        NodeConnectorRef dstNCRef = hostManager.getIpNodeConnectorRef(dstIP);

        if (srcNCRef != null && dstNCRef != null){
            return sendmDNSPacketOut(payload, srcNCRef, dstNCRef);
        }
        return false;
    }

    public boolean sendmDNSPacketOut(byte[] payload,
                                     NodeConnectorRef srcNCRef, NodeConnectorRef dstNCRef){
        LOG.debug("Sending mDNS Packets out");

        if (srcNCRef == null || dstNCRef == null){
            return false;
        }
        InstanceIdentifier<Node> egressNode = InstanceIdentifierUtils.generateNodeInstanceIdentifier(dstNCRef);

        TransmitPacketInput transmitPacketInput = new TransmitPacketInputBuilder()
                            .setPayload(payload)
                            .setNode(new NodeRef(egressNode))
                            .setEgress(dstNCRef)
                            .setIngress(srcNCRef)
                            .build();
        packetProcessingService.transmitPacket(transmitPacketInput);
        return true;
    }

    public boolean dispatchPacket(byte[] payload, Ipv4Address scrIP, Ipv4Address dstIP){
        LOG.info("Dispatch IP Packets");
        NodeConnectorRef dstNCRef = hostManager.getIpNodeConnectorRef(dstIP);
        if (dstNCRef == null){
            LOG.debug("Could not find an entry for:" + dstIP);
        }

        if (dstNCRef != null){
            return sendPacketOut(payload, dstNCRef);
        }
        return false;
    }

    public boolean sendPacketOut(byte[] payload, NodeConnectorRef dstNCRef){

        LOG.debug("Sending general Packet out");

        InstanceIdentifier<Node> egressNode = InstanceIdentifierUtils.generateNodeInstanceIdentifier(dstNCRef);

        TransmitPacketInput transmitPacketInput = new TransmitPacketInputBuilder()
                .setPayload(payload)
                .setNode(new NodeRef(egressNode))
                .setEgress(dstNCRef)
                .build();
        packetProcessingService.transmitPacket(transmitPacketInput);
        return true;
    }
}
