#!/usr/bin/python
"""
This is an example of mininet without controller.
With this example, we will configure each switch single flow to handle packets
with actions=Normal.As a result, switches will forward packet like legacy l2switches do.
"""
from mininet.net import Containernet
from mininet.net import Mininet
from mininet.cli import CLI
from mininet.link import TCLink
from mininet.log import info, setLogLevel
from mininet.node import RemoteController
from mininet.util import pmonitor
from signal import SIGINT
from time import time
import os

setLogLevel('info')

net = Mininet(controller=None)
info('*** Adding hosts\n')

d1 = net.addHost('d1', ip='10.0.0.1', mac='00:00:00:00:00:01')
d2 = net.addHost('d2', ip='10.0.0.2', mac='00:00:00:00:00:02')
d3 = net.addHost('d3', ip='10.0.0.3', mac='00:00:00:00:00:03')
d4 = net.addHost('d4', ip='10.0.0.4', mac='00:00:00:00:00:04')

info('*** Adding switches\n')
s1 = net.addSwitch('s1')
s2 = net.addSwitch('s2')
s3 = net.addSwitch('s3')

info('*** Creating links\n')
net.addLink(d1, s1)
net.addLink(s1, s2, cls=TCLink, delay='100ms', bw=1)
net.addLink(s2, d2)
net.addLink(s2, s3, cls=TCLink, delay='100ms', bw=1)
net.addLink(s3, d3)
net.addLink(s3, d4)
info('*** Starting network\n')
net.start()
# info('*** Testing connectivity\n')
# net.ping([d1, d2, d3, d4])
# info('*** Running script to configure host names')
# d1.cmd("./root/opcua/hostnamegen.sh 4")
# d2.cmd("./root/opcua/hostnamegen.sh 4")
# d3.cmd("./root/opcua/hostnamegen.sh 4")
# d4.cmd("./root/opcua/hostnamegen.sh 4")

# # New change
# hosts = net.hosts
# popens = {}
# h = hosts[0]
# # for h in hosts[0]:
# popens[h] = h.popen("./root/opcua/server_multicast")
#
# pid = popens[h]
# print pid.pid
# endTime = time() + 5
# for host,line in pmonitor(popens):
#     if host:
#         print '<%s>: %s' % (h.name, line)
#
#     if time() >= endTime:
#         for p in popens.values():
#             p.send_signal( SIGINT )
#             os.kill(pid.pid, )
#
#
# New Change
info('*** Running CLI\n')
CLI(net)
info('*** Stopping network')
net.stop()

