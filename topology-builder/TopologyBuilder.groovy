package com.quest.nitro.lamp.tools;

import com.quest.nitro.model.topology.*;
import com.quest.nitro.service.sl.interfaces.topology.TopologyTypeNotFoundException;
import com.quest.nitro.service.sl.interfaces.scheduler.*;
import com.quest.nitro.service.sl.ServiceLocatorFactory;
import com.quest.nitro.service.sl.interfaces.schedule.*;

public class TopologyBuilder extends groovy.util.BuilderSupport {
	static org.apache.commons.logging.Log log = org.apache.commons.logging.LogFactory.getLog(TopologyBuilder.class);

	private Collection<TopologyObject> mMergeList = new LinkedList<TopologyObject>();
	private Deque<DataObject> mParents = new LinkedList<DataObject>();
	private boolean isMerged = false;
	final static topologyService = ServiceLocatorFactory.getLocator().getTopologyService();
	final static scriptingService = ServiceLocatorFactory.getLocator().getScriptingService();
	final static typeComparator = new Comparator<TopologyType>() {
		int compare(TopologyType type1, TopologyType type2) {
			return type1.isAssignableFrom(type2) ? 1 :(type2.isAssignableFrom(type1) ? -1 : 0); 
		}
		boolean equals(Object obj) {return this.equals(obj);}
	}

	private TopologyType topologyObjectType = topologyService.getType('TopologyObject');
	private TopologyType observationType = topologyService.getType('Observation');
	private TopologyType derivationExprTypeType = topologyService.getType('DerivationExprType');
	private TopologyType hostType = topologyService.getType('Host');
	private TopologyType stringType = topologyService.getType('String');

	private long mSubmissionInternval = 1000 * 60 * 15;
	private String mSubmissionGroup = null;
	private Map mSubmitterMap = [:];
	private List<Scheduled> mScheduledTasks = [];
	private Collection<TopologyObject> mPreferredReturnObjects = [] as Set;
	private Collection<TopologyObject> mPreferredReturnDataObjects = [] as Set;
	private Map<String, Closure> mTemplates = [:];
	private Map<String, Collection<String>> mIdentityProperties = [:];
	private boolean showDebugMessage = false;
	private boolean autoAssignParents = true;
	private boolean autoAssignProperties = true;
	private boolean autoReturnRootObjects = true;
	private boolean autoGenerateMetricSubmitters = false;
	private boolean allowChangeIdentityAfterSet = false;
	private boolean warnOnIncompletedIdentities = true;
	private Map mParentHandlers = new SortedHashMap();
	private List<Map> mCurrentHandlers = [];
	private Date mSubmissionStartTime = null;
	private String identityNameSalt = null;

	final static engine = invokeScript('getObservationSubmitterEngine');

	public TopologyBuilder () {
		super();
		mIdentityProperties.put(hostType.getName(), ['name']);
	}

	protected void setParent(Object parent, Object child) {
		if ((parent instanceof TopologyObject) && 
			(child instanceof TopologyObject) &&
			!(parent.equals(child))) {
			logMessage ("setParent(Object parent=${parent}, Object child=${child})");
			try {
				def getMapValueHelper = {m, t ->
					if (m?.size() > 0) {
						def matchedType = m.sortedKeys().find {typeName ->
							return topologyService.getType(typeName).isAssignableFrom(t);
						}
						return m.get(matchedType);
					}
				}

				def parentType = getValueType(parent).getType();
				def childType = getValueType(child).getType();

				def childMap = getMapValueHelper(mParentHandlers, parentType);
				def handler = getMapValueHelper(childMap, childType);
				if (handler) {
					synchronized (mCurrentHandlers) {
						if (!(mCurrentHandlers.contains(childMap))) {
							mCurrentHandlers.add(childMap);
							if (handler instanceof Closure) {
								logMessage("Invoking closure=${handler.toString()}");
								handler.call([parent, child]);
							} else if (handler instanceof String) {
								storeAttributes(parent, createSingleAttribute(handler, child));
							}
							mCurrentHandlers.remove(childMap);
						}
					}
				} else if (autoAssignProperties) {
					def properties = getClosestAssignFromProperties(parentType, childType);
					logMessage("${parentType.getName()}.properties=${properties.collect{p->p.getName()}} matches ${childType.getName()}");
					if (properties?.size() == 1) {
						def property = properties.iterator().next();
						storeAttributes(parent, createSingleAttribute(property.getName(), child));
					}
				}
			} catch (UnsupportedValueTypeException usvte) {
				logMessage ("Unsupported Type. Ignored.", usvte);
			}
		}
	}
	
