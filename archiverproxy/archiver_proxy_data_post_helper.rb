require 'uri'
require 'net/http'
require 'erb'

module ArchiverProxyDataPostHelper
	
		
	def initialize(endpoint, thread_count)
		@endpoint = endpoint
	   	@thread_count = thread_count
	end

	def run
		threads = []
		@thread_count.times do
			threads << Thread.new do
				while true do
					uri = URI.parse @endpoint
					http = Net::HTTP.new(uri.host, uri.port)
					request = Net::HTTP::Post.new("/archiverProxy?op=#{op}", initheader = { 'Content-Type' => 'application/json; charset=utf-8' })
					request.body = body
					response = http.request(request)
					puts "#{Thread.current} : #{op} : #{response.body}"
					#sleep 1
				end
			end
		end
		threads.each { |t| t.join }
	end

	def random_session_id
		('a'..'z').to_a.shuffle[0..7].join
	end

	def body
		template = IO.read(File.join(File.dirname(__FILE__), data_file_path))
		ERB.new(template).result(DataBinding.new(custom_data).data)
	end


	class DataBinding
			
			def initialize(data)
				@data = data		
			end
		
			def data
				binding
			end
	end

end
