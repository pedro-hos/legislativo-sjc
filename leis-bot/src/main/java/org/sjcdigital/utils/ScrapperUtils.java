/**
 * 
 */
package org.sjcdigital.utils;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.CookieHandler;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import javax.enterprise.context.ApplicationScoped;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jsoup.Connection.Method;
import org.jsoup.Connection.Response;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

/**
 * @author pedro-hos@outlook.com
 *
 */
@ApplicationScoped
public class ScrapperUtils {
	
	@ConfigProperty(name = "scrapper.agent")
	String agent;
	
	@ConfigProperty(name = "scrapper.timeout")
	int timeout;
	
	@ConfigProperty(name = "location.files")
	String path;
	
	/**
	 * 
	 * @param url
	 * @return
	 * @throws IOException
	 */
	public Document getDocument(final String url) throws IOException {
		return getResponse(url).parse();
	}
	
	/**
	 * 
	 * @param url
	 * @return
	 * @throws IOException
	 */
	public Response getResponse(final String url) throws IOException {
		
		Response response = Jsoup.connect(url)
								.userAgent(agent)
								.timeout(timeout)
								.method(Method.GET)
								.followRedirects(true)
								.execute();
		return response;
	}
	
	/**
	 * 
	 * @param url
	 * @param parameters
	 * @param cookies
	 * @return 
	 * @return
	 * @throws IOException
	 * @throws URISyntaxException 
	 * @throws InterruptedException 
	 */
	public Path downloadCSV(final String url, Map<String, String> parameters, Map<String, String> cookies) throws IOException, URISyntaxException, InterruptedException {
		
		List<String> cookiesList = new ArrayList<>();
		cookies.forEach((k,v) -> cookiesList.add(k + "=" + v));
		
		ConcurrentHashMap<String, List<String>> cookieHeaders = new ConcurrentHashMap<>();
		cookieHeaders.put("Cookie", cookiesList);
		
		CookieHandler cookieManager = new MyCookieHandler(cookieHeaders);
		
		HttpClient client = HttpClient.newBuilder()
	            					  .version(HttpClient.Version.HTTP_2)
	            					  .cookieHandler(cookieManager)
	            					  .followRedirects(HttpClient.Redirect.NEVER)
	            					  .build();
		
		HttpRequest request = HttpRequest.newBuilder(new URI(url))
										 .timeout(Duration.ofSeconds(100))
	            						 .version(HttpClient.Version.HTTP_2)
	            						 .header("Content-Type", "application/x-www-form-urlencoded; charset=utf-8")
	            						 .header("Accept", "application/CSV")
	            						 .POST(BodyPublishers.ofString(getDataString(parameters)))
	            						 .build();
		
		HttpResponse<Path> response = client.send(request, BodyHandlers.ofFileDownload(Paths.get(path), StandardOpenOption.CREATE, StandardOpenOption.WRITE));
	        
		return response.body();
		
	}
	
	private String getDataString(Map<String, String> params) throws UnsupportedEncodingException{
		return params.keySet().stream()
							  .map(k -> k + "=" + URLEncoder.encode(params.get(k), StandardCharsets.UTF_8))
							  .collect(Collectors.joining("&"));
	}
	
	static class MyCookieHandler extends CookieHandler {

		final ConcurrentHashMap<String, List<String>> cookies;

		MyCookieHandler(ConcurrentHashMap<String, List<String>> map) {
			this.cookies = map;
		}

		@Override
		public Map<String, List<String>> get(URI uri, Map<String, List<String>> requestHeaders) throws IOException {
			return cookies;
		}

		@Override
		public void put(URI uri, Map<String, List<String>> responseHeaders) throws IOException { }
	}

}
