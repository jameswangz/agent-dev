require 'uri'
require 'net/http'

class ExampleAppClient
	
	def initialize(endpoint)
		@endpoint = endpoint
		@uri = URI(@endpoint)
		@http = Net::HTTP.new @uri.host, @uri.port
	end

	def run
		while true do
			begin
				['/javaee-sample/jdbcandjmsqueue', '/javaee-sample/jdbcandjmstopic'].each do |path|
					res = @http.request_get path
					puts "Accessed #{path}, response : #{res.body}" 
				end
			rescue Exception => e
				puts e
			end
			sleep 1 
		end
	end


end

if __FILE__ == $0
	ExampleAppClient.new('http://10.8.255.210:8080').run
end