	private Collection<TopologyProperty> getClosestAssignFromProperties (TopologyType parentType, TopologyType childType) {
		logMessage ("getClosestAssignFromProperties(TopologyType parentType=${parentType.getName()}, TopologyType childType=${childType.getName()})");
		def result = parentType.getPropertiesOfType(childType) ?: [];
		if (0 == result.size()) {
			def childSuperType = childType.getSuperType();
			if (null != childSuperType && !topologyObjectType.equals(childSuperType)) {
				result = getClosestAssignFromProperties(parentType, childSuperType);
			}
		}
		return result;
	}

	protected void nodeCompleted(Object parent, Object node) {
		logMessage("nodeCompleted(Object parent=${parent}, Object node=${node})");
		if (mParents.size() > 0) {
			mParents.pop();
		}
		if (autoReturnRootObjects && (mParents.size() == 0) && (null == parent) && (null != node)) {
			setReturnShell(node);
		}
		if (warnOnIncompletedIdentities && node instanceof DataObject && !(node.is(parent))) {
			def incompletedIdentities = getIdentityProperties(node.getType()).findAll {property ->
				return null == node.get(property);
			}
			if (incompletedIdentities.size() > 0) {
				log.warn ("Missing identity: ${incompletedIdentities.collect {p -> "${p.getName()}: ${p.getValueType().getName()}"}} for type ${node.getType().getName()}");
			}
		}
	}

	protected DataObject createNode(Object name) {
		logMessage ("createNode(Object name=${name})");
		def result = null
		if (!('call'.equals(name))) {
			result = createObject(name, [:]);
			if (autoAssignParents) {
				lookupParents(result);
			}
		}
		return result;
	}

	protected DataObject createNode(Object name, Object value) {
		logMessage ("createNode(Object name=${name}, Object value=${value})");
		try {
			topologyService.getType(name);
		} catch (TopologyTypeNotFoundException ttnfe) {
			if (getCurrent()?.getType()?.findProperty(name)) {
				return createNode('_', createSingleAttribute(name, value));
			}
		}
		return createNode(name, [:], value);
	}

	protected DataObject createNode(Object name, Map attributes) {
		logMessage ("createNode(Object name=${name}, Object attributes=${attributes})");
		def result = null;
		if ('_'.equals(name)) {
			result = getCurrent();
			storeAttributes(result, attributes);
		} else {
			result = createObject((String)name, attributes);
		}
		return result;
	}

	private DataObject getObjectShell(TopologyType type, Map attributes) {
		def identityProperties = getIdentityProperties(type);
		def shell = mMergeList.find {shell ->
			return type.equals(getValueType(shell)) && identityProperties.every {property ->
				def c = shell.get(property);
				def v = attributes.get(property.getName());
				return (null == c && null == v) || c.equals(v);
			}
		}
		if (null == shell) {
			logMessage("Creating a new shell of ${type.getName()}");
			if (topologyObjectType.isAssignableFrom(type)) {
				shell = topologyService.getObjectShell(type);
			} else {
				shell = topologyService.createAnonymousDataObject(type);
			}
			applyTemplate(shell);
		}
		return shell;
	}

