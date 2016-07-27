package com.thinkbiganalytics.rest.exception.nifi;

import com.thinkbiganalytics.nifi.rest.client.NifiComponentNotFoundException;
import com.thinkbiganalytics.rest.exception.BaseExceptionMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;

import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

/**
 * Created by sr186054 on 6/20/16.
 */
@Provider
@Configuration
public class NifiComponentNotFoundExceptionMapper extends BaseExceptionMapper implements ExceptionMapper<NifiComponentNotFoundException> {

    private static final Logger log = LoggerFactory.getLogger(NifiComponentNotFoundExceptionMapper.class);

    @Override
    public Response toResponse(NifiComponentNotFoundException e) {
        return defaultResponse(e);
    }

}