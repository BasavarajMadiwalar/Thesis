#!/usr/bin/python
"""
This is the most simple example to showcase Containernet.
"""
from mininet.net import Containernet
from mininet.node import Controller
from mininet.cli import CLI
from mininet.link import TCLink
from mininet.log import info, setLogLevel
from mininet.node import RemoteController
from mininet.util import pmonitor
from time import time
from signal import SIGINT
import subprocess
from time import sleep


def createNet():

    net = Containernet(controller=None)
    info('*** Adding controller\n')
    net.addController(name="ODL", controller=RemoteController,
                      ip='127.0.0.1', port=6653)
    info('*** Adding docker containers\n')
    d1 = net.addDocker('d1', ip='10.0.0.1', mac='00:00:00:00:00:01', dimage="ubuntu:trusty", volumes=['/home/basavaraj/Th/d1:/root/opcua'])
    d2 = net.addDocker('d2', ip='10.0.0.2', mac='00:00:00:00:00:02', dimage="ubuntu:trusty", volumes=['/home/basavaraj/Th/d2:/root/opcua'])
    d3 = net.addDocker('d3', ip='10.0.0.3', mac='00:00:00:00:00:03', dimage="ubuntu:trusty", volumes=['/home/basavaraj/Th/d3:/root/opcua'])
    d4 = net.addDocker('d4', ip='10.0.0.4', mac='00:00:00:00:00:04', dimage="ubuntu:trusty", volumes=['/home/basavaraj/Th/d4:/root/opcua'])


    info('*** Adding switches\n')
    s1 = net.addSwitch('s1')
    s2 = net.addSwitch('s2')


    info('*** Creating links\n')
    net.addLink(d1, s1)
    net.addLink(d3, s1)
    net.addLink(s1, s2, cls=TCLink, delay='100ms', bw=1)
    net.addLink(d2, s2)
    net.addLink(d4, s2)

    info('*** Starting network\n')
    net.start()
    info('*** Testing connectivity\n')
    net.ping([d1, d2, d3, d4])
    info('*** Running script to configure host names')
    d1.cmd("./root/opcua/hostnamegen.sh 4")
    d2.cmd("./root/opcua/hostnamegen.sh 4")
    d3.cmd("./root/opcua/hostnamegen.sh 4")
    d4.cmd("./root/opcua/hostnamegen.sh 4")

    return net

    # info('*** Running CLI\n')
    # CLI(net)
    # info('*** Stopping network')
    # net.stop()

def runProgram(net):
    info('*** Collecting Host List')

    # Get list of hosts
    hosts = net.hosts
    opcua = hosts[:2]
    ldsservers = hosts[2:4]

    popens1 = {}
    popens2 = {}
    endtime = time() + 30

    for lds in ldsservers:
        popens1[lds] = lds.popen(['./root/opcua/ldsserver'],shell=False)

    sleep(5)

    for opcua_server in opcua:
        popens2[opcua_server] = opcua_server.popen(["./root/opcua/server_multicast1"])
        sleep(1)

    # for opcua_server in opcua:
    #     popens2[opcua_server] = opcua_server.popen("docker exec -i -t mn.d1 /root/opcua/server_multicast1")


    # proc1 = subprocess.Popen("docker exec -i -t mn.d1 /root/opcua/server_multicast1")
    # proc2 = subprocess.Popen("docker exec -i -t mn.d2 /root/opcua/server_multicast1")

    info("Monitoring the output for", 10 , "seconds\n")

    for h, line in pmonitor(popens2, timeoutms=250):
        if h:
            info('<%s>: %s' % ( h.name, line ))

        if time() >= endtime:
            for p in popens2.values():
                p.send_signal(SIGINT)

    for h, line in pmonitor(popens1, timeoutms=250):
        if h:
            info('<%s>: %s' % ( h.name, line ))

        if time() >= endtime:
            for p in popens1.values():
                p.send_signal(SIGINT)


    info('*** Running CLI\n')
    CLI(net)
    info('*** Stopping network')
    net.stop()


if __name__== "__main__":
    setLogLevel('info')
    net = createNet()
    runProgram(net)