	private void applyTemplate (DataObject shell) {
		def type = shell.getType().getName();
		def closure = mTemplates.get(type);
		if (null != closure) {
			removeTemplate(type);
			logMessage ("Applying template for newly created instance of ${type}...");
			def oldCurrent = getCurrent();
			setCurrent(shell);
			setClosureDelegate(closure, shell);
			closure.call();
			setCurrent(oldCurrent);
			addTemplate(type, closure);
		}
	}

	protected DataObject createNode(Object name, Map attributes, Object value) {
		logMessage ("createNode(Object name=${name}, Object attributes=${attributes}, Object value=${value})");
		if ('call'.equals(name)) {
			if (value instanceof TopologyObject) {
				if (isTopologyObjectShell(value)) {
					return value;
				} else {
					def result = topologyService.getObjectShell(value);
					storeAttributes(result, attributes);
					return result; 
				}
			} else if (value instanceof DataObject) {
				return value;
			}
			throw new IllegalArgumentException("Can't update ${value}".toString());
		} else {
			
			def type = topologyService.getType(name);

			def values = (value == null ? [] : (value instanceof Collection ? value : [value]));
			def properties = getIdentityProperties(type);
			if (properties.size() < values.size()) {
				throw new IllegalArgumentException("The size of the values (${values.size()}) used as identity are greater than then number of available identities (${properties.size()})");
			}
			if (autoAssignParents) {
				mParents.each {parent ->
					def hasObjectFromSameType = values.any {v ->
						 return getValueType(v).equals(parent.getType());
					}
					if (!hasObjectFromSameType) {
						values.add(parent); 
					}
				}
			}
			attributes = populateAttributes(properties, values, attributes);
			return createObject((String)name, attributes);
		}
	}
	private Map<String, Object> createSingleAttribute(String key, Object value) {
		def attributes = [:];
		attributes.put(key, value);
		return attributes;
	}
	private void lookupParents(DataObject dataObject) {
		if (mParents.size() > 0) {
			try {
				def properties = getIdentityProperties(dataObject.getType()).findAll {p ->
					return topologyObjectType.isAssignableFrom(p.getTopologyType());
				};
				def attributes = populateAttributes(properties, mParents);
				storeAttributes(dataObject, attributes);
			} catch (Exception ex) {
				logMessage("Unable to auto populate parents, ignored.", ex);
			}
		}
	}

	private Collection<TopologyProperty> getIdentityProperties(TopologyType type) {
		def result = mIdentityProperties.get(type.getName());
		if (null != result) {
			result = result.collect {pName ->
				return type.getProperty(pName);
			}
		} else {
			result = type.getIdentityProperties();
		}
		return result;
	}
	private boolean isIdentityProperty (TopologyProperty property) {
		def identityProperties = mIdentityProperties.get(property.getContainingType().getName());
		return identityProperties?.contains(property.getName()) || property.isIdentity();
	}
	private TopologyType getValueType (Object value) {
		if (value instanceof DataObject) {
			return value.getType();
		}
		try {
			return topologyService.getType(value.class.simpleName);
		} catch (TopologyTypeNotFoundException ttnfe) {
			throw new UnsupportedValueTypeException("Unsupported value: " + value, ttnfe)
		}
	}
	public Map<String, Object> populateAttributes (Collection<TopologyProperty> properties = [], Collection<Object> values = [], Map<String, Object> overriding = [:]) {
		logMessage ("Populate attribute map for properties=${properties.collect {p -> p.getName()}} from values=${values}, and using overriding=${overriding}");

		def isDistinctTypes = {types = [] ->
			def set = types as Set;
			return types.size() == set.size();
		};
		if (overriding?.size() > 0) {
			properties = properties.findAll { p ->
				return !(overriding.keySet().contains(p.getName()));
			}
		}
		def propertyTypes = properties.collect{p ->
			return p.getTopologyType();
		};
		def valueTypes = values.collect {v ->
			return getValueType(v);
		};
		if (isDistinctTypes(propertyTypes) && isDistinctTypes(valueTypes)) {
			def attributes = [:];

			values = new ArrayList(values);
			def pIterator = sortProperties(properties).iterator();
			while (pIterator.hasNext()) {
				def p = pIterator.next();
				def vIterator = values.iterator();
				while (vIterator.hasNext()) {
					def v = vIterator.next();
					if (p.getTopologyType().isAssignableFrom(getValueType(v))) {
						attributes.put(p.getName(), v);
						vIterator.remove();
					}
				}
			}
			attributes.putAll(overriding);
			logMessage ("Attribute populated ${attributes}");
			return attributes;
		}
		throw new IllegalArgumentException("Duplicated types");
	}

