/*
 * Copyright 2017 Artem Labazin <xxlabaza@gmail.com>.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.xxlabaza.luntic.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import feign.Feign;
import feign.Headers;
import feign.Param;
import feign.RequestLine;
import feign.Response;
import feign.Retryer.Default;
import feign.jackson.JacksonDecoder;
import feign.jackson.JacksonEncoder;
import feign.okhttp.OkHttpClient;
import java.util.List;
import java.util.Map;

/**
 *
 * @author Artem Labazin <xxlabaza@gmail.com>
 * @since Jul 24, 2017
 */
interface Api {

    @RequestLine("POST /{group}")
    @Headers("Accept: application/json")
    Response create (@Param("group") String group);

    @RequestLine("POST /{group}")
    @Headers({
        "Accept: application/json",
        "Content-Type: application/json"
    })
    Response create (@Param("group") String group, Map<String, Object> meta);

    @RequestLine("GET /{group}")
    @Headers("Accept: application/json")
    List<Instance> getByGroup (@Param("group") String group);

    @RequestLine("GET /{group}/{id}")
    @Headers("Accept: application/json")
    Instance getByGroupAndId (@Param("group") String group, @Param("id") String id);

    @RequestLine("GET /")
    @Headers("Accept: application/json")
    Map<String, List<Instance>> getAll ();

    @RequestLine("PUT /{group}/{id}")
    @Headers("Accept: application/json")
    Instance update (@Param("group") String group, @Param("id") String id);

    @RequestLine("PUT /{group}/{id}")
    @Headers({
        "Accept: application/json",
        "Content-Type: application/json"
    })
    Instance update (@Param("group") String group, @Param("id") String id, Map<String, Object> meta);

    @RequestLine("DELETE /{group}/{id}")
    void delete (@Param("group") String group, @Param("id") String id);

    static Api connect (String url, ObjectMapper mapper) {
        if (!url.startsWith("http")) {
            url = "http://" + url;
        }
        return Feign.builder()
                .client(new OkHttpClient())
                .encoder(new JacksonEncoder())
                .decoder(new JacksonDecoder(mapper))
                .decode404()
                .retryer(new Default())
                .errorDecoder(new ErrorDecoder())
                .target(Api.class, url);
    }
}
