package system._truck.scripts;


def builder = getTopologyBuilder()
def engine = builder.engine

builder.showDebugMessage = true
builder.submissionInternval = 5000
builder.submissionGroup = 'TruckGroup'

builder.Truck(name : 'BMW', serialNumber : 't1') {
	_ engine : TruckEngine { // the name property must inside inside the closure for enabling the auto parent lookup feature 
		_ name : 'V16'
		_ oilLevel : engine.createSubmitter(100, 30, false) 
		_ status : engine.createSubmitter(['fine', 'general', 'week'])
	}
	
	_ tires : [
		TruckTire(name : 'Tire 1', 'tire_1') {
			_ tirePressure : engine.createSubmitter(575, 63, false)
		},
		TruckTire(name : 'Tire 2', 'tire_2') {
			_ tirePressure : engine.createSubmitter(575, 63, false)
		},
		TruckTire(name : 'Tire 3', 'tire_3') {
			_ tirePressure : engine.createSubmitter(575, 63, false)
		},
		TruckTire(name : 'Tire 4', 'tire_4') {
			_ tirePressure : engine.createSubmitter(575, 63, false)
		}
	]
}

builder.build()
// the calling of build method is equvilant to the calling of the following methods 
// def truck = builder.mergeObject()
// builder.startDataSubmission()