	private DataObject createObject (String type, Map attributes) {
		logMessage ("createNode(String type=${type}, Map attributes=${attributes}");
		def objectType = topologyService.getType(type);
		def result = getObjectShell(objectType, attributes);
		storeAttributes(result, attributes);
		addToMergeShellList(result);
		return result;
	}
	private isTopologyObjectShell(TopologyObject object) {
		return null == object.getUniqueId();
	}
	public Object invokeMethod(String methodName, Object args) {
		//logMessage ("invokeMethod(String methodName=${methodName}, Object args=${args})");
		return super.invokeMethod(methodName, args);
	}
	protected Object doInvokeMethod(String methodName, Object name, Object args) {
		//logMessage ("doInvokeMethod(String methodName=${methodName}, Object name=${name}, Object args=${args})");
		def current = getCurrent();
		if (null != current) {
			mParents.push(current);
		}
		return super.doInvokeMethod(methodName, name, args);
	}
	private Collection<TopologyProperty> sortProperties (Collection<TopologyProperty> properties = []) {
		return new ArrayList(properties).sort {p1, p2 ->
			def pType1 = p1.getTopologyType();
			def pType2 = p2.getTopologyType();
			return typeComparator.compare(pType1, pType2);
		}
		logMessage ("Sorted properties=${properties}");
	}
	private storeAttributes (DataObject object, Map attributes) {
		if (isMerged) {
			throw new IllegalStateException("Builder can't be reused after merged. Create a new builder.");
		}
		def objectType = object.getType();
		logMessage ("storeAttributes attributes=${attributes} to instance of ${objectType.getName()}");
		attributes?.each {k, v ->
			def property = objectType.findProperty(k);
			if (null == property) {
				log.warn("Unable to find property ${objectType.getName()}.${k}. Ignore storing this property.".toString());
			} else if (isDerivedProperty(property)) {
				log.warn("${objectType.getName()}.${property.getName()} is derived. Ignore storing this property.".toString());
			} else {
				if (observationType.isAssignableFrom(property.getTopologyType())) {
					if (!engine.isSubmitter(v)) {
						throw new IllegalArgumentException ("${v} must be instance of SampleSubmitter");
					} else {
						storeSubmitter(object, property, v);
					}
				} else {
					addToMergeShellList(v);
					if (null == v) {
						object.unset(property);
						object.markOverridingProperty(property);
					} else {
						if (property.isMany()) {
							if (!(v instanceof Collection)) {
								v = [v];
							}
							def values = object.get(property);
	
							v.each {value ->
								if (!values.contains(value)) {
									logMessage("Adding ${value} to ${objectType.getName()}.${property.getName()}");
									values.add(value);
								}
							}
						} else {
							def value = object.get(property);
							if (isIdentityProperty(property) && stringType.isAssignableFrom(property.getValueType())) {
								v = getSaltedStringName(v);
							}
							if (!v.equals(value)) {
								logMessage("Setting ${objectType.getName()}.${property.getName()}=${v}");
								if (!allowChangeIdentityAfterSet) {
									if (isIdentityProperty(property) && value) {
										throw new IllegalArgumentException("Identity property=${property.getName()} of type ${objectType.getName()} already been set to value=${value}. Can't change to new value=${v}");
									}
								}
								object.set(property, v);
							}
						}
					}
				}
			}
		}
	}
	private String getSaltedStringName(String value) {
		if (identityNameSalt && value) {
			return "${value} (${identityNameSalt})".toString();
		}
		return value;
	}
	private void storeSubmitter (shell, property, submitter) {
		logMessage("storeSubmitter ${property.getName()}=${submitter} to instance of ${shell.getType().getName()}");
		if (null == mSubmitterMap.get(shell)) {
			mSubmitterMap.put(shell, [:]);
		}
		mSubmitterMap.get(shell).put(property, submitter);
	}
	private List<TopologyObject> mergeData() {
		synchronized (isMerged) {
			if (!isMerged) {
				mPreferredReturnObjects = mPreferredReturnObjects.collect {preferredReturnObject ->
					if (preferredReturnObject instanceof TopologyObject && 
						!isTopologyObjectShell((TopologyObject)preferredReturnObject)) {
						preferredReturnObject = topologyService.getObjectShell(mPreferredReturnObject);
					}
					if (preferredReturnObject instanceof TopologyObject) {
						mMergeList.add(0, preferredReturnObject);
					}
					return preferredReturnObject;
				}
				logMessage("Starting to merge data...");
				
				def result = topologyService.mergeData(mMergeList);
				mMergeList.clear();
				result.each {topologyObject ->
					if (topologyObject && !mMergeList.contains(topologyObject)) {
						mMergeList.add(topologyObject);
				}
				}
				if (mPreferredReturnObjects.size() > 0) {
					logMessage("Setting mMergeList to preferredObjects ${mPreferredReturnObjects}...");
					mPreferredReturnObjects = topologyService.mergeData(mPreferredReturnObjects);
				} else {
					mPreferredReturnObjects = mMergeList;
				}
				mPreferredReturnObjects += mPreferredReturnDataObjects;

				(new ArrayList(mSubmitterMap.keySet())).each {shell ->
					def propertySubmitters = mSubmitterMap.remove(shell);
					def topologyObject = topologyService.mergeData(shell);
					if (topologyObject) {
						mSubmitterMap.put(topologyObject, propertySubmitters);
					}
				}
				logMessage("Data merged. Builder locked");
				isMerged = true;
			}
		}
		return mPreferredReturnObjects; 
	}
	private void addToMergeShellList (Object object) {
		if (object instanceof TopologyObject) {
			addToMergeShellList((TopologyObject) object);
		}
	}
	private void addToMergeShellList (TopologyObject topologyObject) {
		if (topologyObject && isTopologyObjectShell(topologyObject) && 
				!mMergeList.contains(topologyObject)) {
			mMergeList.add(topologyObject);
		}
	}
	public void addTemplate (String type, Closure c) {
		synchronized (mTemplates) {
			mTemplates.put (type, c);
		}
	}
	public void removeTemplate (String type) {
		synchronized (mTemplates) {
			mTemplates.remove (type);
		}
	}
	public void registerTypeHandler (String parentType, Closure c) {
		registerTypeHandler (parentType, (String)null, c);
	}
	public void registerTypeHandler (String parentType, String childType, Closure c) {
		logMessage("registerTypeHandler (String parentType=${parentType}, String childType=${childType}, Closure c=${c.toString()})");
		doRegisterTypeHandler(parentType, childType, c);
	}
	public void registerTypeHandler (String parentType, String property) {
		registerTypeHandler (parentType, (String)null, property);
	}
	public void registerTypeHandler (String parentType, String childType, String property) {
		logMessage("registerTypeHandler (String parentType=${parentType}, String childType=${childType},  String property=${property})");
		doRegisterTypeHandler(parentType, childType, property);
	}
	synchronized private void doRegisterTypeHandler (String parentType, String childType, Object handler) {
		def childMap = mParentHandlers.get(parentType);
		if (null == childMap) {
			childMap = new SortedHashMap();
			mParentHandlers.put(parentType, childMap);
		}
		childMap.put(childType, handler);
	}
	public TopologyObject mergeObject() {
		def objects = mergeObjects();
		return objects?.size() > 0 ? objects.first() : null;
	}
	public List<TopologyObject> mergeObjects() {
		mergeData();
		return mPreferredReturnObjects.asImmutable();
	}
	public Object build() {
		def objects = mergeObjects();
		if (!(mScheduledTasks.size() > 0)) {
			startDataSubmission();
		}
		if (null == objects && objects.size() == 0) {
			return null;
		} else if (objects.size() == 1) {
			return objects.first();
		}
		return objects;
	}
	synchronized public void startDataSubmission() {
		if (!isMerged) {
			throw new IllegalStateException("Unable to start data submission if the objects are not merged.");
		}
		if (mScheduledTasks.size() > 0) {
			throw new IllegalStateException("Another batch of data submission is in place. Stop all submission first");
		}
		def submissionStartTime = mSubmissionStartTime;
		if (isHistoricDataSubmission()) {
			submissionStartTime = null;
		}
		if (autoGenerateMetricSubmitters) {
			generateMetricSubmitters();
		}

		mSubmitterMap.each {topologyObject, propertySubmitters ->
			propertySubmitters.each {property, submitter ->
				def task = engine.submit(topologyObject, property.getName(), submitter, mSubmissionInternval, (Date)submissionStartTime ?: new Date(), (Date)null, (String)mSubmissionGroup);
				mScheduledTasks.add(task);
			}
		}
		startHistoricDataSubmission();
		logMessage("Data Submission started.");
	}
	private void startHistoricDataSubmission() {
		if (isHistoricDataSubmission()) {
			logMessage("Historic data submission started.");
			def submissionStartTime = mSubmissionStartTime.getTime();
			def historicStartTime = System.currentTimeMillis() - mSubmissionInternval;
			while(submissionStartTime < historicStartTime) {
				def historicStartDate = new Date(historicStartTime);
				def historicEndDate = new Date(historicStartTime + mSubmissionInternval);
				logMessage("Submitting data for period ${historicStartDate}-${historicEndDate}");
				historicStartTime = historicStartTime - mSubmissionInternval;
			}
		}
	}
	synchronized public void stopDataSubmission() {
		mScheduledTasks.each {scheduledTask ->
			engine.cancel(scheduledTask);
		}
		mScheduledTasks.clear();
	}
	public void setSubmissionInternval(long submissionInternval) {
		this.mSubmissionInternval = submissionInternval;
	}
	public void setSubmissionGroup(String submissionGroup) {
		this.mSubmissionGroup = submissionGroup;
	}
	public DataObject getParent() {
		return mParents.peekLast();
	}
	public void setReturnShell () {
		def object = getCurrent();
		if (object) {
			setReturnShell(object);
		}
	}
	public void setReturnShell (DataObject object) {
		assert object != null;
		mPreferredReturnDataObjects.add(object);
	}
	public void setReturnShell (TopologyObject object) {
		assert object != null;
		mPreferredReturnObjects.add(object);
	}
	public void showDebugMessage(boolean showDebugMessage) {
		this.showDebugMessage = showDebugMessage; 
	}
	private void logMessage(String message, Throwable e = null) {
		if (showDebugMessage) {
			log.info(message, e);
		}
	}
	public void autoAssignParents(boolean autoAssignParents) {
		this.autoAssignParents = autoAssignParents;
	}
	public void autoAssignProperties(boolean autoAssignProperties) {
		this.autoAssignProperties = autoAssignProperties;
	}
	public void autoReturnRootObjects(boolean autoReturnRootObjects) {
		this.autoReturnRootObjects = autoReturnRootObjects;
	}
	public void autoGenerateMetricSubmitters(boolean autoGenerateMetricSubmitters) {
		this.autoGenerateMetricSubmitters = autoGenerateMetricSubmitters;
	}
	public void allowChangeIdentityAfterSet(boolean allowChangeIdentityAfterSet) {
		this.allowChangeIdentityAfterSet = allowChangeIdentityAfterSet;
	}
	public void warnOnIncompletedIdentities(boolean warnOnIncompletedIdentities) {
		this.warnOnIncompletedIdentities = warnOnIncompletedIdentities;
	}
	public void setIdentityNameSalt(String identityNameSalt) {
		this.identityNameSalt = identityNameSalt;
	}
	
