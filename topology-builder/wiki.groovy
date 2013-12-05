{toc}

h2. Introduction

For dashboard works, data are crucial. We need data in order to test out views and actions.

There are a couple ways of getting data (TopologyObjects)

# Deploy real agents: the proper and eFoglight way to do things
# Deploy simulated agents: more for development, but it simulates read data submission, and this is the closest thing to the real environment (if done properly)
# Capture/Replay data: this is simply not simple :) and not straight forward in some cases.
# Create object shells on server and use DataService to generate data: this is quick, easy, low cast to obtain data. It comes with cons that this  is simply *creating* instead of *collecting* data.

Ideally we'd like to have some sort of agents. However, in the early stage of a project (or in a POC), this could be luxury. Agent team could be in the process of defining the collection model. In addition, deploy and re-deploying agents (esp upgrading) could be a lengthy process which eat up big chunk of dashboard time.

The tool utilize SL calls to create object shells and DataService to publish data. This should be used in these certain cases:

# Just need to data to work with
# Understand the model (very) well
# Data submission is fairly simple
# No agent related artifact is need for views/actions
# Not using this to generate load on server
# You are using this tool internally and limit support, and knows about TopologyService, as well as having a strong Groovy background.


h2. Distribution

Currently the tool is distributed as a part of LAMP cartridges (optional). Cartridge can be obtained [here|http://torrepos.prod.quest.corp/rmdata/quest/apm-toolbox/waverider/latest.integration/archives/Topology-Helper-Tools-5_6_2.car]. Visit [Repos|http://torrepos.prod.quest.corp/bundle/quest/apm-toolbox/waverider/latest]

There's no plan to integrate this as a part of core UI Utils cart or Foglight IDE.

h2. How it works

The tool creates a builder which utilizes the Topology Service to create object shells in a tree structure syntax. For data submission, it uses SchedulerService to submit data periodically or based on user-define schedule.

Supposed we have the following type definition
{code}
<types>
	<type name='Component' extends='TopologyObject'>
		<annotation name="Abstract"/>
		<property name='application' type='Application' is-identity='true'/>
	</type>
	<type name='Application' extends='TopologyObject'>
		<property name='name' type='String' is-identity='true'/>
		<property name='components' type='Component' is-many='true' is-containment='true' />
		<property name='sla' type='Metric' is-containment='true' unit-name='percent'/>
		<property name='homePageResponseTime' type='Metric' is-containment='true' unit-name='millisecond'/>
	</type>
	<type name='EndUserComponent' extends='Component'>
		<property name='name' type='String' is-identity='true'/>
		<property name='transactions' type='Transaction' is-containment='true' is-many='true'/>
	</type>
	<type name='Transaction' extends='TopologyObject'>
		<annotation name="Abstract"/>
		<property name='component' type='Component' is-identity='true'/>
	</type>
	<type name='EndUserTransaction' extends='Transaction'>
		<annotation name="LabelProperty" value="resource"/>
		<property name='resource' type='String' is-identity='true' />
		<property name='responseTime' type='Metric' is-containment='true' />
	</type>
</types>
{code}

h3. Creating Topology Object model

In order to create an application, run this script

{code}
def builder = getTopologyBuilder();
def jira = builder.Application
{
	_ name:"Jira"
	_ components:[
		EndUserComponent {
			_ name:"Web"
			_ application:builder.parent
			_ transactions:[
				EndUserTransaction {
					_ resource:"action=browse&item=products"
					_ component:builder.parent
				},
				EndUserTransaction {
					_ resource:"action=checkout&item=canon"
					_ component:builder.parent
				},
				EndUserTransaction {
					_ resource:"action=find&item=hot"
					_ component:builder.parent
				}
			]
		},
		EndUserComponent {
			_ name:"Load Balancer"
			_ application:builder.parent
		},
	]
}
builder.mergeData();
{code}

h4. Builder Syntax for Creating New Object

In general, when a builder is created, we can create instance of a TopologyType by providing a type name.

The following

{code}
builder.<TopologyTypeName>
{code}

will create an object shell of *<TopologyTypeName>*.

Once a type is define, then we can start setting properties to the object, using this syntax

{code}
builder.<TopologyTypeName>(property:value*) {
	statment
	...
	_ property:value
	_ property:value
	...
	statement
	_ property:value
}
{code}

There are 2 ways to storing values to an object:

# Inside *()*: this piece of code will be executed BEFORE the instance of the type is created. as it doesn't have the current object as scope. Essentially is a short-hand to create a map and ideal to use when setting static/primitive values and has no references to the soon-to-be created object.
# Inside *{}*: this piece of code will be executed AFTER the object shell is created. This is a more advanced version of *()*. After all everything inside this block is executable, and 2 more dynamic properties are available when inside this clousure:
* *builder.current*: return the object itself.
* *builder.parent*: return the shell that immediately contains the current object.

Note: since *{}* is a closure, we can create variable and do assignment.
Note: each line inside *{}* will be excuted. A a result, in order to signal the builder to *set* the *property:value* to the current instance, write the statement begin with '_ ' (notice the space is required). For example:

{code}
builder.Host{
	_ name:"MyHost"
}
{code}

will create a Host, and set the name property to "MyHost". If the code is written without the *-*, nothing happens and nothing gets set. This code is equivalent to 

{code}
builder.Host(name:"MyHost");
{code}

Note: once inside the closure *{}*, we dont need to make explicit call to *build.<TopologyTypeName>*. Simply write *<TopologyTypeName>* as a statement and an object will be created.

h4. Builder Syntax for Updating Existing Object

The syntax is essentially the same with an exception that only 1 object is update at a time and the updating properties must not contain ones are *identity properties* (TBD).

In order to update an object

{code}
builder(<ObjectToUpdate>) {
	statment
	...
	_ property:value
	_ property:value
	...
	statement
	_ property:value
}
{code}

Note: since the *()* syntax already used for the object, only *{}* is supported.

h3. Data Node Handling

The builder allow user to register a closure should a child node of certain type is created. Consider this code

{code}
def builder = getTopologyBuilder();

builder.FSMCategory(name: 'My Category') {
	_ definition : [
		Host(name: 'Host 1'),
		Host(name: 'Host 2'),
		Host(name: 'Host 3'),
	]
}
{code}

could be rewritten as:

{code}
def builder = getTopologyBuilder();
def addToServiceDefinition = {parent, child ->
	parent.definition.add(child);
}
builder.registerTypeHandler('FSMCategory', 'Host', addToServiceDefinition);

builder.FSMCategory(name: 'My Category') {
	Host(name: 'Host 1');
	Host(name: 'Host 2');
	Host(name: 'Host 3');
}
{code}

What the above code doing is to create *My Category*. Once a child (in this case, *Host*) node is created, the builder will executed the provided closure that matched the parent type and the child type (sub type included).

In our case, the code simply say: when a Host is created, invoke *addToServiceDefinition* passing the *FSMCategory* object as *parent* and the *Host* instead as child.

If we want to register for all child node types, we can do
{code}
builder.registerTypeHandler('FSMCategory', addToServiceDefinition);
{code}

The above code will execute *addToServiceDefinition* for any type of child node is created.

h3. Populating Data To Topology Objects

To retrieve the engine and start data collection:
{code}
def engine = getObservationSubmitterEngine();
engine.submit(TopologyObject object, String observationName, SampleSubmitter value, String group)
engine.submit(TopologyObject object, String observationName, SampleSubmitter value, Date executeAt, String group)
engine.submit(TopologyObject object, String observationName, SampleSubmitter value, long internval, String group)
engine.submit(TopologyObject object, String observationName, SampleSubmitter value, long internval, long expiresAfter, String group)
engine.submit(TopologyObject object, String observationName, SampleSubmitter value, long internval, Date expiresAt, String group)
engine.submit(TopologyObject object, String observationName, SampleSubmitter value, Recurrent schedule, String group)
{code}

* *object*: is the merged topology object. *Important*: Must not be a shell
* *observationName*: the name of the metric/observation
* *value*: A submitter. There are 4 builtin submitters. See below
* *internval*: the sample priod. Use to submit data at certain internval. *Caution*: if there are 2 schedules against one observation, the sample priod (in the data) will be set to *0*.
* *schedule*: a Foglight schedule.
* *group* (optional): Group that the task belongs to.

To cancel data submission
{code}
def engine = getObservationSubmitterEngine();
engine.cancel (TopologyObject object, String observationName)
{code}

To cancel all tasks belong to a group
{code}
def engine = getObservationSubmitterEngine();
engine.cancelGroup(String groupName);
{code}

To cancel all data submission
{code}
def engine = getObservationSubmitterEngine();
engine.cancelAll();
{code}

h4. Submitters

The engine has 4 builtin submitters that's ready to use.

h5. Constant Submitter

Submit 1 single value every time schedule/interval is triggered.
{code}
def engine = getObservationSubmitterEngine();
engine.createSubmitter(<the_constant>);
{code}

h5. Normal Distribution Submitter

Similar to Constant Submitter, however, this add some taste to the data so it makes the data looks more realistic. By providing an average number and a deviation, we can general sample data deviate from the average using the bell curve normal distribution.

{code}
def engine = getObservationSubmitterEngine();
engine.createSubmitter(<average>, <deviation>, <isAllowedNegative>);
//For example
engine.createSubmitter(1200, 100, false);
{code}

will generate data with ~70% chance within 1100 and 1300, 95% chance of data fall within 1000 and 1400 etc...

h5. Sequence Constant Submitter

This submitter create a list, and iteratively go over each item in the list and submit in order. When the last item is reached, roll back to the first one.

{code}
def engine = getObservationSubmitterEngine();
engine.createSubmitter([<item1>, <item2>, <itemN>...]);
{code}

h5. Closure Submitter

This is the most flexible submitter and it let user define what to submit. User provide a closure and each time schedule is due, it will trigger the closure and return the value.

{code}
def c = {info ->
	/*
	//	info.topologyObject: the current Topology Object
	//	info.observationName: the current observationName
	//	info.executionTime: the execution time
	*/

	statement
	...
	return <result>;
}
def engine = getObservationSubmitterEngine();
engine.createSubmitter(c);
{code}

h2. Putting All Together

This is an end to end example of how we can utilize both tools to simulate data submittion.

h3. Types

Let's start with some type definitions
{code}
<type name='ComputerComponent' extends='TopologyObject'>
	<property name='computer' type='Computer' is-identity='true' />
</type>
<type name='Computer' extends='ModelRoot'>
	<property name='name' type='String' is-identity='true' />
	<property name='monitor' type='Monitor' is-containment='true'/>
	<property name='keyboard' type='Keyboard' is-containment='true'/>
	<property name='mouse' type='Mouse' is-containment='true'/>
	<property name='case' type='ComputerCase' is-containment='true'/>
</type>
<type name='Monitor' extends='ComputerComponent'>
	<property name='make' type='String'/>
	<property name='size' type='Integer'/>
	<property name='refreshRate' type='Metric' is-containment='true'/>
</type>
<type name='Keyboard' extends='ComputerComponent'>
	<property name='numberOfKeys' type='Integer'/>
	<property name='color' type='String'/>
	<property name='typedRate' type='Metric' is-containment='true'/>
</type>
<type name='Mouse' extends='ComputerComponent'>
	<property name='clickedRate' type='Metric' is-containment='true'/>
</type>
<type name='ComputerCase' extends='ComputerComponent'>
	<property name='hardDrives' type='HardDrive' is-containment='true' is-many='true'/>
	<property name='rams' type='Ram' is-containment='true' is-many='true'/>
</type>
<type name='HardDrive' extends='ComputerComponent'>
	<property name='name' type='String' is-identity='true' />
	<property name='case' type='ComputerCase' is-identity='true' />
	<property name='spinRate' type='Metric' is-containment='true'/>
</type>
<type name='Ram' extends='ComputerComponent'>
	<property name='name' type='String' is-identity='true' />
	<property name='case' type='ComputerCase' is-identity='true' />
	<property name='busRate' type='Metric' is-containment='true'/>
</type>
{code}

h3. Create Instances of Computer

We already defined types, it's time to create some instances

{code}
def builder = getTopologyBuilder();
builder.Computer(name:"myComputer") {
	//keep a copy of the context

	def theComputer = builder.current;
	/*
		there are 2 contexts available:
		builder.current -> the current object inside the current {}
		builder.parent -> the 2nd upper level of {}

	*/

	_ monitor : Monitor(computer:theComputer, make:"Dell", size:44);
	_ keyboard : Keyboard (computer:theComputer, numberOfKeys:100, color:"yellow");
	_ mouse : Mouse(computer:theComputer);

	// when the property name is the same as Groovy/Java keywords, then turn it into a string
	_ 'case' : ComputerCase(computer:theComputer) {
		def theCase = builder.current;
		//theComputer == builder.parent in this case

		_ rams : [
			Ram (computer:theComputer, name:'ram1', 'case':theCase),
			Ram (computer:theComputer, name:'ram2', 'case':theCase),
			]

		//OR, this is another method to store the list.
		def hardDrives = [
			HardDrive (computer:theComputer, name:'hdd1', 'case':theCase),
			HardDrive (computer:theComputer, name:'hdd2', 'case':theCase),
		]
		//then using the old style getter/setter. Why not? :)
		theCase.set('hardDrives', hardDrives);

	}
}
def myComputer = builder.mergeObject();

{code}

After this, an "Computer" instance should be created

h4. Further code Simplification

h5. Passing Identity Values without Specifying the Property Name

Let's use an example above. When creating a computer, we do
{code}
def builder = getTopologyBuilder();
builder.Computer(name:"myComputer") {
	...
	...
}
{code}

In our case, *name* is the identify property of the computer, we can simplify the code to do this:
{code}
def builder = getTopologyBuilder();
builder.Computer("myComputer") {
	...
}
{code}

The builder will automatically match up the all the identity types with the values passed in. Should any 2 identities have the same type name, this feature will not work (due to ambiguity) and throw an exception.

If there are more than 1 identity values could be passed to the element, a list is needed. For example:
{code}
def builder = getTopologyBuilder();
builder.Ram (computer:theComputer, name:'ram1', 'case':theCase) {
	...
}
{code}

would become
{code}
def builder = getTopologyBuilder();
//builder.Ram (theComputer, 'ram1', theCase) will not work
builder.Ram ([theComputer, 'ram1', theCase]) {
	...
}
{code}

h5. Automatic Parent Lookup

The feature is *on* by default. This is a special case for passing identity values above, where the parents will be used as values. This is very helpful in the case where the structure if data is highly hierarchy. The idea bases on Topology identity properties: in the subsequent nested element, the builder will automatically find all parents that matches the required identity type. This process will be ignored if only a customized map is supplied to the element, but it does works if both a customized map and the identity values (without the parent) are supplied to the element. [comment 1|#comment_1]

For example:

{code}
def builder = getTopologyBuilder();
builder.Computer(name:"myComputer") {
	def theComputer = builder.current;
	_ monitor : Monitor(computer:theComputer, make:"Dell", size:44);
}
{code}

would first become

{code}
def builder = getTopologyBuilder();
builder.Computer(name:"myComputer") {
	def theComputer = builder.current;
	_ monitor : Monitor(theComputer) {
		_ make:"Dell";
		_ size: 44;
	}
}
{code}

then with *Automatic Parent Lookup* on, would become

{code}
def builder = getTopologyBuilder();
builder.Computer(name:"myComputer") {
	_ monitor : Monitor() { //the computer will be automatically passed to Monitor
		_ make:"Dell";
		_ size: 44;
	}
}
{code}

*Note*: only a customized map that passed to the Monitor will immediate turn off the feature for that element. For example
{code}
def builder = getTopologyBuilder();
builder.Computer(name:"myComputer") {
	_ monitor : Monitor(make:"Dell", size:44) { //this will not set Monitor.computer to the parent computer.
		...
	}
}
{code}

If we have a computer with dual monitors,  the Monitor type needs to be changed to have 2 identity properties : theComputer and identifier

{code}
<type name='Monitor' extends='ComputerComponent'>
	<property name='identifier' type='String'/>
	<property name='make' type='String'/>
	<property name='size' type='Integer'/>
	<property name='refreshRate' type='Metric' is-containment='true'/>
</type>
{code}

the Auto Parent Lookup feature still works in this way

{code}
def builder = getTopologyBuilder();
builder.Computer(name:"myComputer") {
	_ monitor : Monitor(make:"Dell", size:44, 'monitor_1') { // with a customized map and identity values
		...
	}
}
{code}


To turn off the feature, execute this call

{code}
builder.autoAssignParents(false);
{code}

h3. Simulate data submition

From the type definitions, each *ComputerComponent* instance has some sort of metric. We will then need to simulate these metric submission

{code}
def engine = getObservationSubmitterEngine();
//Create a constant submitter, which each time the schedule is due, it submits the same value
def submitterConstant = engine.createSubmitter(100);
//and this goes to the monitor's refreshRate
engine.submit(myComputer.get('monitor'), 'refreshRate', submitterConstant, 5000);

//Create a list submitter, which each time the schedule is due, it submits the value in the list in order
def submitterConstantList = engine.createSubmitter([60, 65, 59, 65, 60]);
//Average typing skill?
engine.submit(myComputer.get('keyboard'), 'typedRate', submitterConstantList, 5000);

//Create a average submitter, average=100, stdDev = 30, and when encoutering a negative number, do again
def submitterAverage = engine.createSubmitter(100, 30, false);
//100 clicks on average
engine.submit(myComputer.get('mouse'), 'clickedRate', submitterAverage, 5000);

def submitterClouserForHardDrives = engine.createSubmitter {info ->
	def result = 5400;
	/*
		Do something with result
		...
		...
	*/
	return result
}
myComputer.get('case/hardDrives').each {hardDrive ->
	//assign these 2 submitters to a group
	engine.submit(hardDrive, 'spinRate', submitterClouserForHardDrives, 5000, 'hardDrives');
}

return true;
{code}

After the code is executed, navigate to *Topology Submitter Engine* dashboard that comes with the toolkit, you will see something similar to 

!Task Manager.png!

From the UI, user can cancel certain execution task.

h4. Inline submitter

It is also possible combine object creation and data submission at once.

{code}
def builder = getTopologyBuilder();
builder.setSubmissionInternval(5000); //Change to 5 seconds, default is 5 mins (300000);
builder.setSubmissionGroup('computerSubmitter'); //Default is no group
builder.Computer(name:"myComputer") {
	//keep a copy of the context
	def theComputer = builder.current;
	/*
		there are 2 contexts available:
		builder.current -> the current object inside the current {}
		builder.parent -> the 2nd upper level of {}

	*/
	def engine = builder.engine;

	_ monitor : Monitor(computer:theComputer, make:"Dell", size:44) {
		_ refreshRate : engine.createSubmitter(100); //Closure style submitter
	};
	_ keyboard : Keyboard (computer:theComputer, numberOfKeys:100, color:"yellow", typedRate:engine.createSubmitter([60, 65, 59, 65, 60]));
	_ mouse : Mouse(computer:theComputer, clickedRate:engine.createSubmitter(100, 30, false)); //Inline style submitter

	// when the property name is the same as Groovy/Java keywords, then turn it into a string
	_ 'case' : ComputerCase(computer:theComputer) {
		def theCase = builder.current;
		//theComputer == builder.parent in this case

		_ rams : [
			Ram (computer:theComputer, name:'ram1', 'case':theCase),
			Ram (computer:theComputer, name:'ram2', 'case':theCase),
			]

		def submitterClouserForHardDrives = engine.createSubmitter {info ->
			def result = 5400;
			/*
				Do something with result
				...
				...
			*/
			return result
		}
		//OR, this is another method to store the list.
		def hardDrives = [
			HardDrive (computer:theComputer, name:'hdd1', 'case':theCase, spinRate:submitterClouserForHardDrives),
			HardDrive (computer:theComputer, name:'hdd2', 'case':theCase, spinRate:submitterClouserForHardDrives),
		]
		//then using the old style getter/setter. Why not? :)
		theCase.set('hardDrives', hardDrives);

	}
}
def myComputer = builder.mergeObject();
builder.startDataSubmission();
return true;
{code}

The example yields almost the same result (except for some grouping) as the previous examples.

h4. Comments
----
{anchor:comment_1}
(1) I'm not sure the reason why only the customized map can't be implemented, need to confirm with APM guys.
{anchor}
