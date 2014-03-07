require 'uri'
require 'net/http'

class PostCreator

	def initialize(endpoint)
		@uri = URI(endpoint)
		@http = Net::HTTP.new @uri.host, @uri.port
	end

	def create
		headers = {
			'Content-Type' => 'application/x-www-form-urlencoded' 
		}
		res = @http.post('/posts', 'post[title]=rubyclient&post[content]=rubycontent', headers)
		puts res
	end

end

if __FILE__ == $0
	PostCreator.new('http://10.8.255.210:3000').create
end