	public void setSubmissionStartTime(Date submissionStartTime) {
		this.mSubmissionStartTime = submissionStartTime;
	}
	private boolean isHistoricDataSubmission () {
		if (mSubmissionStartTime) {
			return (mSubmissionStartTime.getTime() + (3 * mSubmissionInternval)) < System.currentTimeMillis();
		}
		return false;
	}
	public Collection<TopologyObject> getAllShells() {
		if (isMerged) {
			throw new IllegalStateException("Objects already merged");
		}
		return mMergeList.asImmutable();
	}
	public Collection<TopologyObject> getPreferredShells() {
		if (isMerged) {
			throw new IllegalStateException("Objects already merged");
		}
		return mPreferredReturnObjects.asImmutable();
	}
	public Object getProperty(DataObject object, String path) {
		return getProperty([object], path);
	}
	public Object getProperty(List<DataObject> objects, String path) {
		def result = [];
		if (path) {
			def paths = path.split('/') as List;
			result = getPropertyHelper(objects, paths);
		}
		if (result.size() == 1) {
			result = result.first();
		}
		return result;
	}
	private Object getPropertyHelper(List<DataObject> objects, List<String> paths) {
		def result = [];
		if (null == paths || paths?.size() == 0) {
			return objects;
		}
		else {
			result = objects.collect {object ->
				return object.get(paths[0]);
			}.flatten().findAll {value ->
				return null != value;
			};
			if (paths?.size() > 1) {
				result = getPropertyHelper(result, paths[1..paths.size() - 1]);
			}
		}
		return result;
	}
	private Object generateMetricSubmitter (TopologyType type, TopologyProperty property) {
		logMessage("Generating submitter for ${type.getName()}.${property.getName()}");
		if ('percent'.equals(property.getUnitName())) {
			return engine.createSubmitter(50, 10, 0, 100);
		}
		return engine.createSubmitter(100, 50);
	}
	private boolean isDerivedProperty (TopologyProperty property) {
		return property.isDerived() || null != property.getAnnotationValue(derivationExprTypeType);
	}
	private void generateMetricSubmitters () {
		mMergeList.each {topologyObject ->
			def type = topologyObject.getType();
			def metricType = topologyService.getType('Metric');
			def ignoredProperties = invokeScript('getIgnoredPropertiesByType', [type.getName()]);
			
			type.getPropertiesOfType(metricType).each {p ->
				if (null == mSubmitterMap.get(topologyObject)?.get(p) && 
					!isDerivedProperty(p) && 
					!ignoredProperties.contains(p.getName())) {
					def submitter = generateMetricSubmitter(type, p);
					storeSubmitter (topologyObject, p, submitter);
				}
			}
		}
	}
	private class UnsupportedValueTypeException extends TopologyTypeNotFoundException {}
	private class SortedHashMap extends HashMap {
		public List sortedKeys () {
			return keySet().findAll{key ->
				return null != key;
			}.sort(typeComparator);
		}
	}
	private static final Object invokeScript(String scriptName,Collection<Object> args = []) {
		try {
			def scriptInvocation = scriptingService.prepareNamedScript(scriptName).invocation();
			scriptInvocation.addArgs(args);
			return scriptingService.invoke(scriptInvocation);
		} catch (Exception e) {
			logMessage("Exception when invoking ${scriptName} with ${args}")
			throw e;
		}

	}
}
