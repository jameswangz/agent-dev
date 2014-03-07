package user._foglight.scripts;

import java.util.concurrent.Callable
import java.security.PrivilegedExceptionAction

def tm =  server.TenantManager

tenants = tm.getAllTenants()

def tenants2BeCreated = [
	[name: 'RealPage', desc: 'RealPage Tenant'],
	[name: 'Telefloria', desc: 'Telefloria Tenant']
]

tenants2BeCreated.each { newTenant ->
	if (!tenants.any { it.name == newTenant['name']  }) {
		tm.createTenant(newTenant['name'], newTenant['desc'])		
	}	
}

def realPageTenant = tm.getAllTenants().find { it.name == 'RealPage' }

/*
def subjectLookup = server.SubjectLookup
def subject = subjectLookup.getUser('foglight')

Subject.doAs(subject, new PrivilegedExceptionAction<Void>() {
		public Void run() throws Exception {
			tm.runAs(realPageTenant, new Callable<Object>() {
				public Object call() throws Exception {
					def qs = server.QueryService
					qs.queryTopologyObjects("!EUHitInteraction")
				}
			})
		}	
	}
)
*/

/*
tm.runAs(realPageTenant, new Callable<Object>() {
	public Object call() throws Exception {
		def qs = server.QueryService
		qs.queryTopologyObjects("!EUHitInteraction")
	}
})
*/

tm.runAs(realPageTenant, new Callable<Object>() {
	public Object call() throws Exception {
		def qs = server.QueryService
		qs.queryTopologyObjects("!EUHitInteraction")
	}
})

tm.getAllTenants()
