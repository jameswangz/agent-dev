require 'uri'
require 'net/http'

class ExampleAppClient
	
	def initialize(endpoint)
		@endpoint = endpoint
		@uri = URI(@endpoint)
		@http = Net::HTTP.new @uri.host, @uri.port
	end

	def run
		cookie = login
		while true do
			begin
				['/appfuse/admin/users', '/appfuse/userform', '/appfuse/admin/activeUsers'].each do |path|
					res = @http.request_get(path, 'Cookie' => cookie) 
					puts "Accessed #{path}" 
				end
			rescue Exception => e
				puts e
			end
			sleep 1 
		end
	end

	def login
		res  = @http.get '/appfuse/login'
		cookie = session_cookie_of res
		puts cookie

		headers = {
			'Cookie' => cookie,
			'Content-Type' => 'application/x-www-form-urlencoded' 
		}
		res = @http.post('/appfuse/j_security_check', 'j_username=admin&j_password=admin', headers)
		cookie = session_cookie_of res
		puts cookie
		cookie
	end
	
	def session_cookie_of(res)
		res.response['set-cookie'].split("; ")[0]
	end


end

if __FILE__ == $0
	ExampleAppClient.new('http://10.8.255.210:8080').run
end

