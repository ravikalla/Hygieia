package com.capitalone.dashboard;

import java.net.URI;
import java.net.URISyntaxException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.rallydev.rest.RallyRestApi;

public class RestApiFactory {
	private static final Logger LOGGER = LoggerFactory.getLogger(RestApiFactory.class);

    //Specify your Rally server
    private static final String SERVER = "https://rally1.rallydev.com";

    //Specify your WSAPI version
    private static final String WSAPI_VERSION = "v2.0";

    //Specify your Rally username
    private static final String USERNAME = "";

    //Specify your Rally password
    private static final String PASSWORD = "";

    //Specify your Rally api key
    private static final String API_KEY = "_Hj885bgiTd6R3xBBYpK7dHRTn2xZz0Zdk12OxyI7wyY";

    //If using a proxy specify full url, like http://my.proxy.com:8000
    private static final String PROXY_SERVER = null;

    //If using an authenticated proxy server specify the username and password
    private static final String PROXY_USERNAME = null;
    private static final String PROXY_PASSWORD = null;

    public static RallyRestApi getRestApi() {
        RallyRestApi restApi = null;
        try {
        if(API_KEY != null && !API_KEY.equals("")) {
            restApi = new RallyRestApi(new URI(SERVER), API_KEY);
        } else {
            restApi = new RallyRestApi(new URI(SERVER), USERNAME, PASSWORD);
        }
        if (PROXY_SERVER != null) {
            URI uri = new URI(PROXY_SERVER);
            if (PROXY_USERNAME != null) {
                restApi.setProxy(uri, PROXY_USERNAME, PROXY_PASSWORD);
            } else {
                restApi.setProxy(uri);
            }
        }

        restApi.setWsapiVersion(WSAPI_VERSION);
        } catch (URISyntaxException e) {
        	LOGGER.error("56 : RestApiFactory.getRestApi() : URISyntaxException e: " + e);
        }
        return restApi;
    }
}
