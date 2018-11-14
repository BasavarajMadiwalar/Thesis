import subprocess as sp
from pyroute2 import netns, IPDB, NetNS
import logging
from ovs_vsctl import VSCtl


log = logging.getLogger(__name__)

def createnetns(name):
    log.info("Creating network namespace")
    opcnetns = NetNS(name)

    print(netns.listnetns())
    return opcnetns

def createveth(name):
    log.info("Create and add veth pair to netns")
    ip = IPDB()
    ip.create(ifname='veth0', kind='veth', peer='veth1').commit()
    ip.create(ifname='veth2', kind='veth', peer='veth3').commit()

    with ip.interfaces.veth0 as veth:
        veth.net_ns_fd = name
    with ip.interfaces.veth2 as veth:
        veth.net_ns_fd = name
    ip.release()

def createovs(ovsname):
    log.info("Create ovs and add port with veth1")

    vsctl = VSCtl('tcp', '127.0.0.1', 6640)
    vsctl.run(command='add-br %s'%ovsname)
    vsctl.run(command='set-controller %s tcp:127.0.0.1:6653'%ovsname)
    vsctl.run(command='add-port %s veth1'%ovsname)

def addipaddress(name, opcnetns,ipaddr):
    # create main network % netns network settings database
    main_IPDB = IPDB()
    netns_IPDB = IPDB(opcnetns)


    # Change status of veth1 to up
    with main_IPDB.interfaces.veth1 as veth:
        veth.up()
    with main_IPDB.interfaces.veth3 as veth:
        veth.add_ip("192.168.10.2/24")
        veth.up()
    main_IPDB.release()

    # assign ip address to veth interface inside netns
    with netns_IPDB.interfaces.veth0 as veth:
        veth.add_ip(ipaddr)
        veth.up()

    # assign ip address to veth interface inside netns
    with netns_IPDB.interfaces.veth2 as veth:
        veth.add_ip("192.168.10.1/24")
        veth.up()
    netns_IPDB.release()



if __name__=="__main__":

    logconf = {'format':'[%(asctime)s.%(msecs)-3d: %(name)-16s - %(levelname)-5s] %(message)s', 'datefmt': "%H:%M:%S"}
    logging.basicConfig(level=logging.DEBUG, **logconf)

    opc_client_addr = "10.0.0.200/8"
    nsname = "opcuaclient"
    ovsname = "ovsbr1"

    opcnetns = createnetns(nsname)
    createveth(nsname)
    createovs(ovsname)
    addipaddress(nsname, opcnetns ,opc_client_addr)

