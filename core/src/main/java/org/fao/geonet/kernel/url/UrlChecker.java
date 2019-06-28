package org.fao.geonet.kernel.url;

import com.google.common.base.Function;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.impl.client.HttpClientBuilder;
import org.fao.geonet.domain.LinkStatus;
import org.fao.geonet.utils.GeonetHttpRequestFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.client.ClientHttpResponse;

import javax.annotation.Nullable;
import java.io.IOException;

public class UrlChecker {

    private static final Function<HttpClientBuilder, Void> HTTP_CLIENT_CONFIGURATOR = new Function<HttpClientBuilder, Void>() {
        @Nullable
        @Override
        public Void apply(@Nullable HttpClientBuilder originalConfig) {
            RequestConfig.Builder config = RequestConfig.custom()
                    .setConnectTimeout(1000)
                    .setConnectionRequestTimeout(3000)
                    .setSocketTimeout(5000);
            RequestConfig requestConfig = config.build();
            originalConfig.setDefaultRequestConfig(requestConfig);
            return null;
        }
    };

    @Autowired
    protected GeonetHttpRequestFactory requestFactory;

    public LinkStatus getUrlStatus(String url) {
        return getUrlStatus(url, 5);
    }

    private LinkStatus getUrlStatus(String url, int tryNumber) {
        if (tryNumber < 1) {
            return buildTooManyRedirectStatus();
        }

        try (ClientHttpResponse response = getResponseFromServer(url)) {
            if (response.getStatusCode().is3xxRedirection() && response.getHeaders().containsKey("Location")) {
                // follow the redirects
                return getUrlStatus(response.getHeaders().getFirst("Location"), tryNumber - 1);
            }
            return buildStatus(response);
        } catch (IOException e) {
            return buildIOExceptionStatus(e);
        }
    }

    private ClientHttpResponse getResponseFromServer(String url) throws IOException {
        HttpHead head = new HttpHead(url);
        ClientHttpResponse response = requestFactory.execute(head, HTTP_CLIENT_CONFIGURATOR);
        if (!shouldTryGetInsteadOfHead(response.getRawStatusCode())) {
            return response;
        }
        HttpGet get = new HttpGet(url);
        return requestFactory.execute(get);
    }

    private boolean shouldTryGetInsteadOfHead(int statusCode) {
        return  statusCode == HttpStatus.SC_BAD_REQUEST ||
                statusCode == HttpStatus.SC_METHOD_NOT_ALLOWED ||
                statusCode == HttpStatus.SC_INTERNAL_SERVER_ERROR;
    }

    private LinkStatus buildTooManyRedirectStatus() {
        LinkStatus linkStatus = new LinkStatus();
        linkStatus.setStatusValue("310");
        linkStatus.setStatusInfo("ERR_TOO_MANY_REDIRECTS");
        linkStatus.setFailing(true);
        return linkStatus;
    }

    private LinkStatus buildIOExceptionStatus(IOException e) {
        LinkStatus linkStatus = new LinkStatus();
        linkStatus.setStatusValue("4XX");
        linkStatus.setStatusInfo(e.getMessage());
        linkStatus.setFailing(true);
        return linkStatus;
    }

    private LinkStatus buildStatus(ClientHttpResponse response) throws IOException {
        LinkStatus linkStatus = new LinkStatus();
        linkStatus.setStatusValue(response.getRawStatusCode() + "");
        linkStatus.setStatusInfo(response.getStatusText());
        linkStatus.setFailing(!response.getStatusCode().is2xxSuccessful());
        return linkStatus;
    }
}
