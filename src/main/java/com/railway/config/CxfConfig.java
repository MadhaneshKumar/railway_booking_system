package com.railway.config;

import com.railway.endpoint.BookingEndpoint;
import jakarta.xml.ws.Endpoint;
import org.apache.cxf.Bus;
import org.apache.cxf.jaxws.EndpointImpl;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.List;

@Configuration
public class CxfConfig {

    /** Publishes the SOAP service at /ws/booking (WSDL at /ws/booking?wsdl). */
    @Bean
    public Endpoint bookingEndpoint(Bus bus, BookingEndpoint impl) {
        EndpointImpl endpoint = new EndpointImpl(bus, impl);
        endpoint.publish("/booking");
        return endpoint;
    }

    /** CORS as a servlet filter, because the CXF servlet is not covered by Spring MVC CORS. Restrict origins in production. */
    @Bean
    public FilterRegistrationBean<CorsFilter> corsFilter() {
        CorsConfiguration c = new CorsConfiguration();
        c.setAllowedOriginPatterns(List.of("*"));
        c.addAllowedHeader("*");
        c.addAllowedMethod("*");
        UrlBasedCorsConfigurationSource src = new UrlBasedCorsConfigurationSource();
        src.registerCorsConfiguration("/**", c);
        FilterRegistrationBean<CorsFilter> bean = new FilterRegistrationBean<>(new CorsFilter(src));
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return bean;
    }
}
