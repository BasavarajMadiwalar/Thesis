/*
 * Copyright © 2016 Basavaraj and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */


package org.opendaylight.I4application.impl.utils;

import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv4Address;

import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;

public class MDNSPacketsQueue {

    public static ConcurrentHashMap<Ipv4Address, ArrayList<byte[]>> mDNSPackets = new ConcurrentHashMap<>();


}
