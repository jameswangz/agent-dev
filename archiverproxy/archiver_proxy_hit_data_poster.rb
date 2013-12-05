require_relative 'archiver_proxy_data_post_helper'

include ArchiverProxyDataPostHelper

class ArchiverProxyHitDataPoster

	def op
		'uploadhitdata'
	end

	def data_file_path
		'hit_data.json.erb'
	end

	def custom_data
		{ :session_id => random_session_id }
	end

end

if __FILE__ == $0
	ArchiverProxyHitDataPoster.new('http://10.8.255.236:7630', 3).run()
end

