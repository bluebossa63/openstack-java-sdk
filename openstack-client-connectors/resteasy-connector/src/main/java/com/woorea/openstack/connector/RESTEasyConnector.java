package com.woorea.openstack.connector;

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.ext.ContextResolver;

import org.apache.commons.httpclient.HttpStatus;
import org.jboss.resteasy.client.ClientExecutor;
import org.jboss.resteasy.client.ClientRequest;
import org.jboss.resteasy.client.ClientResponse;
import org.jboss.resteasy.plugins.providers.InputStreamProvider;
import org.jboss.resteasy.plugins.providers.jackson.ResteasyJackson2Provider;
import org.jboss.resteasy.spi.ResteasyProviderFactory;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonRootName;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.jaxrs.json.JacksonJaxbJsonProvider;
import com.fasterxml.jackson.jaxrs.json.JacksonJsonProvider;
import com.woorea.openstack.base.client.OpenStackClientConnector;
import com.woorea.openstack.base.client.OpenStackRequest;
import com.woorea.openstack.base.client.OpenStackResponse;
import com.woorea.openstack.base.client.OpenStackResponseException;

public class RESTEasyConnector implements OpenStackClientConnector {

	public static ObjectMapper DEFAULT_MAPPER;

	public static ObjectMapper WRAPPED_MAPPER;
	
	public static ClientExecutor clientExecutor = ClientRequest.getDefaultExecutor();
	
	public RESTEasyConnector() {}
	
	public RESTEasyConnector(ClientExecutor ce) {
		clientExecutor = ce;
	}
	
	public static class OpenStackProviderFactory extends ResteasyProviderFactory {

		private JacksonJaxbJsonProvider jsonProvider;
		private InputStreamProvider streamProvider;
		

		public OpenStackProviderFactory(ClientExecutor ce) {
			this();
			clientExecutor=ce;
		}
		
		
		public OpenStackProviderFactory() {
			super();
			addContextResolver(new ContextResolver<ObjectMapper>() {
				public ObjectMapper getContext(Class<?> type) {
				return type.getAnnotation(JsonRootName.class) == null ? DEFAULT_MAPPER : WRAPPED_MAPPER;
				}
			});
			jsonProvider = new ResteasyJackson2Provider();

			addMessageBodyReader(jsonProvider);
			addMessageBodyWriter(jsonProvider);

			streamProvider = new InputStreamProvider();
			addMessageBodyReader(streamProvider);
			addMessageBodyWriter(streamProvider);
		}
	}

	private static OpenStackProviderFactory providerFactory;

	static {
		DEFAULT_MAPPER = new ObjectMapper();

		DEFAULT_MAPPER.setSerializationInclusion(Include.NON_NULL);
		DEFAULT_MAPPER.enable(SerializationFeature.INDENT_OUTPUT);
		DEFAULT_MAPPER.enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY);
		DEFAULT_MAPPER.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

		WRAPPED_MAPPER = new ObjectMapper();

		WRAPPED_MAPPER.setSerializationInclusion(Include.NON_NULL);
		WRAPPED_MAPPER.enable(SerializationFeature.INDENT_OUTPUT);
		WRAPPED_MAPPER.enable(SerializationFeature.WRAP_ROOT_VALUE);
		WRAPPED_MAPPER.enable(DeserializationFeature.UNWRAP_ROOT_VALUE);
		WRAPPED_MAPPER.enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY);
		WRAPPED_MAPPER.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
		
		providerFactory = new OpenStackProviderFactory();
	}

	public <T> OpenStackResponse request(OpenStackRequest<T> request) {
		
		//executor!!  ApacheHttpClient4Executor
		
		ClientRequest client = new ClientRequest(UriBuilder.fromUri(request.endpoint() + "/" + request.path()),
				clientExecutor, providerFactory);

		for(Map.Entry<String, List<Object> > entry : request.queryParams().entrySet()) {
			for (Object o : entry.getValue()) {
				client = client.queryParameter(entry.getKey(), String.valueOf(o));
			}
		}

		for (Entry<String, List<Object>> h : request.headers().entrySet()) {
			StringBuilder sb = new StringBuilder();
			for (Object v : h.getValue()) {
				sb.append(String.valueOf(v));
			}
			client.header(h.getKey(), sb);
		}

		if (request.entity() != null) {
			client.body(request.entity().getContentType(), request.entity().getEntity());
		}

		ClientResponse<T> response;

		try {
			response = client.httpMethod(request.method().name(), request.returnType());
		} catch (Exception e) {
			throw new RuntimeException("Unexpected client exception", e);
		}

		if (response.getStatus() == HttpStatus.SC_OK
				|| response.getStatus() == HttpStatus.SC_CREATED
				|| response.getStatus() == HttpStatus.SC_NO_CONTENT
				|| response.getStatus() == HttpStatus.SC_ACCEPTED) {
			return new RESTEasyResponse(client, response);
		}

		response.releaseConnection();

		throw new OpenStackResponseException(response.getResponseStatus()
				.getReasonPhrase(), response.getStatus());
	}

	public static OpenStackProviderFactory getProviderFactory() {
		return providerFactory;
	}

}
