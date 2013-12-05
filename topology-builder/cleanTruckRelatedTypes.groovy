def topologyService = server.TopologyService
def types = new HashSet()
['Truck'].each {
	types.add(topologyService.getType(it))    
}
topologyService.removeTypes(types)

