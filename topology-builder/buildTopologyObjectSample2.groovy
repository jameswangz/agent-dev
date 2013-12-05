//  Script Demo: build 3 instances of QAVirtualMachine topology objects 
def topologyService = server["TopologyService"]
def dataService = server["DataService"]
def queryService = server["QueryService"]
def canonicalFactory = dataService.getCanonicalFactory()

def vmType = topologyService.getType("QAVirtualMachine")
def vmIds = ["zhuvm1023", "zhuvm1011", "zhuvm1059"]
def vmIps = ["10.30.146.23", "10.30.146.57", "10.30.146.98"]
def vmStatuses = [1.0d, 0.0d, 1.0d]

def vmObjectShell = topologyService.getObjectShell(vmType)

vmIds.each { vmId ->
	vmObjectShell.set("id", vmId)
	vmObjectShell.set("name", "QAVm-" + vmId)
	vmObjectShell.set("label", "QAVirtualMachine-" + vmId)
    topologyService.mergeData(vmObjectShell)
}

vmIds.eachWithIndex { vmId, index ->
    def queryResult = queryService.queryTopologyObjects("QAVirtualMachine:id='" + vmId + "'")
    if(queryResult.size() == 0) {
        return "Object: "+ vmId + " not found"
	}
    
    def obj = queryResult.toArray()[0]
	try {
		node = canonicalFactory.createNode( obj, "ipAddress" , vmIps[index], 60 * 60 * 1000 )
		dataService.publish(node)
		node = canonicalFactory.createNode( obj, "hostName" , vmIds[index] , 60 * 60 * 1000 )
		dataService.publish(node)
		node = canonicalFactory.createNode( obj, "domain" , "zhuvm" , 60 * 60 * 1000 )
		dataService.publish(node)
		node = canonicalFactory.createNode( obj, "status" , vmStatuses[index] , 60 * 60 * 1000 )
		dataService.publish(node)
		node = canonicalFactory.createNode( obj, "remark" , "remark text for " + vmIds[index] , 60 * 60 * 1000 )
		dataService.publish(node)
    } catch (Exception e) {
        return e.getMessage()
    }
}

