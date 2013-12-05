require_relative 'archiver_proxy_data_post_helper'

include ArchiverProxyDataPostHelper

class ArchiverProxyCustomAppDataPoster

	def op
		'uploadcustomappdata'
	end

	def data_file_path
		'custom_app_data.json.erb'
	end

	def custom_data
		data = { :session_id => random_session_id }
		traces = []
		if (Time.now.to_i % 9 == 0) 
			puts 'generating long message...'
			traces = 10000.times.collect { |n|
				{ :key => "app#{n}" }
			}
		end
		data[:breakdown_traces] = traces 
		data
	end

end

if __FILE__ == $0
	ArchiverProxyCustomAppDataPoster.new('http://10.8.255.236:7630', 3).run()
end

