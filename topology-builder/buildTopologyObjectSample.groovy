package system._truck.scripts;

def createOrUpdateObject(typeName, propertiesMap) {
	topologyService = server.TopologyService
	def type = topologyService.getType(typeName)
	objectShell = topologyService.getObjectShell(type)
	propertiesMap.each { propertyName, propertyValue ->
		def property = type.getProperty(propertyName)
		if (!property.isMany()) {
			objectShell.set(property, propertyValue)
		} else {
			if (!(propertyValue instanceof Collection)) {
				propertyValue = [propertyValue]
			}
			def values = objectShell.get(property);
			propertyValue.each { value ->
				if (!values.contains(value)) {
					values.add(value);
				}
			}
		}
	}

	return topologyService.mergeData(objectShell)
}

topologyService = server.TopologyService
dataService = server.DataService
canonicalFactory = dataService.getCanonicalFactory()

truck = createOrUpdateObject("Truck", [name:'BMW', serialNumber:"t1"]) 

engine = createOrUpdateObject("TruckEngine", [truck:truck, name:'V16'])
dataService.publish(canonicalFactory.createNode(engine, "oilLevel", 3.0d, 0))
dataService.publish(canonicalFactory.createNode(engine, "status", 'fine', 0))

tire_1 = createOrUpdateObject("TruckTire", [truck:truck, id:'tire_1', name:'Tire 1']) 
dataService.publish(canonicalFactory.createNode(tire_1, "tirePressure", 2.5d, 0))
tire_2= createOrUpdateObject("TruckTire", [truck:truck, id:'tire_2', name:'Tire 2']) 
dataService.publish(canonicalFactory.createNode(tire_2, "tirePressure", 2.6d, 0))
tire_3 = createOrUpdateObject("TruckTire", [truck:truck, id:'tire_3', name:'Tire 3']) 
dataService.publish(canonicalFactory.createNode(tire_3, "tirePressure", 2.6d, 0))
tire_4 = createOrUpdateObject("TruckTire", [truck:truck, id:'tire_4', name:'Tire 4']) 
dataService.publish(canonicalFactory.createNode(tire_4, "tirePressure", 2.5d, 0))

//from non-edit mode to edit-mode
truck_edit = topologyService.beginUpdate(truck)
truck_edit.set("engine", engine)
truck_edit.getList("tires").clear()
truck_edit.getList("tires").addAll([tire_1, tire_2, tire_3, tire_4])
edited_truck = topologyService.endUpdate(truck_edit)
